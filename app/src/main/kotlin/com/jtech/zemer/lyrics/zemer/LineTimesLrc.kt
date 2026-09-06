package com.jtech.zemer.lyrics.zemer

import java.security.MessageDigest
import java.text.Normalizer

/**
 * Applies the resolver's measured `lineTimes` to a plain body the app fetched itself, pairing lines by a
 * TEXT-FREE key so the app's parser never has to split lines exactly the way the server's aligner did.
 *
 * Port of zemer-search `corpus/lyrics.mjs#lineKey`: NFC, Hebrew points and cantillation (U+0591..U+05C7)
 * removed, lower-cased, then only letters and numbers kept (every Unicode L* and N* category, as JS
 * `\p{L}\p{N}`), SHA-1, first 8 hex chars. Pinned to the server by the vectors in `LineTimesLrcTest`.
 *
 * Matching is monotone: a repeated chorus line takes successive timed occurrences, and the produced LRC is
 * always in time order. Timings are measured, never estimated: a parsed line with no key match is not given
 * a time of its own, it rides the preceding matched line's tag (the same equal-time continuation the
 * server's own synced bodies use for a line sung on the previous line's beat), so no text is ever lost to
 * sync; and a body where too few lines matched on either side stays plain.
 */
object LineTimesLrc {
    /** The share of TIMED lines that must find a parsed line, and of PARSED lines that must find a time. */
    const val MIN_MATCHED_SHARE = 0.8

    private val HEBREW_POINTS = Regex("[\u0591-\u05C7]")

    fun lineKey(line: String): String {
        val normalised = Normalizer.normalize(line, Normalizer.Form.NFC).replace(HEBREW_POINTS, "").lowercase()
        val kept = StringBuilder(normalised.length)
        var i = 0
        while (i < normalised.length) {
            val cp = normalised.codePointAt(i)
            if (isLetterOrNumber(cp)) kept.appendCodePoint(cp)
            i += Character.charCount(cp)
        }
        val digest = MessageDigest.getInstance("SHA-1").digest(kept.toString().toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }.substring(0, 8)
    }

    /** JS `\p{L}` = the five letter categories; `\p{N}` = decimal, letter and other numbers (Java's isDigit is Nd only). */
    private fun isLetterOrNumber(cp: Int): Boolean = when (Character.getType(cp).toByte()) {
        Character.UPPERCASE_LETTER, Character.LOWERCASE_LETTER, Character.TITLECASE_LETTER, Character.MODIFIER_LETTER, Character.OTHER_LETTER,
        Character.DECIMAL_DIGIT_NUMBER, Character.LETTER_NUMBER, Character.OTHER_NUMBER -> true
        else -> false
    }

    /**
     * The LRC for [plain] under [lineTimes], or null when the timings do not cover it well enough (fewer than
     * [MIN_MATCHED_SHARE] of the timed lines matched, or fewer than that share of the body's own lines did).
     * Unmatched lines carry the preceding matched line's tag; lines before the first match carry its tag.
     */
    fun apply(plain: String, lineTimes: ZemerLyricsClient.LineTimes): String? {
        val keys = lineTimes.keys
        val times = lineTimes.times
        val timed = minOf(keys.size, times.size)
        if (timed < 4) return null
        val lines = plain.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return null
        val assigned = arrayOfNulls<Double>(lines.size)
        var next = 0
        var matched = 0
        for ((i, line) in lines.withIndex()) {
            val key = lineKey(line)
            var j = next
            while (j < timed && keys[j] != key) j++
            if (j >= timed) continue
            assigned[i] = times[j]
            matched++
            next = j + 1
        }
        if (matched < MIN_MATCHED_SHARE * timed || matched < MIN_MATCHED_SHARE * lines.size) return null
        val first = assigned.first { it != null }!!
        var current = first
        return lines.indices.joinToString("\n") { i ->
            assigned[i]?.let { current = it }
            "[${lrcTime(current)}] ${lines[i]}"
        }
    }

    private fun lrcTime(seconds: Double): String {
        val centis = Math.round(seconds * 100).coerceAtLeast(0)
        val mm = centis / 6000
        val ss = centis % 6000 / 100
        val cc = centis % 100
        return "%02d:%02d.%02d".format(java.util.Locale.US, mm, ss, cc)
    }
}
