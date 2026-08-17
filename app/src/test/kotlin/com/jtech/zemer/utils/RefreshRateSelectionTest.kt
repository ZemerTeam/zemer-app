package com.jtech.zemer.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pins the refresh-rate picker against the regression it was written to fix: "high" must resolve to the
 * HIGHEST rate at the current resolution, not "system default" (the old `preferredDisplayModeId = 0`
 * no-op that left 120Hz panels at 60Hz).
 */
class RefreshRateSelectionTest {

    private val mode60 = DisplayModeInfo(modeId = 1, physicalWidth = 1080, physicalHeight = 2400, refreshRate = 60f)
    private val mode90 = DisplayModeInfo(modeId = 2, physicalWidth = 1080, physicalHeight = 2400, refreshRate = 90f)
    private val mode120 = DisplayModeInfo(modeId = 3, physicalWidth = 1080, physicalHeight = 2400, refreshRate = 120f)

    @Test
    fun `high picks the highest rate at the current resolution`() {
        val picked = selectRefreshRateMode(listOf(mode60, mode90, mode120), current = mode60, high = true)
        assertEquals(mode120, picked)
    }

    @Test
    fun `not high pins an exact 60Hz mode`() {
        val picked = selectRefreshRateMode(listOf(mode60, mode90, mode120), current = mode120, high = false)
        assertEquals(mode60, picked)
    }

    @Test
    fun `not high falls back to the closest to 60 when no exact 60 exists`() {
        val mode48 = DisplayModeInfo(4, 1080, 2400, 48f)
        val picked = selectRefreshRateMode(listOf(mode48, mode90, mode120), current = mode120, high = false)
        assertEquals(mode48, picked) // 48 is closer to 60 than 90
    }

    @Test
    fun `high never drops resolution - a faster low-res mode is excluded`() {
        // A 144Hz mode at a LOWER resolution must not win over the 120Hz mode at the current resolution.
        val fastLowRes = DisplayModeInfo(modeId = 9, physicalWidth = 720, physicalHeight = 1600, refreshRate = 144f)
        val picked = selectRefreshRateMode(listOf(mode60, mode120, fastLowRes), current = mode60, high = true)
        assertEquals(mode120, picked)
    }

    @Test
    fun `null current considers every mode`() {
        val picked = selectRefreshRateMode(listOf(mode60, mode90, mode120), current = null, high = true)
        assertEquals(mode120, picked)
    }

    @Test
    fun `empty mode list returns null`() {
        assertNull(selectRefreshRateMode(emptyList(), current = null, high = true))
        assertNull(selectRefreshRateMode(emptyList(), current = mode60, high = false))
    }
}
