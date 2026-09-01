package com.jtech.zemer.playback.sabr

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Pins the reassembly semantics: segments land at their ABSOLUTE byte offset so the stream is byte-exact
 * regardless of arrival order (the old sequential-append corrupted the container -> garbage duration ->
 * getBufferedPercentage overflow -> crash), reads serve any COVERED region (a seek-restarted session
 * fills a tail while the head stays a gap), errors surface at gaps only AFTER buffered bytes served,
 * and the buffer is DISK-backed (spool file) so length is bounded by disk, not heap.
 */
class SabrBufferTest {

    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    private fun buf(len: Long): SabrBuffer =
        SabrBuffer(len, java.io.File.createTempFile("sabr-test", ".spool").apply { deleteOnExit() })

    @Test
    fun `contiguous watermark advances only over gap-free bytes`() {
        val buf = buf(6)
        buf.writeAt(0, bytes(1, 2), 0, 2)      // [0,2)
        assertEquals(2L, buf.available())
        buf.writeAt(4, bytes(5, 6), 0, 2)      // [4,6) — NOT contiguous yet (gap at 2..4)
        assertEquals(2L, buf.available())
        buf.writeAt(2, bytes(3, 4), 0, 2)      // fills the gap -> now [0,6) contiguous
        assertEquals(6L, buf.available())
    }

    @Test
    fun `out-of-order segment writes reassemble byte-exact`() {
        val buf = buf(6)
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
        val buf = buf(4)
        buf.writeAt(0, bytes(1, 2), 0, 2)
        buf.markComplete() // only 2 of 4 bytes ever arrived (a capped stream)
        val out = ByteArray(4)
        assertEquals(2, buf.read(0, out, 0, 4))
        assertEquals(-1, buf.read(2, out, 0, 4))
    }

    @Test
    fun `write past declared length is ignored, never corrupts or crashes`() {
        val buf = buf(2)
        buf.writeAt(0, bytes(1, 2, 3, 4), 0, 4) // len exceeds buffer -> ignored
        buf.writeAt(0, bytes(1, 2), 0, 2)
        assertEquals(2L, buf.available())
    }

    @Test
    fun `a final segment overshooting contentLength writes its in-range prefix and completes`() {
        // Regression: the old guard dropped the WHOLE segment when its end passed the declared
        // contentLength, so a tail that overshoots (padding / muxer slack / a contentLength a few
        // bytes under the true segment sum) left the buffer permanently short and the drain never
        // completed. The clamp writes the in-range prefix so coverage reaches contentLength.
        val buf = buf(4)
        buf.writeAt(0, bytes(1, 2), 0, 2)             // [0,2)
        buf.writeAt(2, bytes(3, 4, 5, 6), 0, 4)       // final segment overshoots: [2,6) past len 4
        assertEquals(4L, buf.available())              // in-range prefix [2,4) written, not dropped
        assertTrue(buf.completeFromZero())             // coverage reached contentLength -> completes
        val out = ByteArray(4)
        assertEquals(4, buf.read(0, out, 0, 4))
        assertArrayEquals(bytes(1, 2, 3, 4), out)      // only the declared-length bytes, byte-exact
    }

    @Test
    fun `a content length above the old 64 MiB cap still buffers`() {
        // A long opus podcast passes 64 MiB after ~53 min; the old 64 MiB HEAP cap made the buffer
        // zero-length so every write was silently dropped. Disk-backed, the length is a sparse
        // preallocation — writes land and the episode reassembles.
        val len = 64L * 1024 * 1024 + 16
        val buf = buf(len)
        buf.writeAt(0, bytes(1, 2, 3, 4), 0, 4)
        assertEquals(4L, buf.available()) // under the old cap this was 0 (dropped write)
    }

