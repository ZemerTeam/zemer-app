package com.jtech.zemer.utils

import com.jtech.zemer.constants.RefreshRateMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pins the refresh-rate picker across the 3-state policy and any device's mode set: HIGH resolves to the
 * highest rate at the current resolution, STANDARD to ~60Hz, and SYSTEM to no forced mode (adaptive).
 */
class RefreshRateSelectionTest {

    private val mode60 = DisplayModeInfo(modeId = 1, physicalWidth = 1080, physicalHeight = 2400, refreshRate = 60f)
    private val mode90 = DisplayModeInfo(modeId = 2, physicalWidth = 1080, physicalHeight = 2400, refreshRate = 90f)
    private val mode120 = DisplayModeInfo(modeId = 3, physicalWidth = 1080, physicalHeight = 2400, refreshRate = 120f)

    @Test
    fun `high picks the highest rate at the current resolution`() {
        val picked = selectRefreshRateMode(listOf(mode60, mode90, mode120), current = mode60, RefreshRateMode.HIGH)
        assertEquals(mode120, picked)
    }

    @Test
    fun `standard pins an exact 60Hz mode`() {
        val picked = selectRefreshRateMode(listOf(mode60, mode90, mode120), current = mode120, RefreshRateMode.STANDARD)
        assertEquals(mode60, picked)
    }

    @Test
    fun `standard falls back to the closest to 60 when no exact 60 exists`() {
        val mode48 = DisplayModeInfo(4, 1080, 2400, 48f)
        val picked = selectRefreshRateMode(listOf(mode48, mode90, mode120), current = mode120, RefreshRateMode.STANDARD)
        assertEquals(mode48, picked) // 48 is closer to 60 than 90
    }

    @Test
    fun `system never forces a mode`() {
        // Even on a rich LTPO set, SYSTEM leaves the rate unforced so the OS runs adaptive refresh.
        assertNull(selectRefreshRateMode(listOf(mode60, mode90, mode120), current = mode60, RefreshRateMode.SYSTEM))
    }

    @Test
    fun `a 60Hz-only device is a no-op for standard and high`() {
        assertEquals(mode60, selectRefreshRateMode(listOf(mode60), current = mode60, RefreshRateMode.HIGH))
        assertEquals(mode60, selectRefreshRateMode(listOf(mode60), current = mode60, RefreshRateMode.STANDARD))
    }

    @Test
    fun `high never drops resolution - a faster low-res mode is excluded`() {
        // A 144Hz mode at a LOWER resolution must not win over the 120Hz mode at the current resolution.
        val fastLowRes = DisplayModeInfo(modeId = 9, physicalWidth = 720, physicalHeight = 1600, refreshRate = 144f)
        val picked = selectRefreshRateMode(listOf(mode60, mode120, fastLowRes), current = mode60, RefreshRateMode.HIGH)
        assertEquals(mode120, picked)
    }

    @Test
    fun `null current considers every mode`() {
        val picked = selectRefreshRateMode(listOf(mode60, mode90, mode120), current = null, RefreshRateMode.HIGH)
        assertEquals(mode120, picked)
    }

    @Test
    fun `empty mode list returns null for standard and high`() {
        assertNull(selectRefreshRateMode(emptyList(), current = null, RefreshRateMode.HIGH))
        assertNull(selectRefreshRateMode(emptyList(), current = mode60, RefreshRateMode.STANDARD))
    }

    @Test
    fun `preferredDisplayModeId maps a chosen mode to its id, and null to 0`() {
        assertEquals(mode120.modeId, preferredDisplayModeId(mode120))
        // null (SYSTEM, or no modes) resets the window to adaptive, clearing any previously forced mode.
        assertEquals(0, preferredDisplayModeId(null))
    }

    @Test
    fun `preferredRefreshRateHz uses the chosen rate, else 60 for standard and 0 otherwise`() {
        assertEquals(120f, preferredRefreshRateHz(RefreshRateMode.HIGH, mode120), 0.001f)
        assertEquals(60f, preferredRefreshRateHz(RefreshRateMode.STANDARD, null), 0.001f)
        assertEquals(0f, preferredRefreshRateHz(RefreshRateMode.HIGH, null), 0.001f)
        assertEquals(0f, preferredRefreshRateHz(RefreshRateMode.SYSTEM, null), 0.001f)
    }

    @Test
    fun `migration preserves an explicit off as standard, else adopts system`() {
        assertEquals(RefreshRateMode.STANDARD, migrateRefreshRateMode(false)) // explicit battery choice kept
        assertEquals(RefreshRateMode.SYSTEM, migrateRefreshRateMode(true))    // old default was force-on
        assertEquals(RefreshRateMode.SYSTEM, migrateRefreshRateMode(null))    // never set
    }
}
