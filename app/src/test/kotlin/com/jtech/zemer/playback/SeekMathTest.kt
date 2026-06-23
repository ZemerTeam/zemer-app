package com.jtech.zemer.playback

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the forward-seek clamp. The regression: while casting, the remote duration is 0 until the receiver
 * reports it, and the old code did `(pos + skip).coerceAtMost(duration)` — clamping to 0 and snapping a
 * forward double-tap back to the start of the track. The clamp must apply only when the duration is known.
 */
class SeekMathTest {

    @Test
    fun `forward seek clamps to the end when the duration is known`() {
        // 100s in, +5s, of a 180s track -> 105s.
        assertEquals(105_000L, SeekMath.forwardSeekTarget(100_000L, 5_000L, 180_000L))
        // Near the end, the clamp caps at the duration (never past it).
        assertEquals(180_000L, SeekMath.forwardSeekTarget(178_000L, 5_000L, 180_000L))
    }

    @Test
    fun `forward seek does NOT clamp when the duration is unknown`() {
        // Cast track just loaded: remote duration still 0 -> advance, never snap back to 0.
        assertEquals(35_000L, SeekMath.forwardSeekTarget(30_000L, 5_000L, 0L))
        // Local unknown duration is TIME_UNSET (a large negative) -> still must advance, not clamp.
        assertEquals(35_000L, SeekMath.forwardSeekTarget(30_000L, 5_000L, Long.MIN_VALUE + 1))
        assertEquals(35_000L, SeekMath.forwardSeekTarget(30_000L, 5_000L, -1L))
    }

    @Test
    fun `forward seek from zero advances by the skip amount`() {
        assertEquals(5_000L, SeekMath.forwardSeekTarget(0L, 5_000L, 0L))
        assertEquals(5_000L, SeekMath.forwardSeekTarget(0L, 5_000L, 180_000L))
    }
}
