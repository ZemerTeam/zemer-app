package com.jtech.zemer.playback.sabr

/**
 * Pure seek-restart decision shared by [SabrAudioStream] and [SabrVideoStream] (their read
 * orchestration was near-verbatim duplicated). Given the live session's landing anchor and the reader's
 * target byte, it decides whether to let the current demand-paced drain reach the target, wait out a
 * just-started session's landing grace, cold-start a fresh session at an estimated player time, or give
 * up after the restart budget. Fully JVM-unit-tested ([SabrSeekLogicTest]) — no Android, no I/O.
 *
 * Two correctness rules encoded here (both were live-review findings):
 *  - A session that landed AT OR BEFORE the target ([LetDrain]) must NEVER be restarted: re-issuing the
 *    same linear estimate only cancels the one session making forward progress, so a seek that lands
 *    early by more than a catch-up window used to burn the whole restart budget into a player error.
 *  - An unknown duration yields estimate 0, i.e. a from-0 session, which then lands at/before the target
 *    and drains forward — never an endless restart-at-0 loop.
 */
internal object SabrSeekLogic {
    const val LAND_GRACE_MS = 4_000L
    const val SEEK_MARGIN_MS = 5_000L
    const val MAX_SEEK_RESTARTS = 6
    private const val MAX_MARGIN_MS = 60_000L

    /**
     * Linear byte->time estimate for a cold-start seek to [position], pulled back by [marginMs] so the
     * session lands slightly BEFORE the target. 0 when no estimate is possible (unknown [durationMs] /
     * [contentLength], or the very start) — the caller then starts a from-0 session that drains forward.
     */
    fun estimateStartMs(position: Long, durationMs: Long, contentLength: Long, marginMs: Long): Long {
        if (durationMs <= 0 || contentLength <= 0 || position <= 0) return 0
        return (position * durationMs / contentLength - marginMs).coerceAtLeast(0)
    }

    sealed interface Action
    /** An alive session started at/before the target is draining toward it — leave it alone. */
    object LetDrain : Action
    /** A just-started session hasn't landed its first segment yet — give it grace. */
    object Grace : Action
    /** Cold-start a fresh session at [startMs]; the stream adopts [marginMs] for its next decision. */
    data class Restart(val startMs: Long, val marginMs: Long) : Action
    /** The restart budget is exhausted — the stream marks the buffer errored. */
    object GiveUp : Action

    fun decide(
        sessionAlive: Boolean,
        anchor: Long,
        position: Long,
        sinceStartMs: Long,
        restartAttempts: Int,
        durationMs: Long,
        contentLength: Long,
        marginMs: Long,
    ): Action {
        var margin = marginMs
        if (sessionAlive) {
            when {
                anchor < 0 -> if (sinceStartMs < LAND_GRACE_MS) return Grace   // hasn't landed yet
                anchor <= position -> return LetDrain                          // draining toward the target
                else -> margin = (marginMs * 2).coerceAtMost(MAX_MARGIN_MS)    // landed PAST -> widen + re-aim
            }
        }
        if (restartAttempts >= MAX_SEEK_RESTARTS) return GiveUp
        return Restart(estimateStartMs(position, durationMs, contentLength, margin), margin)
    }
}
