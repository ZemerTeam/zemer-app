package com.jtech.zemer.lyrics.lrclib

import kotlinx.serialization.Serializable

/** One LRCLIB `/api/search` hit. */
@Serializable
data class LrcLibTrack(
    val id: Int,
    val trackName: String,
    val artistName: String,
    val duration: Double,
    val plainLyrics: String?,
    val syncedLyrics: String?,
)
