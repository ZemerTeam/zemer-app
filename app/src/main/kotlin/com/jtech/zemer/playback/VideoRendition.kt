package com.jtech.zemer.playback

/**
 * The `customCacheKey`/URI namespace that marks a queue item's MediaItem as its **video rendition**
 * rather than the audio stream. The video-mode swap ([VideoModeController]) replaces the current
 * MediaItem with one keyed `video:<renditionVideoId>`; the resolving data source in
 * [MusicService.createDataSourceFactory] recognizes the prefix and resolves a progressive muxed
 * video stream instead of audio.
 *
 * The prefix is what keeps video bytes out of the audio cache: audio and video renditions of the same
 * id MUST NOT share a cache key (they are different containers), so the key is always the sole
 * discriminator — never reuse a bare videoId for a video rendition.
 */
object VideoRendition {
    const val PREFIX = "video:"

    fun key(renditionVideoId: String): String = PREFIX + renditionVideoId

    fun isVideoKey(key: String): Boolean = key.startsWith(PREFIX)

    /** The bare rendition videoId behind a `video:` key (returns the input unchanged if not a video key). */
    fun renditionId(key: String): String = key.removePrefix(PREFIX)

    /** Max muxed-video bitrate (kbps) on a metered connection (the old VideoPlayerScreen's caps). */
    const val METERED_MAX_KBPS = 1500

    /** Max muxed-video bitrate (kbps) on an unmetered connection. */
    const val UNMETERED_MAX_KBPS = 6000

    /**
     * The ONE video bitrate policy, shared by streaming (video-mode swap) and downloads (muxed save)
     * so neither path can silently fetch YouTube's largest file on a metered connection.
     */
    fun defaultMaxBitrateKbps(metered: Boolean): Int = if (metered) METERED_MAX_KBPS else UNMETERED_MAX_KBPS
}
