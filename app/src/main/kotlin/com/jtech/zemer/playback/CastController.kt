package com.jtech.zemer.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM
import androidx.media3.common.Player.REPEAT_MODE_ONE
import com.jtech.zemer.extensions.metadata
import com.jtech.zemer.utils.reportException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.fcast.sender_sdk.PlaybackState

/**
 * Owns the FCast **cast control plane** — end-of-track auto-advance (its three detectors), the
 * track-change reload of the receiver, and disconnect recovery. It lives on [MusicService]
 * (process-scoped) rather than the Activity-scoped [PlayerConnection], so a cast session keeps
 * advancing through its queue even after the UI Activity is destroyed. [PlayerConnection] delegates its
 * few cast hooks here (queue-start bookkeeping, markRemoteLoaded, advanceRemoteAfterEnd) and drives no
 * cast logic of its own.
 *
 * Everything runs on [scope] (the service's Main scope), so the cast-tracking fields are confined to a
 * single thread and never need `@Volatile` — including the [FCastDiscoveryHandler.onDisconnect] callback,
 * which the SDK invokes from a native thread and which hops onto [scope] before touching them.
 */
class CastController(
    private val service: MusicService,
    private val scope: CoroutineScope,
) {
    private val player get() = service.player
    private val handler get() = service.discoveryHandler

    private val casting: Boolean get() = handler.isConnected

    private var lastTransitionTime = 0L
    private var lastRemotePosition = 0.0
    private var lastRemoteTimeUpdateAt = System.currentTimeMillis()
    private var remoteLoadedMediaId: String? = null
    private var remoteLoadJob: Job? = null

    init {
        // Recover the LOCAL player to the last remote position (paused) on disconnect, so the user can
        // resume on the phone, and reset tracking so a later reconnect/new track doesn't auto-skip on
        // stale near-end state. Invoked from the SDK's native callback thread — hop onto [scope] (main).
        handler.onDisconnect = { lastRemotePosMs ->
            scope.launch {
                lastRemotePosition = 0.0
                lastRemoteTimeUpdateAt = System.currentTimeMillis()
                lastTransitionTime = System.currentTimeMillis()
                remoteLoadedMediaId = null
                player.seekTo(lastRemotePosMs)
                player.prepare()
                player.playWhenReady = false
            }
        }

        scope.launch {
            handler.remoteTime.collect { time ->
                // Track unconditionally: connectTo()/load() reset remoteTime to 0 for a new track, and that
                // 0 must clear a previous track's near-end position — otherwise a fresh connect or a device
                // switch leaves the stall detector comparing the new track's duration against the old
                // track's near-end position and spuriously auto-skipping. nearEnd(dur, 0) is false, so a
                // genuine mid-track 0 is harmless.
                lastRemotePosition = time
                lastRemoteTimeUpdateAt = System.currentTimeMillis()
            }
        }
        scope.launch {
            var lastState = handler.remotePlaybackState.value
            handler.remotePlaybackState.collect { state ->
                val dur = handler.remoteDuration.value
                val pos = handler.remoteTime.value
                // End-of-track shows up differently across receivers and never as one reliable signal:
                //  - IDLE coming from PLAYING (generous window — a coarse clock may stop reporting early), or
                //  - PAUSED coming from PLAYING at pos==duration (some receivers auto-pause at the end instead
                //    of going IDLE or sending an END event; the TIGHT epsilon separates it from a mid-track
                //    pause). The debounce inside advanceRemoteAfterEnd stops double-advancing.
                val endedIdle = state == PlaybackState.IDLE && CastAutoAdvance.finishedNearEnd(dur, pos)
                val endedPause = state == PlaybackState.PAUSED &&
                    CastAutoAdvance.nearEnd(dur, pos, CastAutoAdvance.PAUSED_END_EPSILON_SEC)
                if (casting && lastState == PlaybackState.PLAYING && (endedIdle || endedPause)) {
                    advanceRemoteAfterEnd()
                }
                lastState = state
            }
        }
        scope.launch {
            // Only poll for a stalled remote clock while actually casting (no 1 Hz wakeup otherwise);
            // collectLatest cancels the loop the moment the connection state changes.
            handler.remoteConnectionState.collectLatest {
                while (casting) {
                    delay(1000)
                    val dur = handler.remoteDuration.value
                    val stalledFor = System.currentTimeMillis() - lastRemoteTimeUpdateAt
                    // A deliberately PAUSED track also freezes the remote clock — never treat that as a
                    // finished track, or pausing near the end would silently auto-skip it. nearEnd reads the
                    // *interpolated* clock so a coarse clock that stopped reporting still reaches the end.
                    if (!CastPlayback.isPaused(handler.remotePlaybackState.value) &&
                        CastAutoAdvance.nearEnd(dur, handler.interpolatedRemoteTimeSec(), CastAutoAdvance.STALL_END_EPSILON_SEC) &&
                        CastAutoAdvance.stalled(stalledFor)
                    ) {
                        advanceRemoteAfterEnd()
                    }
                }
            }
        }
    }

    /**
     * Records the media id the receiver is currently playing (set when the picker connects and loads the
     * current item) so the PLAYLIST_CHANGED de-dup in [onMediaItemTransition] doesn't redundantly reload
     * the just-connected track.
     */
    fun markRemoteLoaded(mediaId: String?) {
        remoteLoadedMediaId = mediaId
    }

    /**
     * Cast bookkeeping when a new queue starts while casting: pause local immediately (the new queue's
     * first item then fires PLAYLIST_CHANGED, which loads the receiver), record the play intent, and
     * force the upcoming reload even if the first track id matches what's already loaded.
     */
    fun onPlayQueueWhileCasting() {
        player.pause()
        handler.shouldPlay = true
        remoteLoadedMediaId = null
    }

    /**
     * Advance the receiver at end-of-track: repeat-one replays the current item, otherwise skip to the
     * next (if any). Shared by all three end detectors — the SDK END event, the IDLE-after-PLAYING
     * collector, and the stall poll. Serialised on [scope] so they can't double-advance; the debounce is
     * stamped only when an advance actually happens (a no-op end report on the last track must not burn
     * the window against a later real event).
     */
    fun advanceRemoteAfterEnd() {
        scope.launch {
            if (!CastAutoAdvance.debouncePassed(System.currentTimeMillis(), lastTransitionTime)) return@launch
            // We're advancing because a track finished while playing, so the next one should play — re-assert
            // the intent in case the end was signalled as PAUSED (a receiver auto-pausing at pos==duration).
            handler.shouldPlay = true
            if (player.repeatMode == REPEAT_MODE_ONE) {
                lastTransitionTime = System.currentTimeMillis()
                player.seekTo(player.currentMediaItemIndex, 0)
                triggerRemoteLoad(player.currentMediaItem)
            } else if (canSkipNext()) {
                lastTransitionTime = System.currentTimeMillis()
                // Skip locally; the resulting media-item transition reloads the receiver. The local player
                // stays paused while casting (we never resume local audio), so don't touch playWhenReady.
                try {
                    player.seekToNext()
                } catch (e: Exception) {
                }
            }
        }
    }

    private fun canSkipNext(): Boolean =
        !player.currentTimeline.isEmpty && player.isCommandAvailable(COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)

    private fun triggerRemoteLoad(mediaItem: MediaItem?) {
        val mediaId = mediaItem?.mediaId ?: return
        remoteLoadedMediaId = mediaId
        // Reset remote-clock tracking for the new track (see the remoteTime collector for the full why), and
        // reset the VISIBLE remote clock NOW — handler.load() also resets it but only after the async stream
        // resolve below, and until then the seek bar would show the previous track's near-end position and
        // duration against the just-switched new track (a full bar that then drops to 0).
        lastRemotePosition = 0.0
        lastRemoteTimeUpdateAt = System.currentTimeMillis()
        handler.remoteTime.value = 0.0
        handler.remoteDuration.value = 0.0
        handler.remoteTimeUpdatedAt = System.currentTimeMillis()
        // Cancel any still-in-flight resolve for a previous track so a slow earlier resolve can't land on
        // the receiver after a faster later one (rapid skips would otherwise play whichever URL resolved
        // last by network latency, not the current track).
        remoteLoadJob?.cancel()
        remoteLoadJob = scope.launch {
            val url = service.resolveStreamUrl(mediaId)
            if (!isActive) return@launch // superseded by a newer triggerRemoteLoad
            if (url == null) {
                // The receiver was NOT loaded, so don't let the de-dup keep believing this id is on it —
                // that would suppress a later genuine reload of the same id. Clear it and surface the failure.
                if (remoteLoadedMediaId == mediaId) remoteLoadedMediaId = null
                reportException(IllegalStateException("FCast: could not resolve a stream URL for $mediaId"))
                return@launch
            }
            handler.load(url, service.streamContentType(mediaId), mediaItem.metadata?.toCastMetadata())
        }
    }

    /**
     * Driven by [MusicService.onMediaItemTransition] (the long-lived listener), so the receiver is
     * reloaded on a genuine track change whether or not a [PlayerConnection] is currently bound.
     * PLAYLIST_CHANGED also fires on queue edits that don't change the current track, so for that reason
     * only reload when the item actually differs from what's on the receiver.
     */
    fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        lastTransitionTime = System.currentTimeMillis()
        val isCurrentItemChange = reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK ||
            reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO ||
            reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT ||
            (reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED &&
                mediaItem?.mediaId != remoteLoadedMediaId)
        if (casting && isCurrentItemChange) {
            player.pause() // stop local playback immediately
            triggerRemoteLoad(mediaItem)
        }
    }
}
