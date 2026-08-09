package com.jtech.zemer.playback.relay

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The relay URL contract (handoff-docs/zemer-app-filtered-playback-relay-request.md): a plain videoId maps
 * to `{BASE}/stream?v=<id>`, and a `video:` rendition key degrades to its base id as audio (the relay
 * serves audio only), so an accidental video-mode open in relay mode never produces a malformed URL.
 */
class RelayStreamTest {

    @Test
    fun `stream url is base plus videoId`() {
        assertEquals("https://stream.zemer.io/stream?v=sgvKThxpuSQ", RelayStream.streamUrl("sgvKThxpuSQ"))
    }

    @Test
    fun `a video-mode rendition key degrades to its base id as audio`() {
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
