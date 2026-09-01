package com.dpi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pins the STATELESS density re-application decision (issue #521): the framework wipes the
 * `updateConfiguration` override on every system config delivery, so re-application fires from
 * several hooks and must be idempotent — with no memory to corrupt, the target derives only from
 * the CURRENT system-native dpi, so a genuine density change (Display size) rescales by
 * construction, even to a value that equals a previously applied one.
 */
class DensityMathTest {

    @Test
    fun `scales from the native dpi`() {
        assertEquals(360, DensityMath.targetDpi(currentDpi = 480, nativeDpi = 480, scale = 0.75f))
    }

    @Test
    fun `already at the target is a no-op - re-application never compounds the scale`() {
        assertNull(DensityMath.targetDpi(currentDpi = 360, nativeDpi = 480, scale = 0.75f))
    }

    @Test
    fun `a framework wipe back to native rescales`() {
        // Applied 480 -> 360, then a config delivery reset the Resources to 480: scale again.
        assertEquals(360, DensityMath.targetDpi(currentDpi = 480, nativeDpi = 480, scale = 0.75f))
    }

    @Test
    fun `a genuine system density change rescales from the NEW native base`() {
        // User changed Display size 480 -> 420; the Resources still holds our old 360.
        assertEquals(315, DensityMath.targetDpi(currentDpi = 360, nativeDpi = 420, scale = 0.75f))
    }

    @Test
    fun `a system density change landing exactly on the previously applied value still rescales`() {
        // Native went 480 -> 360 (= the old applied value): the stateless target is 270, and the
        // Resources at 360 is no longer "already correct" - the case one-field memory could not
        // distinguish.
        assertEquals(270, DensityMath.targetDpi(currentDpi = 360, nativeDpi = 360, scale = 0.75f))
    }

    @Test
    fun `native scale is always a no-op once the Resources sits at native`() {
        assertNull(DensityMath.targetDpi(currentDpi = 480, nativeDpi = 480, scale = 1.0f))
    }

    @Test
    fun `native scale restores a Resources stuck off-native`() {
        // Scale switched back to 1.0 but a stale scaled config survived in some Resources: heal it.
        assertEquals(480, DensityMath.targetDpi(currentDpi = 360, nativeDpi = 480, scale = 1.0f))
    }

    @Test
    fun `a scale that rounds to the current dpi is a no-op`() {
        assertNull(DensityMath.targetDpi(currentDpi = 480, nativeDpi = 480, scale = 1.001f))
    }

    @Test
    fun `nonsense dpi values never produce a write`() {
        assertNull(DensityMath.targetDpi(currentDpi = 0, nativeDpi = 480, scale = 0.75f))
        assertNull(DensityMath.targetDpi(currentDpi = 480, nativeDpi = 0, scale = 0.75f))
        assertNull(DensityMath.targetDpi(currentDpi = 480, nativeDpi = -160, scale = 0.75f))
        assertNull(DensityMath.targetDpi(currentDpi = 1, nativeDpi = 1, scale = 0.1f))
    }
}
