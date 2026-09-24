package com.jtech.zemer.lyrics

import com.jtech.zemer.constants.EnableSimpMusicKey
import com.jtech.zemer.lyrics.simpmusic.SimpMusicLyrics

object SimpMusicLyricsProvider : LyricsProvider {
    override val name = "SimpMusic"

    override val enabledKey = EnableSimpMusicKey

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Result<String> = SimpMusicLyrics.getLyrics(id, duration)
}
