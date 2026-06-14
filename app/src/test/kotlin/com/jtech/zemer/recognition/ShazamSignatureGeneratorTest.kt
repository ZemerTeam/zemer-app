package com.jtech.zemer.recognition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64
import java.util.zip.CRC32
import kotlin.math.PI
import kotlin.math.sin

/**
 * Pins the ported Shazam fingerprint generator. We don't hardcode a brittle golden base64 string;
 * instead we verify the structural invariants that the C++/vibra port must preserve — the data-URI
 * shape, the fixed header magics, a self-consistent CRC32, and that the generator is deterministic
 * and sensitive to the input — which together lock the math without depending on the host platform.
 */
class ShazamSignatureGeneratorTest {

    private fun sinePcm(freqHz: Double, sampleCount: Int, amplitude: Int = 8000): ByteArray {
        val sampleRate = 16_000.0
        val bytes = ByteArray(sampleCount * 2)
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until sampleCount) {
            val v = (amplitude * sin(2.0 * PI * freqHz * i / sampleRate)).toInt().toShort()
            buf.putShort(v)
        }
        return bytes
    }

    private fun decodeSignature(uri: String): ByteArray {
        val prefix = "data:audio/vnd.shazam.sig;base64,"
        assertTrue("unexpected prefix: $uri", uri.startsWith(prefix))
        return Base64.getDecoder().decode(uri.removePrefix(prefix))
    }

    @Test
    fun `produces a valid data uri with the expected header and crc`() {
        val signature = VibraSignature.fromI16(sinePcm(1000.0, 24_000))
        val bytes = decodeSignature(signature)
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        // Fixed header magics from the vibra signature format.
        assertEquals(0xcafe2580.toInt(), bb.getInt(0))
        assertEquals(0x94119c00.toInt(), bb.getInt(12))

        // CRC32 stored at offset 4 must equal CRC32 over everything from offset 8 onwards.
        val storedCrc = bb.getInt(4)
        val crc = CRC32().apply { update(bytes, 8, bytes.size - 8) }
        assertEquals(crc.value.toInt(), storedCrc)
    }

    @Test
    fun `is deterministic for identical input`() {
        val pcm = sinePcm(1000.0, 24_000)
        assertEquals(VibraSignature.fromI16(pcm), VibraSignature.fromI16(pcm.copyOf()))
    }

    @Test
    fun `different audio yields a different signature`() {
        val a = VibraSignature.fromI16(sinePcm(440.0, 24_000))
        val b = VibraSignature.fromI16(sinePcm(2000.0, 24_000))
        assertNotEquals(a, b)
    }

    @Test
    fun `rejects odd-length pcm`() {
        assertThrows(IllegalArgumentException::class.java) {
            VibraSignature.fromI16(ByteArray(3))
        }
    }
}
