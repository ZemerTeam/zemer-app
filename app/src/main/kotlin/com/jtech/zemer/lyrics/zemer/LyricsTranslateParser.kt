package com.jtech.zemer.lyrics.zemer

/**
 * Port of zemer-search `harvester/lyricstranslate-harvest.mjs#songBody` (lyricstranslate.com). The song page
 * carries the ORIGINAL-language text in `<div class="translate__text ..." lang="xx" id="song-body">`
 * (translations live on separate pages and are never fetched); the body nests further divs, so the extent is
 * found by depth-aware div scanning, not a lazy match. A Cloudflare challenge page has no `#song-body`
 * and parses to null, which the provider chain treats like any dead source (next provider).
 */
object LyricsTranslateParser {
    private val OPEN = Regex("""<div class="translate__text[^"]*"[^>]*lang="[^"]*"[^>]*id="song-body">""")
    private val OPEN_ANY = Regex("""<div[^>]*id="song-body"[^>]*>""")
    private val DIV = Regex("""<div[\s>]|</div>""")
    private val BR = Regex("""<br[^>]*/?>""", RegexOption.IGNORE_CASE)
    private val BLOCK_CLOSE = Regex("""</(?:p|div)>""", RegexOption.IGNORE_CASE)
    private val TAG = Regex("""<[^>]+>""")
    private val TRAIL = Regex("""[ \t]+\n""")
    private val LEAD = Regex("""\n[ \t]+""")
    private val BLANKS = Regex("""\n{3,}""")

    fun parse(html: String): String? {
        val open = OPEN.find(html) ?: OPEN_ANY.find(html) ?: return null
        val start = open.range.last + 1
        var depth = 1
        var body: String? = null
        var m = DIV.find(html, start)
        while (m != null) {
            if (m.value == "</div>") { depth--; if (depth == 0) { body = html.substring(start, m.range.first); break } }
            else depth++
            m = m.next()
        }
        val text = HtmlEntities.unescape((body ?: return null).replace(BR, "\n").replace(BLOCK_CLOSE, "\n").replace(TAG, ""))
            .replace(TRAIL, "\n").replace(LEAD, "\n").replace(BLANKS, "\n\n").trim()
        return text.ifEmpty { null }
    }
}
