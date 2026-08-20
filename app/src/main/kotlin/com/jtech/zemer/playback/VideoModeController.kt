package com.jtech.zemer.playback

import android.os.Handler
import android.os.Looper
import android.view.TextureView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import com.jtech.zemer.constants.BlockVideosKey
import com.jtech.zemer.constants.VideoQualityKey
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
    private var videoModeVideoKey: String? = null
    private var pendingSwap: Boolean = false
    private var currentSurface: TextureView? = null

    // ---- Quality state (the beyond-720p switcher) --------------------------
    // The persisted default target (VideoQualityKey; AUTO = the automatic progressive pick), kept
    // current by an async collector like blockVideosNow. AUTO is a safe synchronous seed: worst case
    // the first video-mode entry plays the automatic pick and upgrades when the ladder lands.
    @Volatile
    private var defaultVideoQuality: String = VideoQualityLogic.AUTO

    // Per-id quality ladders published by the stream resolver, and the CURRENT effective per-item
    // quality state. Two maps on purpose:
    //  - qualityOverrides = the effective session pick (an in-player choice OR a machine write from
    //    the rebuffer guard / error revert). Drives STREAMING entry.
    //  - userQualityPicks = ONLY the user's explicit switcher choices. Drives DOWNLOADS, so a
    //    transient guard-downgrade or an error's AUTO pin never silently downgrades a later download
    //    of that item (the user asked to watch, not to permanently save low quality).
    // Concurrent maps, NOT main-confined like the swap state: [downloadVideoQuality] is read from the
    // player menu's IO coroutine while the main thread writes picks — a plain HashMap would race.
    // Ladders are decoder-capability-filtered at publish ([VideoDecoderCaps]).
    private val qualityOverrides = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val userQualityPicks = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val qualityLadders = java.util.concurrent.ConcurrentHashMap<String, List<VideoQualityRung>>()

    // The itag of the rendition currently on the player (the plain automatic pick's resolved itag, or
    // an explicit rung's itag). Lets a switcher tap of the ALREADY-PLAYING quality no-op instead of
    // re-swapping identical bytes under a new cache key.
    private var currentRenditionItag: Int? = null

    // The effective quality TARGET for an item: an in-player pick if one exists, else the persisted
    // Settings default. A user who explicitly sets a quality (in Settings OR the in-player switcher)
    // gets it honored on EVERY connection — it is a deliberate choice, not "silent", so it is NOT
    // metered-gated (an earlier metered gate here dropped the Settings default to AUTO on cellular,
    // which read as "my setting is ignored"). Data protection lives where it belongs: the AUTOMATIC
    // pick (AUTO) keeps its metered bitrate cap, and the rebuffer guard downgrades a target the link
    // can't sustain. A user who wants to save data sets AUTO or a lower default.
    private fun effectiveQualityTarget(itemId: String?): String =
        qualityOverrides[itemId] ?: defaultVideoQuality

    private val _videoQualities = MutableStateFlow<List<VideoQualityRung>>(emptyList())
    /** The CURRENT video-mode item's selectable quality ladder (empty = no switcher). */
    val videoQualities: StateFlow<List<VideoQualityRung>> = _videoQualities.asStateFlow()

    private val _currentVideoQuality = MutableStateFlow(VideoQualityLogic.AUTO)
    /** The active rung's label, or [VideoQualityLogic.AUTO] for the automatic pick. */
    val currentVideoQuality: StateFlow<String> = _currentVideoQuality.asStateFlow()

    // Rebuffer guard state (main-thread confined, reset on every swap/exit): mid-play stall
    // timestamps for the CURRENT rendition, whether playback has reached READY since the last swap
    // (a prepare's initial buffering is not a stall), and a seek exemption (a user seek buffers
    // legitimately — MusicService.onPositionDiscontinuity flags it before the BUFFERING arrives).
    private val videoStallTimes = mutableListOf<Long>()
    private var videoReachedReady = false
    // Timestamp of the last user seek — a stall within SEEK_GRACE_MS of it is seek-caused, not a
    // network stall. A timestamp self-expires (a seek into an already-buffered region fires no
    // BUFFERING/READY to clear a boolean flag, so a stale flag would swallow the next real stall).
    private var lastSeekAtMs: Long = 0L

    // When we last swapped a video rendition back to plain audio (background revert, cast/block/error
    // revert). The revert seeks to the video position and re-prepares the bare-id audio, which must
    // re-fetch bytes from a region the audio cache never held (video bytes live in the isolated
    // video:/videoaudio: namespaces) — that fetch can fail transiently (a stale/absent URL, network
    // constrained during an app-switch). MusicService checks this window so such a failure REFRESHES
    // the audio URL and re-prepares (recover, keep music playing) instead of parking the player.
    @Volatile
    private var lastRevertToAudioAtMs: Long = 0L

    /**
     * True if a video→audio revert re-prepare happened within [windowMs] (its error is recoverable).
     * ONE-SHOT: consumes the window so the recovery is attempted once — a SECOND failure right after
     * falls through to normal error handling instead of looping the URL-refresh recovery.
     */
    fun revertedToAudioWithin(windowMs: Long): Boolean {
        val within = lastRevertToAudioAtMs != 0L &&
            android.os.SystemClock.elapsedRealtime() - lastRevertToAudioAtMs <= windowMs
        if (within) lastRevertToAudioAtMs = 0L
        return within
    }

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
        // The persisted default quality target (the Settings preference) — applies to NEW plays;
        // an in-player pick overrides it per item via qualityOverrides.
        scope.launch {
            service.dataStore.data
                .map { it[VideoQualityKey] ?: VideoQualityLogic.AUTO }
                .distinctUntilChanged()
                .collect { defaultVideoQuality = it }
        }
    }

    // ---- Quality switcher --------------------------------------------------

    /**
     * The stream resolver just served a video rendition from a player response — publish that
     * response's quality ladder (decoder-capability-filtered) for the switcher. Runs on the resolver's
     * IO thread; all state is touched on [scope] (main). If the automatic pick is playing and the
     * effective target is an explicit rung, upgrade to it now that the ladder is known
     * (position-continuous — the first entry never waits a second round-trip to start).
     */
    fun onVideoQualitiesResolved(renditionId: String, rungs: List<VideoQualityRung>, resolvedItag: Int? = null) {
        if (rungs.isEmpty()) return
        val usable = rungs.filter { it.progressive || VideoDecoderCaps.supports(it) }
        if (usable.isEmpty()) return
        scope.launch {
            qualityLadders[renditionId] = usable
            // RELAY serves one fixed rendition — a late (pre-relay-toggle) prefetch callback must
            // never swap an adaptive :q key into a relay session (the merge-audio half is not a
            // video: key and the relay factory can't resolve it). Publish the ladder, swap nothing.
            if (_isVideoMode.value && videoRenditionId == renditionId && !service.isRelayPlaybackMode()) {
                _videoQualities.value = usable
                if (resolvedItag != null && currentRenditionItag == null) currentRenditionItag = resolvedItag
                if (_currentVideoQuality.value == VideoQualityLogic.AUTO) {
                    val rung = VideoQualityLogic.selectRung(usable, effectiveQualityTarget(videoModeItemId))
                    if (rung != null) {
                        if (rung.itag == resolvedItag) {
                            // The automatic pick already streams exactly this rung — a re-swap would
                            // replace the same bytes under a new cache key (redundant prepare +
                            // duplicate spans). Surface the truthful label AND record the itag so a
                            // later tap of that same label no-ops instead of re-swapping.
                            currentRenditionItag = rung.itag
                            _currentVideoQuality.value = rung.label
                        } else {
                            swapToRung(rung)
                        }
                    }
                }
            }
        }
    }

    /**
     * The in-player quality switcher: swap the CURRENT video-mode item to [label]'s rung (or back to
     * the automatic pick for [VideoQualityLogic.AUTO]), position-continuous. The pick is remembered
     * for this item for the session. No-ops when not in video mode, on a LOCAL rendition (the
     * downloaded file has one baked quality — the switcher is hidden there), and in RELAY mode (the
     * relay serves one fixed rendition; quality keys must never reach the relay resolver).
     */
    fun setVideoQuality(label: String) {
        val itemId = videoModeItemId ?: return
        if (renditionKind == RenditionKind.LOCAL || service.isRelayPlaybackMode()) return
        // An explicit pick is recorded in BOTH maps: qualityOverrides drives the swap and future
        // entries, userQualityPicks is what downloads read (machine downgrades never land here).
        qualityOverrides[itemId] = label
        userQualityPicks[itemId] = label
        val renditionId = videoRenditionId ?: return
        // SABR pins an exact itag, so a quality pick RE-RESOLVES the dual-track session at the new target
        // and swaps (there is no DIRECT :q cache key to re-key). Tapping the rung already playing no-ops.
        if (service.isSabrPlaybackMode()) {
            if (label != VideoQualityLogic.AUTO) {
                val target = qualityLadders[renditionId]?.let { VideoQualityLogic.selectRung(it, label) }
                if (target != null && target.itag == currentRenditionItag) { _currentVideoQuality.value = target.label; return }
            }
            val audioItem = videoModeAudioItem ?: return
            resolveAndSwapSabr(renditionId, audioItem, player.currentMediaItemIndex, label, entry = false)
            return
        }
        if (label == VideoQualityLogic.AUTO) {
            // Back to the plain automatic key — itag is unknown until the resolver reports it.
            currentRenditionItag = null
            swapToVideoKey(VideoRendition.key(renditionId), VideoQualityLogic.AUTO)
            return
        }
        val rungs = qualityLadders[renditionId] ?: return
        VideoQualityLogic.selectRung(rungs, label)?.let { rung ->
            // Tapping the quality that is ALREADY playing (e.g. the automatic pick's true label) is a
            // no-op — never re-swap identical bytes under a new cache key.
            if (rung.itag == currentRenditionItag) {
                _currentVideoQuality.value = rung.label
            } else {
                swapToRung(rung)
            }
        }
    }

    /**
     * The quality label a video DOWNLOAD of [mediaId] should target, or null for the automatic
     * (progressive) pick: the user's EXPLICIT in-player pick for that item, else the persisted
     * default. Deliberately NOT the effective session state — a rebuffer downgrade or an error's
     * AUTO pin (both machine writes to qualityOverrides) must not silently downgrade a later
     * download of what the user chose to watch.
     */
    fun downloadVideoQuality(mediaId: String): String? =
        (userQualityPicks[mediaId] ?: defaultVideoQuality).takeIf { it != VideoQualityLogic.AUTO }

    private fun swapToRung(rung: VideoQualityRung) {
        val renditionId = videoRenditionId ?: return
        currentRenditionItag = rung.itag
        swapToVideoKey(VideoRendition.key(renditionId, rung.itag, rung.progressive), rung.label)
    }

    // ---- Rebuffer guard (avoid mid-play buffering: drop a rung instead of stalling) ------------

    /** MusicService hook: a user seek's buffering must not count as a stall (timestamp self-expires). */
    fun onSeekDiscontinuity() {
        lastSeekAtMs = android.os.SystemClock.elapsedRealtime()
    }

    /**
     * MusicService hook for playback-state changes. A STATE_BUFFERING after READY while a STREAMING
     * video rendition plays is a mid-play stall — the network cannot sustain the rung's bitrate.
     * Repeated stalls ([VideoQualityLogic.shouldDowngradeForRebuffer]) drop ONE rung,
     * position-continuous, and pin the item there so the ladder callback can't upgrade straight
     * back. Seek-caused buffering is exempt; LOCAL renditions and plain audio playback never count.
     */
    fun onPlaybackStateChanged(state: Int) {
        if (!_isVideoMode.value || renditionKind == RenditionKind.LOCAL) {
            videoReachedReady = false
            videoStallTimes.clear()
            return
        }
        when (state) {
            Player.STATE_READY -> videoReachedReady = true
            Player.STATE_BUFFERING -> {
                if (!videoReachedReady) return
                val now = android.os.SystemClock.elapsedRealtime()
                // A stall within the grace window of a user seek is seek-caused, not a network stall.
                if (now - lastSeekAtMs <= SEEK_GRACE_MS) return
                videoStallTimes.add(now)
                if (VideoQualityLogic.shouldDowngradeForRebuffer(videoStallTimes, now)) {
                    videoStallTimes.clear()
                    downgradeForStall()
                }
            }
            else -> {}
        }
    }

    private companion object {
        const val SEEK_GRACE_MS = 6_000L
    }

    private fun downgradeForStall() {
        val renditionId = videoRenditionId ?: return
        // AUTO already plays the cheapest single-stream pick — nothing to drop to.
        val current = _currentVideoQuality.value
        if (current == VideoQualityLogic.AUTO) return
        val rungs = qualityLadders[renditionId] ?: return
        // Step down exactly ONE rung, so playback settles on the HIGHEST rung that actually plays
        // (e.g. 2160p → 1440p → 1080p → 720p, stopping the moment 720p is stable). We deliberately do
        // NOT use the bandwidth estimate to jump multiple rungs: right after a stall media3's estimate
        // is depressed, and a rung's `bitrate` is its PEAK (well above its sustained average), so a
        // bandwidth-gated jump over-dropped (2160p → 480p when 720p was fine). One step at a time is
        // predictable and lands correctly; a genuinely slow link just takes a few steps to converge.
        val below = VideoQualityLogic.rungBelow(rungs, current) ?: return
        // Pin the item to the lower rung so onVideoQualitiesResolved can't bounce back up.
        videoModeItemId?.let { qualityOverrides[it] = below.label }
        // SABR re-resolves the dual-track session at the lower rung (no DIRECT :q key to re-swap).
        if (service.isSabrPlaybackMode()) {
            val audioItem = videoModeAudioItem ?: return
            resolveAndSwapSabr(renditionId, audioItem, player.currentMediaItemIndex, below.label, entry = false)
            return
        }
        swapToRung(below)
    }

    /** Replace our own current video rendition with the same item under [key] — the quality re-swap. */
    private fun swapToVideoKey(key: String, label: String) {
        // Never install a quality-rung key in a relay session (belt over the callers' own guards —
        // this is the single chokepoint that mutates the media item to a video: key).
        if (!_isVideoMode.value || renditionKind == RenditionKind.LOCAL || service.isRelayPlaybackMode()) return
        val index = player.currentMediaItemIndex
        if (index == C.INDEX_UNSET || index >= player.mediaItemCount) return
        val current = player.getMediaItemAt(index)
        val currentKey = current.localConfiguration?.customCacheKey ?: return
        // Only ever re-swap OUR parked rendition (same identity rule as exitVideoModeSameItem).
        if (current.mediaId != videoModeItemId || !VideoRendition.isVideoKey(currentKey)) return
        if (currentKey == key) {
            _currentVideoQuality.value = label
            return
        }
        val position = player.currentPosition
        val playWhenReady = player.playWhenReady
        videoModeItemId?.let { listenAccumulator.onSwap(it) }
        pendingSwap = true
        player.replaceMediaItem(index, current.buildUpon().setUri(key).setCustomCacheKey(key).build())
        player.seekTo(index, position)
        player.prepare()
        player.playWhenReady = playWhenReady
        mainHandler.post { pendingSwap = false }
        videoModeVideoKey = key
        _currentVideoQuality.value = label
        // A fresh rendition starts a fresh stall history (the swap's own prepare is not a stall).
        videoStallTimes.clear()
        videoReachedReady = false
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
            // The in-memory registry OR the persisted SongEntity.isVideo flag (Song.toMediaMetadata):
            // both are authoritative video classifications, and the persisted flag is what survives
            // process death — after a queue restore the registry is empty, so without it the toggle
            // silently disappeared for a flagged song until the on-expand probe round-tripped. A
            // LEARNED musicVideoType (including ATV) still overrides both — see availability().
            corpusVideoSong = VideoSongIds.contains(id) || meta.isVideo,
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
        if (!wasLocal) {
            // Invalidate the ACTUAL rendition key in play (quality rungs carry their itag in the key),
            // the plain automatic key (seeded by prefetch/entry from the SAME player response, so it
            // is dead too — leaving it lets a re-entry replay the dead URL until it times out), and
            // the merge-audio partner an adaptive rung streams alongside.
            videoModeVideoKey?.let { service.invalidateStreamCache(it) }
            videoRenditionId?.let {
                service.invalidateStreamCache(VideoRendition.key(it))
                service.invalidateStreamCache(VideoRendition.mergeAudioKey(it))
            }
            // Do NOT override the user's quality on error — a video-mode error is almost always a
            // stale/expired signed URL (fixed by the cache invalidation above → a fresh resolution on
            // re-entry), and a rung the device genuinely can't decode is already filtered out
            // (VideoDecoderCaps). Pinning AUTO here silently discarded the user's chosen quality on a
            // transient blip. A rung that truly can't sustain surfaces as stalls, not a hard error,
            // and the rebuffer guard handles that.
        }
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
            // surface renders it. No source swap, no seek (works offline). One baked quality — no
            // switcher (the ladder stays empty).
            _isVideoMode.value = true
            return
        }

        val renditionId = rendition.renditionVideoId ?: run { clearState(); return }

        // SABR video mode: resolve the dual-track (video + audio) session asynchronously, then swap. The
        // swapped item keeps a `video:<id>:q<itag>` CACHE KEY (so the exit/own-swap machinery recognises
        // it AND each rung is a distinct item) but a `sabrvideo://<id>` URI, which routes it to the
        // isolated SABR MergingMediaSource in createMediaSourceFactory (never the DIRECT/relay resolver).
        // SABR pins an exact itag, so the in-player quality switcher IS offered (unlike RELAY's fixed
        // rendition); a pick re-resolves. Gated on SABR mode, so DIRECT/RELAY video is byte-for-byte
        // unchanged. RELAY takes priority over SABR (mirrors the playback data-source dispatcher:
        // relay -> sabr -> direct), so relay video keeps its own &kind=video path.
        if (service.isSabrPlaybackMode() && !service.isRelayPlaybackMode()) {
            enterVideoModeSabr(audioItem, index, renditionId)
            return
        }
        // Quality: when this id's ladder is already known (a re-entry this session), enter DIRECTLY
        // at the effective target's rung; otherwise start on the automatic pick — the ladder arrives
        // with the resolution (onVideoQualitiesResolved) and upgrades position-continuously. RELAY
        // always uses the plain key (fixed server-side rendition — quality keys never reach it).
        val target = effectiveQualityTarget(audioItem.mediaId)
        val knownRungs = if (service.isRelayPlaybackMode()) null else qualityLadders[renditionId]
        val entryRung = knownRungs?.let { VideoQualityLogic.selectRung(it, target) }
        val videoKey = entryRung?.let { VideoRendition.key(renditionId, it.itag, it.progressive) }
            ?: VideoRendition.key(renditionId)
        _videoQualities.value = knownRungs.orEmpty()
        _currentVideoQuality.value = entryRung?.label ?: VideoQualityLogic.AUTO
        // Known rung → its itag; plain automatic key → unknown until the resolver reports it.
        currentRenditionItag = entryRung?.itag

        val position = player.currentPosition
        val playWhenReady = player.playWhenReady
        listenAccumulator.onSwap(audioItem.mediaId)
        pendingSwap = true
        player.replaceMediaItem(index, buildVideoItem(audioItem, videoKey))
        // Explicit seek is deterministic regardless of media3's replace-current-item position semantics.
        player.seekTo(index, position)
        player.prepare()
        player.playWhenReady = playWhenReady
        // Clear the pending mark after media3's swap callbacks have been dispatched (they are enqueued on
        // this Looper during the calls above; this post runs after them).
        mainHandler.post { pendingSwap = false }
        videoModeVideoKey = videoKey
        // Fresh rendition, fresh rebuffer-guard history (the entry prepare is not a stall).
        videoStallTimes.clear()
        videoReachedReady = false
        _isVideoMode.value = true
    }

    /**
     * SABR video entry: optimistically flip [_isVideoMode] on (audio keeps playing until the swap lands),
     * then resolve + swap at the effective quality target. Unlike RELAY, SABR pins an exact itag, so the
     * in-player switcher IS offered — the ladder is published on resolve and a pick re-resolves ([setVideoQuality]).
     */
    private fun enterVideoModeSabr(audioItem: MediaItem, index: Int, renditionId: String) {
        _videoQualities.value = emptyList()
        _currentVideoQuality.value = VideoQualityLogic.AUTO
        currentRenditionItag = null
        _isVideoMode.value = true
        resolveAndSwapSabr(renditionId, audioItem, index, effectiveQualityTarget(audioItem.mediaId), entry = true)
    }

    /**
     * Resolve a SABR dual-track session at [targetLabel] off the main thread, then swap on the main thread
     * — shared by the initial entry and every quality switch. Each rung gets its OWN `video:<id>:q<itag>`
     * cache key (so media3 re-prepares) over the same `sabrvideo://<id>` URI (merge routing); the ladder +
     * chosen rung are published for the switcher. Mirrors [enterVideoMode]'s pendingSwap/onSwap discipline
     * so the swap is never mis-classified as a real transition. [entry] true reverts to audio on failure;
     * a failed quality SWITCH keeps the current rung playing.
     */
    private fun resolveAndSwapSabr(renditionId: String, audioItem: MediaItem, index: Int, targetLabel: String, entry: Boolean) {
        val position = player.currentPosition
        val playWhenReady = player.playWhenReady
        scope.launch {
            val result = try {
                com.jtech.zemer.playback.sabr.SabrVideoResolver.resolve(renditionId, service.sabrEnabledClients(), targetLabel)
            } catch (e: Exception) {
                null
            }
            withContext(Dispatchers.Main) {
                // The user may have toggled off / skipped / the queue moved while resolving.
                val stillOurs = _isVideoMode.value && videoModeItemId == audioItem.mediaId &&
                    player.currentMediaItemIndex == index && player.currentMediaItem?.mediaId == audioItem.mediaId
                if (!stillOurs) {
                    com.jtech.zemer.playback.sabr.SabrVideoRegistry.remove(renditionId)
                    return@withContext
                }
                if (result == null) {
                    if (entry) { exitVideoModeSameItem(); _videoErrorEvents.emit(Unit) }
                    // A failed switch leaves the current rung playing (its stream is still registered).
                    return@withContext
                }
                qualityLadders[renditionId] = result.ladder
                _videoQualities.value = result.ladder
                _currentVideoQuality.value = result.chosen.label
                currentRenditionItag = result.chosen.itag
                val cacheKey = VideoRendition.key(renditionId, result.chosen.itag, progressive = false)
                videoModeVideoKey = cacheKey
                listenAccumulator.onSwap(audioItem.mediaId)
                pendingSwap = true
                player.replaceMediaItem(index, audioItem.buildUpon().setUri(result.uri).setCustomCacheKey(cacheKey).build())
                player.seekTo(index, position)
                player.prepare()
                player.playWhenReady = playWhenReady
                mainHandler.post { pendingSwap = false }
                videoStallTimes.clear()
                videoReachedReady = false
            }
        }
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
            // Mark the revert window so a transient failure of the audio re-prepare below recovers
            // (URL refresh + re-prepare) instead of parking the player — see MusicService.onPlayerError.
            lastRevertToAudioAtMs = android.os.SystemClock.elapsedRealtime()
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
        // End of this video-mode session: release the SABR dual-track stream (kills its drain thread +
        // drops the config). Every exit path funnels through clearState, so this is the ONE removal
        // point; a no-op for DIRECT/RELAY renditions (nothing registered under the id).
        videoRenditionId?.let { com.jtech.zemer.playback.sabr.SabrVideoRegistry.remove(it) }
        videoModeItemId = null
        videoModeItemIndex = C.INDEX_UNSET
        videoModeAudioItem = null
        renditionKind = null
        videoRenditionId = null
        videoModeVideoKey = null
        currentRenditionItag = null
        videoStallTimes.clear()
        videoReachedReady = false
        lastSeekAtMs = 0L
        _videoQualities.value = emptyList()
        _currentVideoQuality.value = VideoQualityLogic.AUTO
        _isVideoMode.value = false
    }

    private fun buildVideoItem(audioItem: MediaItem, videoKey: String): MediaItem {
        // buildUpon() preserves mediaId + tag (MediaMetadata) + media3 MediaMetadata; only the URI and
        // cache key change to the video: namespace (isolating video bytes from the audio cache).
        return audioItem.buildUpon().setUri(videoKey).setCustomCacheKey(videoKey).build()
    }
}
