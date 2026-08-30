package com.metrolist.simpmusic

import com.metrolist.simpmusic.models.LyricsData
import com.metrolist.simpmusic.models.SimpMusicApiResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlin.math.abs

object SimpMusicLyrics {
    private const val BASE_URL = "https://api-lyrics.simpmusic.org/v1/"

    private val client by lazy {
        HttpClient(CIO) {
            install(ContentNegotiation) {
                json(
                    Json {
                        isLenient = true
                        ignoreUnknownKeys = true
                        explicitNulls = false
                    },
                )
            }

            install(HttpTimeout) {
                requestTimeoutMillis = 15000
                connectTimeoutMillis = 10000
                socketTimeoutMillis = 15000
            }

            defaultRequest {
                url(BASE_URL)
                header(HttpHeaders.Accept, "application/json")
                header(HttpHeaders.UserAgent, "SimpMusicLyrics/1.0")
                header(HttpHeaders.ContentType, "application/json")
            }

            expectSuccess = false
        }
    }

    suspend fun getLyricsByVideoId(videoId: String): List<LyricsData> = runCatching {
        val response = client.get(BASE_URL + videoId)

        if (response.status == HttpStatusCode.OK) {
            val apiResponse = response.body<SimpMusicApiResponse>()
            if (apiResponse.success) {
                apiResponse.data
            } else {
                emptyList()
            }
        } else {
            emptyList()
        }
    }.getOrDefault(emptyList())

    suspend fun getLyrics(
        videoId: String,
        duration: Int = 0,
    ): Result<String> = runCatching {
        val tracks = getLyricsByVideoId(videoId)

        if (tracks.isEmpty()) {
            throw IllegalStateException("Lyrics unavailable")
        }

        val bestMatch = if (duration > 0 && tracks.size > 1) {
            tracks.minByOrNull { track ->
                abs((track.duration ?: 0) - duration)
            }
        } else {
            tracks.firstOrNull()
        }

        val lyrics = firstNonBlankLyrics(bestMatch?.syncedLyrics, bestMatch?.plainLyrics)
            ?: throw IllegalStateException("Lyrics unavailable")

        lyrics
    }

    suspend fun getAllLyrics(
        videoId: String,
        duration: Int = 0,
        callback: (String) -> Unit,
    ) {
        val tracks = getLyricsByVideoId(videoId)
        var count = 0
        var plain = 0

        val sortedTracks = if (duration > 0) {
            tracks.sortedBy { abs((it.duration ?: 0) - duration) }
        } else {
            tracks
        }

        sortedTracks.forEach { track ->
            if (count <= 4) {
                val synced = track.syncedLyrics
                if (!synced.isNullOrBlank() && abs((track.duration ?: 0) - duration) <= 5) {
                    count++
                    callback(synced)
                }
                val plainLyrics = track.plainLyrics
                if (!plainLyrics.isNullOrBlank() && abs((track.duration ?: 0) - duration) <= 5 && plain == 0) {
                    count++
                    plain++
                    callback(plainLyrics)
                }
            }
        }
    }
}

/**
 * The first non-blank lyrics body, preferring synced over plain. SimpMusic returns syncedLyrics = ""
 * (empty, not null) for plain-only tracks, so a plain elvis on syncedLyrics took the empty string and
 * left the pane permanently blank. Blank entries are skipped so plain is used, or null if neither has
 * content.
 */
internal fun firstNonBlankLyrics(synced: String?, plain: String?): String? =
    synced?.takeIf { it.isNotBlank() } ?: plain?.takeIf { it.isNotBlank() }
