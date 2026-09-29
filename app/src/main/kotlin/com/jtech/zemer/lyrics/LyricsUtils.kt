package com.jtech.zemer.lyrics

import android.text.format.DateUtils
import java.util.Locale

@Suppress("RegExpRedundantEscape")
object LyricsUtils {
    private val LINE_REGEX = "((\\[\\d\\d:\\d\\d\\.\\d{2,3}\\] ?)+)(.+)".toRegex()
    private val TIME_REGEX = "\\[(\\d\\d):(\\d\\d)\\.(\\d{2,3})\\]".toRegex()

    /** Enhanced-LRC word tag, e.g. `<00:18.59>`, as emitted by SimpMusic's richSyncLyrics. */
    private val WORD_TIME_REGEX = "<(\\d\\d):(\\d\\d)\\.(\\d{2,3})>".toRegex()

    fun parseLyrics(lyrics: String): List<LyricsEntry> =
        lyrics
            .lines()
            .flatMap { line ->
                parseLine(line).orEmpty()
            }.sorted()

    /** True when at least one line carries `<mm:ss.xx>` word tags. */
    fun isWordSynced(lyrics: String): Boolean = WORD_TIME_REGEX.containsMatchIn(lyrics)

    /** True when the body has measured line timings (`[mm:ss.xx]`), i.e. it can scroll in time with the song. */
    fun isSynced(lyrics: String): Boolean = TIME_REGEX.containsMatchIn(lyrics)

    /** A body worth serving has at least [MIN_LYRIC_LINES] non-blank lines (a title echo or a one-line stub is not lyrics). */
    const val MIN_LYRIC_LINES = 4
    fun hasLyricBody(text: String): Boolean = text.lineSequence().count { it.isNotBlank() } >= MIN_LYRIC_LINES

    /**
     * The lyrics with any word tags removed and spacing normalised, for places that expose the raw
     * LRC body (the lyrics tag embedded in a download). Line timestamps are kept.
     */
    fun stripWordTags(lyrics: String): String =
        if (!isWordSynced(lyrics)) {
            lyrics
        } else {
            lyrics.lines().joinToString("\n") { line ->
                val match = LINE_REGEX.matchEntire(line.trim())
                if (match == null) {
                    line
                } else {
                    val (text, _) = splitWords(match.groupValues[3])
                    "${match.groupValues[1].trim()} $text"
                }
            }
        }

    private fun parseLine(line: String): List<LyricsEntry>? {
        if (line.isEmpty()) {
            return null
        }
        val matchResult = LINE_REGEX.matchEntire(line.trim()) ?: return null
        val times = matchResult.groupValues[1]
        val (text, words) = splitWords(matchResult.groupValues[3])
        if (text.isEmpty()) return null
        val timeMatchResults = TIME_REGEX.findAll(times)

        return timeMatchResults
            .map { timeMatchResult ->
                LyricsEntry(toMillis(timeMatchResult), text, words)
            }.toList()
    }

    /**
     * Splits the body of a line into its display text and per-word timings. Plain lines come back
     * unchanged with no words. Word-synced lines are tokenised on the `<mm:ss.xx>` tags; SimpMusic
     * emits two shapes, `<t>The <t>club` and `<t> We're <t>   <t> no`, so whitespace-only tokens are
     * dropped and the remaining tokens are trimmed and re-joined with single spaces.
     */
    private fun splitWords(body: String): Pair<String, List<LyricsWord>> {
        val tags = WORD_TIME_REGEX.findAll(body).toList()
        if (tags.isEmpty()) return body to emptyList()

        val words = ArrayList<LyricsWord>(tags.size)
        tags.forEachIndexed { index, tag ->
            val end = tags.getOrNull(index + 1)?.range?.first ?: body.length
            val token = body.substring(tag.range.last + 1, end).trim()
            if (token.isNotEmpty()) {
                words += LyricsWord(toMillis(tag), token)
            }
        }
        // Text before the first tag (rare, but keep it rather than drop it).
        val lead = body.substring(0, tags.first().range.first).trim()
        val text = buildString {
            if (lead.isNotEmpty()) append(lead)
            words.forEach {
                if (isNotEmpty()) append(' ')
                append(it.text)
            }
        }
        return text to words
    }

    private fun toMillis(match: MatchResult): Long {
        val min = match.groupValues[1].toLong()
        val sec = match.groupValues[2].toLong()
        val milString = match.groupValues[3]
        var mil = milString.toLong()
        if (milString.length == 2) {
            mil *= 10
        }
        return min * DateUtils.MINUTE_IN_MILLIS + sec * DateUtils.SECOND_IN_MILLIS + mil
    }

    /** Highlight lead. Was 300 ms; on fast lines that plus output latency read as "a line early". */
    const val LINE_LOOKAHEAD_MS = 150L

    fun findCurrentLineIndex(
        lines: List<LyricsEntry>,
        position: Long,
    ): Int {
        for (index in lines.indices) {
            if (lines[index].time >= position + LINE_LOOKAHEAD_MS) {
                return index - 1
            }
        }
        return lines.lastIndex
    }

    /**
     * Number of words in [words] that have started by [position], i.e. the count to render as sung.
     * Uses a smaller lookahead than the line lookahead so the highlight lands on the beat.
     */
    fun sungWordCount(
        words: List<LyricsWord>,
        position: Long,
    ): Int {
        var count = 0
        for (word in words) {
            if (word.time <= position + 100L) count++ else break
        }
        return count
    }

    /**
     * Keep only well-formed, monotonic `[mm:ss.xx]` lines of an LRC body, re-formatted with `Locale.US` (the
     * default locale would print a comma decimal or non-ASCII digits, which no LRC parser reads); null unless
     * at least four sung lines remain, or when the timestamps run backwards.
     */
    fun cleanLrc(sub: String?): String? {
        val rows = ArrayList<String>(); var last = -1.0
        // lineSequence: LF, CRLF and CR-only bodies all split into rows (a CR-only reply used to be one rejected row).
        for (l in (sub ?: "").lineSequence()) {
            val m = Regex("""^\[(\d+):(\d+(?:\.\d+)?)]\s?(.*)$""").find(l) ?: continue
            val t = m.groupValues[1].toInt() * 60 + m.groupValues[2].toDouble()
            if (t < last) return null
            last = t
            rows.add(String.format(Locale.US, "[%s:%05.2f] %s", m.groupValues[1].padStart(2, '0'), m.groupValues[2].toDouble(), m.groupValues[3].trim()))
        }
        return if (rows.count { !it.endsWith("] ") && !it.endsWith("]") } >= 4) rows.joinToString("\n") else null
    }
}