    @Test
    fun `an out-of-range content length fails LOUDLY at construction, never a silent zero-buffer`() {
        for (len in listOf(0L, -1L, SabrBuffer.MAX_BUFFER_BYTES + 1)) {
            try {
                buf(len)
                fail("expected IllegalArgumentException for length $len")
            } catch (expected: IllegalArgumentException) {
            }
        }
        assertFalse(SabrBuffer.lengthValid(0))
        assertFalse(SabrBuffer.lengthValid(SabrBuffer.MAX_BUFFER_BYTES + 1))
        assertTrue(SabrBuffer.lengthValid(1))
        assertTrue(SabrBuffer.lengthValid(SabrBuffer.MAX_BUFFER_BYTES))
    }

    @Test
    fun `an errored buffer serves its reassembled bytes first and throws only at the gap`() {
        // An incomplete drain marks ERROR (never a clean EOF) — but the reader must still get every
        // byte that arrived, so playback reaches the stall point instead of erroring far behind it.
        val buf = buf(4)
        buf.writeAt(0, bytes(1, 2), 0, 2)
        buf.markError("incomplete drain")
        assertTrue(buf.failed())
        val out = ByteArray(4)
        assertEquals(2, buf.read(0, out, 0, 4)) // buffered bytes still served
        try {
            buf.read(2, out, 0, 4) // the gap -> the error surfaces
            fail("expected IOException at the gap")
        } catch (expected: java.io.IOException) {
        }
    }

    @Test
    fun `markError from another thread wakes a reader parked in read`() {
        // The destroy() path: a reader blocked waiting for bytes that will never arrive must be woken
        // with an error, never left parked forever (the infinite-buffering hang).
        val buf = buf(8)
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

    @Test
    fun `a MID-STREAM region is covered and readable while the head stays a gap (seek-restart shape)`() {
        val buf = buf(10)
        buf.writeAt(6, bytes(7, 8, 9, 10), 0, 4) // a seeked session's tail [6,10) — head [0,6) is a gap
        assertFalse(buf.coveredAt(0))
        assertTrue(buf.coveredAt(6))
        assertTrue(buf.coveredAt(9))
        assertEquals(10L, buf.coverageEndFrom(6))
        assertEquals(3L, buf.coverageEndFrom(3)) // uncovered -> identity
        assertEquals(0L, buf.available())        // contiguous-from-0 untouched by the tail
        val out = ByteArray(4)
        assertEquals(4, buf.readCovered(6, out, 0, 4))
        assertArrayEquals(bytes(7, 8, 9, 10), out)
        assertEquals(0, buf.readCovered(0, out, 0, 4)) // the head gap: not served, no error
    }

    @Test
    fun `demand pacing gates on the reader watermark and releases as the reader consumes`() {
        val buf = buf(100)
        buf.writeAt(0, ByteArray(50), 0, 50)
        // Reader has consumed nothing: 50 bytes buffered ahead > 10 -> the session should pause.
        assertEquals(50L, buf.demandGap())
        val released = java.util.concurrent.CompletableFuture<Boolean>()
        val pacer = Thread { buf.awaitDemand(10) { false }; released.complete(true) }
        pacer.start()
        Thread.sleep(100)
        assertFalse(released.isDone) // still paused
        // Reader consumes to byte 45: gap = 50-45 = 5 <= 10 -> the pacer must release.
        val out = ByteArray(45)
        var pos = 0
        while (pos < 45) { val n = buf.readCovered(pos.toLong(), out, pos, 45 - pos); if (n <= 0) break; pos += n }
        assertTrue(released.get(5, java.util.concurrent.TimeUnit.SECONDS))
        pacer.join(5000)
    }

    @Test
    fun `completeFrom serves an existing spool file read-only (the replay cache)`() {
        val f = java.io.File.createTempFile("sabr-test", ".done").apply { deleteOnExit() }
        f.writeBytes(bytes(9, 8, 7, 6))
        val buf = SabrBuffer.completeFrom(f, 4)
        assertTrue(buf.completeFromZero())
        val out = ByteArray(4)
        assertEquals(4, buf.read(0, out, 0, 4))
        assertArrayEquals(bytes(9, 8, 7, 6), out)
        assertEquals(-1, buf.read(4, out, 0, 4))
        buf.release(deleteFile = false)
        assertTrue(f.exists()) // the cache file must survive release
    }
}
