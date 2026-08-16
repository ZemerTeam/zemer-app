package com.jtech.zemer.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoRenditionTest {
    @Test
    fun `key round-trips`() {
        val key = VideoRendition.key("abc123")
        assertEquals("video:abc123", key)
        assertTrue(VideoRendition.isVideoKey(key))
        assertEquals("abc123", VideoRendition.renditionId(key))
    }

    @Test
    fun `a bare id is not a video key`() {
        assertFalse(VideoRendition.isVideoKey("abc123"))
        assertEquals("abc123", VideoRendition.renditionId("abc123"))
    }

    @Test
    fun `explicit quality keys carry the itag and the merge marker`() {
        val progressive = VideoRendition.key("abc123", 22, progressive = true)
        val adaptive = VideoRendition.key("abc123", 137, progressive = false)
        assertEquals("video:abc123:p22", progressive)
        assertEquals("video:abc123:q137", adaptive)
        assertTrue(VideoRendition.isVideoKey(progressive))
        assertTrue(VideoRendition.isVideoKey(adaptive))
        // Only the adaptive (video-only) key demands the audio merge.
        assertFalse(VideoRendition.isAdaptiveVideoKey(progressive))
        assertTrue(VideoRendition.isAdaptiveVideoKey(adaptive))
        assertFalse(VideoRendition.isAdaptiveVideoKey(VideoRendition.key("abc123")))
    }

    @Test
    fun `renditionId strips any itag suffix`() {
        assertEquals("abc123", VideoRendition.renditionId("video:abc123:q137"))
        assertEquals("abc123", VideoRendition.renditionId("video:abc123:p22"))
    }

    @Test
    fun `renditionItag parses the suffix and null for the automatic key`() {
        assertEquals(137, VideoRendition.renditionItag("video:abc123:q137"))
        assertEquals(22, VideoRendition.renditionItag("video:abc123:p22"))
        assertEquals(null, VideoRendition.renditionItag(VideoRendition.key("abc123")))
        assertEquals(null, VideoRendition.renditionItag("video:abc123:x9"))
    }

    @Test
    fun `merge audio key namespace`() {
        val key = VideoRendition.mergeAudioKey("abc123")
        assertEquals("videoaudio:abc123", key)
        assertTrue(VideoRendition.isMergeAudioKey(key))
        assertFalse(VideoRendition.isMergeAudioKey("video:abc123"))
        assertFalse(VideoRendition.isVideoKey(key))
    }

    @Test
    fun `allRenditionKeys matches the whole family and nothing else`() {
        val cached = listOf(
            "abc123",                    // bare audio — NOT a rendition key
            "video:abc123",
            "video:abc123:q137",
            "video:abc123:p22",
            "videoaudio:abc123",
            "video:abc123XY",            // a DIFFERENT id sharing the prefix — must not match
            "video:other",
        )
        assertEquals(
            listOf("video:abc123", "video:abc123:q137", "video:abc123:p22", "videoaudio:abc123"),
            VideoRendition.allRenditionKeys("abc123", cached),
        )
    }

    @Test
    fun `baseVideoId maps every rendition key of one listen to the same id`() {
        // Audio, video (plain + itag rungs) and merge-audio all collapse to one base id, so they share
        // one watch-time cpn.
        assertEquals("abc123", VideoRendition.baseVideoId("abc123"))
        assertEquals("abc123", VideoRendition.baseVideoId("video:abc123"))
        assertEquals("abc123", VideoRendition.baseVideoId("video:abc123:q137"))
        assertEquals("abc123", VideoRendition.baseVideoId("video:abc123:p22"))
        assertEquals("abc123", VideoRendition.baseVideoId("videoaudio:abc123"))
    }
}
