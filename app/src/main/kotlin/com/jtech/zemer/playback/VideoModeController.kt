package com.jtech.zemer.playback

import android.os.Handler
import android.os.Looper
import android.view.TextureView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
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
    // paths). Seeded UNKNOWN (null), not false: the naive "seed false, the collector's first emission
    // lands before any toggle" reasoning misses a restored queue — computeAvailability can run
    // SYNCHRONOUSLY off recomputeNow() (MusicService.onEvents, on the current-item metadata change)
    // during queue restore, before blockVideosFlow's first DataStore emission arrives, for an item
    // that is ALREADY known video-capable from its persisted SongEntity.isVideo flag (Song.toMediaMetadata,
    // not the corpus tap boundary) with its local file already resolved (player.prepare() during
    // restore). A blocked user could see the toggle for that narrow window. Null means "not yet known"
    // and is read fail-safe as BLOCKED (never leak availability while the real answer is unknown).
    @Volatile
    private var blockVideosNow: Boolean? = null

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

    private val _videoModeAvailable = MutableStateFlow(false)

    /**
     * Whether the current item can show video (and the toggle should appear). The UI must read THIS and
     * not re-derive block/cast/availability conditions itself.
     *
     * Published two ways: the combine below reacts to every async input (cast/block/cache/network/
     * station), and [recomputeNow] republishes SYNCHRONOUSLY from the service the moment the current
     * item changes — the flow-propagation hops of a nested combine land a few frames after the
     * metadata, which flashed the pill in mid player-open. Synchronous republish makes the pill state
     * atomic with the track.
     */
    val videoModeAvailable: StateFlow<Boolean> = _videoModeAvailable.asStateFlow()

    /** Recompute availability in the CALLER's stack (main) — see [videoModeAvailable]. */
    fun recomputeNow() {
        _videoModeAvailable.value = computeAvailability() != null
    }

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
        // The async availability inputs — anything recomputeNow's synchronous path can't observe.
        scope.launch {
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
            ) { _, _ -> }.collect { recomputeNow() }
        }
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
            // Unknown (not-yet-collected) reads as BLOCKED — never leak the toggle while the real
            // preference value hasn't arrived yet (see the field's kdoc).
            blockVideos = blockVideosNow ?: true,
            stationBroadcast = service.isStationBroadcast.value,
            localVideoFile = meta.isVideo && service.playbackSourceIsLocalFile(id),
            online = service.isNetworkConnected.value,
            musicVideoType = avail?.musicVideoType,
            corpusVideoSong = VideoSongIds.contains(id),
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

    // Ids currently in-flight OR permanently resolved — one SUCCESSFUL metadata call per item, ever
    // (dedups concurrent/duplicate requests too). A failed fetch is removed again so a later call can
    // retry — see [requestVideoAvailability]. Mutated only inside [scope] (main), but requests may
    // ARRIVE from the data-source resolver thread, so [requestVideoAvailability] hops onto the scope
    // before touching it.
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
                    blockVideos = blockVideosNow ?: true,
                    musicVideoType = availabilityCache.get(mediaId)?.musicVideoType,
                    counterpartResolved = availabilityCache.get(mediaId)?.counterpartResolved == true,
                )
            ) {
                return@launch
            }
            if (!service.isNetworkConnected.value || !availabilityProbed.add(mediaId)) return@launch
            val result = withContext(Dispatchers.IO) {
                YTPlayerUtils.playerResponseForMetadata(mediaId)
            }
            if (result.isFailure) {
                // Transient failure (not "successfully learned unknown") — un-mark so a LATER call
                // (re-open the player, re-tap, next session's cache hit) gets a fresh attempt instead
                // of the toggle staying silently hidden for this id for the rest of the process.
                availabilityProbed.remove(mediaId)
                return@launch
            }
            recordMusicVideoType(mediaId, result.getOrNull()?.videoDetails?.musicVideoType)
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
    fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int): Boolean =
        when (
            VideoModeLogic.classifyTransition(
                pendingSwap = pendingSwap,
                isRepeatOfSameItem = reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT,
                newMediaId = mediaItem?.mediaId,
                videoModeItemId = videoModeItemId,
            )
        ) {
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
        // media3 MASKS transport commands: seekToNext()/seekToPrevious() update currentMediaItemIndex
        // synchronously on the calling thread, before the corresponding onMediaItemTransition actually
        // dispatches. If an exit trigger unrelated to that skip (block flag flipping, cast connecting,
        // an error) lands in that gap, `index` already points at the NEWLY selected item, not ours —
        // trusting it blindly (as this used to) would replaceMediaItem the user's fresh selection with
        // the departed audio item. Verify identity first, mirroring revertDepartedItem's by-identity
        // check; on a mismatch the in-flight transition's own TRACK_CHANGE -> revertDepartedItem() will
        // correctly restore the ACTUAL departed item, so clearState() here is enough.
        val itemAtIndexIsOurs = index != C.INDEX_UNSET && index < player.mediaItemCount &&
            run {
                val item = player.getMediaItemAt(index)
                val isVideoKey = item.localConfiguration?.customCacheKey
                    ?.let { VideoRendition.isVideoKey(it) } ?: false
                VideoModeLogic.shouldRestoreDepartedItem(videoModeItemId, item.mediaId, isVideoKey)
            }
        if (kind != RenditionKind.LOCAL && audioItem != null && itemAtIndexIsOurs) {
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
