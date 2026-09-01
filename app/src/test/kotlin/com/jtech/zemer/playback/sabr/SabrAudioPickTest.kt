package com.jtech.zemer.playback.sabr

import com.jtech.zemer.constants.AudioQuality
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins SABR's audio-format pick to the DIRECT resolver's rule (YTPlayerUtils): bitrate weighted by the
 * quality preference (AUTO follows the metered state), with the opus/webm streaming bonus. Full DIRECT
 * parity - a SABR listen picks the same audio format a DIRECT listen would.
 */
class SabrAudioPickTest {

    private class Fmt(val itag: Int, val bitrate: Int, val mime: String)

    private val formats = listOf(
        Fmt(140, 128_000, "audio/mp4; codecs=\"mp4a.40.2\""), // aac 128k
        Fmt(251, 160_000, "audio/webm; codecs=\"opus\""),      // opus 160k
        Fmt(249, 50_000, "audio/webm; codecs=\"opus\""),       // opus 50k
    )

    private fun pick(q: AudioQuality, metered: Boolean, opusAllowed: Boolean = true) =
        SabrPlayerResolver.pickAudio(formats, { it.bitrate }, { it.mime }, q, metered, opusAllowed)?.itag

    @Test
    fun `HIGH picks the highest-bitrate original, opus bonus favours webm`() {
        assertEquals(251, pick(AudioQuality.HIGH, metered = false))
        assertEquals(251, pick(AudioQuality.HIGH, metered = true)) // HIGH ignores metered
    }

    @Test
    fun `AUTO on unmetered behaves like HIGH`() {
        assertEquals(251, pick(AudioQuality.AUTO, metered = false))
    }

    @Test
    fun `AUTO on metered prefers the lowest bitrate`() {
        // metered flips the bitrate weight to negative, so the smallest opus wins (opus bonus is uniform).
        assertEquals(249, pick(AudioQuality.AUTO, metered = true))
    }

    @Test
    fun `LOW picks the lowest bitrate`() {
        assertEquals(249, pick(AudioQuality.LOW, metered = false))
    }

    @Test
    fun `a COMPATIBLE download (opus not allowed) picks AAC, never Opus`() {
        // downloadOpusOk=false (COMPATIBLE / pre-API-29): restrict to audio/mp4 and drop the opus bonus,
        // so the AAC track wins even at HIGH — DIRECT's download-format parity.
        assertEquals(140, pick(AudioQuality.HIGH, metered = false, opusAllowed = false))
        assertEquals(140, pick(AudioQuality.AUTO, metered = false, opusAllowed = false))
    }

    @Test
    fun `opus-not-allowed with no AAC in the pool falls back to whatever exists`() {
        val webmOnly = listOf(Fmt(251, 160_000, "audio/webm; codecs=\"opus\""), Fmt(249, 50_000, "audio/webm; codecs=\"opus\""))
        val itag = SabrPlayerResolver.pickAudio(webmOnly, { it.bitrate }, { it.mime }, AudioQuality.HIGH, false, opusAllowed = false)?.itag
        assertEquals(251, itag) // no mp4 -> the filter would empty the pool, so it falls back to all
    }
}
