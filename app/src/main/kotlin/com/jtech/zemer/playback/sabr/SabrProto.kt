package com.jtech.zemer.playback.sabr

import java.io.ByteArrayOutputStream

/**
 * Minimal protobuf wire writer/reader for the SABR request/response messages. Deliberately tiny (no
 * codegen, no dependency): SABR needs only varint (wire 0), length-delimited (wire 2) and fixed32
 * (wire 5) fields. Field numbers live in [SabrMessages]. A faithful port of the proven Node reference
 * in `tests/sabr-stream.mjs` — the two are kept behavior-identical (byte outputs pinned by SabrProtoTest).
 */
internal object SabrProto {

    /** LEB128 unsigned varint. */
    fun varint(value: Long): ByteArray {
        val out = ByteArrayOutputStream()
        var v = value
        do {
            var b = (v and 0x7f).toInt()
            v = v ushr 7
            if (v != 0L) b = b or 0x80
            out.write(b)
        } while (v != 0L)
        return out.toByteArray()
    }

    private fun tag(field: Int, wire: Int) = varint(((field.toLong()) shl 3) or wire.toLong())

    /** wire type 0 (varint) field. */
    fun vField(field: Int, value: Long): ByteArray = tag(field, 0) + varint(value)

    /** wire type 2 (length-delimited) field. */
    fun bField(field: Int, bytes: ByteArray): ByteArray = tag(field, 2) + varint(bytes.size.toLong()) + bytes

    fun sField(field: Int, s: String): ByteArray = bField(field, s.toByteArray(Charsets.UTF_8))

    fun concat(vararg parts: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        for (p in parts) out.write(p)
        return out.toByteArray()
    }

    fun concat(parts: List<ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        for (p in parts) out.write(p)
        return out.toByteArray()
    }

    /** A decoded protobuf field value: either a varint [v] or a length-delimited byte slice [bytes]. */
    class Value(val v: Long, val bytes: ByteArray?)

    /**
     * Parse a protobuf message into field-number -> ordered values. Unknown/other wire types are skipped
     * safely (fixed64 as 8 bytes, fixed32 as 4). Never throws on truncation — returns what it parsed.
     */
    fun read(buf: ByteArray, from: Int = 0, to: Int = buf.size): Map<Int, MutableList<Value>> {
        val out = HashMap<Int, MutableList<Value>>()
        var p = from
        while (p < to) {
            val (tag, ts) = readVarint(buf, p); p += ts
            if (p > to) break
            val field = (tag ushr 3).toInt()
            val wire = (tag and 7L).toInt()
            val value: Value = when (wire) {
                0 -> { val (v, vs) = readVarint(buf, p); p += vs; Value(v, null) }
                2 -> { val (len, ls) = readVarint(buf, p); p += ls; val lenInt = len.toInt(); if (lenInt < 0) break; val end = (p + lenInt).coerceAtMost(to); val slice = buf.copyOfRange(p, end); p = end; Value(0, slice) }
                5 -> { val v = le32(buf, p); p += 4; Value(v, null) }
                1 -> { p += 8; Value(0, null) }
                else -> break
            }
            out.getOrPut(field) { ArrayList() }.add(value)
        }
        return out
    }

    /** LEB128 read -> (value, bytesConsumed). */
    fun readVarint(buf: ByteArray, pos: Int): Pair<Long, Int> {
        var shift = 0
        var result = 0L
        var p = pos
        while (p < buf.size) {
            val b = buf[p].toInt() and 0xff
            result = result or ((b.toLong() and 0x7f) shl shift)
            p++
            if (b and 0x80 == 0) break
            shift += 7
        }
        return result to (p - pos)
    }

    private fun le32(buf: ByteArray, p: Int): Long {
        if (p + 4 > buf.size) return 0
        return ((buf[p].toLong() and 0xff)) or
            ((buf[p + 1].toLong() and 0xff) shl 8) or
            ((buf[p + 2].toLong() and 0xff) shl 16) or
            ((buf[p + 3].toLong() and 0xff) shl 24)
    }
}

/** Convenience accessors over a parsed message. */
internal fun Map<Int, MutableList<SabrProto.Value>>.longAt(field: Int): Long = this[field]?.firstOrNull()?.v ?: 0L
internal fun Map<Int, MutableList<SabrProto.Value>>.bytesAt(field: Int): ByteArray? = this[field]?.firstOrNull()?.bytes
