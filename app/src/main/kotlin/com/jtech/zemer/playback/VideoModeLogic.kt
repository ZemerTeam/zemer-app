package com.jtech.zemer.playback

import com.metrolist.innertube.models.WatchEndpoint.WatchEndpointMusicSupportedConfigs.WatchEndpointMusicConfig.Companion.MUSIC_VIDEO_TYPE_ATV

/**
 * Pure decision logic for video mode — no player, no Android, fully JVM-unit-tested. [VideoModeController]
 * owns the state machine and player mutations; everything that can be a pure function lives here so the
 * "when is video available / is this transition my own swap" rules are provable without a device.
 */
object VideoModeLogic {

    /** Which video rendition (if any) the current queue item can show. */
    enum class RenditionKind {
        /** The item already plays a downloaded muxed video file — attach a surface, no source swap (works offline). */
        LOCAL,

        /** The item is itself a video (musicVideoType != ATV) — the rendition id is its own id. */
        SELF,

        /** The item is an audio song with a known video counterpart — the rendition id is the counterpart's id. */
        COUNTERPART,
    }

    /**
     * @param renditionVideoId the id to resolve a video stream for on a swap; null for [RenditionKind.LOCAL]
     *   (no swap — the current source already carries the video track).
     */
    data class Rendition(val kind: RenditionKind, val renditionVideoId: String?)

    /**
     * The single source of truth for "can the current item show video, and as what". Returns null when
     * video is unavailable (the toggle must then be hidden). Re-evaluated whenever any input changes
     * (current item, cast state, block flag, availability cache) and again at the moment the toggle is
     * pressed.
     *
     * @param mediaId the current queue item's id (the audio/canonical id).
     * @param casting true while a cast session is connected (I5: video and cast are mutually exclusive).
     * @param blockVideos the [com.jtech.zemer.constants.BlockVideosKey] content filter (I1: blocked ⇒ no video anywhere).
     * @param localVideoFile true when the item is playing from a downloaded muxed video file.
     * @param online true when the device has validated internet. A [RenditionKind.SELF]/[RenditionKind.COUNTERPART]
     *   rendition STREAMS (I8: the toggle makes no offline promises beyond a downloaded muxed file), so it is
     *   offered only when online — otherwise tapping it would just throw a source error and revert. [RenditionKind.LOCAL]
     *   plays from disk and stays available offline.
     * @param musicVideoType the item's own music-video type from the availability cache (null = unknown).
     * @param corpusVideoSong true when the CORPUS classified the item as a video-song
     *   ([com.jtech.zemer.playback.VideoSongIds] — authoritative server data, not a guess). Grants
     *   SELF availability instantly while YouTube's own type is still unknown; a LEARNED type always
     *   governs (an ATV verdict overrides the corpus and hides the toggle).
     * @param counterpartVideoId a known video counterpart id for the item, or null.
     * @param isBlockedRendition content-filter check for a specific rendition id (BlockedIdsCache) — a
     *   Firestore-blocked video is never watchable even when its audio side is fine.
     */
    fun availability(
        mediaId: String,
        casting: Boolean,
        blockVideos: Boolean,
        stationBroadcast: Boolean = false,
        relayMode: Boolean = false,
        localVideoFile: Boolean,
        online: Boolean,
        musicVideoType: String?,
        corpusVideoSong: Boolean = false,
        counterpartVideoId: String?,
        isBlockedRendition: (String) -> Boolean,
    ): Rendition? {
        // I5 + I1: no video while casting or when videos are blocked. A Zemer Station broadcast is
        // play/stop only — the toggle would mutate the synchronized timeline behind every station
        // transport guard, so it is simply never offered there. RELAY playback is audio-only (the relay
        // serves audio bytes for a videoId), so a video rendition would draw a blank surface — hide it.
        if (casting || blockVideos || stationBroadcast || relayMode) return null

        // LOCAL first: a downloaded muxed file plays the video track with no swap and works offline.
        if (localVideoFile && !isBlockedRendition(mediaId)) {
            return Rendition(RenditionKind.LOCAL, null)
        }

        // Everything past here STREAMS the video — never offer it offline (it would only throw a source
        // error and revert). A downloaded muxed file is the sole offline video path (LOCAL, above).
        if (!online) return null

        // SELF: the item is itself a video. Requires a KNOWN non-ATV type; a known ATV type falls
        // through to the counterpart tier (an audio song can still have a separate music video).
        if (musicVideoType != null && musicVideoType != MUSIC_VIDEO_TYPE_ATV) {
            return if (isBlockedRendition(mediaId)) null else Rendition(RenditionKind.SELF, mediaId)
        }

        // COUNTERPART: an audio song with an authoritative video counterpart.
        if (counterpartVideoId != null) {
            return if (isBlockedRendition(counterpartVideoId)) null
            else Rendition(RenditionKind.COUNTERPART, counterpartVideoId)
        }

        // Type still UNKNOWN, but the CORPUS classified this a video-song — authoritative server data
        // (every Zemer videos-category row), not a guess: SELF, instantly, no round-trip. A LEARNED
        // ATV type never reaches here (it exits null above via the counterpart tier), so YouTube's own
        // verdict still governs once known.
        if (musicVideoType == null && corpusVideoSong) {
            return if (isBlockedRendition(mediaId)) null else Rendition(RenditionKind.SELF, mediaId)
        }

        return null
    }

