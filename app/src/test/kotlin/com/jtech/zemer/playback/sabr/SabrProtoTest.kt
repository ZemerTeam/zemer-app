package com.jtech.zemer.playback.sabr

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/** Pins the protobuf wire primitives (LEB128 varints, field tags, round-trip read) SABR requests build on. */
class SabrProtoTest {

    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    @Test
    fun `varint encodes LEB128`() {
        assertArrayEquals(bytes(0), SabrProto.varint(0))
        assertArrayEquals(bytes(1), SabrProto.varint(1))
        assertArrayEquals(bytes(127), SabrProto.varint(127))
        assertArrayEquals(bytes(0x80, 0x01), SabrProto.varint(128))
        assertArrayEquals(bytes(0xAC, 0x02), SabrProto.varint(300))
    }

    @Test
    fun `vField writes tag then varint value`() {
        // field 28, wire 0 -> tag 224 -> varint [0xE0,0x01]; value 0 -> [0x00]
        assertArrayEquals(bytes(0xE0, 0x01, 0x00), SabrProto.vField(28, 0))
        // field 40, wire 0 -> tag 320 -> varint [0xC0,0x02]; value 1
        assertArrayEquals(bytes(0xC0, 0x02, 0x01), SabrProto.vField(40, 1))
    }

    @Test
    fun `bField writes tag length payload`() {
        assertArrayEquals(bytes(0x0A, 0x01, 0xAA), SabrProto.bField(1, bytes(0xAA)))
        assertArrayEquals(bytes(0x2A, 0x02, 0x01, 0x02), SabrProto.bField(5, bytes(1, 2)))
    }

    @Test
    fun `read round-trips varint and length-delimited fields`() {
        val msg = SabrProto.concat(
            SabrProto.vField(9, 12345),
            SabrProto.bField(2, bytes(0xDE, 0xAD)),
            SabrProto.vField(9, 6),
        )
        val m = SabrProto.read(msg)
        assertEquals(12345L, m[9]!![0].v)
        assertEquals(6L, m[9]!![1].v) // repeated field keeps order
        assertArrayEquals(bytes(0xDE, 0xAD), m.bytesAt(2))
        assertEquals(12345L, m.longAt(9)) // longAt returns the first
    }

    @Test
    fun `readVarint reports bytes consumed`() {
        val (v, n) = SabrProto.readVarint(bytes(0xAC, 0x02, 0xFF), 0)
        assertEquals(300L, v)
        assertEquals(2, n)
    }

    @Test
    fun `read never throws on a length-delimited field whose length overflows Int`() {
        // Regression: a wire-2 field whose length varint decodes to a value whose .toInt() is negative
        // made end < p and copyOfRange(p, end) throw IllegalArgumentException, violating the
        // "never throws on truncation" contract (and aborting a whole SABR response parse via a caught
        // exception that then marked the stream errored instead of skipping the bad frame).
        // field 2, wire 2 -> tag 0x12; length = a 5-byte varint for 0xFFFFFFFF (overflows Int to negative).
        val msg = bytes(0x12, 0xFF, 0xFF, 0xFF, 0xFF, 0x0F, 0x01, 0x02)
        val m = SabrProto.read(msg) // must return gracefully, not throw
        assertEquals(0, m.size)      // the malformed frame is skipped, no field decoded
    }
}
