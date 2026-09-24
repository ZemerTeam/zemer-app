package com.jtech.zemer.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EpisodeResumeTest {
    private val dur = 60 * 60 * 1000L // a 1h episode

    @Test
    fun `at the beginning does not resume`() {
        assertFalse(EpisodeResume.shouldResume(0L, dur))
        assertFalse(EpisodeResume.shouldResume(EpisodeResume.RESUME_EDGE_MS, dur))
        assertFalse(EpisodeResume.shouldResume(EpisodeResume.RESUME_EDGE_MS - 1, dur))
    }

    @Test
    fun `mid episode resumes`() {
        assertTrue(EpisodeResume.shouldResume(EpisodeResume.RESUME_EDGE_MS + 1, dur))
        assertTrue(EpisodeResume.shouldResume(dur / 2, dur))
    }

    @Test
    fun `finished episode restarts from zero`() {
        // Within the completion edge of the end (or past it) is "done" -> do not resume.
        assertFalse(EpisodeResume.shouldResume(dur, dur))
        assertFalse(EpisodeResume.shouldResume(dur - EpisodeResume.COMPLETION_EDGE_MS, dur))
        assertFalse(EpisodeResume.shouldResume(dur - EpisodeResume.COMPLETION_EDGE_MS + 1, dur))
        // Just before the completion edge still resumes.
        assertTrue(EpisodeResume.shouldResume(dur - EpisodeResume.COMPLETION_EDGE_MS - 1, dur))
    }

    @Test
    fun `unknown duration resumes on any past-edge position`() {
        assertTrue(EpisodeResume.shouldResume(EpisodeResume.RESUME_EDGE_MS + 1, null))
        assertTrue(EpisodeResume.shouldResume(5 * 60 * 60 * 1000L, null)) // even a huge value
        assertFalse(EpisodeResume.shouldResume(EpisodeResume.RESUME_EDGE_MS, null))
        // Non-positive duration is treated as unknown (no completion cap).
        assertTrue(EpisodeResume.shouldResume(EpisodeResume.RESUME_EDGE_MS + 1, 0L))
    }

    @Test
    fun `timeLeftMs returns remaining only for a resumable position`() {
        // Mid-episode: remaining is duration - position.
        assertEquals(dur / 2, EpisodeResume.timeLeftMs(dur / 2, dur))
        // At the start / finished / null inputs: no hint.
        assertNull(EpisodeResume.timeLeftMs(0L, dur))
        assertNull(EpisodeResume.timeLeftMs(EpisodeResume.RESUME_EDGE_MS, dur))
        assertNull(EpisodeResume.timeLeftMs(dur, dur)) // finished
        assertNull(EpisodeResume.timeLeftMs(null, dur))
        assertNull(EpisodeResume.timeLeftMs(dur / 2, null)) // unknown duration -> cannot compute remaining
    }

    @Test
    fun `timeLeftMs never returns negative`() {
        // Defensive: a resumable position can't exceed duration, but the result is floored at 0.
        val left = EpisodeResume.timeLeftMs(EpisodeResume.RESUME_EDGE_MS + 1, dur)
        assertTrue(left != null && left >= 0)
    }

    @Test
    fun `shouldSave mirrors the start edge`() {
        assertFalse(EpisodeResume.shouldSave(0L))
        assertFalse(EpisodeResume.shouldSave(EpisodeResume.RESUME_EDGE_MS))
        assertTrue(EpisodeResume.shouldSave(EpisodeResume.RESUME_EDGE_MS + 1))
    }
}
