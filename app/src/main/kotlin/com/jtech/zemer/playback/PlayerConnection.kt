package com.jtech.zemer.playback

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM
import androidx.media3.common.Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM
import androidx.media3.common.Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM
import androidx.media3.common.Player.REPEAT_MODE_OFF
import androidx.media3.common.Player.REPEAT_MODE_ONE
import androidx.media3.common.Player.STATE_ENDED
import androidx.media3.common.Timeline
import com.jtech.zemer.db.MusicDatabase
import com.jtech.zemer.extensions.currentMetadata
import com.jtech.zemer.extensions.getCurrentQueueIndex
import com.jtech.zemer.extensions.getQueueWindows
import com.jtech.zemer.extensions.metadata
import com.jtech.zemer.playback.MusicService.MusicBinder
import com.jtech.zemer.playback.queues.Queue
import com.jtech.zemer.extensions.togglePlayPause
import com.jtech.zemer.utils.reportException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.fcast.sender_sdk.PlaybackState
import org.fcast.sender_sdk.DeviceConnectionState
import kotlinx.coroutines.delay

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerConnection(
    context: Context,
    binder: MusicBinder,
    val database: MusicDatabase,
    parentScope: CoroutineScope,
) : Player.Listener {
    val service = binder.service
    val player = service.player

    // Instance-owned scope (a child of the host's scope) so [dispose] cancels every collector/launch
    // this connection started. The host re-creates a PlayerConnection on each service re-bind; without
    // this the cast collectors would pile up on the long-lived lifecycleScope and a single track-end
    // would fire advanceRemoteAfterEnd once per leaked instance.
    val scope = CoroutineScope(parentScope.coroutineContext + SupervisorJob(parentScope.coroutineContext[Job]))

    val playbackState = MutableStateFlow(player.playbackState)
    private val playWhenReady = MutableStateFlow(player.playWhenReady)

    val isCasting = service.discoveryHandler.remoteConnectionState.map { connectionState: DeviceConnectionState ->
        connectionState is DeviceConnectionState.Connected
    }.stateIn(scope, SharingStarted.Lazily, false)

    val isPlaying =
        combine(playbackState, playWhenReady, isCasting, service.discoveryHandler.remotePlaybackState) { playbackState, playWhenReady, casting, remoteState ->
            if (casting && remoteState != null) {
                CastPlayback.isPlaying(remoteState)
            } else {
                playWhenReady && playbackState != STATE_ENDED
            }
        }.stateIn(
            scope,
            SharingStarted.Lazily,
            player.playWhenReady && player.playbackState != STATE_ENDED
        )

    val mediaMetadata = MutableStateFlow(player.currentMetadata)

    val currentSong =
        mediaMetadata.flatMapLatest {
            database.song(it?.id)
        }
    val currentLyrics = mediaMetadata.flatMapLatest { mediaMetadata ->
        database.lyrics(mediaMetadata?.id)
    }
    val currentFormat =
        mediaMetadata.flatMapLatest { mediaMetadata ->
            database.format(mediaMetadata?.id)
        }

    val queueTitle = MutableStateFlow<String?>(null)
    val queueWindows = MutableStateFlow<List<Timeline.Window>>(emptyList())
    val currentMediaItemIndex = MutableStateFlow(-1)
    val currentWindowIndex = MutableStateFlow(-1)

    val shuffleModeEnabled = MutableStateFlow(false)
    val repeatMode = MutableStateFlow(REPEAT_MODE_OFF)

    val canSkipPrevious = MutableStateFlow(true)
    val canSkipNext = MutableStateFlow(true)

    val error = MutableStateFlow<PlaybackException?>(null)
    val waitingForNetworkConnection = service.waitingForNetworkConnection

    private var lastTransitionTime = 0L
    private var lastRemotePosition = 0.0
    private var lastRemoteTimeUpdateAt = System.currentTimeMillis()
    private var remoteLoadedMediaId: String? = null

    // Stored so [dispose] can clear it from the singleton handler only if it's still ours.
    private val onDisconnectCallback: (Long) -> Unit = { lastRemotePos ->
        // Reset cast tracking so a later reconnect/new track doesn't auto-skip on stale near-end state,
        // and so the PLAYLIST_CHANGED de-dup doesn't suppress a real load against a now-stale id.
        lastRemotePosition = 0.0
        lastRemoteTimeUpdateAt = System.currentTimeMillis()
        lastTransitionTime = System.currentTimeMillis()
        remoteLoadedMediaId = null
        scope.launch {
            player.seekTo(lastRemotePos)
            player.prepare()
            player.playWhenReady = false
        }
    }

    init {
        player.addListener(this)

        playbackState.value = player.playbackState
        playWhenReady.value = player.playWhenReady
        mediaMetadata.value = player.currentMetadata
        queueTitle.value = service.queueTitle
        queueWindows.value = player.getQueueWindows()
        currentWindowIndex.value = player.getCurrentQueueIndex()
        currentMediaItemIndex.value = player.currentMediaItemIndex
        shuffleModeEnabled.value = player.shuffleModeEnabled
        repeatMode.value = player.repeatMode

        service.discoveryHandler.onDisconnect = onDisconnectCallback

        scope.launch {
            service.discoveryHandler.remoteTime.collect { time ->
                if (time > 0) lastRemotePosition = time
                lastRemoteTimeUpdateAt = System.currentTimeMillis()
            }
        }
        scope.launch {
            var lastState = service.discoveryHandler.remotePlaybackState.value
            service.discoveryHandler.remotePlaybackState.collect { state ->
                val dur = service.discoveryHandler.remoteDuration.value
                // IDLE coming from PLAYING near the end means the track finished. Debounce is enforced
                // inside advanceRemoteAfterEnd (shared with the other detectors, on the main thread).
                if (isCasting.value && state == PlaybackState.IDLE && lastState == PlaybackState.PLAYING &&
                    CastAutoAdvance.nearEnd(dur, lastRemotePosition, CastAutoAdvance.IDLE_END_EPSILON_SEC)
                ) {
                    advanceRemoteAfterEnd()
                }
                lastState = state
            }
        }
        scope.launch {
            // Only poll for a stalled remote clock while actually casting (no 1 Hz wakeup otherwise);
            // collectLatest cancels the loop the moment casting stops.
            isCasting.collectLatest { casting ->
                while (casting) {
                    delay(1000)
                    val dur = service.discoveryHandler.remoteDuration.value
                    val stalledFor = System.currentTimeMillis() - lastRemoteTimeUpdateAt
                    // A deliberately PAUSED track also freezes the remote clock — never treat that as a
                    // finished track, or pausing near the end would silently auto-skip it.
                    if (!CastPlayback.isPaused(service.discoveryHandler.remotePlaybackState.value) &&
                        CastAutoAdvance.nearEnd(dur, lastRemotePosition, CastAutoAdvance.STALL_END_EPSILON_SEC) &&
                        CastAutoAdvance.stalled(stalledFor)
                    ) {
                        advanceRemoteAfterEnd()
                    }
                }
            }
        }
    }

    fun playQueue(queue: Queue) {
        service.playQueue(queue)
        // While casting, pause local immediately; the new queue's first item then fires
        // onMediaItemTransition (PLAYLIST_CHANGED), which loads the remote with the correct item
        // (currentMediaItem here is stale - the queue hasn't loaded yet).
        if (isCasting.value) {
            player.pause()
        }
    }

    fun startRadioSeamlessly() {
        service.startRadioSeamlessly()
    }

    fun playNext(item: MediaItem) = playNext(listOf(item))

    fun playNext(items: List<MediaItem>) {
        service.playNext(items)
    }

    fun addToQueue(item: MediaItem) = addToQueue(listOf(item))

    fun addToQueue(items: List<MediaItem>) {
        service.addToQueue(items)
    }

    fun toggleLike() {
        service.toggleLike()
    }

    fun playPause() {
        if (isCasting.value) {
            val remoteState = service.discoveryHandler.remotePlaybackState.value
            if (CastPlayback.isPlaying(remoteState)) {
                service.discoveryHandler.pause()
            } else {
                service.discoveryHandler.play()
            }
        } else {
            player.togglePlayPause()
        }
    }

    fun seekTo(positionMs: Long) {
        if (isCasting.value) {
            service.discoveryHandler.seek(CastPlayback.msToRemoteSeconds(positionMs))
        } else {
            player.seekTo(positionMs)
        }
    }

    /** Current playback position (ms) — the remote clock while casting, else the local player. */
    fun currentPositionMs(): Long =
        if (isCasting.value) CastPlayback.remoteSecondsToMs(service.discoveryHandler.remoteTime.value)
        else player.currentPosition

    /** Current item duration (ms) — the remote clock while casting, else the local player. */
    fun currentDurationMs(): Long =
        if (isCasting.value) CastPlayback.remoteSecondsToMs(service.discoveryHandler.remoteDuration.value)
        else player.duration

    /**
     * Records the media id the receiver is currently playing (set when the picker connects and loads
     * the current item via the handler) so the PLAYLIST_CHANGED de-dup in [onMediaItemTransition] is
     * accurate and doesn't redundantly reload the just-connected track.
     */
    fun markRemoteLoaded(mediaId: String?) {
        remoteLoadedMediaId = mediaId
    }

    fun seekToNext() {
        if (!player.currentTimeline.isEmpty && player.isCommandAvailable(COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)) {
            try {
                player.seekToNext()
                // While casting the local player stays paused (the receiver plays); the resulting
                // media-item transition reloads the receiver. Only resume local audio when not casting.
                if (!isCasting.value) {
                    player.prepare()
                    player.playWhenReady = true
                }
            } catch (e: Exception) {
            }
        }
    }

    fun seekToPrevious() {
        if (!player.currentTimeline.isEmpty && player.isCommandAvailable(COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)) {
            try {
                player.seekToPrevious()
                if (!isCasting.value) {
                    player.prepare()
                    player.playWhenReady = true
                }
            } catch (e: Exception) {
            }
        }
    }

    override fun onPlaybackStateChanged(state: Int) {
        playbackState.value = state
        error.value = player.playerError
    }

    override fun onPlayWhenReadyChanged(
        newPlayWhenReady: Boolean,
        reason: Int,
    ) {
        playWhenReady.value = newPlayWhenReady
    }

    /**
     * Advance the receiver at end-of-track: repeat-one replays the current item, otherwise skip to the
     * next (if any). Shared by all three end detectors — the SDK END event, the IDLE-after-PLAYING
     * collector, and the stall poll.
     *
     * The body runs on the connection scope (main): the SDK END callback invokes this from a native
     * thread, and Media3's player must be touched on its application thread. Doing the debounce check
     * and the lastTransitionTime stamp here — serialised on one thread — also stops the three detectors
     * from double-advancing, including the repeat-one path, which fires no media-item transition of its
     * own and so would otherwise never refresh the debounce window.
     */
    fun advanceRemoteAfterEnd() {
        scope.launch {
            if (!CastAutoAdvance.debouncePassed(System.currentTimeMillis(), lastTransitionTime)) return@launch
            lastTransitionTime = System.currentTimeMillis()
            if (player.repeatMode == REPEAT_MODE_ONE) {
                player.seekTo(player.currentMediaItemIndex, 0)
                triggerRemoteLoad(player.currentMediaItem)
            } else if (canSkipNext.value) {
                seekToNext()
            }
        }
    }

    private fun triggerRemoteLoad(mediaItem: MediaItem?) {
        val mediaId = mediaItem?.mediaId ?: return
        remoteLoadedMediaId = mediaId
        // New track loading: reset remote-clock tracking so the stall / near-end detectors don't fire on
        // the previous track's stale near-end position before the new track first reports its own clock.
        lastRemotePosition = 0.0
        lastRemoteTimeUpdateAt = System.currentTimeMillis()
        scope.launch {
            val url = service.resolveStreamUrl(mediaId) ?: return@launch
            service.discoveryHandler.load(url, service.streamContentType(mediaId), mediaItem.metadata?.toCastMetadata())
        }
    }

    override fun onMediaItemTransition(
        mediaItem: MediaItem?,
        reason: Int,
    ) {
        lastTransitionTime = System.currentTimeMillis()
        mediaMetadata.value = mediaItem?.metadata
        currentMediaItemIndex.value = player.currentMediaItemIndex
        currentWindowIndex.value = player.getCurrentQueueIndex()
        updateCanSkipPreviousAndNext()

        // PLAYLIST_CHANGED also fires on queue edits that don't change the current track, so for that
        // reason only reload when the item actually differs from what's on the receiver (SEEK/AUTO are
        // genuine track changes; REPEAT intentionally replays the same id).
        val isCurrentItemChange = reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK ||
            reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO ||
            reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT ||
            (reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED &&
                mediaItem?.mediaId != remoteLoadedMediaId)
        if (isCasting.value && isCurrentItemChange) {
            player.pause() // Stop local playback immediately
            triggerRemoteLoad(mediaItem)
        }
    }

    override fun onTimelineChanged(
        timeline: Timeline,
        reason: Int,
    ) {
        queueWindows.value = player.getQueueWindows()
        queueTitle.value = service.queueTitle
        currentMediaItemIndex.value = player.currentMediaItemIndex
        currentWindowIndex.value = player.getCurrentQueueIndex()
        updateCanSkipPreviousAndNext()
    }

    override fun onShuffleModeEnabledChanged(enabled: Boolean) {
        shuffleModeEnabled.value = enabled
        queueWindows.value = player.getQueueWindows()
        currentWindowIndex.value = player.getCurrentQueueIndex()
        updateCanSkipPreviousAndNext()
    }

    override fun onRepeatModeChanged(mode: Int) {
        repeatMode.value = mode
        updateCanSkipPreviousAndNext()
    }

    override fun onPlayerErrorChanged(playbackError: PlaybackException?) {
        if (playbackError != null) {
            reportException(playbackError)
        }
        error.value = playbackError
    }

    private fun updateCanSkipPreviousAndNext() {
        if (!player.currentTimeline.isEmpty) {
            val window =
                player.currentTimeline.getWindow(player.currentMediaItemIndex, Timeline.Window())
            canSkipPrevious.value = player.isCommandAvailable(COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM) ||
                    !window.isLive ||
                    player.isCommandAvailable(COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            canSkipNext.value = window.isLive &&
                    window.isDynamic ||
                    player.isCommandAvailable(COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
        } else {
            canSkipPrevious.value = false
            canSkipNext.value = false
        }
    }

    fun dispose() {
        player.removeListener(this)
        // Clear the singleton handler's callback only if it is still ours (a newer PlayerConnection may
        // have already replaced it), then cancel every collector/launch this connection owns.
        if (service.discoveryHandler.onDisconnect === onDisconnectCallback) {
            service.discoveryHandler.onDisconnect = null
        }
        scope.cancel()
    }
}
