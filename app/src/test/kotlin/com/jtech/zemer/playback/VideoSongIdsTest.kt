package com.jtech.zemer.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

/**
 * Regression tests for the Song/Video toggle disappearing mid-session (#385): the registry is an
 * access-ordered LRU, and only get/put refresh recency — so [VideoSongIds.contains] must read via
 * `get`, or the CURRENTLY PLAYING id (read every availability recompute, never re-marked) ages out
 * after 512 later marks and the toggle silently vanishes for the song on screen.
 *
 * VideoSongIds is a process-wide singleton, so every test uses fresh unique ids (no reset API —
 * deliberately, production has no reset either).
 */
class VideoSongIdsTest {
    private fun uniqueId() = "vid-" + UUID.randomUUID()

    @Test
    fun `contains reads refresh recency so a re-read id survives eviction pressure`() {
        val playing = uniqueId()
        VideoSongIds.mark(playing)

        // Two full waves of eviction pressure with a read between them: the read must keep the id
        // alive through the second wave (with containsKey it would age out during the second).
        repeat(511) { VideoSongIds.mark(uniqueId()) }
        assertTrue("id evicted before the LRU was even full", VideoSongIds.contains(playing))
        repeat(511) { VideoSongIds.mark(uniqueId()) }
        assertTrue("contains() read did not refresh recency", VideoSongIds.contains(playing))
    }

    @Test
    fun `an unread id still ages out under sustained marks`() {
        val stale = uniqueId()
        VideoSongIds.mark(stale)
        repeat(513) { VideoSongIds.mark(uniqueId()) }
        assertFalse("LRU bound stopped evicting", VideoSongIds.contains(stale))
    }

    @Test
    fun `unmarked id is not contained`() {
        assertFalse(VideoSongIds.contains(uniqueId()))
    }
}
