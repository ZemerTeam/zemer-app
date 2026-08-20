package com.jtech.zemer.playback.sabr

import com.jtech.zemer.playback.VideoQualityRung
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pins the DOWNLOAD gates on the SABR rung pick (DIRECT's YTPlayerUtils parity): a download must only
 * ever pick a remux-capable rung — no av01, and webm/vp9 only where the framework muxer accepts
 * Opus-in-WebM (API 29+). An ungated pick drained hundreds of MB into a deterministic INCOMPATIBLE mux
 * on every explicit high-quality download. Streaming picks are unrestricted (playback needs no mux).
 */
class SabrVideoRungPickTest {

    private fun rung(label: String, height: Int, mime: String, itag: Int, bitrate: Int = height * 1000) =
        VideoQualityRung(
            label = label, height = height, width = height * 16 / 9, itag = itag, bitrate = bitrate,
            mimeType = mime, progressive = false,
            // The real ladder's flag semantics: avc1-in-mp4 only (av01 rides mp4 too but is NOT mp4Avc).
            mp4Avc = com.jtech.zemer.playback.VideoQualityLogic.isMp4Avc(mime),
        )

    private val vp9_2160 = rung("2160p", 2160, "video/webm; codecs=\"vp9\"", 315)
    private val vp9_1440 = rung("1440p", 1440, "video/webm; codecs=\"vp9\"", 271)
    private val avc_1080 = rung("1080p", 1080, "video/mp4; codecs=\"avc1.640028\"", 137)
    private val avc_720 = rung("720p", 720, "video/mp4; codecs=\"avc1.4d401f\"", 136)
    private val av01_1080 = rung("1080p", 1080, "video/mp4; codecs=\"av01.0.08M.08\"", 399)
    private val ladder = listOf(vp9_2160, vp9_1440, avc_1080, avc_720)

    @Test
    fun `streaming pick is unrestricted - an explicit 2160p pins the vp9 rung`() {
        assertEquals(vp9_2160, SabrVideoResolver.pickRung(ladder, "2160p", maxAutoBitrateKbps = null))
    }

    @Test
    fun `download pick without Opus-WebM mux support lands on the best mp4-avc rung instead`() {
        // Pre-API-29 device: MediaMuxer rejects Opus in WebM, so a 2160p (vp9-only) download target
        // must resolve to the highest remux-capable rung, never drain a file no mux can save.
        val picked = SabrVideoResolver.pickRung(
            ladder, "2160p", maxAutoBitrateKbps = null,
            downloadable = true, opusWebmMuxSupported = false,
        )
        assertEquals(avc_1080, picked)
    }

    @Test
    fun `download pick with Opus-WebM support keeps the vp9 rung`() {
        val picked = SabrVideoResolver.pickRung(
            ladder, "2160p", maxAutoBitrateKbps = null,
            downloadable = true, opusWebmMuxSupported = true,
        )
        assertEquals(vp9_2160, picked)
    }

    @Test
    fun `the download gate applies to the AUTO fallback pool too, and av01 never downloads`() {
        // AUTO on an av01-only ladder: nothing is remux-capable -> null (a clean failure), never an
        // av01 drain into a guaranteed-INCOMPATIBLE mux.
        assertNull(
            SabrVideoResolver.pickRung(
                listOf(av01_1080), com.jtech.zemer.playback.VideoQualityLogic.AUTO, maxAutoBitrateKbps = null,
                downloadable = true, opusWebmMuxSupported = true,
            )
        )
        // AUTO on a mixed ladder picks the best downloadable rung at-or-below the AUTO height cap.
        val picked = SabrVideoResolver.pickRung(
            listOf(av01_1080, avc_720), com.jtech.zemer.playback.VideoQualityLogic.AUTO, maxAutoBitrateKbps = null,
            downloadable = true, opusWebmMuxSupported = true,
        )
        assertEquals(avc_720, picked)
    }
}
