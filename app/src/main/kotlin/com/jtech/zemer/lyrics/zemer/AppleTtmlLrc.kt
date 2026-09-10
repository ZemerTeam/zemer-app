package com.jtech.zemer.lyrics.zemer

import com.jtech.zemer.lyrics.LyricsUtils
import com.jtech.zemer.lyrics.musixmatch.MusixmatchLyrics
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Apple Music catalog lyrics for a server-vouched `catalogId`, from the paxsenix mirror (`/apple-music/lyrics?id=`).
 * A synced lyric (`type: "Line"`) carries Apple's own line onsets: its ready `lrc` is used when clean, else the
 * TTML (`<p begin="m:ss.mmm">line</p>`, word-timed songs nest `<span>`s) is converted here. An unsynced lyric
 * (`type: "None"`) carries no onsets at all — `<p>` without `begin` — so its `plain` text is served with the
 * bracketed section labels (`[Verse]`) dropped. Never an estimated time. Same sanity rules as the other synced
 * ports (>= 4 lines, monotonic starts); pinned by the golden tests `apple-1571752969.json` / `apple-unsynced-reply.json`.
 */
object AppleTtmlLrc {
    @Serializable data class Reply(val type: String? = null, val lrc: String? = null, val plain: String? = null, val ttmlContent: String? = null)

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }

    fun url(catalogId: String): String = "https://lyrics.paxsenix.org/apple-music/lyrics?id=$catalogId"

    private val LINE = Regex("""<p\b[^>]*\bbegin="([^"]+)"[^>]*>(.*?)</p>""", RegexOption.DOT_MATCHES_ALL)
    private val TAG = Regex("""<[^>]+>""")
    private val WS = Regex("""\s+""")

    private val SECTION_LABEL = Regex("""^\[[^\]]*]$""")

    /** The body for a paxsenix reply: the synced LRC (ready `lrc`, else the TTML onsets), or the plain text of an unsynced lyric; null when nothing is usable. */
    fun fromReply(body: String): String? {
        val r = runCatching { json.decodeFromString(Reply.serializer(), body) }.getOrNull() ?: return null
        if (r.type == "Line") MusixmatchLyrics.cleanLrc(r.lrc)?.let { return it }   // drops the `[by:…]` credit tag, keeps only monotonic timed lines
        r.ttmlContent?.let(::toLrc)?.let { return it }
        return r.plain?.let(::plainBody)
    }

    /** The unsynced text with its `[Verse]`/`[Chorus]` section labels dropped, or null when fewer than four lines remain. */
    fun plainBody(plain: String): String? =
        plain.lines().map { it.trim() }.filter { it.isNotEmpty() && !SECTION_LABEL.matches(it) }.joinToString("\n").takeIf(LyricsUtils::hasLyricBody)

    /** TTML -> LRC: each `<p begin>` becomes `[mm:ss.xx] text` (inner tags dropped, entities decoded). */
    fun toLrc(ttml: String): String? {
        val rows = ArrayList<Pair<Double, String>>()
        for (m in LINE.findAll(ttml)) {
            val start = seconds(m.groupValues[1]) ?: continue
            val text = HtmlEntities.unescape(m.groupValues[2].replace(TAG, " ")).replace(WS, " ").trim()
            if (text.isNotEmpty()) rows += start to text
        }
        if (rows.size < 4) return null
        for (k in 1 until rows.size) if (rows[k].first + 0.01 < rows[k - 1].first) return null
        return rows.joinToString("\n") { (t, text) -> "[${JkaraokeLrc.lrcTime(t)}] $text" }
    }

    /** TTML clock times: `ss.mmm`, `m:ss.mmm` or `h:mm:ss.mmm`. */
    fun seconds(clock: String): Double? {
        val parts = clock.trim().split(':')
        if (parts.isEmpty() || parts.size > 3) return null
        var total = 0.0
        for (p in parts) total = total * 60 + (p.toDoubleOrNull() ?: return null)
        return total.takeIf { it >= 0 }
    }
}
