package com.jtech.zemer.lyrics.simpmusic

import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlin.math.abs
import com.jtech.zemer.lyrics.LyricsHttp
import io.ktor.client.request.get

/**
 * api-lyrics.simpmusic.org client for the SimpMusic lyrics provider: a videoId-keyed, community-filled
 * catalog, so a hit is this recording only when its duration is known and agrees with ours
 * ([sameRecording]), and its timings are trusted only within [SYNC_TOLERANCE_SEC] ([syncAllowed]).
 */
object SimpMusicLyrics {
    private const val BASE_URL = "https://api-lyrics.simpmusic.org/v1/"

    /** Synced/word-synced bodies are used only when the source track is within this many seconds of ours. */
    const val SYNC_TOLERANCE_SEC = 1

    /** A track counts as THIS recording only when its duration is known and within this many seconds of ours. */
    const val IDENTITY_TOLERANCE_SEC = 5

    private suspend fun getLyricsByVideoId(videoId: String): List<SimpMusicLyricsData> = runCatching {
        val response = LyricsHttp.client.get(BASE_URL + videoId) {
            header(HttpHeaders.Accept, "application/json")
            header(HttpHeaders.UserAgent, "SimpMusicLyrics/1.0")
        }

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

    /**
     * A Zemer resolver `simpmusic` pointer: the one audio-verified entry ([entryId]) of the track's catalog, with
     * no duration matching (the server vetted the row). There is no per-entry endpoint, so the by-videoId catalog
     * is fetched and the entry picked by id; an entry missing from it (deleted upstream) is null, so the walk
     * continues. The server's [synced] flag decides whether its timings are served - see [entryBody].
     */
    suspend fun getLyricsByEntry(videoId: String, entryId: String, synced: Boolean): String? =
        entryBody(getLyricsByVideoId(videoId).firstOrNull { it.id == entryId }, synced)

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

/**
 * The body of a server-vetted catalog [entry]: with [synced] the server verified its timings, so the word- or
 * line-synced body is served (plain only as the fallback); without it the timings are unverified and ONLY the
 * plain text is served, never a drifting sync. A missing entry is null.
 */
internal fun entryBody(entry: SimpMusicLyricsData?, synced: Boolean): String? = when {
    entry == null -> null
    synced -> firstNonBlankLyrics(entry.richSyncLyrics, entry.syncedLyrics, entry.plainLyrics)
    else -> firstNonBlankLyrics(entry.plainLyrics)
}

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
