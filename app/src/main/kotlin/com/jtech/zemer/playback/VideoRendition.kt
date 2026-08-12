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

    private const val PROGRESSIVE_ITAG_MARK = ":p"
    private const val ADAPTIVE_ITAG_MARK = ":q"
    private const val MERGE_AUDIO_PREFIX = "videoaudio:"

    fun key(renditionVideoId: String): String = PREFIX + renditionVideoId

    /**
     * The key for an EXPLICIT quality rung: `video:<id>:p<itag>` for a progressive (muxed) rung,
     * `video:<id>:q<itag>` for an adaptive video-only rung. The itag lives IN the key so two rungs'
     * bytes can never share cache spans (the container-mixing corruption class), and the `:q` mark is
     * what tells the media-source factory this item needs the audio merge. The plain `video:<id>` key
     * stays the automatic progressive pick (back-compat with existing cached spans).
     */
    fun key(renditionVideoId: String, itag: Int, progressive: Boolean): String =
        PREFIX + renditionVideoId + (if (progressive) PROGRESSIVE_ITAG_MARK else ADAPTIVE_ITAG_MARK) + itag

    fun isVideoKey(key: String): Boolean = key.startsWith(PREFIX)

    /** An adaptive video-only rendition key — playback must MERGE it with the [mergeAudioKey] stream. */
    fun isAdaptiveVideoKey(key: String): Boolean =
        isVideoKey(key) && key.removePrefix(PREFIX).contains(ADAPTIVE_ITAG_MARK)

    /**
     * The bare rendition videoId behind a `video:` key, any itag suffix stripped (returns the input
     * unchanged if not a video key). videoIds never contain ':', so splitting on it is safe.
     */
    fun renditionId(key: String): String = key.removePrefix(PREFIX).substringBefore(':')

    /** The explicit itag encoded in a rendition key, or null for the plain automatic key. */
    fun renditionItag(key: String): Int? {
        val suffix = key.removePrefix(PREFIX).substringAfter(':', missingDelimiterValue = "")
        if (suffix.length < 2 || (suffix[0] != 'p' && suffix[0] != 'q')) return null
        return suffix.drop(1).toIntOrNull()
    }

    /**
     * The cache key of the AUDIO stream merged under an adaptive video rendition. Distinct from the
     * bare id (the normal audio path's key) so merge-audio bytes can never mix with the main audio
     * cache's spans (which may hold a different itag/container).
     */
    fun mergeAudioKey(renditionVideoId: String): String = MERGE_AUDIO_PREFIX + renditionVideoId

    fun isMergeAudioKey(key: String): Boolean = key.startsWith(MERGE_AUDIO_PREFIX)

    /**
     * Every cache key the id's video renditions may have written — the plain key, all itag-suffixed
     * keys, and the merge-audio key. The delete path purges these so stale spans can't poison a later
     * play ([MusicService]'s cache-correctness invariant).
     */
    fun allRenditionKeys(renditionVideoId: String, cachedKeys: Collection<String>): List<String> {
        val plain = key(renditionVideoId)
        return cachedKeys.filter {
            it == plain || it.startsWith("$plain:") || it == mergeAudioKey(renditionVideoId)
        }
    }

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
