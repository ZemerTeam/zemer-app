package com.jtech.zemer.ui

import com.jtech.zemer.ui.utils.HOME_EASTER_EGG_TAPS
import com.jtech.zemer.ui.utils.HOME_EASTER_EGG_TAP_WINDOW_MS
import com.jtech.zemer.ui.utils.easterEggTapCount
import org.junit.Assert.assertEquals
import org.junit.Test

/** The Home-title easter egg's tap-sequence rule: quick taps accumulate, an idle gap resets. */
class HomeTitleEasterEggTest {

    @Test
    fun `quick taps accumulate to the trigger count`() {
        var count = 0
        var lastAt = 0L
        var now = 10_000L
        repeat(HOME_EASTER_EGG_TAPS) {
            count = easterEggTapCount(count, lastAt, now)
            lastAt = now
            now += 300
        }
        assertEquals(HOME_EASTER_EGG_TAPS, count)
    }

    @Test
    fun `an idle gap resets the sequence to one`() {
        val count = easterEggTapCount(previousCount = 4, lastTapAtMs = 10_000, nowMs = 10_000 + HOME_EASTER_EGG_TAP_WINDOW_MS + 1)
        assertEquals(1, count)
    }

    @Test
    fun `a tap exactly at the window edge still counts`() {
        val count = easterEggTapCount(previousCount = 2, lastTapAtMs = 10_000, nowMs = 10_000 + HOME_EASTER_EGG_TAP_WINDOW_MS)
        assertEquals(3, count)
    }
}
