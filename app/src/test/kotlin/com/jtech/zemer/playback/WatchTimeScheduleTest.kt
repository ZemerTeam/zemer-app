package com.jtech.zemer.playback

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the official watchtime flush cadence (handoff: emulate-youtube-music-stream). The live
 * `/player` response gives `[10,20,30]` scheduled + `40` default; base.js `klA` uses the same as its
 * fallback when the response omits them. Offsets are wall-clock ms since playback start.
 */
class WatchTimeScheduleTest {

    @Test
    fun `server schedule fires at 10s 20s 30s then every 40s`() {
        val s = WatchTimeSchedule(listOf(10, 20, 30), 40)

        assertEquals(10_000L, s.flushOffsetMs(0))
        assertEquals(20_000L, s.flushOffsetMs(1))
        assertEquals(30_000L, s.flushOffsetMs(2))
        assertEquals(70_000L, s.flushOffsetMs(3))
        assertEquals(110_000L, s.flushOffsetMs(4))
        assertEquals(150_000L, s.flushOffsetMs(5))
    }

    @Test
    fun `null schedule falls back to the base_js default 10 20 30 then 40`() {
        val s = WatchTimeSchedule(null, null)

        assertEquals(10_000L, s.flushOffsetMs(0))
        assertEquals(20_000L, s.flushOffsetMs(1))
        assertEquals(30_000L, s.flushOffsetMs(2))
        assertEquals(70_000L, s.flushOffsetMs(3))
    }

    @Test
    fun `empty and non-positive inputs fall back to the default`() {
        val s = WatchTimeSchedule(emptyList(), 0)

        assertEquals(10_000L, s.flushOffsetMs(0))
        assertEquals(70_000L, s.flushOffsetMs(3))
    }

    @Test
    fun `unsorted scheduled seconds are normalized to ascending offsets`() {
        val s = WatchTimeSchedule(listOf(30, 10, 20), 40)

        assertEquals(10_000L, s.flushOffsetMs(0))
        assertEquals(30_000L, s.flushOffsetMs(2))
    }

    @Test
    fun `a single scheduled second then the default interval`() {
        val s = WatchTimeSchedule(listOf(15), 45)

        assertEquals(15_000L, s.flushOffsetMs(0))
        assertEquals(60_000L, s.flushOffsetMs(1))
        assertEquals(105_000L, s.flushOffsetMs(2))
    }
}
