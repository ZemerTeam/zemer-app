package com.jtech.zemer.viewmodels

import org.junit.Assert.assertEquals
import org.junit.Test

class RestoreRetryDelayTest {
    @Test
    fun `backoff doubles from 2s and caps at 30s`() {
        assertEquals(listOf(2_000L, 4_000L, 8_000L, 16_000L, 30_000L, 30_000L, 30_000L), (0..6).map(::restoreRetryDelayMs))
    }

    @Test
    fun `a huge attempt count never overflows`() {
        assertEquals(30_000L, restoreRetryDelayMs(10_000))
    }
}
