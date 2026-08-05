package com.jtech.zemer.playback

import android.os.Handler
import android.os.Looper
import android.view.TextureView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import com.jtech.zemer.constants.BlockVideosKey
import com.jtech.zemer.playback.VideoModeLogic.RenditionKind
import com.jtech.zemer.playback.VideoModeLogic.TransitionClass
import com.jtech.zemer.utils.BlockedIdsCache
import com.jtech.zemer.utils.ContentFilterState
import com.jtech.zemer.utils.YTPlayerUtils
import com.jtech.zemer.utils.dataStore
import com.jtech.zemer.utils.reportException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    private var currentSurface: TextureView? = null

    // Latest BlockVideosKey value, kept current by the block collector in init{} — so availability never
    // does a blocking dataStore read on the main thread (the combine transform + setVideoMode are hot/UI
    // paths). Seeded false rather than read eagerly: this lazy controller is first constructed on the main
    // thread (PlayerConnection reads its flows in its property initializers), and the collector below
    // publishes the real value on its first emission (immediate for a DataStore flow) before any toggle.
    @Volatile
    private var blockVideosNow: Boolean = false

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
            combine(
                service.currentMediaMetadata,
                service.discoveryHandler.remoteConnectionState,
                blockVideosFlow,
                availabilityCache.revision,
                // A station broadcast starting/ending must re-evaluate (the toggle is never offered
                // during a broadcast); recompute covers local-file source resolution.
                combine(service.isStationBroadcast, recompute) { _, _ -> },
            ) { _, _, _, _, _ -> },
            // Connectivity gates the streaming renditions (a downloaded LOCAL file stays available offline).
            service.isNetworkConnected,
        ) { _, _ -> computeAvailability() != null }
            .stateIn(scope, SharingStarted.Eagerly, false)

    /**
     * Whether the CURRENT item should download its muxed video rather than audio-only (Option A). The
     * player download menu reads this so a video-capable item is never saved audio-only (which would
     * leave the toggle silently streaming). Connectivity-independent (you download while online, and a
     * blocked item's row is hidden by [DownloadMenuLogic] regardless).
     */
    val currentItemIsVideo: StateFlow<Boolean> =
        combine(service.currentMediaMetadata, availabilityCache.revision) { meta, _ ->
            meta != null &&
                VideoModeLogic.isVideoDownloadItem(availabilityCache.get(meta.id)?.musicVideoType, meta.isVideo)
        }.stateIn(scope, SharingStarted.Eagerly, false)

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
            blockVideosFlow.collect { blocked ->
                blockVideosNow = blocked
                if (blocked && _isVideoMode.value) revertToAudio()
            }
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
            blockVideos = blockVideosNow,
            stationBroadcast = service.isStationBroadcast.value,
            localVideoFile = meta.isVideo && service.playbackSourceIsLocalFile(id),
            online = service.isNetworkConnected.value,
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

    /** Attach the render surface. Applied to the player only while in video mode. */
    fun setVideoSurface(view: TextureView?) {
        currentSurface = view
        if (_isVideoMode.value) player.setVideoTextureView(view)
    }

    /**
     * Detach [view] — but only if it is still the attached surface. Makes the inline↔fullscreen handoff
     * order-independent: a leaving view's `onDispose` can't detach the surface a newly-composed view just
     * attached (the two live in different Compose subtrees, so their dispose/attach order is not
     * guaranteed). `clearVideoTextureView` is itself a no-op in ExoPlayer if [view] isn't the active one.
     */
    fun clearVideoSurface(view: TextureView) {
        if (currentSurface === view) currentSurface = null
        player.clearVideoTextureView(view)
    }

    // Ids already probed this session — one metadata call per item, ever. Mutated only inside
    // [scope] (main), but requests may ARRIVE from the data-source resolver thread, so
    // [requestVideoAvailability] hops onto the scope before touching it.
    private val availabilityProbed = mutableSetOf<String>()

    /**
     * On-demand SELF-type probe for the expanded player's current item. Normally the type comes free
     * from the item's stream resolution ([recordMusicVideoType]) — but the availability cache is
     * in-memory and a disk-cache hit SKIPS resolution entirely (MusicService's cached early-return),
     * so after a process restart a fully-cached video-song would never record its type and the
     * Song/Video toggle would silently stay hidden. One metadata-only player call per unknown item
     * per session closes that hole. (On-demand COUNTERPART discovery stays dormant — the step-3
     * authenticated `next()` probe found none; this records the item's OWN type only.)
     */
    fun requestVideoAvailability(mediaId: String) {
        // Callable from any thread (the expanded player AND the data-source resolver) — all state is
        // touched on [scope] (main).
        scope.launch {
            if (!VideoModeLogic.shouldRequestAvailability(
                    casting = service.discoveryHandler.isConnected,
                    blockVideos = blockVideosNow,
                    musicVideoType = availabilityCache.get(mediaId)?.musicVideoType,
                    counterpartResolved = availabilityCache.get(mediaId)?.counterpartResolved == true,
                )
            ) {
                return@launch
            }
            if (!service.isNetworkConnected.value || !availabilityProbed.add(mediaId)) return@launch
            val type = withContext(Dispatchers.IO) {
                YTPlayerUtils.playerResponseForMetadata(mediaId).getOrNull()?.videoDetails?.musicVideoType
            }
            recordMusicVideoType(mediaId, type)
        }
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
     *
     * A [RenditionKind.LOCAL] error is NOT handled here (returns false): LOCAL never swapped the
     * source, so the failure is the downloaded file itself — exactly what the service's normal error
     * pipeline (self-repair, network wait, auto-skip) exists for. Exiting only clears the video-mode
     * state; without the service pipeline the player would sit in ERROR forever (nothing re-prepares).
     */
    fun onPlayerError(error: PlaybackException): Boolean {
        if (!_isVideoMode.value) return false
        val wasLocal = renditionKind == RenditionKind.LOCAL
        if (!wasLocal) videoRenditionId?.let { service.invalidateStreamCache(VideoRendition.key(it)) }
        reportException(error, "Video mode playback error")
        exitVideoModeSameItem()
        scope.launch { _videoErrorEvents.emit(Unit) }
        return !wasLocal
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
        currentSurface?.let { player.setVideoTextureView(it) }

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
        currentSurface?.let { player.clearVideoTextureView(it) }
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
            // Flip the UI to audio now, but keep the swap-classification identity
            // (videoModeItemId + pendingSwap) alive until media3's swap callbacks have been
            // dispatched — clearing it synchronously would make the restore swap's own
            // onMediaItemTransition classify as a real TRACK_CHANGE, which clears the
            // ListenAccumulator's swap mark and double-counts the listen (double play event +
            // history insert). Mirrors enterVideoMode's deferred pendingSwap handling.
            _isVideoMode.value = false
            mainHandler.post {
                pendingSwap = false
                clearState()
            }
        } else {
            clearState()
        }
    }

    /** A real track change moved off the video-mode item — restore the DEPARTED item to audio (I2). */
    private fun revertDepartedItem() {
        currentSurface?.let { player.clearVideoTextureView(it) }
        val kind = renditionKind
        val audioItem = videoModeAudioItem
        val departedId = videoModeItemId
        if (kind != RenditionKind.LOCAL && audioItem != null && departedId != null) {
            // Find OUR parked video rendition by identity (same mediaId AND a video: cache key), not the
            // stored index. A within-queue transition (skip/seek/auto-advance) leaves it where it was, but
            // a queue reorder during video mode can move it off videoModeItemIndex WITHOUT firing a
            // transition — so an index-only check would miss it and leave an orphaned video: item that
            // later streams muxed video with no surface. A fresh playQueue()/setMediaItems() replaced the
            // whole timeline, so nothing matches (different ids / no video key) and we clobber nothing —
            // the "tap a new song while in video mode plays the wrong item" bug stays fixed.
            for (i in 0 until player.mediaItemCount) {
                val item = player.getMediaItemAt(i)
                val isVideoKey = item.localConfiguration?.customCacheKey
                    ?.let { VideoRendition.isVideoKey(it) } ?: false
                if (VideoModeLogic.shouldRestoreDepartedItem(departedId, item.mediaId, isVideoKey)) {
                    // A non-current replaceMediaItem fires no transition, so no pendingSwap dance is needed.
                    player.replaceMediaItem(i, audioItem)
                    break
                }
            }
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
