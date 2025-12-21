package com.jtech.zemer.utils

/**
 * Utility class for building Zemer video links
 */
object VideoLinkBuilder {
    private const val ZEMER_VIDEO_BASE_URL = "https://video.zemer.io"

    fun videoLink(videoId: String): String = "$ZEMER_VIDEO_BASE_URL/watch?v=$videoId"
}