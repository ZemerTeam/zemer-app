package com.jtech.zemer.lyrics.zemer

/**
 * Port of zemer-search `corpus/lyrics.mjs#parseJyrics`. Pinned to the server parser by golden files
 * (test resources `lyrics/jyrics-*.html` + `jyrics-golden.json`): any divergence fails the unit test.
 *
 * Page shape: <article> … "LYRIC" heading, one header <p> (title/artist/composer/album), one <p> per
 * STANZA with <br /> per LINE, then <ul class="related-list"> (the album's other songs — cut BEFORE
 * stripping tags or the tracklist leaks into the lyrics).
 */
object JyricsParser {
    data class Parsed(val header: String, val plain: String)

    private val RELATED_CUT = Regex("""<ul[^>]*class="[^"]*related-list|<h\d[^>]*>\s*Other Songs from""", RegexOption.IGNORE_CASE)
    private val SCRIPT_STYLE = Regex("""<script[\s\S]*?</script>|<style[\s\S]*?</style>|<!--[\s\S]*?-->""")
    private val BR = Regex("""<br\s*/?>\s*""", RegexOption.IGNORE_CASE)
    private val P_END = Regex("""</p>\s*""", RegexOption.IGNORE_CASE)
    private val BLOCK_END = Regex("""</(div|h\d|li)>\s*""", RegexOption.IGNORE_CASE)
    private val TAG = Regex("""<[^>]+>""")
    private val SPACES = Regex("""[ \t ]+""")
    private val NAV = Regex("""^(Print|SHARE|Added by|admin)$""", RegexOption.IGNORE_CASE)
    private val LABEL = Regex("""^\(?\s*(verse|chorus|bridge|intro|outro|pre-?chorus|hook|refrain|interlude|פזמון|בית)\s*[\d\w]*\s*:?\s*\)?$""", RegexOption.IGNORE_CASE)
    private val CREDIT = Regex("""^\(?\s*(composed|arranged|written|lyrics|words|music|produced|recorded|later recorded|originally|from the album|album)\b[^\n]*\bby\b|^\(?\s*(composed|arranged|recorded)\b|^(מילים|לחן|עיבוד|הפקה)\s*:""", RegexOption.IGNORE_CASE)


    fun parse(html: String): Parsed {
        var art = Regex("""<article[\s\S]*?</article>""").find(html)?.value ?: html
        RELATED_CUT.find(art)?.let { art = art.substring(0, it.range.first) }
        val text = HtmlEntities.unescape(
            art.replace(SCRIPT_STYLE, "").replace(BR, "\n").replace(P_END, "\n\n").replace(BLOCK_END, "\n").replace(TAG, ""),
        )
        val lines = text.split("\n").map { it.replace(SPACES, " ").trim() }
        val i = lines.indexOfFirst { it.equals("LYRIC", ignoreCase = true) }
        val j = lines.withIndex().indexOfFirst { (k, l) -> k > i && l.startsWith("Other Songs from", ignoreCase = true) }
        val body = lines.subList(if (i >= 0) i + 1 else 0, if (j > 0) j else lines.size).filterNot { NAV.matches(it) }.toMutableList()
        while (body.isNotEmpty() && body.first().isEmpty()) body.removeAt(0)
        val header = if (body.isNotEmpty()) body.removeAt(0) else ""
        val out = ArrayList<String>()
        for (l in body) {
            if (LABEL.matches(l) || CREDIT.containsMatchIn(l)) continue
            if (l.isNotEmpty() || (out.isNotEmpty() && out.last().isNotEmpty())) out.add(l)
        }
        while (out.isNotEmpty() && out.last().isEmpty()) out.removeAt(out.size - 1)
        while (out.isNotEmpty() && out.first().isEmpty()) out.removeAt(0)
        return Parsed(header, out.joinToString("\n"))
    }
}
