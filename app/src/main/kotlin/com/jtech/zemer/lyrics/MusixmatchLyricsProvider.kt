package com.jtech.zemer.lyrics

import android.content.Context
import com.jtech.zemer.constants.EnableMusixmatchKey
import com.jtech.zemer.lyrics.model.LyricsUnavailableException
import com.jtech.zemer.lyrics.musixmatch.MusixmatchLyrics

/** Musixmatch, on-device and gated (see [MusixmatchLyrics]); after the Zemer resolver and the videoId-keyed providers. */
object MusixmatchLyricsProvider : LyricsProvider {
    override val name = "Musixmatch"

    private lateinit var appContext: Context

    fun init(context: Context) { appContext = context.applicationContext }

    override val enabledKey = EnableMusixmatchKey

    /** A Zemer resolver `musixmatch` pointer, fetched by id under this phone's token (no title/artist matching: the server vetted the row). */
    suspend fun lyricsById(commontrackId: Long, trackId: Long, synced: Boolean): String? = MusixmatchLyrics.getLyricsById(appContext, commontrackId, trackId, synced)

    override suspend fun getLyrics(id: String, title: String, artist: String, duration: Int, album: String?): Result<String> = runCatching {
        val j = MusixmatchLyrics.getLyrics(appContext, title, artist, duration) ?: throw LyricsUnavailableException
        j.synced ?: j.plain
    }
}
