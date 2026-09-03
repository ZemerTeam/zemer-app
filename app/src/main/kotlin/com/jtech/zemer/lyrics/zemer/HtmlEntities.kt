package com.jtech.zemer.lyrics.zemer

/** The entity unescape both page parsers (Jyrics, Shironet) need, byte-identical to the server's (golden-pinned through them). */
object HtmlEntities {
    private val NUMERIC = Regex("""&#(\d+);""")

    fun unescape(s: String): String = s
        .replace("&#8217;", "'").replace("&rsquo;", "'")
        .replace("&#8211;", "\u2013").replace("&ndash;", "\u2013")
        .replace("&amp;", "&").replace("&quot;", "\"").replace("&#039;", "'").replace("&nbsp;", " ")
        .replace(NUMERIC) { m -> m.groupValues[1].toInt().toChar().toString() }
}
