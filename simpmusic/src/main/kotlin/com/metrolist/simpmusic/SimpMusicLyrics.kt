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

    /** A track counts as THIS recording only when its duration is known and within this many seconds of ours. */
    const val IDENTITY_TOLERANCE_SEC = 5

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
        // The videoId key alone is not identity: the catalog is community-filled. Only a track whose known
        // duration agrees with ours is this recording; the rest are a miss, never plain text. (A wrong-text
        // upload that also matches the duration still passes - no client gate can tell; Report is the remedy.)
        val tracks = getLyricsByVideoId(videoId).filter { sameRecording(it.duration, duration) }

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
}

/**
 * Whether a SimpMusic track of [trackDuration] seconds is the recording we are playing ([duration] seconds):
 * both durations known and within [SimpMusicLyrics.IDENTITY_TOLERANCE_SEC]. An entry with no duration is
 * unverifiable and is a miss, not "probably right".
 */
internal fun sameRecording(trackDuration: Int?, duration: Int): Boolean =
    duration > 0 && trackDuration != null && abs(trackDuration - duration) <= SimpMusicLyrics.IDENTITY_TOLERANCE_SEC

/**
 * Whether a synced/word-synced body from a SimpMusic track of [trackDuration] seconds may be shown for a
 * song of [duration] seconds: the timings fit only the same cut, within [SimpMusicLyrics.SYNC_TOLERANCE_SEC];
 * an unknown duration on either side never syncs.
 */
internal fun syncAllowed(trackDuration: Int?, duration: Int): Boolean =
    duration > 0 && trackDuration != null && abs(trackDuration - duration) <= SimpMusicLyrics.SYNC_TOLERANCE_SEC

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
