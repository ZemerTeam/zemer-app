package com.jtech.zemer.utils

/**
 * Utility class for building Zemer video links
 */
object VideoLinkBuilder {
    private const val ZEMER_VIDEO_BASE_URL = "https://video.zemer.io"
    private const val ZEMER_MUSIC_BASE_URL = "https://music.zemer.io"

    fun videoLink(videoId: String): String = "$ZEMER_VIDEO_BASE_URL/watch?v=$videoId"

    /** The shareable song link — the same URL MainActivity's `watch` deep link plays back. */
    fun watchLink(videoId: String): String = "$ZEMER_MUSIC_BASE_URL/watch?v=$videoId"

    /**
     * The shareable EPISODE link: the watch URL plus the owning show id. The `podcast` param routes
     * the receiving app's `watch` deep link to the podcast show screen instead of the music play
     * path (which is artist-whitelist filtered and dead-ends on an episode). Older receivers ignore
     * the extra param, so the link degrades to today's behavior rather than breaking.
     */
    fun episodeLink(videoId: String, podcastId: String?): String =
        if (podcastId.isNullOrBlank()) watchLink(videoId)
        else "${watchLink(videoId)}&podcast=$podcastId"

    /** The shareable channel link — the same URL MainActivity's `channel` deep link parses back. */
    fun channelLink(channelId: String): String = "$ZEMER_MUSIC_BASE_URL/channel/$channelId"
}