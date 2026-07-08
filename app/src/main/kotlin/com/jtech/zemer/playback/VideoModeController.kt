package com.jtech.zemer.playback

import android.os.Handler
import android.os.Looper
import android.view.SurfaceView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import com.jtech.zemer.constants.BlockVideosKey
import com.jtech.zemer.playback.VideoModeLogic.RenditionKind
import com.jtech.zemer.playback.VideoModeLogic.TransitionClass
import com.jtech.zemer.utils.BlockedIdsCache
import com.jtech.zemer.utils.ContentFilterState
import com.jtech.zemer.utils.dataStore
import com.jtech.zemer.utils.get
import com.jtech.zemer.utils.reportException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.fcast.sender_sdk.DeviceConnectionState

/**
 * Owns the audio↔video **rendition swap** for the current queue item — the service-scoped state machine
 * behind the in-player Song/Video toggle (the [CastController] pattern). The queue item never changes
 * (same mediaId, same MediaMetadata tag, same index — I4); a "video mode" only replaces the current
 * MediaItem's URI/cacheKey with a `video:<id>` rendition ([VideoRendition]) that the resolver serves as a
 * progressive muxed stream, seeks to the captured position, and reverts on ANY track transition, cast
 * connect, block toggle, error, or restart (I2/I5).
 *
 * All pure decisions live in [VideoModeLogic]/[ListenAccumulator] (JVM-tested); this class does the
 * player mutations, which need a device and are covered by the step-3 on-device checklist. Everything
 * runs on [scope] (the service Main scope), so the swap-tracking fields are single-thread-confined.
 *
 * NOTE (step-3 empirical shrink, 2026-07-08): the authenticated `next()` counterpart probe found NO
 * `playlistPanelVideoWrapperRenderer`s (see the step-3 PROGRESS report), so the COUNTERPART rendition —
 * an audio song → its separate music video — does not light up for the tested account. The plumbing is
 * kept (it costs nothing and turns on automatically if a pooled/Premium account ever returns wrappers,
 * fed passively via [recordCounterparts]); the shipping renditions are SELF (a video item shows its own
 * video) and LOCAL (a downloaded muxed video file). Pooled-account counterpart availability is flagged
 * for the step-6 on-device pass.
 */
