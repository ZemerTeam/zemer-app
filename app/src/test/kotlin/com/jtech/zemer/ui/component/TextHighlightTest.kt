package com.jtech.zemer.ui.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** [highlightMatchRanges] — the pure matcher behind the browse screens' search-match highlight. */
class TextHighlightTest {

    @Test
    fun `blank query matches nothing`() {
        assertTrue(highlightMatchRanges("Avraham Fried", "").isEmpty())
        assertTrue(highlightMatchRanges("Avraham Fried", "   ").isEmpty())
    }

    @Test
    fun `no occurrence matches nothing`() {
        assertTrue(highlightMatchRanges("Avraham Fried", "xyz").isEmpty())
    }

    @Test
    fun `match is case-insensitive`() {
        assertEquals(listOf(8..12), highlightMatchRanges("Avraham Fried", "fried"))
    }

    @Test
    fun `query whitespace is trimmed`() {
        assertEquals(listOf(8..12), highlightMatchRanges("Avraham Fried", " Fried "))
    }

    @Test
    fun `every non-overlapping occurrence is matched`() {
        assertEquals(listOf(0..1, 2..3), highlightMatchRanges("abab", "ab"))
    }

    @Test
    fun `repeated overlapping pattern advances past each match`() {
        // "aaaa" with query "aa": non-overlapping -> two matches, not three.
        assertEquals(listOf(0..1, 2..3), highlightMatchRanges("aaaa", "aa"))
    }

    @Test
    fun `hebrew text matches and query whitespace trims first`() {
        assertEquals(listOf(0..2), highlightMatchRanges("משה יעס", "משה "))
    }
}
