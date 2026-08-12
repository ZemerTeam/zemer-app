package com.jtech.zemer.playback

import com.metrolist.innertube.models.response.PlayerResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The quality-ladder rules behind the beyond-720p switcher. The fixture mirrors the LIVE WEB_REMIX
 * shape measured by tests/video-qualities.mjs (2026-08-11): adaptive avc1 video-only 144p…1080p,
 * vp9-only 1440p/2160p, progressive muxed 360p (itag 18), opus/aac audio.
 */
class VideoQualityLogicTest {

    private fun format(
        itag: Int,
        mimeType: String,
        bitrate: Int,
        width: Int? = null,
        height: Int? = null,
        qualityLabel: String? = null,
        audioQuality: String? = null,
        audioChannels: Int? = null,
    ) = PlayerResponse.StreamingData.Format(
        itag = itag, url = "https://example/$itag", mimeType = mimeType, bitrate = bitrate,
        width = width, height = height, contentLength = 1000L, quality = "q", fps = 24,
        qualityLabel = qualityLabel, averageBitrate = bitrate, audioQuality = audioQuality,
        approxDurationMs = "1000", audioSampleRate = null, audioChannels = audioChannels,
        loudnessDb = null, lastModified = null, signatureCipher = null, audioTrack = null,
    )

    private fun videoOnly(itag: Int, label: String, height: Int, mime: String, bitrate: Int = height * 1000) =
        format(itag, mime, bitrate, width = height * 16 / 9, height = height, qualityLabel = label)

    private fun progressive(itag: Int, label: String, height: Int, bitrate: Int = height * 900) =
        format(
            itag, "video/mp4; codecs=\"avc1.42001E, mp4a.40.2\"", bitrate,
            width = height * 16 / 9, height = height, qualityLabel = label,
            audioQuality = "AUDIO_QUALITY_LOW", audioChannels = 2,
        )

    private fun audio(itag: Int = 251) =
        format(itag, "audio/webm; codecs=\"opus\"", 140_000)

    private val avc = "video/mp4; codecs=\"avc1.640028\""
    private val vp9 = "video/webm; codecs=\"vp9\""
    private val av1 = "video/mp4; codecs=\"av01.0.08M.08\""

    // The measured live shape: progressive 360p; adaptive avc1 ≤1080p; vp9-only 1440p/2160p.
    private fun liveStreamingData() = PlayerResponse.StreamingData(
        formats = listOf(progressive(18, "360p", 360)),
        adaptiveFormats = listOf(
            audio(),
            videoOnly(313, "2160p", 2160, vp9),
            videoOnly(271, "1440p", 1440, vp9),
            videoOnly(137, "1080p", 1080, avc),
            videoOnly(136, "720p", 720, avc),
            videoOnly(135, "480p", 480, avc),
            videoOnly(134, "360p", 360, avc),
            videoOnly(133, "240p", 240, avc),
            videoOnly(160, "144p", 144, avc),
        ),
        expiresInSeconds = 21540,
    )

    @Test
    fun `ladder is one rung per label sorted high to low`() {
        val rungs = VideoQualityLogic.rungs(liveStreamingData())
        assertEquals(
            listOf("2160p", "1440p", "1080p", "720p", "480p", "360p", "240p", "144p"),
            rungs.map { it.label },
        )
    }

    @Test
    fun `progressive wins its label over adaptive`() {
        // 360p exists both progressive (itag 18) and adaptive (itag 134): the single-stream one wins.
        val rung = VideoQualityLogic.rungs(liveStreamingData()).first { it.label == "360p" }
        assertEquals(18, rung.itag)
        assertTrue(rung.progressive)
    }

    @Test
    fun `within a label the better codec wins then higher bitrate`() {
        val data = PlayerResponse.StreamingData(
            formats = null,
            adaptiveFormats = listOf(
                videoOnly(399, "1080p", 1080, av1, bitrate = 9_000_000),
                videoOnly(248, "1080p", 1080, vp9, bitrate = 5_000_000),
                videoOnly(137, "1080p", 1080, avc, bitrate = 4_000_000),
                videoOnly(1370, "720p", 720, avc, bitrate = 2_000_000),
                videoOnly(1371, "720p", 720, avc, bitrate = 3_000_000),
            ),
            expiresInSeconds = 1,
        )
        val rungs = VideoQualityLogic.rungs(data)
        assertEquals(137, rungs.first { it.label == "1080p" }.itag) // avc1 beats vp9/av01
        assertEquals(1371, rungs.first { it.label == "720p" }.itag) // same codec: higher bitrate
    }

    @Test
    fun `vp9 rungs are not mp4Avc so downloads restrict correctly`() {
        val rungs = VideoQualityLogic.rungs(liveStreamingData())
        assertTrue(rungs.first { it.label == "1080p" }.mp4Avc)
        assertEquals(false, rungs.first { it.label == "2160p" }.mp4Avc)
    }

    @Test
    fun `selectRung exact label`() {
        val rungs = VideoQualityLogic.rungs(liveStreamingData())
        assertEquals(137, VideoQualityLogic.selectRung(rungs, "1080p")?.itag)
        assertEquals(313, VideoQualityLogic.selectRung(rungs, "2160p")?.itag)
    }

    @Test
    fun `selectRung treats the label as a target height`() {
        val rungs = VideoQualityLogic.rungs(liveStreamingData()).filter { it.height != 1080 }
        // 1080p absent -> the best rung at or below the target.
        assertEquals("720p", VideoQualityLogic.selectRung(rungs, "1080p")?.label)
        // Target below the whole ladder -> the lowest rung, never null (an explicit pick fail-softs).
        assertEquals("240p", VideoQualityLogic.selectRung(rungs.filter { it.height >= 240 }, "100p")?.label)
    }

