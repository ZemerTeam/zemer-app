package com.jtech.zemer.lyrics.simpmusic

import com.jtech.zemer.constants.EnableSimpMusicKey
import com.jtech.zemer.lyrics.LyricsProvider

object SimpMusicLyricsProvider : LyricsProvider {
    override val name = "SimpMusic"

    override val enabledKey = EnableSimpMusicKey

    /** A Zemer resolver `simpmusic` pointer, fetched as the exact verified entry (no duration matching: the server vetted the row). */
    suspend fun lyricsByEntry(videoId: String, entryId: String, synced: Boolean): String? = SimpMusicLyrics.getLyricsByEntry(videoId, entryId, synced)

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Result<String> = SimpMusicLyrics.getLyrics(id, duration)
}
