package com.jtech.zemer.playback.relay

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The relay URL contract (handoff-docs/zemer-app-filtered-playback-relay-request.md):
 *  - [RelayStream.streamUrl] / [RelayStream.playbackUrl] for a plain id -> the audio stream,
 *  - [RelayStream.playbackUrl] for a `video:` rendition key -> the 360p muxed video stream (`&kind=video`),
 *  - [RelayStream.downloadUrl] -> the dedicated full-file `/download` endpoint,
 *  - a `video:` key reduces to its base id for the audio-only [streamUrl].
 */
class RelayStreamTest {

    @Test
    fun `stream url is base plus videoId (audio)`() {
        assertEquals("https://stream.zemer.io/stream?v=sgvKThxpuSQ", RelayStream.streamUrl("sgvKThxpuSQ"))
    }

    @Test
    fun `playback url for a plain id is the audio stream`() {
        assertEquals("https://stream.zemer.io/stream?v=sgvKThxpuSQ", RelayStream.playbackUrl("sgvKThxpuSQ"))
    }

    @Test
    fun `playback url for a video rendition key is the 360p video stream`() {
        assertEquals(
            "https://stream.zemer.io/stream?v=sgvKThxpuSQ&kind=video",
            RelayStream.playbackUrl("video:sgvKThxpuSQ"),
        )
    }

    @Test
    fun `download url hits the dedicated download endpoint`() {
        assertEquals("https://stream.zemer.io/download?v=sgvKThxpuSQ", RelayStream.downloadUrl("sgvKThxpuSQ"))
        // A video: key reduces to its base id for a download too.
        assertEquals("https://stream.zemer.io/download?v=sgvKThxpuSQ", RelayStream.downloadUrl("video:sgvKThxpuSQ"))
    }

    @Test
    fun `streamUrl degrades a video-mode rendition key to its base id as audio`() {
        assertEquals("sgvKThxpuSQ", RelayStream.videoId("video:sgvKThxpuSQ"))
        assertEquals("https://stream.zemer.io/stream?v=sgvKThxpuSQ", RelayStream.streamUrl("video:sgvKThxpuSQ"))
    }

    @Test
    fun `a plain id passes through unchanged`() {
        assertEquals("sgvKThxpuSQ", RelayStream.videoId("sgvKThxpuSQ"))
    }

    @Test
    fun `health url`() {
        assertEquals("https://stream.zemer.io/health", RelayStream.healthUrl())
    }
}
