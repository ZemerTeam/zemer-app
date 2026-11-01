package com.jtech.zemer.utils.mp4

import org.junit.Assert.assertEquals
import org.junit.Test

class OggMuxRefusalsTest {
    @Test
    fun `a refused track counts, a working rewrap clears the count`() {
        assertEquals(1, nextOggMuxRefusals(current = 0, ok = false, refused = true))
        assertEquals(OGG_REFUSALS_BEFORE_AAC, nextOggMuxRefusals(current = 1, ok = false, refused = true))
        assertEquals(0, nextOggMuxRefusals(current = 1, ok = true, refused = false))
    }

    @Test
    fun `a failure that is not a refused track leaves the count`() {
        // a broken file (no audio track, a read error) says nothing about the device muxer
        assertEquals(1, nextOggMuxRefusals(current = 1, ok = false, refused = false))
    }
}
