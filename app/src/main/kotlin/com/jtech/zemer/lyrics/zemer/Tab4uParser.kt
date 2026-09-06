package com.jtech.zemer.lyrics.zemer

/**
 * Port of zemer-search `harvester/tab4u-harvest.mjs#extractSheet` (tab4u.com chord sheets), pinned by the
 * golden test (`lyrics/tab4u-*.html` + `.expected.txt`). The sheet is a table whose `<td class="song">` cells
 * carry the sung lines in document order; `<td class="chords">` rows are the chord line above each lyric and
 * are skipped, as are short `xxx:` section cells (פתיחה:, מעבר:) and Latin-only annotation rows. Fewer than
 * six lyric lines is a stub, not a song.
 */
object Tab4uParser {
    const val MIN_LINES = 6

    private val CELL = Regex("""<td class="(chords|song)[^"]*"[^>]*>(.*?)</td>""", RegexOption.DOT_MATCHES_ALL)
    private val TAG = Regex("""<[^>]+>""")
    private val APOS = Regex("""&#0?39;""")
    private val SPACES = Regex("""\s+""")
    private val SECTION = Regex("""^[\p{L}" ]{2,12}:$""")
    private val HEBREW = Regex("""[א-ת]""")

    /** The lyric lines of the sheet, or null when the page holds fewer than [MIN_LINES] of them. */
    fun parse(html: String): String? {
        val lines = ArrayList<String>()
        for (m in CELL.findAll(html)) {
            val t = clean(m.groupValues[2])
            if (t.isEmpty() || m.groupValues[1] == "chords") continue
            if (SECTION.matches(t)) continue
            if (!HEBREW.containsMatchIn(t)) continue
            lines += t
        }
        return if (lines.size < MIN_LINES) null else lines.joinToString("\n")
    }

    private fun clean(s: String): String = s.replace(TAG, "").replace("&nbsp;", " ").replace("&amp;", "&")
        .replace("&quot;", "\"").replace(APOS, "'").replace(SPACES, " ").trim()
}