class VideoModeController(
    private val service: MusicService,
    private val scope: CoroutineScope,
) {
    private val player get() = service.player
    private val mainHandler = Handler(Looper.getMainLooper())

    /** The per-item video knowledge store (music-video type + any counterpart), filled from playback + next(). */
    val availabilityCache = VideoAvailabilityCache()

    private val listenAccumulator = ListenAccumulator()

    // Swap state (main-thread confined). videoModeItemId != null ⇔ in video mode.
    private var videoModeItemId: String? = null
    private var videoModeItemIndex: Int = C.INDEX_UNSET
    private var videoModeAudioItem: MediaItem? = null
    private var renditionKind: RenditionKind? = null
    private var videoRenditionId: String? = null
    private var pendingSwap: Boolean = false
    private var currentSurface: SurfaceView? = null

    private val _isVideoMode = MutableStateFlow(false)
    val isVideoMode: StateFlow<Boolean> = _isVideoMode.asStateFlow()

    // Bumped when a signal the availability flow can't otherwise observe changes (local-file source
    // resolution). The cache has its own revision; block/cast/metadata are their own flows.
    private val recompute = MutableStateFlow(0)

    private val _videoErrorEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    /** One-shot: a video-mode playback error reverted to audio → the UI shows a snackbar. */
    val videoErrorEvents: SharedFlow<Unit> = _videoErrorEvents.asSharedFlow()

    private val blockVideosFlow =
        service.dataStore.data.map { it[BlockVideosKey] ?: false }.distinctUntilChanged()

    /**
     * Whether the current item can show video (and the toggle should appear). The UI must read THIS and
     * not re-derive block/cast/availability conditions itself.
     */
    val videoModeAvailable: StateFlow<Boolean> =
        combine(
            service.currentMediaMetadata,
            service.discoveryHandler.remoteConnectionState,
            blockVideosFlow,
            availabilityCache.revision,
            recompute,
        ) { _, _, _, _, _ -> computeAvailability() != null }
            .stateIn(scope, SharingStarted.Eagerly, false)

    init {
        // I5: a cast session starting forces audio (the receiver only ever gets the audio stream, keyed
        // on the real id) — revert the local timeline item back to audio.
        scope.launch {
            service.discoveryHandler.remoteConnectionState.collect { state ->
                if (state is DeviceConnectionState.Connected && _isVideoMode.value) revertToAudio()
            }
        }
        // I1: blocking videos mid-playback must drop video mode immediately.
        scope.launch {
            blockVideosFlow.collect { blocked -> if (blocked && _isVideoMode.value) revertToAudio() }
        }
    }

    /** Pure availability for the CURRENT item (null ⇒ no toggle). Recomputed by [videoModeAvailable]. */
    private fun computeAvailability(): VideoModeLogic.Rendition? {
        val meta = service.currentMediaMetadata.value ?: return null
        val id = meta.id
        val avail = availabilityCache.get(id)
        return VideoModeLogic.availability(
            mediaId = id,
            casting = service.discoveryHandler.isConnected,
            blockVideos = service.dataStore.get(BlockVideosKey, false),
            localVideoFile = meta.isVideo && service.playbackSourceIsLocalFile(id),
            musicVideoType = avail?.musicVideoType,
            counterpartVideoId = avail?.counterpartVideoId,
            isBlockedRendition = { rid -> BlockedIdsCache.isBlocked(rid, ContentFilterState.current) },
        )
    }

    // ---- UI-facing API (via PlayerConnection) ------------------------------

    /** Enter/leave video mode. No-op when unavailable (blocked/casting/no rendition). */
    fun setVideoMode(enabled: Boolean) {
        if (enabled) {
            if (_isVideoMode.value) return
            val rendition = computeAvailability() ?: return
            enterVideoMode(rendition)
        } else {
            exitVideoModeSameItem()
        }
    }

    /** Attach/detach the render surface. Applied to the player only while in video mode. */
    fun setVideoSurface(view: SurfaceView?) {
        currentSurface = view
        if (_isVideoMode.value) player.setVideoSurfaceView(view)
    }

    /**
     * Reserved for on-demand counterpart discovery (DESIGN §1 source 3). Currently a cache-only no-op:
     * the authenticated on-demand `next()` probe returned no counterparts for the tested account (step-3
     * report), so firing one per expanded item would be pure waste. Re-enable once pooled/Premium
     * counterpart availability is confirmed on-device (step 6). SELF availability needs no lookup — it
     * comes free from the current item's playback resolution ([recordMusicVideoType]).
     */
    fun requestVideoAvailability(@Suppress("UNUSED_PARAMETER") mediaId: String) {
        // intentionally no network call — see kdoc.
    }

    // ---- MusicService hooks ------------------------------------------------

    /** Record the current item's music-video type from a playback resolution (drives SELF availability). */
    fun recordMusicVideoType(mediaId: String, musicVideoType: String?) {
        availabilityCache.recordMusicVideoType(mediaId, musicVideoType)
    }

    /** Passively fold in a `next()` response's counterpart map (the free counterpart source). */
    fun recordCounterparts(counterparts: Map<String, String>) {
        if (counterparts.isNotEmpty()) availabilityCache.recordCounterparts(counterparts)
    }

    /** The playback source for an item was just decided (local file vs stream) — recompute availability. */
    fun onPlaybackSourceResolved() {
        recompute.value = recompute.value + 1
    }

    /** Route every `onPlaybackStatsReady` here so a swap-ended session never double-fires the listen (I4). */
    fun onStatsReady(mediaId: String, playTimeMs: Long): ListenAccumulator.Result =
        listenAccumulator.onStatsReady(mediaId, playTimeMs)

    /**
     * Classify an `onMediaItemTransition`. Returns true iff it is our own swap (the caller then skips the
     * cast/auto-load-more/save-queue side effects and keeps video mode); a real track change reverts to
     * audio (I2) and returns false so the caller runs its normal transition handling.
     */
    fun onMediaItemTransition(mediaItem: MediaItem?, @Suppress("UNUSED_PARAMETER") reason: Int): Boolean =
        when (VideoModeLogic.classifyTransition(pendingSwap, mediaItem?.mediaId, videoModeItemId)) {
            TransitionClass.OWN_SWAP -> true
            TransitionClass.TRACK_CHANGE -> {
                listenAccumulator.onTrackTransition()
                if (_isVideoMode.value) revertDepartedItem()
                false
            }
        }

    /**
     * Handle a player error while in video mode (I8): revert to audio at the captured position, report,
     * and surface a one-shot error. Returns true iff handled — the caller must then NOT run its audio
     * 403-refresh path (which operates on the real id and would invalidate the wrong cache entry).
     */
    fun onPlayerError(error: PlaybackException): Boolean {
        if (!_isVideoMode.value) return false
        videoRenditionId?.let { service.invalidateStreamCache(VideoRendition.key(it)) }
        reportException(error, "Video mode playback error")
        exitVideoModeSameItem()
        scope.launch { _videoErrorEvents.emit(Unit) }
        return true
    }

    /** Force audio (cast/error revert of the CURRENT, still-playing item — position-continuous). */
    fun revertToAudio() {
        if (_isVideoMode.value) exitVideoModeSameItem()
    }

    // ---- swap mechanics ----------------------------------------------------

    private fun enterVideoMode(rendition: VideoModeLogic.Rendition) {
        val index = player.currentMediaItemIndex
        val audioItem = player.currentMediaItem ?: return
        if (index == C.INDEX_UNSET) return

        videoModeItemId = audioItem.mediaId
        videoModeItemIndex = index
        videoModeAudioItem = audioItem
        renditionKind = rendition.kind
        videoRenditionId = rendition.renditionVideoId
        currentSurface?.let { player.setVideoSurfaceView(it) }

        if (rendition.kind == RenditionKind.LOCAL) {
            // The current (downloaded muxed) source already carries the video track — attaching the
            // surface renders it. No source swap, no seek (works offline).
            _isVideoMode.value = true
            return
        }

        val renditionId = rendition.renditionVideoId ?: run { clearState(); return }
        val position = player.currentPosition
        val playWhenReady = player.playWhenReady
        listenAccumulator.onSwap(audioItem.mediaId)
        pendingSwap = true
        player.replaceMediaItem(index, buildVideoItem(audioItem, renditionId))
        // Explicit seek is deterministic regardless of media3's replace-current-item position semantics.
        player.seekTo(index, position)
        player.prepare()
        player.playWhenReady = playWhenReady
        // Clear the pending mark after media3's swap callbacks have been dispatched (they are enqueued on
        // this Looper during the calls above; this post runs after them).
        mainHandler.post { pendingSwap = false }
        _isVideoMode.value = true
    }

    /** Exit video mode on the CURRENT item, position-continuous (user toggle-off / cast / block / error). */
    private fun exitVideoModeSameItem() {
        if (!_isVideoMode.value) return
        currentSurface?.let { player.clearVideoSurfaceView(it) }
        val kind = renditionKind
        val audioItem = videoModeAudioItem
        val index = player.currentMediaItemIndex
        if (kind != RenditionKind.LOCAL && audioItem != null && index != C.INDEX_UNSET) {
            val position = player.currentPosition
            val playWhenReady = player.playWhenReady
            videoModeItemId?.let { listenAccumulator.onSwap(it) }
            pendingSwap = true
            player.replaceMediaItem(index, audioItem)
            player.seekTo(index, position)
            player.prepare()
            player.playWhenReady = playWhenReady
            mainHandler.post { pendingSwap = false }
        }
        clearState()
    }

    /** A real track change moved off the video-mode item — restore the DEPARTED index to audio (I2). */
    private fun revertDepartedItem() {
        currentSurface?.let { player.clearVideoSurfaceView(it) }
        val kind = renditionKind
        val audioItem = videoModeAudioItem
        val index = videoModeItemIndex
        if (kind != RenditionKind.LOCAL && audioItem != null && index in 0 until player.mediaItemCount) {
            // A non-current replaceMediaItem fires no transition, so no pendingSwap dance is needed; this
            // just ensures a skip-back doesn't land on the video rendition.
            player.replaceMediaItem(index, audioItem)
        }
        clearState()
    }

    private fun clearState() {
        videoModeItemId = null
        videoModeItemIndex = C.INDEX_UNSET
        videoModeAudioItem = null
        renditionKind = null
        videoRenditionId = null
        _isVideoMode.value = false
    }

    private fun buildVideoItem(audioItem: MediaItem, renditionId: String): MediaItem {
        val key = VideoRendition.key(renditionId)
        // buildUpon() preserves mediaId + tag (MediaMetadata) + media3 MediaMetadata; only the URI and
        // cache key change to the video: namespace (isolating video bytes from the audio cache).
        return audioItem.buildUpon().setUri(key).setCustomCacheKey(key).build()
    }
}
