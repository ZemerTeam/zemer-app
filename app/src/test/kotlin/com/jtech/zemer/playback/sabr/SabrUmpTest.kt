package com.jtech.zemer.playback.sabr

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the UMP custom variable-length integer (leading-byte width bits) and frame splitting — the tricky
 * decode that is NOT the protobuf varint. Cases match the proven Node reference `umpVar`.
 */
class SabrUmpTest {

    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    @Test
    fun `single byte values under 128`() {
        assertEquals(5L to 1, SabrUmp.readVarint(bytes(5), 0))
        assertEquals(127L to 1, SabrUmp.readVarint(bytes(127), 0))
    }

    @Test
    fun `two byte width 128 to 191`() {
        // b0=128 -> width 2, value = (128 & 0x3f) + b1*64 = 0 + 1*64 = 64
        assertEquals(64L to 2, SabrUmp.readVarint(bytes(0x80, 0x01), 0))
        // b0=191, b1=2 -> (191 & 0x3f) + 2*64 = 63 + 128 = 191
        assertEquals(191L to 2, SabrUmp.readVarint(bytes(191, 2), 0))
    }

    @Test
    fun `three byte width 192 to 223`() {
        // b0=192 -> width 3, value = (192 & 0x1f) + b1*32 + b2*8192 = 0 + 1*32 + 0 = 32
        assertEquals(32L to 3, SabrUmp.readVarint(bytes(192, 1, 0), 0))
    }

    @Test
    fun `parse splits parts by type and size`() {
        // part type 20 (size1 varint), size 3, payload [1,2,3]; then type 21, size 2, payload [9,9]
        val body = bytes(20, 3, 1, 2, 3, 21, 2, 9, 9)
        val parts = SabrUmp.parse(body)
        assertEquals(2, parts.size)
        assertEquals(SabrUmp.MEDIA_HEADER, parts[0].type)
        assertArrayEquals(bytes(1, 2, 3), parts[0].payload)
        assertEquals(SabrUmp.MEDIA, parts[1].type)
        assertArrayEquals(bytes(9, 9), parts[1].payload)
    }

    @Test
    fun `parse stops cleanly on truncation`() {
        // type present but size varint says 5 while only 2 bytes remain -> clamp, no crash
        val parts = SabrUmp.parse(bytes(20, 5, 1, 2))
        assertEquals(1, parts.size)
        assertArrayEquals(bytes(1, 2), parts[0].payload)
    }

    @Test
    fun `parse does not crash on a body truncated mid-varint`() {
        // A trailing byte announcing a width-3 varint (0xC0) with no continuation bytes must NOT throw
        // IndexOutOfBounds — the "stops cleanly on truncation" contract. One clean part, then bail.
        val parts = SabrUmp.parse(bytes(20, 1, 7, 0xC0))
        assertEquals(1, parts.size)
        assertArrayEquals(bytes(7), parts[0].payload)
    }

    @Test
    fun `parse does not crash when the size varint is truncated`() {
        // type=20 read, then a width-4 size varint (0xE0) with only 1 of 4 bytes present -> bail, no crash.
        val parts = SabrUmp.parse(bytes(20, 0xE0))
        assertEquals(0, parts.size)
    }
}
