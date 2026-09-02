package com.jtech.zemer.lyrics

import android.content.Context
import com.jtech.zemer.constants.EnableYouTubeLyricsKey
import com.jtech.zemer.utils.dataStore
import com.jtech.zemer.utils.get
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.WatchEndpoint
import com.jtech.zemer.lyrics.model.LyricsUnavailableException

object YouTubeLyricsProvider : LyricsProvider {
    override val name = "YouTube Music"

    override fun isEnabled(context: Context): Boolean = context.dataStore[EnableYouTubeLyricsKey] ?: true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Result<String> =
        runCatching {
            val nextResult = YouTube.next(WatchEndpoint(videoId = id)).getOrThrow()
            YouTube
                .lyrics(
                    endpoint = nextResult.lyricsEndpoint
                        ?: throw LyricsUnavailableException,
                ).getOrThrow() ?: throw LyricsUnavailableException
        }
}
