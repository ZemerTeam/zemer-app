package com.jtech.zemer.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class StringUtilsTest {
    @Test
    fun `bidiIsolate wraps text in first-strong isolates`() {
        assertEquals("⁨עמיחי⁩", bidiIsolate("עמיחי"))
    }

    @Test
    fun `bidiIsolate leaves empty text empty so joinByBullet still drops it`() {
        assertEquals("1 time", joinByBullet(bidiIsolate(""), "1 time"))
    }
}