    @Test
    fun `selectRung auto and unparsable are null`() {
        val rungs = VideoQualityLogic.rungs(liveStreamingData())
        assertNull(VideoQualityLogic.selectRung(rungs, VideoQualityLogic.AUTO))
        assertNull(VideoQualityLogic.selectRung(rungs, null))
        assertNull(VideoQualityLogic.selectRung(rungs, "best"))
        assertNull(VideoQualityLogic.selectRung(emptyList(), "1080p"))
    }

    @Test
    fun `downloadable selection keeps vp9 (WebM remux) but drops av01`() {
        val all = VideoQualityLogic.rungs(liveStreamingData())
        // vp9 2160p remuxes to WebM — downloadable.
        assertEquals("2160p", VideoQualityLogic.selectRung(all, "2160p", downloadable = true)?.label)
        // An av01-only rung is stream-only: with 1080p filtered out, an av01 1080p is skipped and the
        // download falls to the next remuxable rung below the target.
        val withAv1Top = VideoQualityLogic.rungs(
            PlayerResponse.StreamingData(
                formats = null,
                adaptiveFormats = listOf(
                    videoOnly(399, "1080p", 1080, av1),
                    videoOnly(136, "720p", 720, avc),
                ),
                expiresInSeconds = 1,
            ),
        )
        assertEquals("1080p", VideoQualityLogic.selectRung(withAv1Top, "1080p")?.label)
        assertEquals("720p", VideoQualityLogic.selectRung(withAv1Top, "1080p", downloadable = true)?.label)
    }

    @Test
    fun `vp9 rungs are not downloadable below the Opus-in-WebM muxer API`() {
        val all = VideoQualityLogic.rungs(liveStreamingData())
        // API >= 29: vp9 2160p downloads (WebM mux). Below: the download falls to the best avc1 rung.
        assertEquals(
            "2160p",
            VideoQualityLogic.selectRung(all, "2160p", downloadable = true, opusWebmMuxSupported = true)?.label,
        )
        assertEquals(
            "1080p",
            VideoQualityLogic.selectRung(all, "2160p", downloadable = true, opusWebmMuxSupported = false)?.label,
        )
    }

    @Test
    fun `formatForItag finds across both lists`() {
        val data = liveStreamingData()
        assertEquals(18, VideoQualityLogic.formatForItag(data, 18)?.itag)
        assertEquals(313, VideoQualityLogic.formatForItag(data, 313)?.itag)
        assertNull(VideoQualityLogic.formatForItag(data, 999))
        // The audio format is never a video rung.
        assertNull(VideoQualityLogic.formatForItag(data, 251))
    }

    @Test
    fun `heightOfLabel parses leading digits`() {
        assertEquals(1080, VideoQualityLogic.heightOfLabel("1080p"))
        assertEquals(720, VideoQualityLogic.heightOfLabel("720p60"))
        assertNull(VideoQualityLogic.heightOfLabel("auto"))
    }

    @Test
    fun `rebuffer guard fires only on repeated stalls within the window`() {
        val w = VideoQualityLogic.REBUFFER_WINDOW_MS
        // One stall: never.
        assertEquals(false, VideoQualityLogic.shouldDowngradeForRebuffer(listOf(1_000L), 1_000L))
        // Two stalls inside the window: downgrade.
        assertTrue(VideoQualityLogic.shouldDowngradeForRebuffer(listOf(1_000L, 20_000L), 20_000L))
        // Two stalls but the first aged out of the window: no downgrade.
        assertEquals(
            false,
            VideoQualityLogic.shouldDowngradeForRebuffer(listOf(1_000L, w + 10_000L), w + 10_000L),
        )
    }

    @Test
    fun `an early stall right after READY downgrades immediately`() {
        // One stall 5s after first READY: immediate downgrade (the rung is clearly unsustainable).
        assertTrue(
            VideoQualityLogic.shouldDowngradeForRebuffer(listOf(6_000L), nowMs = 6_000L, readyAtMs = 1_000L),
        )
        // The same single stall long after READY: not yet (the 2-in-window rule governs).
        assertEquals(
            false,
            VideoQualityLogic.shouldDowngradeForRebuffer(listOf(100_000L), nowMs = 100_000L, readyAtMs = 1_000L),
        )
    }

    @Test
    fun `stall downgrade steps down exactly one rung and stops at the bottom`() {
        // The rebuffer guard drops ONE rung per stall (2160p -> 1440p -> 1080p -> 720p ...), so
        // playback settles on the highest rung that actually plays instead of over-dropping.
        val rungs = VideoQualityLogic.rungs(liveStreamingData())
        assertEquals("1440p", VideoQualityLogic.rungBelow(rungs, "2160p")?.label)
        assertEquals("1080p", VideoQualityLogic.rungBelow(rungs, "1440p")?.label)
        assertEquals("720p", VideoQualityLogic.rungBelow(rungs, "1080p")?.label)
        // Bottom of the ladder / unknown label: nowhere to go.
        assertNull(VideoQualityLogic.rungBelow(rungs, "144p"))
        assertNull(VideoQualityLogic.rungBelow(rungs, "999p"))
    }

    @Test
    fun `decoderMimeType maps the three codec families`() {
        assertEquals("video/avc", VideoQualityLogic.decoderMimeType(avc))
        assertEquals("video/x-vnd.on2.vp9", VideoQualityLogic.decoderMimeType(vp9))
        assertEquals("video/av01", VideoQualityLogic.decoderMimeType(av1))
        assertNull(VideoQualityLogic.decoderMimeType("video/mp4; codecs=\"hvc1\""))
    }
}
