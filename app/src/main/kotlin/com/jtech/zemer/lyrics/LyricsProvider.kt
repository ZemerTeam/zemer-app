package com.jtech.zemer.lyrics

import android.content.Context

/** A lyrics body plus the label to persist in `LyricsEntity.provider` ("Zemer · jkaraoke", "SimpMusic", ...). */
data class LabeledLyrics(val label: String, val lyrics: String)

interface LyricsProvider {
    val name: String

    fun isEnabled(context: Context): Boolean

    suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String? = null,
    ): Result<String>

    suspend fun getAllLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String? = null,
        callback: (String) -> Unit,
    ) {
        getLyrics(id, title, artist, duration, album).onSuccess(callback)
    }

    /**
     * [getLyrics] with the provenance label attached. The label is part of the result, never looked up
     * afterwards, so the auto-fetch path and the picker persist the same string for the same source.
     * Providers with sub-sources (Zemer) override this; everyone else is labelled with [name].
     */
    suspend fun getLabeledLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String? = null,
    ): Result<LabeledLyrics> = getLyrics(id, title, artist, duration, album).map { LabeledLyrics(name, it) }

    /** [getAllLyrics] with the provenance label attached to every candidate. */
    suspend fun getAllLabeledLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String? = null,
        callback: (LabeledLyrics) -> Unit,
    ) {
        getAllLyrics(id, title, artist, duration, album) { callback(LabeledLyrics(name, it)) }
    }
}
