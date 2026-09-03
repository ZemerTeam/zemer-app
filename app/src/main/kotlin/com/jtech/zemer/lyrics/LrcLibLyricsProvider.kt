package com.jtech.zemer.lyrics

import com.metrolist.lrclib.LrcLib
import com.jtech.zemer.constants.EnableLrcLibKey

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

    override suspend fun getAllLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
        callback: (String) -> Unit,
    ) {
        LrcLib.getAllLyrics(title, artist, duration, album, callback)
    }
}
