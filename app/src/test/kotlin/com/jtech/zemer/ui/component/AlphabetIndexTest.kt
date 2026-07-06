package com.jtech.zemer.ui.component

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the pure name→letter bucketing rule behind the Artists-tab fast scroller's preview bubble
 * (Latin case-fold, Hebrew kept, digits/symbols → '#'). No Android runtime needed — the logic
 * lives in AlphabetIndex.kt, free of Compose.
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
}
