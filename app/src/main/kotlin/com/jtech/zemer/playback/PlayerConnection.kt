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
import com.jtech.zemer.utils.reportException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.fcast.sender_sdk.PlaybackState
import org.fcast.sender_sdk.DeviceConnectionState
import org.fcast.sender_sdk.Metadata
import android.util.Log
import kotlinx.coroutines.delay
import org.fcast.sender_sdk.CastingDevice
import org.fcast.sender_sdk.DeviceEventHandler

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerConnection(
    context: Context,
    binder: MusicBinder,
    val database: MusicDatabase,
    val scope: CoroutineScope,
) : Player.Listener {
    val service = binder.service
    val player = service.player

    val playbackState = MutableStateFlow(player.playbackState)
    private val playWhenReady = MutableStateFlow(player.playWhenReady)

    val isCasting = service.discoveryHandler.remoteConnectionState.map { connectionState: DeviceConnectionState ->
        connectionState is org.fcast.sender_sdk.DeviceConnectionState.Connected
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

    val currentPosition = combine(isCasting, service.discoveryHandler.remoteTime, mediaMetadata) { casting, remoteTime, _ ->
        if (casting) CastPlayback.remoteSecondsToMs(remoteTime) else player.currentPosition
    }.stateIn(scope, SharingStarted.Lazily, player.currentPosition)

    val duration = combine(isCasting, service.discoveryHandler.remoteDuration, mediaMetadata) { casting, remoteDuration, _ ->
        if (casting) CastPlayback.remoteSecondsToMs(remoteDuration) else player.duration
    }.stateIn(scope, SharingStarted.Lazily, player.duration)

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

        service.discoveryHandler.onDisconnect = { lastRemotePos ->
            scope.launch {
                player.seekTo(lastRemotePos)
                player.prepare()
                player.playWhenReady = false
            }
        }

        scope.launch {
            service.discoveryHandler.remoteTime.collect { time ->
                if (time > 0) lastRemotePosition = time
                Log.d("FCastAutoAdvance", "remoteTime=$time")
                lastRemoteTimeUpdateAt = System.currentTimeMillis()
            }
        }
        scope.launch {
            var lastState = service.discoveryHandler.remotePlaybackState.value
            service.discoveryHandler.remotePlaybackState.collect { state ->
                val dur = service.discoveryHandler.remoteDuration.value
                Log.d("FCastAutoAdvance", "state=$state lastState=$lastState isCasting=${isCasting.value} dur=$dur lastRemotePosition=$lastRemotePosition")
                if (isCasting.value && state == PlaybackState.IDLE && lastState == PlaybackState.PLAYING) {
                    // If we're near the end, transition to next song
                    if (CastAutoAdvance.nearEnd(dur, lastRemotePosition, CastAutoAdvance.IDLE_END_EPSILON_SEC) &&
                        CastAutoAdvance.debouncePassed(System.currentTimeMillis(), lastTransitionTime)) {
                        if (player.repeatMode == REPEAT_MODE_ONE) {
                            player.seekTo(player.currentMediaItemIndex, 0)
                            triggerRemoteLoad(player.currentMediaItem)
                        } else if (canSkipNext.value) {
                            seekToNext()
                        }
                    }
                }
                lastState = state
            }
        }
        scope.launch {
            while (true) {
                delay(1000)
                if (isCasting.value) {
                    val dur = service.discoveryHandler.remoteDuration.value
                    val stalledFor = System.currentTimeMillis() - lastRemoteTimeUpdateAt
                    if (CastAutoAdvance.nearEnd(dur, lastRemotePosition, CastAutoAdvance.STALL_END_EPSILON_SEC) &&
                        CastAutoAdvance.stalled(stalledFor) &&
                        CastAutoAdvance.debouncePassed(System.currentTimeMillis(), lastTransitionTime)) {
                        Log.d("FCastAutoAdvance", "Stall detected near track end, advancing")
                        if (player.repeatMode == REPEAT_MODE_ONE) {
                            player.seekTo(player.currentMediaItemIndex, 0)
                            triggerRemoteLoad(player.currentMediaItem)
                        } else if (canSkipNext.value) {
                            seekToNext()
                        }
                    }
                }
            }
        }
    }

    fun playQueue(queue: Queue) {
        service.playQueue(queue)
        if (isCasting.value) {
            player.pause()
            triggerRemoteLoad(player.currentMediaItem)
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
            if (player.isPlaying) {
                player.pause()
            } else {
                player.play()
            }
        }
    }

    fun seekTo(positionMs: Long) {
        if (isCasting.value) {
            service.discoveryHandler.seek(CastPlayback.msToRemoteSeconds(positionMs))
        } else {
            player.seekTo(positionMs)
        }
    }

    fun seekToNext() {
        if (isCasting.value) {
            player.seekToNext()
        } else {
            if (!player.currentTimeline.isEmpty && player.isCommandAvailable(COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)) {
                try {
                    player.seekToNext()
                    player.prepare()
                    player.playWhenReady = true
                } catch (e: Exception) {
                }
            }
        }
    }

    fun seekToPrevious() {
        if (isCasting.value) {
            player.seekToPrevious()
        } else {
            if (!player.currentTimeline.isEmpty && player.isCommandAvailable(COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)) {
                try {
                    player.seekToPrevious()
                    player.prepare()
                    player.playWhenReady = true
                } catch (e: Exception) {
                }
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

    private fun triggerRemoteLoad(mediaItem: MediaItem?) {
        val mediaId = mediaItem?.mediaId ?: return
        scope.launch {
            val url = service.resolveStreamUrl(mediaId)
            val contentType = service.currentContentType
            val metadata = mediaItem.metadata?.let {
                Metadata(
                    title = "${it.title} - ${it.artists.joinToString(", ") { a -> a.name }}",
                    thumbnailUrl = it.thumbnailUrl
                )
            }
            if (url != null && contentType != null) {
                service.discoveryHandler.load(url, contentType, metadata)
            }
        }
    }

    override fun onMediaItemTransition(

        mediaItem: MediaItem?,
        reason: Int,
    ) {
        Log.d("FCast", "onMediaItemTransition reason=$reason isCasting=${isCasting.value}")
        lastTransitionTime = System.currentTimeMillis()
        mediaMetadata.value = mediaItem?.metadata
        currentMediaItemIndex.value = player.currentMediaItemIndex
        currentWindowIndex.value = player.getCurrentQueueIndex()
        updateCanSkipPreviousAndNext()

        if (isCasting.value && (reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK ||
                               reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO ||
                               reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT)) {
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
    }
}
