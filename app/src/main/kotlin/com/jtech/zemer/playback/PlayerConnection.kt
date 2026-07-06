package com.jtech.zemer.playback

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM
import androidx.media3.common.Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM
import androidx.media3.common.Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM
import androidx.media3.common.Player.REPEAT_MODE_OFF
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
import org.fcast.sender_sdk.DeviceConnectionState

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerConnection(
    context: Context,
    binder: MusicBinder,
    val database: MusicDatabase,
    parentScope: CoroutineScope,
) : Player.Listener {
    val service = binder.service
    val player = service.player

    // Instance-owned scope (a child of the host's scope) so [dispose] cancels every collector/launch this
    // connection started. The host re-creates a PlayerConnection on each service re-bind; without this the
    // UI-state collectors (isCasting / isPlaying / currentSong …) would pile up on the long-lived
    // lifecycleScope. The cast control plane itself lives on the service-scoped CastController, not here.
    val scope = CoroutineScope(parentScope.coroutineContext + SupervisorJob(parentScope.coroutineContext[Job]))

    val playbackState = MutableStateFlow(player.playbackState)
    private val playWhenReady = MutableStateFlow(player.playWhenReady)

    val isCasting = service.discoveryHandler.remoteConnectionState.map { connectionState: DeviceConnectionState ->
        connectionState is DeviceConnectionState.Connected
    }.stateIn(scope, SharingStarted.Lazily, false)

    val isPlaying =
        combine(playbackState, playWhenReady, isCasting, service.discoveryHandler.remotePlaybackState) { playbackState, playWhenReady, casting, remoteState ->
            if (casting) {
                // Once the receiver reports its state, mirror it. In the brief window after connecting but
                // before the first remote playbackStateChanged arrives, fall back to the cast play intent
                // (shouldPlay) — NOT the local player, which we just paused, so the button would otherwise
                // flash "paused" while the receiver is actually starting playback.
                if (remoteState != null) CastPlayback.isPlaying(remoteState)
                else service.discoveryHandler.shouldPlay
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
    }

    fun playQueue(queue: Queue) {
        service.playQueue(queue)
        // While casting, hand the queue-start bookkeeping to the service-owned controller (pause local,
        // record the play intent, force the upcoming PLAYLIST_CHANGED to reload the receiver). current
        // mediaItem here is stale — the queue hasn't loaded yet — so the reload happens on the transition.
        if (isCasting.value) {
            service.castController.onPlayQueueWhileCasting()
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
            // isRemotePlaying falls back to the play intent before the receiver's first state report,
            // matching the isPlaying flow — so the tap toggles what the button shows.
            if (service.discoveryHandler.isRemotePlaying()) {
                service.discoveryHandler.pause()
            } else {
                service.discoveryHandler.play()
            }
        } else {
            player.togglePlayPause()
        }
    }

    /**
     * The play/pause-button action: replay from the start when the local queue has ended ([localEnded]),
     * otherwise toggle. While casting the receiver is the source of truth, so always toggle the remote —
     * never restart the (paused) local player on top of the cast stream.
     */
    fun playPauseOrReplay(localEnded: Boolean) {
        if (localEnded && !isCasting.value) {
            player.seekTo(0, 0)
            player.playWhenReady = true
        } else {
            playPause()
        }
    }

    fun seekTo(positionMs: Long) {
        if (isCasting.value) {
            service.discoveryHandler.seek(CastPlayback.msToRemoteSeconds(positionMs))
        } else {
            player.seekTo(positionMs)
        }
    }

    /**
     * Pause the cast receiver because a full-screen local video is taking over the phone's audio, so the
     * receiver isn't playing underneath it. Returns whether we actually paused an active cast, so the
     * caller can [resumeCastAfterVideo] only what it interrupted. No-op (returns false) when not casting
     * or the receiver was already paused.
     */
    fun pauseCastForVideo(): Boolean {
        val pause = CastPlayback.shouldPauseCastForVideo(isCasting.value, service.discoveryHandler.remotePlaybackState.value)
        if (pause) service.discoveryHandler.pause()
        return pause
    }

    /** Resume the cast receiver after the local video closed — only if we paused it and it's still connected. */
    fun resumeCastAfterVideo(pausedByVideo: Boolean) {
        if (CastPlayback.shouldResumeCastAfterVideo(pausedByVideo, isCasting.value)) service.discoveryHandler.play()
    }

    /** Current playback position (ms) — the smoothed remote clock while casting, else the local player. */
    fun currentPositionMs(): Long =
        if (isCasting.value) CastPlayback.remoteSecondsToMs(service.discoveryHandler.interpolatedRemoteTimeSec())
        else player.currentPosition

    /** Current item duration (ms) — the remote clock while casting, else the local player. */
    fun currentDurationMs(): Long =
        if (isCasting.value) CastPlayback.remoteSecondsToMs(service.discoveryHandler.remoteDuration.value)
        else player.duration

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
                // While casting the local clock is frozen at the connect position, so seekToPrevious's
                // "restart current track if >3s in" misfires: a within-item seek fires no media-item
                // transition and the receiver is never reloaded. Skip straight to the previous item,
                // like the widget path (MusicService.onStartCommand ACTION_PREV).
                if (isCasting.value) {
                    player.seekToPreviousMediaItem()
                } else {
                    player.seekToPrevious()
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

    override fun onMediaItemTransition(
        mediaItem: MediaItem?,
        reason: Int,
    ) {
        // The cast receiver reload is owned by the service-scoped CastController (driven from
        // MusicService.onMediaItemTransition), so it survives this Activity-scoped connection being
        // disposed. Here we only update the UI-facing state.
        mediaMetadata.value = mediaItem?.metadata
        currentMediaItemIndex.value = player.currentMediaItemIndex
        currentWindowIndex.value = player.getCurrentQueueIndex()
        updateCanSkipPreviousAndNext()
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
        // Cancel every collector/launch this connection owns. The cast control plane (including the
        // handler's onDisconnect callback) lives on the service-scoped CastController, so it is
        // intentionally NOT torn down here — casting keeps working across Activity rebinds.
        scope.cancel()
    }
}
