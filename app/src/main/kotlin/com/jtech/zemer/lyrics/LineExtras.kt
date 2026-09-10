package com.jtech.zemer.lyrics

import com.jtech.zemer.lyrics.zemer.LineTimesLrc
import com.jtech.zemer.lyrics.zemer.ZemerLyricsClient

/** Which extra, if any, is rendered under each sung line. [OFF] is the default: nothing changes until the user picks one. */
enum class LineExtrasLanguage { OFF, ENGLISH, HEBREW, ROMANIZED }

/**
 * The resolver's per-line translations / romanization, paired to the app's OWN parsed lines by the text-free
 * [LineTimesLrc.lineKey] (never by index: the server keyed its stored lines, the app splits its own body, and
 * the two need not agree line for line). A line the server did not key, or whose entry is blank, gets nothing.
 * Pure and JVM-tested (`LineExtrasTest`).
 */
data class LineExtras(
    private val byLanguage: Map<LineExtrasLanguage, Map<String, String>>,
    /** `"machine"` = machine translation, labelled once per song in the source header; anything else is unlabelled. */
    val source: String?,
) {
    val isMachine: Boolean get() = source == MACHINE

    /** The languages this song actually carries (the picker offers every language; an absent one simply renders nothing). */
    val languages: Set<LineExtrasLanguage> get() = byLanguage.keys

    /** The extra to render under [line] in [language], or null (also for [LineExtrasLanguage.OFF]). */
    fun textFor(line: String, language: LineExtrasLanguage): String? = byLanguage[language]?.get(LineTimesLrc.lineKey(line))

    /** One lookup per parsed line, computed once per body + language (the renderer indexes this, never hashes per frame). */
    fun forLines(lines: List<String>, language: LineExtrasLanguage): List<String?> {
        val map = byLanguage[language] ?: return List(lines.size) { null }
        return lines.map { map[LineTimesLrc.lineKey(it)] }
    }

    companion object {
        const val MACHINE = "machine"

        /** The wire shape folded into per-language key→text maps; null when no language carries a single non-blank entry. */
        fun from(wire: ZemerLyricsClient.LineExtras?): LineExtras? {
            if (wire == null) return null
            val byLanguage = HashMap<LineExtrasLanguage, Map<String, String>>()
            fun fold(language: LineExtrasLanguage, texts: List<String>?) {
                if (texts == null) return
                val map = HashMap<String, String>()
                for (i in wire.keys.indices) {
                    val text = texts.getOrNull(i)?.trim().orEmpty()
                    if (text.isNotEmpty()) map.putIfAbsent(wire.keys[i], text)
                }
                if (map.isNotEmpty()) byLanguage[language] = map
            }
            fold(LineExtrasLanguage.ENGLISH, wire.en)
            fold(LineExtrasLanguage.HEBREW, wire.he)
            fold(LineExtrasLanguage.ROMANIZED, wire.roman)
            return if (byLanguage.isEmpty()) null else LineExtras(byLanguage, wire.source)
        }
    }
}
