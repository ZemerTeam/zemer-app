package com.jtech.zemer.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test

class EpisodeSpeedTest {

    @Test
    fun `cycles through the pill steps and wraps`() {
        assertEquals(1.25f, nextEpisodeSpeed(1f))
        assertEquals(1.5f, nextEpisodeSpeed(1.25f))
        assertEquals(2f, nextEpisodeSpeed(1.75f))
        assertEquals(1f, nextEpisodeSpeed(2f))
    }

    @Test
    fun `an off-cycle speed set by the tempo dialog cycles from the nearest step`() {
        assertEquals(1f, nextEpisodeSpeed(1.9f)) // nearest 2x -> wraps to 1x
        assertEquals(1.25f, nextEpisodeSpeed(0.5f)) // nearest 1x
        assertEquals(1.75f, nextEpisodeSpeed(1.6f)) // nearest 1.5x
    }

    @Test
    fun `labels drop the decimal only for whole numbers`() {
        assertEquals("1×", episodeSpeedLabel(1f))
        assertEquals("1.25×", episodeSpeedLabel(1.25f))
        assertEquals("2×", episodeSpeedLabel(2f))
    }

    @Test
    fun `skip forward advances 30s and clamps to a known duration`() {
        assertEquals(90_000L, episodeSkipTarget(60_000L, 3_600_000L, forward = true))
        assertEquals(3_600_000L, episodeSkipTarget(3_590_000L, 3_600_000L, forward = true))
    }

    @Test
    fun `skip forward with an unset duration is unclamped (the player clamps internally)`() {
        assertEquals(90_000L, episodeSkipTarget(60_000L, -1L, forward = true))
        assertEquals(90_000L, episodeSkipTarget(60_000L, 0L, forward = true))
    }

    @Test
    fun `skip back rewinds 30s and clamps to the start`() {
        assertEquals(30_000L, episodeSkipTarget(60_000L, 3_600_000L, forward = false))
        assertEquals(0L, episodeSkipTarget(10_000L, 3_600_000L, forward = false))
    }
}
