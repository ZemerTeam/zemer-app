package com.dpi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pins the density re-application decision (issue #521): it must be IDEMPOTENT (the framework wipes
 * the `updateConfiguration` override on every system config delivery, so re-application fires from
 * several hooks and must never compound), and it must treat any non-applied incoming dpi as the
 * CURRENT native base so a real system density change (Display size) rescales from the new value.
 */
class DensityMathTest {

    @Test
    fun `scales from the native dpi`() {
        assertEquals(360, DensityMath.targetDpi(currentDpi = 480, lastAppliedDpi = null, scale = 0.75f))
    }

    @Test
    fun `already-applied dpi is a no-op - re-application never compounds the scale`() {
        assertNull(DensityMath.targetDpi(currentDpi = 360, lastAppliedDpi = 360, scale = 0.75f))
    }

    @Test
    fun `a framework wipe back to native rescales`() {
        // Applied 480 -> 360, then a config delivery reset the dpi to 480: scale again.
        assertEquals(360, DensityMath.targetDpi(currentDpi = 480, lastAppliedDpi = 360, scale = 0.75f))
    }

    @Test
    fun `a genuine system density change rescales from the NEW base, not the startup one`() {
        // User changed Display size 480 -> 420 while our 360 was applied: 420 is the new native.
        assertEquals(315, DensityMath.targetDpi(currentDpi = 420, lastAppliedDpi = 360, scale = 0.75f))
    }

    @Test
    fun `a scale that rounds to the incoming dpi is a no-op`() {
        assertNull(DensityMath.targetDpi(currentDpi = 480, lastAppliedDpi = null, scale = 1.001f))
    }

    @Test
    fun `nonsense dpi values never produce a write`() {
        assertNull(DensityMath.targetDpi(currentDpi = 0, lastAppliedDpi = null, scale = 0.75f))
        assertNull(DensityMath.targetDpi(currentDpi = -160, lastAppliedDpi = null, scale = 0.75f))
        assertNull(DensityMath.targetDpi(currentDpi = 1, lastAppliedDpi = null, scale = 0.1f))
    }
}
