package com.jtech.zemer.lyrics.zemer

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Apple Music catalog lyrics for a server-vouched `catalogId`: the paxsenix mirror
 * (`/apple-music/lyrics?id=`) returns Apple's TTML (`<p begin="m:ss.mmm" end="…">line</p>`, one `<p>` per sung
 * line; word-timed songs nest `<span>`s inside), converted here to line-synced LRC. Only the `<p>` onsets are
 * used — Apple's own line timings, never estimated ones. Same sanity rules as the other synced ports (>= 4
 * lines, monotonic starts); pinned by the golden test `apple-1571752969.json`.
 */
object AppleTtmlLrc {
    @Serializable data class Reply(val ttmlContent: String? = null)

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }

    fun url(catalogId: String): String = "https://lyrics.paxsenix.org/apple-music/lyrics?id=$catalogId"

    private val LINE = Regex("""<p\b[^>]*\bbegin="([^"]+)"[^>]*>(.*?)</p>""", RegexOption.DOT_MATCHES_ALL)
    private val TAG = Regex("""<[^>]+>""")
    private val WS = Regex("""\s+""")

    /** The LRC for a paxsenix reply body, or null when it carries no usable TTML. */
    fun fromReply(body: String): String? = runCatching { json.decodeFromString(Reply.serializer(), body) }.getOrNull()?.ttmlContent?.let(::toLrc)

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
