package com.jtech.zemer.playback

/** Pure seek-target math, extracted so the clamp rules are unit-testable without a player or the FCast SDK. */
object SeekMath {
    /**
     * Forward-seek target (ms): advance [positionMs] by [skipMs], clamping to [durationMs] **only when the
     * duration is known** (> 0). A 0 duration (a cast track before the receiver has reported its duration)
     * or a TIME_UNSET/negative local duration must NOT clamp — clamping to it would seek to 0 instead of
     * forward, snapping a forward double-tap back to the start of the track.
     */
    fun forwardSeekTarget(positionMs: Long, skipMs: Long, durationMs: Long): Long {
        val target = positionMs + skipMs
        return if (durationMs > 0) target.coerceAtMost(durationMs) else target
    }
}
