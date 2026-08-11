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
