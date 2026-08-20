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
    init {
        // Refuse an out-of-range contentLength LOUDLY at construction. The old degenerate ByteArray(0)
        // made writeAt a silent no-op while the session still drained the whole stream over the network,
        // then EOF'd at byte 0 with no error — callers must pre-check with [lengthValid] and fail fast.
        require(lengthValid(expectedLength)) { "SABR contentLength out of range: $expectedLength" }
    }

    private val lock = Object()
    // Whole-stream in-memory buffer sized to the format's contentLength. The cap is a safety net against a
    // bogus/huge contentLength allocating unbounded RAM — NOT a real-content limit: a long opus podcast at
    // itag 251 (~160 kbps) passes 64 MiB after only ~53 min, so a smaller cap silently dropped every write
    // (data = ByteArray(0), writeAt no-ops) and made long episodes reassemble to nothing. 512 MiB clears
    // multi-hour audio while still refusing an absurd length.
    private val data = ByteArray(expectedLength.toInt())
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

    /** Whether the session behind this buffer failed — a failed buffer must never be reused. */
    fun failed(): Boolean = synchronized(lock) { error != null }

    /**
     * Read up to [length] bytes at absolute [position], blocking until the contiguous region covers it
     * (or the session completes / errors). Returns the count read, or -1 at end of stream. Throws on
     * error — but only once the buffered bytes are exhausted: an errored session (a mid-drain shortfall,
     * a destroyed stream) still serves everything it reassembled, and the error surfaces exactly at the
     * gap instead of discarding minutes of playable audio behind the read position.
     */
    fun read(position: Long, into: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        synchronized(lock) {
            while (position >= contiguous && !complete && error == null) lock.wait()
            if (position < contiguous) {
                val avail = (contiguous - position)
                val n = minOf(length.toLong(), avail).toInt()
                System.arraycopy(data, position.toInt(), into, offset, n)
                return n
            }
            error?.let { throw java.io.IOException("SABR session failed: $it") }
            return -1
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

    companion object {
        // 512 MiB ceiling: covers multi-hour opus audio, refuses an absurd/bogus contentLength.
        internal const val MAX_BUFFER_BYTES = 512L * 1024 * 1024

        /** Whether [len] is a contentLength this buffer can hold — pre-check before constructing. */
        fun lengthValid(len: Long): Boolean = len in 1..MAX_BUFFER_BYTES
    }
}
