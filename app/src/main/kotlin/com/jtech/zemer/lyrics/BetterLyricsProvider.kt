package com.jtech.zemer.lyrics

import android.content.Context
import com.jtech.zemer.constants.EnableBetterLyricsKey
import com.jtech.zemer.utils.dataStore
import com.jtech.zemer.utils.get
import com.metrolist.music.betterlyrics.BetterLyrics

object BetterLyricsProvider : LyricsProvider {
    override val name = "BetterLyrics"

    override fun isEnabled(context: Context): Boolean = context.dataStore[EnableBetterLyricsKey] ?: true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Result<String> = BetterLyrics.getLyrics(title, artist, duration, album)
}
