package com.jtech.zemer.playback

import java.util.Locale

/**
 * Accumulates the media-time ranges ACTUALLY played during one playback session, and drains them as
 * the `st`/`et` parameter lists of a watchtime ping.
 *
 * Pure and single-caller-thread-confined (the service main scope) — no player, no clock. The owner
 * feeds it real position observations:
 *  - [onPlay] when playback (re)starts at a position — opens a segment;
 *  - [onProgress] with the current position while playing — extends the open segment;
 *  - [onPause] when playback stops — closes the open segment;
 *  - [onSeek] on a position discontinuity — closes at the departed position, reopens at the target;
 *  - [drain] to emit everything watched since the previous drain (the delta model the official web
 *    client uses: each ping carries only the newly watched ranges, never a cumulative resend).
 *
 * The hard rule is honesty: a range is reported iff the player actually traversed it. Seeking is
 * never watched time; a paused player accumulates nothing.
 */
class WatchTimeSegments {

    /**
     * One drain's watched ranges as the `st`/`et` strings of a watchtime ping, plus [watchedMs] —
     * the total real media-time these ranges cover (the deferred-offline queue sums it across a
     * listen to gate and to report `rt`). Defaulted so the zero-length final-ping construction is
     * unaffected.
     */
    data class Drained(val st: String, val et: String, val watchedMs: Long = 0L)

    private val pending = mutableListOf<Pair<Long, Long>>()
    private var openStartMs = -1L
    private var lastPositionMs = 0L

    /** Whether a segment is currently open (playback believed live). */
    val isOpen: Boolean get() = openStartMs >= 0

    /**
     * The last real media position observed for THIS item (open segment's latest progress, or the
     * last close point). The session's end position must fall back to this — never the player's
     * current position, which after a track/queue change already belongs to a DIFFERENT item and
     * would fabricate a range spanning into the new track.
     */
    fun lastKnownPositionMs(): Long = lastPositionMs

    fun onPlay(positionMs: Long) {
        if (openStartMs < 0) {
            openStartMs = positionMs.coerceAtLeast(0)
        }
        lastPositionMs = positionMs.coerceAtLeast(0)
    }

    fun onProgress(positionMs: Long) {
        if (openStartMs < 0) return
        // A backwards jump without a seek event (timeline correction) must not fabricate time —
        // close what was genuinely played and restart from the corrected position.
        if (positionMs < lastPositionMs) {
            closeAt(lastPositionMs)
            openStartMs = positionMs.coerceAtLeast(0)
        }
        lastPositionMs = positionMs.coerceAtLeast(0)
    }

    fun onPause(positionMs: Long) {
        closeAt(positionMs)
    }

    /** [wasPlaying] keeps a paused seek from opening a segment that nothing is playing. */
    fun onSeek(fromPositionMs: Long, toPositionMs: Long, wasPlaying: Boolean) {
        closeAt(fromPositionMs)
        if (wasPlaying) {
            openStartMs = toPositionMs.coerceAtLeast(0)
        }
        lastPositionMs = toPositionMs.coerceAtLeast(0)
    }

    /**
     * Emits every range watched since the previous drain and re-opens at [currentPositionMs] when
     * [stillPlaying], so the session keeps accumulating seamlessly. Null when nothing meaningful
     * (< [MIN_SEGMENT_MS] total) was watched — the caller skips the ping rather than sending noise.
     */
    fun drain(currentPositionMs: Long, stillPlaying: Boolean): Drained? {
        if (openStartMs >= 0) {
            closeAt(maxOf(currentPositionMs, lastPositionMs))
        }
        val emitted = pending.filter { (start, end) -> end - start >= MIN_SEGMENT_MS }
        pending.clear()
        if (stillPlaying) {
            openStartMs = currentPositionMs.coerceAtLeast(0)
            lastPositionMs = openStartMs
        }
        if (emitted.isEmpty()) return null
        return Drained(
            st = emitted.joinToString(",") { formatSeconds(it.first) },
            et = emitted.joinToString(",") { formatSeconds(it.second) },
            watchedMs = emitted.sumOf { (start, end) -> end - start },
        )
    }

    private fun closeAt(positionMs: Long) {
        if (openStartMs < 0) return
        val end = maxOf(positionMs, openStartMs)
        if (end > openStartMs) {
            pending += openStartMs to end
        }
        openStartMs = -1
        lastPositionMs = end
    }

    companion object {
        /** Below this a "segment" is jitter (double events, rounding), not listening. */
        const val MIN_SEGMENT_MS = 500L

        /** Media/wall times ride the beacons as seconds with one decimal (the web client's shape). */
        fun formatSeconds(ms: Long): String = String.format(Locale.US, "%.1f", ms / 1000.0)
    }
}
