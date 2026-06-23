package com.jtech.zemer.playback

/**
 * Pure end-of-track decision logic for FCast auto-advance, extracted from [PlayerConnection] so the
 * timing thresholds are unit-testable without a player, the FCast SDK, or an Android runtime.
 *
 * Two detectors decide a cast track has finished and the queue should advance:
 *  - the remote device reports IDLE coming from PLAYING ([nearEnd] with [IDLE_END_EPSILON_SEC]), and
 *  - the remote clock stops advancing near the end ([nearEnd] with [STALL_END_EPSILON_SEC] + [stalled]).
 *
 * Both are gated by [debouncePassed] so they — and a genuine media-item transition — can't
 * double-advance and skip a track. Remote position/duration are in SECONDS (as the FCast SDK
 * reports them); the debounce/stall windows are in MILLISECONDS.
 */
object CastAutoAdvance {
    /** How close to the end (sec) an IDLE-after-PLAYING report counts as "finished". */
    const val IDLE_END_EPSILON_SEC = 2.0

    /** How close to the end (sec) a stalled remote clock counts as "finished". */
    const val STALL_END_EPSILON_SEC = 3.0

    /** Remote time must have been silent at least this long (ms) to count as stalled. */
    const val STALL_SILENCE_MS = 4000L

    /** Debounce so the idle and stall detectors (and a real transition) can't double-advance. */
    const val ADVANCE_DEBOUNCE_MS = 8000L

    /** The remote clock is within [epsilonSec] of — or past — the track end. */
    fun nearEnd(durationSec: Double, lastPositionSec: Double, epsilonSec: Double): Boolean =
        durationSec > 0.0 && lastPositionSec >= durationSec - epsilonSec

    /** Enough time has passed since the last track transition to allow another advance. */
    fun debouncePassed(nowMs: Long, lastTransitionMs: Long): Boolean =
        nowMs - lastTransitionMs > ADVANCE_DEBOUNCE_MS

    /** The remote clock has been silent long enough to treat playback as stalled. */
    fun stalled(stalledForMs: Long): Boolean = stalledForMs > STALL_SILENCE_MS
}
