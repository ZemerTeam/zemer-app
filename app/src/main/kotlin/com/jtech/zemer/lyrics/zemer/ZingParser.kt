package com.jtech.zemer.lyrics.zemer

/**
 * Port of zemer-search `harvester/lyrics-zingmusic.mjs#zingToPlain`, golden-pinned by `lyrics/zing-golden.json`
 * (real tracks parsed by the server). zingmusic stores lyrics as Quill editor HTML: `<p>` per line or a single
 * `<pre>` block with newlines; section markers ("[פתיחה]", "[בית 1]", "(Chorus)") annotate structure and are
 * not sung, so they are dropped.
 */
object ZingParser {
    private val BR = Regex("""<\s*br\s*/?>""", RegexOption.IGNORE_CASE)
    private val BLOCK_END = Regex("""</(p|div|pre|li|h\d)>""", RegexOption.IGNORE_CASE)
    private val TAG = Regex("""<[^>]+>""")
    private val ENTITY = Regex("""&#(\d+);""")
    private val SPACES = Regex("""[ \t ]+""")
    private val MARKER = Regex("""^\[[^\]]*\]$""")
    private val PAREN_MARKER = Regex("""^\((?:verse|chorus|bridge|intro|outro|פזמון|בית|מעבר|גשר)\b[^)]*\)$""", RegexOption.IGNORE_CASE)

    fun toPlain(html: String?): String {
        if (html.isNullOrEmpty()) return ""
        val text = html.replace(BR, "\n").replace(BLOCK_END, "\n").replace(TAG, "")
            .replace("&nbsp;", " ").replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&#039;", "'")
            .replace("&lt;", "<").replace("&gt;", ">").replace(ENTITY) { m -> m.groupValues[1].toInt().toChar().toString() }
        val out = ArrayList<String>()
        for (raw in text.split("\n")) {
            val l = raw.replace(SPACES, " ").trim()
            if (MARKER.matches(l) || PAREN_MARKER.matches(l)) continue
            if (l.isNotEmpty() || (out.isNotEmpty() && out.last().isNotEmpty())) out.add(l)
        }
        while (out.isNotEmpty() && out.last().isEmpty()) out.removeAt(out.size - 1)
        while (out.isNotEmpty() && out.first().isEmpty()) out.removeAt(0)
        return out.joinToString("\n")
    }
}
