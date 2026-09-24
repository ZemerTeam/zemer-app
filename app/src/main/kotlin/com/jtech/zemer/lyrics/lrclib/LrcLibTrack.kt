package com.jtech.zemer.lyrics.lrclib

import kotlinx.serialization.Serializable

/**
 * One LRCLIB record, as returned by both `/api/search` (a hit the [LrcLib] provider gates) and `/api/get/<id>`
 * (the row the Zemer resolver vouched for by id, see `ZemerLyricsClient.lrclibBody`).
 */
@Serializable
data class LrcLibTrack(
    val id: Long = 0,
    val trackName: String = "",
    val artistName: String = "",
    val duration: Double = 0.0,
    val plainLyrics: String? = null,
    val syncedLyrics: String? = null,
    val instrumental: Boolean = false,
)
