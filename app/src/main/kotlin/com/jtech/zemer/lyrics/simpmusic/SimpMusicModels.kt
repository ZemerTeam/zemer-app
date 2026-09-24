package com.jtech.zemer.lyrics.simpmusic

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One SimpMusic catalog entry - only the fields the provider reads (the client ignores unknown keys, so
 * the wire's title/artist/album/vote metadata is simply not modelled).
 */
@Serializable
data class SimpMusicLyricsData(
    @SerialName("durationSeconds")
    val duration: Int? = null,
    val syncedLyrics: String? = null,
    @SerialName("plainLyric")
    val plainLyrics: String? = null,
    val richSyncLyrics: String? = null,
)

@Serializable
data class SimpMusicApiResponse(
    val type: String? = null,
    val data: List<SimpMusicLyricsData> = emptyList(),
) {
    val success: Boolean
        get() = type == "success"
}
