package com.jtech.zemer.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsUtilsTest {
    @Test
    fun `plain line-synced lyrics parse with no words`() {
        val lines = LyricsUtils.parseLyrics("[00:01.50] hello there\n[00:03.00] second")
        assertEquals(2, lines.size)
        assertEquals(LyricsEntry(1500L, "hello there"), lines[0])
        assertTrue(lines[0].words.isEmpty())
        assertFalse(LyricsUtils.isWordSynced("[00:01.50] hello there"))
    }

    @Test
    fun `compact word tags give display text and word timings`() {
        val lines = LyricsUtils.parseLyrics("[00:09.73] <00:09.73>The <00:09.92>club <00:10.28>isn't")
        assertEquals(1, lines.size)
        assertEquals(9730L, lines[0].time)
        assertEquals("The club isn't", lines[0].text)
        assertEquals(
            listOf(LyricsWord(9730L, "The"), LyricsWord(9920L, "club"), LyricsWord(10280L, "isn't")),
            lines[0].words,
        )
    }

    @Test
    fun `spaced word tags with whitespace separators and trailing end tag`() {
        val rich = "[offset:-0.196136956521738]\n[00:18.59] <00:18.59> We're <00:18.81>   <00:18.86> no <00:18.95>   <00:19.01> strangers <00:19.73>"
        assertTrue(LyricsUtils.isWordSynced(rich))
        val lines = LyricsUtils.parseLyrics(rich)
        assertEquals(1, lines.size)
        assertEquals("We're no strangers", lines[0].text)
        assertEquals(listOf(18590L, 18860L, 19010L), lines[0].words.map { it.time })
    }

    @Test
    fun `stripWordTags keeps line timestamps and cleans text`() {
        val rich = "[00:09.73] <00:09.73>The <00:09.92>club\n[00:18.59] <00:18.59> We're <00:18.81>   <00:18.86> no <00:18.95>"
        assertEquals("[00:09.73] The club\n[00:18.59] We're no", LyricsUtils.stripWordTags(rich))
    }

    @Test
    fun `sungWordCount counts words started by position`() {
        val words = listOf(LyricsWord(1000L, "a"), LyricsWord(2000L, "b"), LyricsWord(3000L, "c"))
        assertEquals(0, LyricsUtils.sungWordCount(words, 500L))
        assertEquals(1, LyricsUtils.sungWordCount(words, 1000L))
        assertEquals(2, LyricsUtils.sungWordCount(words, 1950L))
        assertEquals(3, LyricsUtils.sungWordCount(words, 9000L))
    }
}
