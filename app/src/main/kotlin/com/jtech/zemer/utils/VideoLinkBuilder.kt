package com.jtech.zemer.utils

/**
 * Utility class for building Zemer video links
 */
object VideoLinkBuilder {
    private const val ZEMER_VIDEO_BASE_URL = "https://video.zemer.io"
    private const val ZEMER_MUSIC_BASE_URL = "https://music.zemer.io"

    fun videoLink(videoId: String): String = "$ZEMER_VIDEO_BASE_URL/watch?v=$videoId"

    /** The shareable channel link — the same URL MainActivity's `channel` deep link parses back. */
    fun channelLink(channelId: String): String = "$ZEMER_MUSIC_BASE_URL/channel/$channelId"
}