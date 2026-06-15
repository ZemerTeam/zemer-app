package com.jtech.zemer.widget

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the SizeMode.Exact compact rule: the seek row is dropped (rather than clipped) when the
 * widget is shorter than the height its full layout needs.
 */
class WidgetLayoutTest {

    @Test
    fun `seek row shows at and above the threshold`() {
        assertTrue(WidgetLayout.showSeekRow(WidgetLayout.COMPACT_SEEK_THRESHOLD_DP))
        assertTrue(WidgetLayout.showSeekRow(84f))
        assertTrue(WidgetLayout.showSeekRow(220f))
    }

    @Test
    fun `seek row is hidden below the threshold`() {
        assertFalse(WidgetLayout.showSeekRow(WidgetLayout.COMPACT_SEEK_THRESHOLD_DP - 1f))
        assertFalse(WidgetLayout.showSeekRow(40f))
        assertFalse(WidgetLayout.showSeekRow(0f))
    }
}
