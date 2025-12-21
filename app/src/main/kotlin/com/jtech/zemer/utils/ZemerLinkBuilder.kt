package com.jtech.zemer.utils

/**
 * Utility class for building Zemer music links
 */
object ZemerLinkBuilder {
    private const val ZEMER_BASE_URL = "https://music.zemer.io"

    fun songLink(videoId: String): String = "$ZEMER_BASE_URL/watch?v=$videoId"

    fun playlistLink(playlistId: String): String = "$ZEMER_BASE_URL/playlist?list=$playlistId"

    fun artistChannelLink(channelId: String): String = "$ZEMER_BASE_URL/channel/$channelId"

    fun albumLink(albumId: String): String = playlistLink(albumId) // Albums use playlist format
}