package com.jtech.zemer.playback.sabr

/**
 * UMP (googlevideo "Ultra Media Protocol") frame parser. A SABR response body is a sequence of parts,
 * each = umpVarint(partType) + umpVarint(partSize) + partSize bytes. The UMP varint is NOT the protobuf
 * varint: the leading byte's high bits encode the total width (like UTF-8). Ported byte-for-byte from
 * the proven Node reference (`tests/sabr-stream.mjs` umpVar) and pinned by SabrUmpTest.
 */
internal object SabrUmp {

    /** Part-type ids we act on (the rest are informational and ignored). */
    const val MEDIA_HEADER = 20
    const val MEDIA = 21
    const val MEDIA_END = 22
    const val NEXT_REQUEST_POLICY = 35
    const val FORMAT_INITIALIZATION_METADATA = 42
    const val SABR_REDIRECT = 43
    const val SABR_ERROR = 44
    const val SABR_CONTEXT_UPDATE = 57
    const val STREAM_PROTECTION_STATUS = 58

    class Part(val type: Int, val payload: ByteArray)

    /** Read a UMP variable-length integer at [pos] -> (value, bytesConsumed). */
    fun readVarint(buf: ByteArray, pos: Int): Pair<Long, Int> {
        val b0 = buf[pos].toInt() and 0xff
        val size = when {
            b0 < 128 -> 1
            b0 < 192 -> 2
            b0 < 224 -> 3
            b0 < 240 -> 4
            else -> 5
        }
        fun u(i: Int) = (buf[pos + i].toInt() and 0xff).toLong()
        val value = when (size) {
            1 -> b0.toLong()
            2 -> (b0.toLong() and 0x3f) + u(1) * 64
            3 -> (b0.toLong() and 0x1f) + u(1) * 32 + u(2) * 8192
            4 -> (b0.toLong() and 0x0f) + u(1) * 16 + u(2) * 4096 + u(3) * 1048576
            else -> u(1) + u(2) * 256 + u(3) * 65536 + u(4) * 16777216
        }
        return value to size
    }

    /** Split a response body into its UMP parts. Stops cleanly on truncation. */
    fun parse(buf: ByteArray): List<Part> {
        val parts = ArrayList<Part>()
        var p = 0
        while (p < buf.size) {
            val (type, ts) = readVarint(buf, p); p += ts
            if (p >= buf.size) break
            val (size, ss) = readVarint(buf, p); p += ss
            val end = (p + size.toInt()).coerceAtMost(buf.size)
            parts.add(Part(type.toInt(), buf.copyOfRange(p, end)))
            p = end
        }
        return parts
    }
}
