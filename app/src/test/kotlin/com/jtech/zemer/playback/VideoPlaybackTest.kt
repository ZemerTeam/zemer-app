package com.jtech.zemer.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure coverage of the shared video routing rule. Guards the one invariant the whole feature rests on:
 * a video plays as audio whenever it is blocked OR the audio-only pref is set, and watching is only
 * ever allowed when imagery is not blocked.
 */
class VideoPlaybackTest {

    @Test
    fun `non-videos always play as audio regardless of flags`() {
        for (block in listOf(false, true)) {
            for (pref in listOf(false, true)) {
                assertTrue(videoPlaysAsAudio(isVideo = false, blockVideos = block, playVideosAsAudio = pref))
            }
        }
    }

    @Test
    fun `a video watches by default when nothing forces audio`() {
        assertFalse(videoPlaysAsAudio(isVideo = true, blockVideos = false, playVideosAsAudio = false))
    }

    @Test
    fun `blocked videos always play as audio`() {
        assertTrue(videoPlaysAsAudio(isVideo = true, blockVideos = true, playVideosAsAudio = false))
        assertTrue(videoPlaysAsAudio(isVideo = true, blockVideos = true, playVideosAsAudio = true))
    }

    @Test
    fun `the audio-only preference makes unblocked videos play as audio`() {
        assertTrue(videoPlaysAsAudio(isVideo = true, blockVideos = false, playVideosAsAudio = true))
    }

    @Test
    fun `watching is allowed only when imagery is not blocked`() {
        assertTrue(videoWatchAllowed(blockVideos = false))
        assertFalse(videoWatchAllowed(blockVideos = true))
    }
}
