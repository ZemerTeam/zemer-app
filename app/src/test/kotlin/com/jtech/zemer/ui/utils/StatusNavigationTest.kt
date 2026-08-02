package com.jtech.zemer.ui.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class StatusNavigationTest {
    @Test
    fun `storyRoute encodes the creator index`() {
        assertEquals("story/0", storyRoute(0))
        assertEquals("story/7", storyRoute(7))
    }
}
