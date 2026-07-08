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
}
