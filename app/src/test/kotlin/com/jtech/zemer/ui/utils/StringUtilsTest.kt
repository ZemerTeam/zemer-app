package com.jtech.zemer.ui.utils

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class StringUtilsTest {

    @Test
    fun `numberFormatter groups thousands with the locale separator - comma in en-US`() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.US)
            assertEquals("97,469", numberFormatter(97469))
            assertEquals("1,000,000", numberFormatter(1_000_000))
            assertEquals("0", numberFormatter(0))
        } finally {
            Locale.setDefault(previous)
        }
    }
}
