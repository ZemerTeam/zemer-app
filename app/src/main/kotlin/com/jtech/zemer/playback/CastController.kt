package com.jtech.zemer.playback

import android.widget.Toast
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM
import androidx.media3.common.Player.REPEAT_MODE_ONE
import com.jtech.zemer.R
import com.jtech.zemer.extensions.metadata
import com.jtech.zemer.utils.reportException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.fcast.sender_sdk.DeviceConnectionState
import org.fcast.sender_sdk.PlaybackState
import timber.log.Timber

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
    private companion object {
        /** See the onDisconnect handler: long enough for a device switch's new connect to re-arm. */
        const val RELAY_STOP_GRACE_MS = 2_000L

        /**
         * Minimum increase (sec) in the remote clock that counts as real forward progress for the idle
         * watchdog. Above the receivers' clock jitter, below one coarse (~1 Hz) report's worth of play.
         */
        const val PROGRESS_EPSILON_SEC = 0.25
    }

    private val player get() = service.player
    private val handler get() = service.discoveryHandler

    private val casting: Boolean get() = handler.isConnected

    private var lastTransitionTime = 0L
    private var lastRemotePosition = 0.0
    private var lastRemoteTimeUpdateAt = System.currentTimeMillis()
    // Wall-clock ms of the last time the remote clock actually moved FORWARD (real playback progress) —
    // as opposed to lastRemoteTimeUpdateAt, which a paused receiver's repeated same-position reports keep
    // bumping. Drives the CastIdleWatchdog: a pause or a frozen-playing stall stops advancing this, a
    // genuine play resumes it. Reset to "now" on every fresh load/connect so each track gets full grace.
    private var lastForwardProgressAt = System.currentTimeMillis()
    private var remoteLoadedMediaId: String? = null
    private var remoteLoadJob: Job? = null

    // Receiver-playback-error recovery state (see CastErrorRecovery for the ladder + why it exists).
    // Scope-confined like everything else here.
    private var errorAttempts = 0
    private var consecutiveErrorAdvances = 0
    private var lastErrorHandledAt = 0L

    init {
        // Recover the LOCAL player to the last remote position (paused) on disconnect, so the user can
        // resume on the phone, and reset tracking so a later reconnect/new track doesn't auto-skip on
        // stale near-end state. Invoked from the SDK's native callback thread — hop onto [scope] (main).
        handler.onDisconnect = { lastRemotePosMs ->
            scope.launch {
                lastRemotePosition = 0.0
                lastRemoteTimeUpdateAt = System.currentTimeMillis()
                lastForwardProgressAt = System.currentTimeMillis()
                lastTransitionTime = System.currentTimeMillis()
                remoteLoadedMediaId = null
                errorAttempts = 0
                consecutiveErrorAdvances = 0
                lastErrorHandledAt = 0L
                player.seekTo(lastRemotePosMs)
                player.prepare()
                player.playWhenReady = false
                // Stop the stream relay only after a grace period, and only if still disconnected: a
                // device SWITCH disconnects the old device first, and its deferred Disconnected
                // callback lands here AFTER the new connect has already handed out a relay URL —
                // stopping immediately would kill the URL the new receiver is about to fetch.
                launch {
                    delay(RELAY_STOP_GRACE_MS)
                    if (!casting && handler.remoteConnectionState.value is DeviceConnectionState.Disconnected) {
                        service.stopCastRelay()
                    }
                }
            }
        }

        scope.launch {
            handler.remoteTime.collect { time ->
                // Track unconditionally: connectTo()/load() reset remoteTime to the (usually 0) resume
                // position for a new track, and that reset must clear a previous track's near-end
                // position — otherwise a fresh connect or a device
                // switch leaves the stall detector comparing the new track's duration against the old
                // track's near-end position and spuriously auto-skipping. nearEnd(dur, 0) is false, so a
                // genuine mid-track 0 is harmless.
                // A strictly-increasing clock is real playback progress — feed the idle watchdog. A pause
                // freezes the clock and a track-load reset moves it backward (to 0/resume), so neither
                // counts, exactly as intended.
                if (time > lastRemotePosition + PROGRESS_EPSILON_SEC) {
                    lastForwardProgressAt = System.currentTimeMillis()
                }
                lastRemotePosition = time
                lastRemoteTimeUpdateAt = System.currentTimeMillis()
                // Real playback progress proves the current load works: restart the error-recovery
                // ladder from the top and clear the abandoned-tracks streak, so a much later failure
                // (e.g. a mid-track CDN reconnect refusal) retries gently instead of skipping tracks.
                if (CastErrorRecovery.progressResetsCounters(time)) {
                    errorAttempts = 0
                    consecutiveErrorAdvances = 0
                }
            }
        }
        scope.launch {
            var lastState = handler.remotePlaybackState.value
            handler.remotePlaybackState.collect { state ->
                val dur = handler.remoteDuration.value
                val pos = handler.remoteTime.value
                // End-of-track shows up differently across receivers and never as one reliable signal:
                //  - IDLE coming from PLAYING (generous window — a coarse clock may stop reporting early).
                //    Chromecast resets the reported clock to 0 milliseconds BEFORE the IDLE report, so the
                //    IDLE check reads the end-edge position (last real progress report when the current one
                //    is a fresh 0) — judging the raw 0 is exactly the bug that froze the queue at track end.
                //  - PAUSED coming from PLAYING at pos==duration (some receivers auto-pause at the end instead
                //    of going IDLE or sending an END event; the TIGHT epsilon separates it from a mid-track
                //    pause). The debounce inside advanceRemoteAfterEnd stops double-advancing.
                val endedIdle = state == PlaybackState.IDLE &&
                    CastAutoAdvance.finishedNearEnd(dur, CastAutoAdvance.endEdgePositionSec(pos, handler.lastProgressSec))
                val endedPause = state == PlaybackState.PAUSED &&
                    CastAutoAdvance.nearEnd(dur, pos, CastAutoAdvance.PAUSED_END_EPSILON_SEC)
                if (casting && lastState == PlaybackState.PLAYING && (endedIdle || endedPause)) {
                    advanceRemoteAfterEnd()
                }
                // A resume restarts the idle grace: give a just-un-paused track the full stalled-idle
                // window to make progress before the watchdog judges it dead.
                if (state == PlaybackState.PLAYING && lastState != PlaybackState.PLAYING) {
                    lastForwardProgressAt = System.currentTimeMillis()
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
                    val state = handler.remotePlaybackState.value
                    val stalledFor = System.currentTimeMillis() - lastRemoteTimeUpdateAt
                    val nearEnd = CastAutoAdvance.nearEnd(
                        dur, handler.interpolatedRemoteTimeSec(), CastAutoAdvance.STALL_END_EPSILON_SEC,
                    )
                    // A deliberately PAUSED track also freezes the remote clock — never treat that as a
                    // finished track, or pausing near the end would silently auto-skip it. nearEnd reads the
                    // *interpolated* clock so a coarse clock that stopped reporting still reaches the end.
                    if (!CastPlayback.isPaused(state) && nearEnd && CastAutoAdvance.stalled(stalledFor)) {
                        advanceRemoteAfterEnd()
                    } else if (CastIdleWatchdog.shouldEndIdleSession(
                            state, System.currentTimeMillis() - lastForwardProgressAt,
                        )
                    ) {
                        // The session has gone dead — paused-and-abandoned, or playing-but-cut-off with no
                        // Disconnected from the SDK. Tear it down so it doesn't hold the relay/service open
                        // forever. disconnect() recovers the local player (paused, at the last position) and
                        // stops the relay via onDisconnect; casting then goes false and this loop exits.
                        endIdleSession()
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
        // A fresh connect starts a fresh error-recovery ladder and a fresh idle-watchdog grace window.
        errorAttempts = 0
        consecutiveErrorAdvances = 0
        lastErrorHandledAt = 0L
        lastForwardProgressAt = System.currentTimeMillis()
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

    /**
     * Auto-end a cast session the [CastIdleWatchdog] has judged dead (paused-and-abandoned, or
     * playing-but-cut-off with no SDK Disconnected). [FCastDiscoveryHandler.disconnect] tears the
     * receiver connection down through the single teardown path — recovering the local player to the
     * last position (paused) and stopping the stream relay via the onDisconnect callback.
     */
    private fun endIdleSession() {
        Timber.tag("CastController").i("Auto-ending idle/stalled cast session (nothing playing)")
        Toast.makeText(service, service.getString(R.string.cast_session_ended_idle), Toast.LENGTH_LONG).show()
        handler.disconnect()
    }

    /**
     * [resumeSec] is 0 for a normal track change; an error-recovery reload passes the last played
     * position so a track that failed minutes in resumes there instead of restarting. [useRelay] is
     * false only for the ladder's DIRECT_URL rung — hand the receiver the raw googlevideo URL in case
     * it's the relay itself the receiver can't fetch from.
     */
    private fun triggerRemoteLoad(mediaItem: MediaItem?, resumeSec: Double = 0.0, useRelay: Boolean = true) {
        val mediaId = mediaItem?.mediaId ?: return
        remoteLoadedMediaId = mediaId
        // Reset remote-clock tracking for the new track (see the remoteTime collector for the full why), and
        // reset the VISIBLE remote clock NOW — handler.load() also resets it but only after the async stream
        // resolve below, and until then the seek bar would show the previous track's near-end position and
        // duration against the just-switched new track (a full bar that then drops to 0).
        lastRemotePosition = resumeSec
        lastRemoteTimeUpdateAt = System.currentTimeMillis()
        lastForwardProgressAt = System.currentTimeMillis() // fresh track: full idle-watchdog grace
        handler.remoteTime.value = resumeSec
        handler.remoteDuration.value = 0.0
        handler.remoteTimeUpdatedAt = System.currentTimeMillis()
        handler.lastProgressSec = resumeSec
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
            // Prefer the phone-side relay URL (Stage 2 of the cast-403 fix; falls back to the direct
            // googlevideo URL when the relay can't serve). Suspends on IO — re-check supersession after.
            val castUrl = if (useRelay) service.relayedStreamUrl(mediaId, url) else url
            if (!isActive) return@launch
            handler.load(castUrl, service.streamContentType(mediaId), mediaItem.metadata?.toCastMetadata(), resumeSec)
        }
    }

    /**
     * Recovery for a receiver-reported playback error (its fetch of the stream URL failed — on some
     * networks googlevideo refuses the receiver's connections intermittently; see [CastErrorRecovery]).
     * Escalates reload → fresh resolve → direct (de-relayed) URL → advance (capped) → give up, instead
     * of the old behaviour of silently leaving the session dead. Invoked from the SDK's native callback
     * thread — hops onto [scope] before touching state.
     */
    fun onRemotePlaybackError(message: String) {
        scope.launch {
            if (!casting) return@launch
            val now = System.currentTimeMillis()
            // A single broken fetch can surface as several error callbacks in quick succession; only
            // the first of a burst climbs the ladder.
            if (!CastErrorRecovery.isNewFailure(now, lastErrorHandledAt)) return@launch
            lastErrorHandledAt = now
            // Where playback actually got to, captured before any reload resets the trackers — so a
            // track that died minutes in resumes there instead of restarting from 0.
            val resumeSec = handler.lastProgressSec
            val canAdvance = player.repeatMode != REPEAT_MODE_ONE && canSkipNext()
            // The de-relay rung only makes sense when what the receiver is failing on IS a relay URL.
            val canTryDirect = service.castStreamRelay.servesUrl(handler.currentStreamUrl)
            val attempt = errorAttempts++
            val action = CastErrorRecovery.actionForAttempt(attempt, consecutiveErrorAdvances, canAdvance, canTryDirect)
            Timber.tag("CastController").w(
                "Receiver playback error (attempt=%d, action=%s, resume=%.1fs): %s", attempt, action, resumeSec, message,
            )
            when (action) {
                CastErrorRecovery.Action.RELOAD -> {
                    // Re-send what the receiver already had: a fresh receiver connection is often all
                    // that's needed. Fall back to a resolve if there's somehow nothing to re-send.
                    val url = handler.currentStreamUrl
                    val type = handler.currentContentType
                    if (url != null && type != null) {
                        handler.load(url, type, handler.currentMetadata, resumeSec)
                    } else {
                        triggerRemoteLoad(player.currentMediaItem, resumeSec)
                    }
                }
                CastErrorRecovery.Action.RESOLVE_FRESH -> {
                    player.currentMediaItem?.mediaId?.let { service.invalidateStreamCache(it) }
                    triggerRemoteLoad(player.currentMediaItem, resumeSec)
                }
                CastErrorRecovery.Action.DIRECT_URL -> {
                    // Maybe the receiver can't fetch from the relay at all (cleartext policy, phone
                    // unreachable) — try the raw googlevideo URL, already freshly minted by the
                    // RESOLVE_FRESH rung. Per-track and non-sticky: the next track relays again.
                    triggerRemoteLoad(player.currentMediaItem, resumeSec, useRelay = false)
                }
                CastErrorRecovery.Action.ADVANCE -> {
                    consecutiveErrorAdvances++
                    lastTransitionTime = now
                    handler.shouldPlay = true
                    // Skip locally; the media-item transition reloads the receiver (same as end-advance).
                    runCatching { player.seekToNext() }
                }
                CastErrorRecovery.Action.GIVE_UP -> {
                    reportException(
                        IllegalStateException("FCast: cast error recovery gave up after repeated receiver errors: $message"),
                    )
                    Toast.makeText(service, service.getString(R.string.cast_playback_failed), Toast.LENGTH_LONG).show()
                    // End the dead session rather than leaving it connected but silent — the receiver will
                    // never play, so keeping the relay/service alive for it is pointless.
                    handler.disconnect()
                }
            }
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
        // Every track change starts its own error-recovery ladder (the consecutive-advance streak is
        // deliberately NOT reset here — an error-advance is itself a transition, and the streak is what
        // stops a dead network from skipping through the whole queue; real progress resets it).
        errorAttempts = 0
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
