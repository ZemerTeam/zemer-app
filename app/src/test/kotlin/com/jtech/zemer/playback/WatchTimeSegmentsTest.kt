package com.jtech.zemer.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the watch-time honesty rules: a range is reported iff the player actually traversed it —
 * seeks are never watched time, a paused player accumulates nothing, drains are deltas (never
 * cumulative resends), and sub-jitter segments are dropped.
 */
class WatchTimeSegmentsTest {

    @Test
    fun `continuous playback drains the played range`() {
        val s = WatchTimeSegments()
        s.onPlay(0)
        s.onProgress(30_000)

        val d = s.drain(30_000, stillPlaying = true)!!

        assertEquals("0.0", d.st)
        assertEquals("30.0", d.et)
    }

    @Test
    fun `drains are deltas - the second ping carries only newly watched time`() {
        val s = WatchTimeSegments()
        s.onPlay(0)
        s.drain(30_000, stillPlaying = true)

        val second = s.drain(60_000, stillPlaying = true)!!

        assertEquals("30.0", second.st)
        assertEquals("60.0", second.et)
    }

    @Test
    fun `a seek closes at the departed position and reopens at the target - the gap is never reported`() {
        val s = WatchTimeSegments()
        s.onPlay(0)
        s.onSeek(10_000, 60_000, wasPlaying = true)
        s.onProgress(65_000)

        val d = s.drain(65_000, stillPlaying = false)!!

        assertEquals("0.0,60.0", d.st)
        assertEquals("10.0,65.0", d.et)
    }

    @Test
    fun `a paused seek opens nothing`() {
        val s = WatchTimeSegments()
        s.onSeek(0, 30_000, wasPlaying = false)

        assertNull(s.drain(30_000, stillPlaying = false))
        assertFalse(s.isOpen)
    }

    @Test
    fun `pause closes the segment and no time accrues while paused`() {
        val s = WatchTimeSegments()
        s.onPlay(0)
        s.onPause(12_000)

        val d = s.drain(99_000, stillPlaying = false)!!

        assertEquals("0.0", d.st)
        assertEquals("12.0", d.et)
    }

    @Test
    fun `sub-jitter segments are dropped, not reported`() {
        val s = WatchTimeSegments()
        s.onPlay(5_000)

        assertNull(s.drain(5_200, stillPlaying = false))
    }

    @Test
    fun `a backwards position without a seek never fabricates time`() {
        val s = WatchTimeSegments()
        s.onPlay(0)
        s.onProgress(10_000)
        s.onProgress(2_000)

        val d = s.drain(6_000, stillPlaying = false)!!

        assertEquals("0.0,2.0", d.st)
        assertEquals("10.0,6.0", d.et)
    }

    @Test
    fun `drain while still playing reopens seamlessly`() {
        val s = WatchTimeSegments()
        s.onPlay(0)
        s.drain(30_000, stillPlaying = true)

        assertTrue(s.isOpen)
    }

    @Test
    fun `seconds format is one-decimal with a dot regardless of locale`() {
        assertEquals("1.2", WatchTimeSegments.formatSeconds(1_234))
        assertEquals("0.0", WatchTimeSegments.formatSeconds(0))
        assertEquals("192.5", WatchTimeSegments.formatSeconds(192_500))
    }

    // --- lastKnownPositionMs: the end-of-session fallback that must NOT be the new item's position ---

    @Test
    fun `lastKnownPositionMs tracks progress while playing`() {
        val s = WatchTimeSegments()
        s.onPlay(0)
        s.onProgress(42_000)

        assertEquals(42_000L, s.lastKnownPositionMs())
    }

    @Test
    fun `lastKnownPositionMs holds the departed position after playback stops`() {
        val s = WatchTimeSegments()
        s.onPlay(10_000)
        s.onProgress(154_000)
        s.onPause(154_000)

        // After a track change the player jumps to the new item, but the segment still knows THIS
        // item's real end — the session's final ping must close here, not at the new item's position.
        assertEquals(154_000L, s.lastKnownPositionMs())
    }

    @Test
    fun `lastKnownPositionMs is zero for a session that never played`() {
        assertEquals(0L, WatchTimeSegments().lastKnownPositionMs())
    }

    // --- Drained.watchedMs: the real watched total the deferred-offline queue sums to gate + report rt ---

    @Test
    fun `drain reports the watched milliseconds of its ranges`() {
        val s = WatchTimeSegments()
        s.onPlay(0)
        s.onProgress(30_000)

        assertEquals(30_000L, s.drain(30_000, stillPlaying = false)!!.watchedMs)
    }

    @Test
    fun `watchedMs sums multiple ranges and excludes seeked-over time`() {
        val s = WatchTimeSegments()
        s.onPlay(0)
        s.onProgress(20_000)
        s.onSeek(20_000, 60_000, wasPlaying = true) // 20s..60s skipped, not watched
        s.onProgress(80_000)

        // Watched: 0..20 (20s) + 60..80 (20s) = 40s; the seeked 20..60 is not counted.
        assertEquals(40_000L, s.drain(80_000, stillPlaying = false)!!.watchedMs)
    }
}
