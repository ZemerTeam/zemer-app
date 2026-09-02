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

    /** Synced/word-synced bodies are used only when the source track is within this many seconds of ours. */
    const val SYNC_TOLERANCE_SEC = 1

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
            tracks.minByOrNull { track -> durationDelta(track.duration, duration) }
        } else {
            tracks.firstOrNull()
        }

        // Timings are only trustworthy for the SAME recording: within 1 s of the track we are playing
        // (SYNC_TOLERANCE_SEC). Otherwise the words are still fine — serve plain, never a drifting sync.
        val syncOk = bestMatch != null && syncAllowed(bestMatch.duration, duration)
        val lyrics = (if (syncOk) firstNonBlankLyrics(bestMatch?.richSyncLyrics, bestMatch?.syncedLyrics, bestMatch?.plainLyrics)
                      else firstNonBlankLyrics(bestMatch?.plainLyrics))
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
            tracks.sortedBy { durationDelta(it.duration, duration) }
        } else {
            tracks
        }

        sortedTracks.forEach { track ->
            if (count <= 4) {
                val rich = track.richSyncLyrics
                if (!rich.isNullOrBlank() && syncAllowed(track.duration, duration)) {
                    count++
                    callback(rich)
                }
                val synced = track.syncedLyrics
                if (!synced.isNullOrBlank() && syncAllowed(track.duration, duration)) {
                    count++
                    callback(synced)
                }
                val plainLyrics = track.plainLyrics
                if (!plainLyrics.isNullOrBlank() && durationDelta(track.duration, duration) <= 5 && plain == 0) {
                    count++
                    plain++
                    callback(plainLyrics)
                }
            }
        }
    }
}

/**
 * Whether a synced/word-synced body from a SimpMusic track of [trackDuration] seconds may be shown for a
 * song of [duration] seconds. The lookup is keyed by videoId, so the entry is the same recording by
 * construction; the duration check only guards against an upload made against a different cut. An
 * unknown duration on either side (null, or 0 for ours) is therefore accepted, not treated as a 0 s track
 * that fails the 1 s gate for every real song.
 */
internal fun syncAllowed(trackDuration: Int?, duration: Int): Boolean =
    duration <= 0 || trackDuration == null || abs(trackDuration - duration) <= SimpMusicLyrics.SYNC_TOLERANCE_SEC

/** Distance in seconds between a track and ours for ranking; unknown durations sort last. */
internal fun durationDelta(trackDuration: Int?, duration: Int): Int =
    if (trackDuration == null) Int.MAX_VALUE else abs(trackDuration - duration)

/**
 * The first non-blank lyrics body in preference order (word-synced, line-synced, plain). SimpMusic
 * returns syncedLyrics = "" (empty, not null) for plain-only tracks, so a plain elvis on syncedLyrics
 * took the empty string and left the pane permanently blank. Blank entries are skipped, or null if
 * none has content.
 *
 * richSyncLyrics is enhanced LRC: each line keeps its `[mm:ss.xx]` timestamp and adds `<mm:ss.xx>`
 * tags before every word, so any plain-LRC consumer still parses it as line-synced lyrics.
 */
internal fun firstNonBlankLyrics(vararg candidates: String?): String? =
    candidates.firstOrNull { !it.isNullOrBlank() }
