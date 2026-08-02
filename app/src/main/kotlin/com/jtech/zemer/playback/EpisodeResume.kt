package com.jtech.zemer.playback

/**
 * Pure decision logic for podcast-episode resume, extracted so it is unit-testable without a running
 * ExoPlayer/Room (the save/restore wiring in MusicService is the untestable part; this is the rule).
 */
object EpisodeResume {
    /** Below this from the start we treat the episode as "at the beginning" (no save, no resume seek). */
    const val RESUME_EDGE_MS = 5_000L

    /** Within this of the END we treat the episode as finished, so a replay restarts from 0. */
    const val COMPLETION_EDGE_MS = 15_000L

    /**
     * Whether a saved position should be resumed. False when we are still near the start (nothing
     * meaningful to resume) OR when the saved position is effectively at the end (a finished episode
     * restarts from 0 instead of dumping the user at the outro). [durationMs] null = unknown length,
     * so the completion check is skipped and we resume on any past-the-edge position.
     */
    fun shouldResume(savedMs: Long, durationMs: Long?): Boolean {
        if (savedMs <= RESUME_EDGE_MS) return false
        if (durationMs != null && durationMs > 0 && savedMs >= durationMs - COMPLETION_EDGE_MS) return false
        return true
    }

    /** Whether a position is worth persisting (past the "at the beginning" edge). */
    fun shouldSave(positionMs: Long): Boolean = positionMs > RESUME_EDGE_MS
}
