package com.jtech.zemer.lyrics

import com.jtech.zemer.constants.EnableYouTubeLyricsKey
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.WatchEndpoint
import com.jtech.zemer.lyrics.model.LyricsUnavailableException

object YouTubeLyricsProvider : LyricsProvider {
    override val name = "YouTube Music"

    override val enabledKey = EnableYouTubeLyricsKey
    /** YouTube's transcript/tab is not identity-gated: served only when no trusted provider answered. */
    override val lowTrust = true

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
