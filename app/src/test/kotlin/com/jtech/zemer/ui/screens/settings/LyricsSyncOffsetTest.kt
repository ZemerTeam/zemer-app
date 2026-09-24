package com.jtech.zemer.ui.screens.settings

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Regression: the offset dialog inherited the history-duration slider's hard-coded Reset value (30 s),
 * so Reset + Confirm wrote a 30000 ms offset. Reset now returns to 0 and the conversion is exact.
 */
class LyricsSyncOffsetTest {
    @Test
    fun `reset value maps to zero offset`() {
        assertEquals(0, lyricsSyncOffsetMs(0f))
    }

    @Test
    fun `range ends map to plus and minus 1500 ms`() {
        assertEquals(-1500, lyricsSyncOffsetMs(-1.5f))
        assertEquals(1500, lyricsSyncOffsetMs(1.5f))
    }

    @Test
    fun `values snap to 50 ms steps`() {
        assertEquals(50, lyricsSyncOffsetMs(0.05f))
        assertEquals(-250, lyricsSyncOffsetMs(-0.25f))
        assertEquals(30000, lyricsSyncOffsetMs(30f)) // the old reset value, kept out of range by the slider
    }
}
