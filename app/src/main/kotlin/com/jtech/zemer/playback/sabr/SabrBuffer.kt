package com.jtech.zemer.playback.sabr

import java.util.TreeMap

/**
 * A thread-safe reassembly buffer for one SABR session. The session thread writes each media segment at
 * its ABSOLUTE byte offset ([writeAt], the segment's startRange) — exactly like the proven Node harness,
 * so the reassembled stream is byte-exact regardless of the order segments arrive in. The ExoPlayer read
 * thread [read]s sequentially, blocking until the CONTIGUOUS filled region from 0 reaches its position
 * (or the session completes / errors). The whole stream (a few MB) is held in memory so backward seeks
 * are free. [expectedLength] is the format's contentLength (known up front from /player).
 */
internal class SabrBuffer(val expectedLength: Long) {
    private val lock = Object()
    private val data = ByteArray(if (expectedLength in 1..(64L * 1024 * 1024)) expectedLength.toInt() else 0)
    // Filled byte intervals [start, endExclusive), merged; used to compute the contiguous watermark from 0.
    private val intervals = TreeMap<Long, Long>()
    private var contiguous = 0L
    private var complete = false
    private var error: String? = null

    /** Write [len] bytes from [src] at absolute [offset] in the reassembled stream. */
    fun writeAt(offset: Long, src: ByteArray, from: Int, len: Int) {
        if (len <= 0 || offset < 0) return
        synchronized(lock) {
            val end = offset + len
            if (end > data.size) return // guard: never write past the declared contentLength
            System.arraycopy(src, from, data, offset.toInt(), len)
            addInterval(offset, end)
            recomputeContiguous()
            lock.notifyAll()
        }
    }

    fun markComplete() = synchronized(lock) { complete = true; lock.notifyAll() }
    fun markError(message: String) = synchronized(lock) { if (error == null) error = message; lock.notifyAll() }

    /** Contiguous bytes available from 0 (for diagnostics). */
    fun available(): Long = synchronized(lock) { contiguous }

    /**
     * Read up to [length] bytes at absolute [position], blocking until the contiguous region covers it
     * (or the session completes / errors). Returns the count read, or -1 at end of stream. Throws on error.
     */
    fun read(position: Long, into: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        synchronized(lock) {
            while (position >= contiguous && !complete && error == null) lock.wait()
            error?.let { throw java.io.IOException("SABR session failed: $it") }
            if (position >= contiguous) return -1
            val avail = (contiguous - position)
            val n = minOf(length.toLong(), avail).toInt()
            System.arraycopy(data, position.toInt(), into, offset, n)
            return n
        }
    }

    private fun addInterval(start: Long, end: Long) {
        var s = start
        var e = end
        // Merge with any overlapping/adjacent existing intervals.
        val head = intervals.floorEntry(s)
        if (head != null && head.value >= s) { s = head.key; if (head.value > e) e = head.value; intervals.remove(head.key) }
        val overlapping = intervals.subMap(s, true, e, true).keys.toList()
        for (k in overlapping) { val v = intervals.remove(k)!!; if (v > e) e = v }
        intervals[s] = e
    }

    private fun recomputeContiguous() {
        val first = intervals.firstEntry() ?: return
        if (first.key == 0L) contiguous = first.value
    }
}
