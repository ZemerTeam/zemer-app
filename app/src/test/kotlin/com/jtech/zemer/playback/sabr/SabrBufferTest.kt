package com.jtech.zemer.playback.sabr

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the reassembly bug that crashed playback: segments must land at their ABSOLUTE byte offset so the
 * stream is byte-exact regardless of arrival order (the old sequential-append corrupted the container,
 * which made the extractor report a garbage duration -> getBufferedPercentage overflow -> crash).
 */
class SabrBufferTest {

    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    @Test
    fun `contiguous watermark advances only over gap-free bytes`() {
        val buf = SabrBuffer(expectedLength = 6)
        buf.writeAt(0, bytes(1, 2), 0, 2)      // [0,2)
        assertEquals(2L, buf.available())
        buf.writeAt(4, bytes(5, 6), 0, 2)      // [4,6) — NOT contiguous yet (gap at 2..4)
        assertEquals(2L, buf.available())
        buf.writeAt(2, bytes(3, 4), 0, 2)      // fills the gap -> now [0,6) contiguous
        assertEquals(6L, buf.available())
    }

    @Test
    fun `out-of-order segment writes reassemble byte-exact`() {
        val buf = SabrBuffer(expectedLength = 6)
        // Deliberately write segment 3 before segment 2 before segment 1 (init).
        buf.writeAt(4, bytes(50, 60), 0, 2)
        buf.writeAt(2, bytes(30, 40), 0, 2)
        buf.writeAt(0, bytes(10, 20), 0, 2)
        buf.markComplete()
        val out = ByteArray(6)
        var pos = 0
        while (pos < 6) { val n = buf.read(pos.toLong(), out, pos, 6 - pos); if (n <= 0) break; pos += n }
        assertArrayEquals(bytes(10, 20, 30, 40, 50, 60), out)
    }

    @Test
    fun `read past the contiguous end returns end-of-stream after completion`() {
        val buf = SabrBuffer(expectedLength = 4)
        buf.writeAt(0, bytes(1, 2), 0, 2)
        buf.markComplete() // only 2 of 4 bytes ever arrived (a capped stream)
        val out = ByteArray(4)
        assertEquals(2, buf.read(0, out, 0, 4))
        assertEquals(-1, buf.read(2, out, 0, 4))
    }

    @Test
    fun `write past declared length is ignored, never corrupts or crashes`() {
        val buf = SabrBuffer(expectedLength = 2)
        buf.writeAt(0, bytes(1, 2, 3, 4), 0, 4) // len exceeds buffer -> ignored
        buf.writeAt(0, bytes(1, 2), 0, 2)
        assertEquals(2L, buf.available())
    }

    @Test
    fun `a content length above the old 64 MiB cap still buffers`() {
        // A long opus podcast passes 64 MiB after ~53 min; the old 64 MiB cap made the buffer zero-length
        // so EVERY write was silently dropped (available stayed 0) and the episode reassembled to nothing.
        // A length just over the old cap must now allocate a real buffer and accept writes.
        val len = 64L * 1024 * 1024 + 16
        val buf = SabrBuffer(expectedLength = len)
        buf.writeAt(0, bytes(1, 2, 3, 4), 0, 4)
        assertEquals(4L, buf.available()) // under the old cap this was 0 (dropped write)
    }

    @Test
    fun `an out-of-range content length fails LOUDLY at construction, never a silent zero-buffer`() {
        // The old degenerate ByteArray(0) made writeAt a silent no-op while the session still drained
        // the whole stream over the network, then EOF'd at byte 0 with no error.
        for (len in listOf(0L, -1L, SabrBuffer.MAX_BUFFER_BYTES + 1)) {
            try {
                SabrBuffer(expectedLength = len)
                org.junit.Assert.fail("expected IllegalArgumentException for length $len")
            } catch (expected: IllegalArgumentException) {
            }
        }
        assertEquals(false, SabrBuffer.lengthValid(0))
        assertEquals(false, SabrBuffer.lengthValid(SabrBuffer.MAX_BUFFER_BYTES + 1))
        assertEquals(true, SabrBuffer.lengthValid(1))
        assertEquals(true, SabrBuffer.lengthValid(SabrBuffer.MAX_BUFFER_BYTES))
    }

    @Test
    fun `an errored buffer serves its reassembled bytes first and throws only at the gap`() {
        // An incomplete drain marks ERROR (never a clean EOF) — but the reader must still get every
        // byte that arrived, so playback reaches the stall point instead of erroring far behind it.
        val buf = SabrBuffer(expectedLength = 4)
        buf.writeAt(0, bytes(1, 2), 0, 2)
        buf.markError("incomplete drain")
        assertEquals(true, buf.failed())
        val out = ByteArray(4)
        assertEquals(2, buf.read(0, out, 0, 4)) // buffered bytes still served
        try {
            buf.read(2, out, 0, 4) // the gap -> the error surfaces
            org.junit.Assert.fail("expected IOException at the gap")
        } catch (expected: java.io.IOException) {
        }
    }

    @Test
    fun `markError from another thread wakes a reader parked in read`() {
        // The destroy() path: a reader blocked waiting for bytes that will never arrive must be woken
        // with an error, never left parked forever (the infinite-buffering hang).
        val buf = SabrBuffer(expectedLength = 8)
        val outcome = java.util.concurrent.CompletableFuture<String>()
        val reader = Thread {
            try {
                buf.read(0, ByteArray(4), 0, 4)
                outcome.complete("returned")
            } catch (e: java.io.IOException) {
                outcome.complete("threw")
            }
        }
        reader.start()
        Thread.sleep(50) // let the reader park in wait()
        buf.markError("SABR stream destroyed")
        assertEquals("threw", outcome.get(5, java.util.concurrent.TimeUnit.SECONDS))
        reader.join(5000)
    }
}
