package com.jtech.zemer.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the watch-time honesty rules (handoff: emulate-youtube-music-stream): a range is reported iff
 * the player actually traversed it — seeks are never watched time, a paused player accumulates
 * nothing, drains are deltas (never cumulative resends), and sub-jitter segments are dropped.
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
}
