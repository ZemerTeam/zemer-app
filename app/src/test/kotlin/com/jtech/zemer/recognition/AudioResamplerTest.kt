package com.jtech.zemer.recognition

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Pins the resampler's contract: correct output length for the 44.1 kHz → 16 kHz conversion the
 * fingerprinter requires, a no-op when the rate already matches, and little-endian 16-bit round-trip.
 */
class AudioResamplerTest {

    // AudioFormat.ENCODING_PCM_16BIT — inlined as a literal so this stays a pure-JVM test.
    private val pcm16 = 2

    private fun pcm(shorts: ShortArray): ByteArray {
        val bytes = ByteArray(shorts.size * 2)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(shorts)
        return bytes
    }

    @Test
    fun `downsamples 44100 to 16000 with the expected length`() = runBlocking {
        val inputShorts = 44100
        val input = pcm(ShortArray(inputShorts) { (it % 100).toShort() })
        val decoded = DecodedAudio(input, channelCount = 1, sampleRate = 44100, pcmEncoding = pcm16)

        val result = AudioResampler.resample(decoded, 16000).getOrThrow()

        val expectedShorts = (inputShorts * (16000.0 / 44100)).toInt()
        assertEquals(16000, result.sampleRate)
        assertEquals(expectedShorts * 2, result.data.size)
    }

    @Test
    fun `is a no-op when the sample rate already matches`() = runBlocking {
        val input = pcm(shortArrayOf(1, 2, 3, 4, 5, 6))
        val decoded = DecodedAudio(input, channelCount = 1, sampleRate = 16000, pcmEncoding = pcm16)

        val result = AudioResampler.resample(decoded, 16000).getOrThrow()

        assertEquals(16000, result.sampleRate)
        assertArrayEquals(input, result.data)
    }

    @Test
    fun `preserves little-endian byte order on a no-op`() = runBlocking {
        // 0x0102 little-endian => bytes [0x02, 0x01]
        val input = byteArrayOf(0x02, 0x01, 0x04, 0x03)
        val decoded = DecodedAudio(input, channelCount = 1, sampleRate = 16000, pcmEncoding = pcm16)

        val result = AudioResampler.resample(decoded, 16000).getOrThrow()

        assertArrayEquals(input, result.data)
    }
}
