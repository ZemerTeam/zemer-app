package com.jtech.zemer.lyrics.zemer

/**
 * Port of zemer-search `harvester/zemirotdb-harvest.mjs#extractHebrew` (zemirotdatabase.org), pinned by the
 * golden test (`lyrics/zemirotdb-*.html` + `.expected.txt`). The Hebrew text sits in `<div id='hebrew'>` with
 * `<br>` per line; a piyut stored as one or two long blocks is re-broken on sentence ends, and only lines with
 * Hebrew letters are kept. Parity means matching the server's stored text, comma-joined stanzas included, so the
 * body gate is the server's (twelve words), not the four-line rule the other sources use.
 */
object ZemirotDbParser {
    const val MIN_WORDS = 12

    private val DIV = Regex("""<div id='hebrew'[^>]*>(.*?)</div>""", RegexOption.DOT_MATCHES_ALL)
    private val BR = Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE)
    private val TAG = Regex("""<[^>]+>""")
    private val APOS = Regex("""&#0?39;""")
    private val SPACES = Regex("""\s+""")
    private val SENTENCE_END = Regex("""(?<=[.:!?])\s+""")
    private val HEBREW = Regex("""[א-ת]""")

    /** The Hebrew lines of the page, or null when there are fewer than [MIN_WORDS] words of them. */
    fun parse(html: String): String? {
        val block = DIV.find(html)?.groupValues?.get(1) ?: return null
        var lines = clean(block).split("\n").map { it.replace(SPACES, " ").trim() }.filter { it.isNotEmpty() }
        if (lines.size <= 2) lines = lines.joinToString(" ").split(SENTENCE_END).map { it.trim() }.filter { it.isNotEmpty() }
        lines = lines.filter { HEBREW.containsMatchIn(it) }
        if (lines.joinToString(" ").split(SPACES).size < MIN_WORDS) return null
        return lines.joinToString("\n")
    }

    private fun clean(s: String): String = s.replace(BR, "\n").replace(TAG, "").replace("&nbsp;", " ")
        .replace("&amp;", "&").replace("&quot;", "\"").replace(APOS, "'").replace("\u200B", "")
}
