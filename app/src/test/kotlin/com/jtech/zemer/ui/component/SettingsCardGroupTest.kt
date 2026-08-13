package com.jtech.zemer.ui.component

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsCardGroupTest {

    // The grouped-card stack geometry: lone card fully rounded; outer edges large, seams small.
    @Test
    fun settingsCardCorners_stackGeometry() {
        assertEquals(24 to 24, settingsCardCorners(0, 1))

        assertEquals(24 to 6, settingsCardCorners(0, 3))
        assertEquals(6 to 6, settingsCardCorners(1, 3))
        assertEquals(6 to 24, settingsCardCorners(2, 3))

        assertEquals(24 to 6, settingsCardCorners(0, 2))
        assertEquals(6 to 24, settingsCardCorners(1, 2))
    }
}
