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
}
