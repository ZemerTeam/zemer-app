package com.jtech.zemer.lyrics

import com.jtech.zemer.constants.EnableYouTubeLyricsKey
import com.metrolist.innertube.YouTube

object YouTubeSubtitleLyricsProvider : LyricsProvider {
    override val name = "YouTube Subtitle"

    override val enabledKey = EnableYouTubeLyricsKey
    /** YouTube's transcript/tab is not identity-gated: served only when no trusted provider answered. */
    override val lowTrust = true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Result<String> = YouTube.transcript(id)
}
