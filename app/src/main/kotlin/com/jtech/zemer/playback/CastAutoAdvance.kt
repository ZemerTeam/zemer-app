package com.jtech.zemer.playback

/**
 * Pure end-of-track decision logic for FCast auto-advance, extracted from [PlayerConnection] so the
 * timing thresholds are unit-testable without a player, the FCast SDK, or an Android runtime.
 *
 * Two detectors decide a cast track has finished and the queue should advance:
 *  - the remote device reports IDLE coming from PLAYING near the end ([finishedNearEnd]), and
 *  - the remote clock stops advancing near the end ([nearEnd] with [STALL_END_EPSILON_SEC] + [stalled]),
 *    fed the *interpolated* clock so a coarse remote clock still reaches the end.
 *
 * Both are gated by [debouncePassed] so they — and a genuine media-item transition — can't
 * double-advance and skip a track. Remote position/duration are in SECONDS (as the FCast SDK
 * reports them); the debounce/stall windows are in MILLISECONDS.
 */
object CastAutoAdvance {
    /** How close to the end (sec) a stalled remote clock counts as "finished". */
    const val STALL_END_EPSILON_SEC = 3.0

    /** Remote time must have been silent at least this long (ms) to count as stalled. */
    const val STALL_SILENCE_MS = 4000L

    /** Debounce so the idle and stall detectors (and a real transition) can't double-advance. */
    const val ADVANCE_DEBOUNCE_MS = 8000L

    /** Floor of the IDLE-from-PLAYING "finished" window (sec) — see [finishedNearEnd]. */
    const val IDLE_END_WINDOW_SEC = 10.0

    /** Proportional tail of the IDLE-from-PLAYING "finished" window — see [finishedNearEnd]. */
    const val IDLE_END_TAIL_FRACTION = 0.1

    /**
     * How close to the end (sec) a PAUSED-after-PLAYING report counts as the track finishing. TIGHT on
     * purpose: some receivers auto-pause at exactly `pos == duration` to signal end-of-track (no IDLE, no
     * END event), and this must be distinguishable from a genuine user pause — only a pause in the final
     * couple of seconds (indistinguishable from the track ending) is treated as finished.
     */
    const val PAUSED_END_EPSILON_SEC = 2.0

    /** The remote clock is within [epsilonSec] of — or past — the track end. */
    fun nearEnd(durationSec: Double, lastPositionSec: Double, epsilonSec: Double): Boolean =
        durationSec > 0.0 && lastPositionSec >= durationSec - epsilonSec

    /**
     * The position (sec) to judge an IDLE end-of-track edge against. Chromecast-protocol receivers
     * reset the reported clock to 0 immediately BEFORE reporting IDLE at end-of-track (observed:
     * `timeChanged -> 0.0` a few ms ahead of the IDLE report), so on the IDLE edge the current report
     * already reads 0 and only the last real progress report still holds the true end position. A
     * genuine report (> 0) always wins; the fallback stands in only when the clock was just reset.
     * FCast receivers go IDLE with the clock still near the end, so for them this is the identity.
     */
    fun endEdgePositionSec(reportedSec: Double, lastProgressSec: Double): Double =
        if (reportedSec > 0.0) reportedSec else lastProgressSec

    /**
     * Whether an IDLE-after-PLAYING report should count as "the track finished". Generous on purpose: a
     * coarse FCast clock can stop reporting several seconds before the real end, so we accept the last
     * report being within the larger of [IDLE_END_WINDOW_SEC] or [IDLE_END_TAIL_FRACTION] of the duration.
     * The false positive (the user stops on the TV in the final stretch) is benign — advancing then is
     * indistinguishable from letting the track finish — whereas a missed end leaves the queue stuck.
     */
    fun finishedNearEnd(durationSec: Double, lastPositionSec: Double): Boolean {
        if (durationSec <= 0.0) return false
        val window = maxOf(IDLE_END_WINDOW_SEC, durationSec * IDLE_END_TAIL_FRACTION)
        return lastPositionSec >= durationSec - window
    }

    /** Enough time has passed since the last track transition to allow another advance. */
    fun debouncePassed(nowMs: Long, lastTransitionMs: Long): Boolean =
        nowMs - lastTransitionMs > ADVANCE_DEBOUNCE_MS

    /** The remote clock has been silent long enough to treat playback as stalled. */
    fun stalled(stalledForMs: Long): Boolean = stalledForMs > STALL_SILENCE_MS
}
