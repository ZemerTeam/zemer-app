package com.jtech.zemer.playback.sabr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Pins the SABR video-rung selection: at-or-below the target, avc1 preferred, never null when rungs exist. */
class SabrVideoQualityTest {

    private fun r(itag: Int, h: Int, mime: String, bitrate: Int = 1000) =
        SabrVideoQuality.Rung(itag, h, mime, bitrate, contentLength = 1000L)

    private val ladder = listOf(
        r(160, 144, "video/mp4; codecs=\"avc1\""),
        r(133, 240, "video/mp4; codecs=\"avc1\""),
        r(134, 360, "video/mp4; codecs=\"avc1\""),
        r(135, 480, "video/mp4; codecs=\"avc1\""),
        r(136, 720, "video/mp4; codecs=\"avc1\""),
        r(247, 720, "video/webm; codecs=\"vp9\""),
        r(137, 1080, "video/mp4; codecs=\"avc1\""),
        r(399, 1080, "video/mp4; codecs=\"av01\""),
    )

    @Test
    fun `picks the best rung at or below the target`() {
        assertEquals(136, SabrVideoQuality.select(ladder, 720)?.itag)
        assertEquals(134, SabrVideoQuality.select(ladder, 360)?.itag)
        assertEquals(137, SabrVideoQuality.select(ladder, 1080)?.itag)
    }

    @Test
    fun `prefers avc1 over vp9 or av01 at the same height`() {
        // 720p has both avc1 (136) and vp9 (247) -> avc1 wins
        assertEquals(136, SabrVideoQuality.select(ladder, 720)?.itag)
        // 1080p has avc1 (137) and av01 (399) -> avc1 wins
        assertEquals(137, SabrVideoQuality.select(ladder, 1080)?.itag)
    }

    @Test
    fun `falls to the smallest rung when every rung is taller than the target`() {
        val tall = listOf(r(136, 720, "video/mp4; codecs=\"avc1\""), r(137, 1080, "video/mp4; codecs=\"avc1\""))
        assertEquals(136, SabrVideoQuality.select(tall, 240)?.itag) // 240 target, smallest available is 720
    }

    @Test
    fun `null only when there are no rungs`() {
        assertNull(SabrVideoQuality.select(emptyList(), 720))
    }
}
