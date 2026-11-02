package com.jtech.zemer.ui.screens.artist

import org.junit.Assert.assertEquals
import org.junit.Test

class ArtistHeaderTest {
    @Test
    fun `the header offset follows the screen width and is never negative`() {
        assertEquals(756f, artistHeaderTopPx(1080), 0.01f)
        assertEquals(56f, artistHeaderTopPx(240), 0.01f)
        assertEquals(0f, artistHeaderTopPx(120), 0f) // narrower than the overlap: clamped, not negative
        assertEquals(0f, artistHeaderTopPx(0), 0f)
    }
}
