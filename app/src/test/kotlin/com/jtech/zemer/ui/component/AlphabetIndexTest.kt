package com.jtech.zemer.ui.component

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the pure fast-scroll index rules behind the Artists-tab letter scrollbar: name→letter
 * bucketing (Latin case-fold, Hebrew kept, digits/symbols → '#'), one entry per bucket pointing at
 * its first item, and even thinning when more letters exist than fit on screen. No Android runtime
 * needed — the logic lives in AlphabetIndex.kt, free of Compose.
 */
class AlphabetIndexTest {

    @Test
    fun `bucket uppercases latin, keeps hebrew, folds digits and symbols to the other bucket`() {
        assertEquals('A', alphabetBucketOf("avraham Fried"))
        assertEquals('B', alphabetBucketOf("Benny Friedman"))
        assertEquals('א', alphabetBucketOf("אברהם פריד"))
        assertEquals(ALPHABET_OTHER_BUCKET, alphabetBucketOf("8th Day"))
        assertEquals(ALPHABET_OTHER_BUCKET, alphabetBucketOf("!!!"))
        assertEquals(ALPHABET_OTHER_BUCKET, alphabetBucketOf(""))
    }

    @Test
    fun `bucket skips leading punctuation to the first real letter`() {
        assertEquals('S', alphabetBucketOf("\"Shirim\" Choir"))
        assertEquals('ד', alphabetBucketOf("(ד\"ר) כהן"))
    }

    @Test
    fun `index has one entry per bucket pointing at the first item of that bucket`() {
        val names = listOf("Avi", "avner", "Benny", "אבי", "אורי", "ברוך")
        assertEquals(
            listOf(
                AlphabetIndexEntry('A', 0),
                AlphabetIndexEntry('B', 2),
                AlphabetIndexEntry('א', 3),
                AlphabetIndexEntry('ב', 5),
            ),
            alphabetIndexOf(names),
        )
    }

    @Test
    fun `index keeps list order and first occurrence for repeated buckets`() {
        // The input is pre-sorted by the DAO; the index must follow it, never re-sort.
        val names = listOf("ברוך", "אבי", "Avi")
        assertEquals(
            listOf(
                AlphabetIndexEntry('ב', 0),
                AlphabetIndexEntry('א', 1),
                AlphabetIndexEntry('A', 2),
            ),
            alphabetIndexOf(names),
        )
    }

    @Test
    fun `thinning keeps every entry when it fits`() {
        val entries = alphabetIndexOf(listOf("A1", "B1", "C1"))
        assertEquals(entries, thinAlphabetIndex(entries, 3))
        assertEquals(entries, thinAlphabetIndex(entries, 10))
    }

    @Test
    fun `thinning keeps first and last and samples evenly in between`() {
        val entries = ('A'..'Z').mapIndexed { i, c -> AlphabetIndexEntry(c, i) }
        val thinned = thinAlphabetIndex(entries, 5)
        assertEquals(5, thinned.size)
        assertEquals('A', thinned.first().letter)
        assertEquals('Z', thinned.last().letter)
        // Evenly spread: 26 letters over 4 gaps of 6.25 rounds to gaps of 6 or 7, never lopsided.
        val gaps = thinned.zipWithNext { a, b -> b.itemIndex - a.itemIndex }
        assertEquals(listOf(6, 7, 6, 6), gaps)
    }

    @Test
    fun `thinning degenerate row counts`() {
        val entries = ('A'..'E').mapIndexed { i, c -> AlphabetIndexEntry(c, i) }
        assertEquals(emptyList<AlphabetIndexEntry>(), thinAlphabetIndex(entries, 0))
        assertEquals(listOf(AlphabetIndexEntry('A', 0)), thinAlphabetIndex(entries, 1))
    }
}
