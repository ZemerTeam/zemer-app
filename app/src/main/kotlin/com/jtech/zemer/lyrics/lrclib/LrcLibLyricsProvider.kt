package com.jtech.zemer.lyrics.lrclib

import com.jtech.zemer.constants.EnableLrcLibKey
import com.jtech.zemer.lyrics.LyricsProvider

object LrcLibLyricsProvider : LyricsProvider {
    override val name = "LrcLib"

    override val enabledKey = EnableLrcLibKey

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Result<String> = LrcLib.getLyrics(title, artist, duration, album)
}
