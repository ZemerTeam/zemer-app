package com.jtech.zemer.lyrics.zemer

/**
 * Port of zemer-search `corpus/lyrics.mjs#parseShironet` (Shironet, shironet.mako.co.il — Israel's canonical
 * lyrics database). Pinned to the server parser by the golden test (`lyrics/shironet-*.html` + `shironet-golden.json`).
 *
 * Page shape: `<title>מילים לשיר TITLE - ARTIST - שירונט</title>`; the lyrics live in
 * `<span class="artist_lyrics_text">` with `<br>` per LINE (a run of blank lines = stanza break); section
 * labels ("פזמון:") are not sung and are dropped.
 */
object ShironetParser {
    data class Parsed(val title: String, val artist: String, val plain: String)

    private val TITLE = Regex("""<title>\s*מילים לשיר\s*([\s\S]*?)\s*-\s*שירונט\s*</title>""")
    private val SPAN = Regex("""<span[^>]*class="artist_lyrics_text"[^>]*>([\s\S]*?)</span>""")
    private val BR = Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE)
    private val TAG = Regex("""<[^>]+>""")
    private val SPACES = Regex("""[ \t ]+""")
    private val LABEL = Regex("""^\(?\s*(פזמון|בית|גשר|מעבר|סיום|פתיחה|verse|chorus|bridge|intro|outro)\s*[\d\w]*\s*:?\s*\)?$""", RegexOption.IGNORE_CASE)


    fun parse(html: String): Parsed {
        val t = HtmlEntities.unescape(TITLE.find(html)?.groupValues?.get(1) ?: "")
        val k = t.lastIndexOf(" - ")
        val title = (if (k > 0) t.substring(0, k) else t).trim()
        val artist = (if (k > 0) t.substring(k + 3) else "").trim()
        val span = SPAN.find(html)?.groupValues?.get(1) ?: ""
        val text = HtmlEntities.unescape(span.replace(BR, "\n").replace(TAG, ""))
        val out = ArrayList<String>()
        for (raw in text.split("\n")) {
            val l = raw.replace(SPACES, " ").trim()
            if (LABEL.matches(l)) continue
            if (l.isNotEmpty() || (out.isNotEmpty() && out.last().isNotEmpty())) out.add(l)
        }
        while (out.isNotEmpty() && out.last().isEmpty()) out.removeAt(out.size - 1)
        while (out.isNotEmpty() && out.first().isEmpty()) out.removeAt(0)
        return Parsed(title, artist, out.joinToString("\n"))
    }
}
