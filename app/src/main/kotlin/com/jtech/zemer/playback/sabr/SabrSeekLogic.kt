package com.jtech.zemer.playback.sabr

/**
 * Pure seek-restart decision shared by [SabrAudioStream] and [SabrVideoStream]. Given the live session's
 * landing anchor and the reader's target byte, it decides whether to let the current demand-paced drain
 * reach the target, wait out a just-started session's landing grace, cold-start a fresh session at an
 * estimated player time, or give up after the restart budget. Fully JVM-unit-tested
 * ([SabrSeekLogicTest]) — no Android, no I/O.
 *
 * Two correctness rules encoded here:
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
    /**
     * An alive session started at/before the target is draining toward it — leave it alone. The stream
     * raises the buffer's demand watermark to the target ([SabrBuffer.raiseDemandTo]) so the paced
     * drain actually proceeds toward it instead of waiting on the pre-seek read position.
     */
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
        // a session that keeps dying young is restarted with growing pauses, never in a tight loop
        if (!sessionAlive && sinceStartMs < restartBackoffMs(restartAttempts)) return Grace
        return Restart(estimateStartMs(position, durationMs, contentLength, margin), margin)
    }

    /** The pause before restarting after [attempts] restarts: none, then 1 s, 2 s, 4 s … at most 16 s. */
    fun restartBackoffMs(attempts: Int): Long = if (attempts <= 0) 0L else 1_000L shl (attempts - 1).coerceAtMost(4)

    /**
     * Whether a served read refills the restart budget: the first read after a seek (not [continuous] with the
     * previous one), or a read from a session that has run steadily for [STABLE_SESSION_MS]. A run of short sessions
     * each serving a little (a client the server caps after a free window) never does, so it ends in [GiveUp].
     */
    fun resetsRestartBudget(continuous: Boolean, sessionAgeMs: Long): Boolean = !continuous || sessionAgeMs >= STABLE_SESSION_MS

    const val STABLE_SESSION_MS = 30_000L
}
