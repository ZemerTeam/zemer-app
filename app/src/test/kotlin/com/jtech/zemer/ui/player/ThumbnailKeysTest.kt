package com.jtech.zemer.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test

class ThumbnailKeysTest {

    @Test
    fun `distinct songs keep their ids as keys`() {
        assertEquals(listOf("a", "b", "c"), thumbnailKeys(listOf("a", "b", "c")))
    }

    @Test
    fun `a song before and after the current one gets unique keys`() {
        val keys = thumbnailKeys(listOf("AgiUQbdbSUk", "x", "AgiUQbdbSUk"))
        assertEquals(listOf("AgiUQbdbSUk", "x", "AgiUQbdbSUk#1"), keys)
    }

    @Test
    fun `the same song three times and empty ids never repeat a key`() {
        assertEquals(3, thumbnailKeys(listOf("a", "a", "a")).toSet().size)
        assertEquals(2, thumbnailKeys(listOf("", "")).toSet().size)
    }
}
