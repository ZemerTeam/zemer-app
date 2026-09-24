package com.jtech.zemer.lyrics

/**
 * One timed word inside a line of word-synced (enhanced LRC) lyrics. [time] is the absolute
 * position in milliseconds at which the word starts being sung.
 */
data class LyricsWord(
    val time: Long,
    val text: String,
)

data class LyricsEntry(
    val time: Long,
    val text: String,
    /** Per-word timings when the line came from word-synced lyrics; empty for plain line sync. */
    val words: List<LyricsWord> = emptyList(),
) : Comparable<LyricsEntry> {
    override fun compareTo(other: LyricsEntry): Int = (time - other.time).toInt()

    companion object {
        val HEAD_LYRICS_ENTRY = LyricsEntry(0L, "")
    }
}
