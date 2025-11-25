package com.metrolist.music.lyrics

import android.content.Context
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.WatchEndpoint
import com.metrolist.music.lyrics.model.LyricsUnavailableException

object YouTubeLyricsProvider : LyricsProvider {
    override val name = "YouTube Music"

    override fun isEnabled(context: Context) = true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
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