    /**
     * Whether the availability cache should be probed on-demand for the current item — true only when a
     * counterpart could still turn up (type is ATV or unknown AND we haven't resolved a counterpart yet)
     * and video is otherwise permitted. Keeps the UI from firing a `next()` for items we already know
     * about (a resolved item, or a self-video which needs no counterpart).
     */
    fun shouldRequestAvailability(
        casting: Boolean,
        blockVideos: Boolean,
        musicVideoType: String?,
        counterpartResolved: Boolean,
        relayMode: Boolean = false,
    ): Boolean {
        if (casting || blockVideos || counterpartResolved || relayMode) return false
        // A known self-video needs no counterpart lookup; only ATV/unknown items do.
        return musicVideoType == null || musicVideoType == MUSIC_VIDEO_TYPE_ATV
    }

    /**
     * Whether the current playing item should download its **muxed video** (Option A) rather than
     * audio-only. True when the item is itself a video — either already flagged ([metadataIsVideo]) or a
     * known non-ATV music-video type from the availability cache. Saving the muxed file means the
     * Song/Video toggle then works fully offline via [RenditionKind.LOCAL] (no streaming, no source
     * error), and one "Remove" truthfully covers both renditions — a video item never gets downloaded
     * audio-only, which would leave the toggle silently streaming (the reported "download → toggle
     * source error" bug). A plain ATV song stays an audio download.
     */
    fun isVideoDownloadItem(musicVideoType: String?, metadataIsVideo: Boolean): Boolean =
        metadataIsVideo || (musicVideoType != null && musicVideoType != MUSIC_VIDEO_TYPE_ATV)

    enum class TransitionClass {
        /**
         * Not a real move to another listen — keep video mode, skip side effects (cast reload,
         * auto-load-more, save-queue, station resync). Either our own rendition swap (replaceMediaItem
         * of the current item) or a repeat-one loop restarting the SAME video item — both leave the
         * user watching the same content, just re-entering it.
         */
        OWN_SWAP,

        /** A real move to another listen (auto-advance, skip, a different item) — revert to audio (I2). */
        TRACK_CHANGE,
    }

    /**
     * Classifies an `onMediaItemTransition`. Because a swap preserves the item's mediaId, our own swap is
     * the transition that arrives while a swap is pending AND still on the same item. A repeat-one loop
     * of the SAME video item (media3 fires `MEDIA_ITEM_TRANSITION_REASON_REPEAT`) is likewise NOT a real
     * track change — reverting there would kick the user back to audio, un-seeked, on every single loop,
     * which is exactly the opposite of what repeat-one promises. Everything else — a different id
     * (advance/skip), or a repeat restart while NOT in video mode for that id — is a real track change
     * that must revert video mode. Robust to the swap firing no transition at all: the pending mark is
     * cleared deterministically by the controller after the swap, so a later real transition always
     * classifies as TRACK_CHANGE.
     */
    fun classifyTransition(
        pendingSwap: Boolean,
        isRepeatOfSameItem: Boolean,
        newMediaId: String?,
        videoModeItemId: String?,
    ): TransitionClass =
        if (videoModeItemId != null &&
            newMediaId == videoModeItemId &&
            (pendingSwap || isRepeatOfSameItem)
        ) {
            TransitionClass.OWN_SWAP
        } else {
            TransitionClass.TRACK_CHANGE
        }

    /**
     * Whether reverting a departed video-mode item should write the audio item back at its old index.
     *
     * Only true when the item STILL sitting at that index is our own video rendition — i.e. the same
     * mediaId AND a `video:` cache key. A within-queue transition (skip/seek/auto-advance) leaves our
     * rendition parked at that index, so we restore it to audio (a skip-back must not land on video).
     *
     * But a fresh `playQueue()`/`setMediaItems()` replaces the whole timeline: the old index now holds a
     * DIFFERENT item (the just-tapped song). Blindly writing there clobbers it — the "tap a new song while
     * in video mode plays the wrong item" bug. In that case (id mismatch, or same id but no longer a video
     * key) return false and leave the new queue untouched.
     */
    fun shouldRestoreDepartedItem(
        departedItemId: String?,
        itemAtIndexId: String?,
        itemAtIndexIsVideoKey: Boolean,
    ): Boolean =
        departedItemId != null && itemAtIndexId == departedItemId && itemAtIndexIsVideoKey
}
