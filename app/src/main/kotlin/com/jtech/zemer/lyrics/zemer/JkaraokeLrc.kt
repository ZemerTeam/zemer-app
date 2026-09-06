package com.jtech.zemer.lyrics.zemer

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Locale

/**
 * Port of zemer-search `harvester/lyrics-jkaraoke.mjs#toLrc`: jkaraoke feed lines [{start,end,text}] ->
 * plain + line-synced LRC. Same sanity rules as the server (>=4 lines, monotonic starts, inside the
 * duration); pinned by the golden test `jkaraoke-1971-golden.json`.
 *
 * Karaoke sources time a line EARLY so the singer can read it: over 17,340 human-timed jkaraoke lines the voice
 * starts a median 0.31 s after the karaoke time, though ~15 % of songs cue AFTER the voice. The resolver carries
 * this recording's own measured median (`offsetSec`, applied by the provider only when `offsetFrom == "measured"`),
 * and every emitted line time is `start + offset`.
 * The sanity rules run on the raw feed starts (the offset is a rendering correction, not feed data).
 */
object JkaraokeLrc {
    @Serializable data class FeedLine(val start: Double? = null, val end: Double? = null, val text: String? = null)
    @Serializable data class FeedSong(val id: Long, val title: String? = null, val duration: Int? = null, val lyrics: List<FeedLine> = emptyList())
    @Serializable data class FeedPage(val data: List<FeedSong> = emptyList())

    val json = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }

    data class Lrc(val plain: String, val synced: String)

    fun lrcTime(s: Double): String {
        val m = (s / 60).toInt()
        val sec = s - m * 60
        return String.format(Locale.US, "%02d:%05.2f", m, sec)
    }

    fun toLrc(lines: List<FeedLine>, duration: Int?, offsetSec: Double = 0.0): Lrc? {
        val ls = lines.filter { it.start != null && !it.text.isNullOrBlank() }
        if (ls.size < 4) return null
        for (k in 1 until ls.size) if (ls[k].start!! + 0.01 < ls[k - 1].start!!) return null
        if (ls.first().start!! < 0 || (duration != null && duration > 0 && ls.last().start!! > duration + 2)) return null
        val plain = ls.joinToString("\n") { it.text!!.trim() }
        val synced = ls.joinToString("\n") { "[${lrcTime((it.start!! + offsetSec).coerceAtLeast(0.0))}] ${it.text!!.trim()}" }
        return Lrc(plain, synced) // line times only: jkaraoke measures lines, and no timing is ever estimated
    }

    /** Find [songId] in a feed page body and build its LRC (line times shifted by [offsetSec]), or null. */
    fun fromFeedPage(pageJson: String, songId: Long, offsetSec: Double = 0.0): Lrc? {
        val song = json.decodeFromString(FeedPage.serializer(), pageJson).data.firstOrNull { it.id == songId } ?: return null
        return toLrc(song.lyrics, song.duration, offsetSec)
    }
}
