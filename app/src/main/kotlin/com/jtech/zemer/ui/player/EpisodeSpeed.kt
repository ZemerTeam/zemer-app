package com.jtech.zemer.ui.player

import kotlin.math.abs

/** The episode speed-pill cycle. Pure (JVM-tested in EpisodeSpeedTest). */
val EPISODE_SPEEDS = listOf(1f, 1.25f, 1.5f, 1.75f, 2f)

fun episodeSpeedLabel(s: Float): String =
    if (s == s.toLong().toFloat()) "${s.toLong()}×" else "$s×"

/**
 * The pill's next speed, cycling from the step NEAREST to [current] — the Tempo & Pitch dialog can
 * set any value, and cycling from a stale/off-cycle value silently overrode the user's choice.
 */
fun nextEpisodeSpeed(current: Float): Float {
    val nearest = EPISODE_SPEEDS.indices.minByOrNull { abs(EPISODE_SPEEDS[it] - current) } ?: 0
    return EPISODE_SPEEDS[(nearest + 1) % EPISODE_SPEEDS.size]
}

/** The ±30s episode skip step (ms). */
const val EPISODE_SKIP_MS = 30_000L

/**
 * The ±30s skip buttons' seek target: clamped to the track start, and to the end when the duration
 * is known (an unset media3 duration is <= 0 — then the forward target is left unclamped and the
 * player clamps internally). Pure (JVM-tested in EpisodeSpeedTest) so the player and the progress
 * bar's optimistic position can't disagree about where a skip lands.
 */
fun episodeSkipTarget(currentMs: Long, durationMs: Long, forward: Boolean): Long =
    if (forward) {
        val target = currentMs + EPISODE_SKIP_MS
        if (durationMs > 0) target.coerceAtMost(durationMs) else target
    } else {
        (currentMs - EPISODE_SKIP_MS).coerceAtLeast(0)
    }
