package com.jtech.zemer.lyrics

import android.content.Context
import com.jtech.zemer.constants.EnableYouTubeLyricsKey
import com.jtech.zemer.utils.dataStore
import com.jtech.zemer.utils.get
import com.metrolist.innertube.YouTube

object YouTubeSubtitleLyricsProvider : LyricsProvider {
    override val name = "YouTube Subtitle"

    override fun isEnabled(context: Context): Boolean = context.dataStore[EnableYouTubeLyricsKey] ?: true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Result<String> = YouTube.transcript(id)
}
