package com.jtech.zemer.playback.sabr

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.TreeMap

/**
 * A thread-safe, DISK-BACKED reassembly buffer for one SABR session. The session thread writes each
 * media segment at its ABSOLUTE byte offset ([writeAt], the segment's startRange) — exactly like the
 * proven Node harness, so the reassembled stream is byte-exact regardless of the order segments arrive
 * in. The reader serves any COVERED region ([readCovered] — not just a prefix: a seek-restarted session
 * (see [SabrAudioStream]) writes a tail region while the head may stay a gap, and reads inside covered
 * regions are always served. Backing is a spool FILE, not a heap array — a multi-hour podcast episode
 * or a 2160p video track must never risk an OutOfMemoryError; the OS page cache keeps hot reads cheap.
 *
 * [expectedLength] is the format's contentLength (known up front from /player). The reader's progress
 * ([lastReadEnd]) feeds [awaitDemand] — the demand-pacing gate a PLAYBACK session waits on so the drain
 * follows what the player consumes instead of pulling the whole track eagerly (proven live: a SABR
 * session keeps serving after minutes-long idle gaps between POSTs).
 */
internal class SabrBuffer private constructor(
    val expectedLength: Long,
    val file: File,
    preComplete: Boolean,
) {
    /** A fresh (empty) buffer spooling to [file]; refuses an out-of-range length LOUDLY (see [lengthValid]). */
    constructor(expectedLength: Long, file: File) : this(expectedLength, file, preComplete = false)

    init {
        require(lengthValid(expectedLength)) { "SABR contentLength out of range: $expectedLength" }
    }

    private val lock = Object()
    private val raf = RandomAccessFile(file, if (preComplete) "r" else "rw")
    // Filled byte intervals [start, endExclusive), merged; the coverage map reads consult.
    private val intervals = TreeMap<Long, Long>()
    private var contiguous = 0L
    private var complete = false
    private var error: String? = null
    private var closed = false
    // The reader's high-water mark (end of the last serve) — the demand signal for pacing.
    private var lastReadEnd = 0L

    init {
        if (preComplete) {
            intervals[0L] = expectedLength
            contiguous = expectedLength
            complete = true
        } else {
            // Preallocate (sparse where the filesystem supports it) so positional writes never extend.
            raf.setLength(expectedLength)
        }
    }

    /** Write [len] bytes from [src] at absolute [offset] in the reassembled stream. */
    fun writeAt(offset: Long, src: ByteArray, from: Int, len: Int) {
        if (len <= 0 || offset < 0) return
        synchronized(lock) {
            if (closed || error != null) return
            val end = offset + len
            if (end > expectedLength) return // guard: never write past the declared contentLength
            try {
                raf.seek(offset)
                raf.write(src, from, len)
            } catch (e: IOException) {
                error = "spool write failed: ${e.message}"
                lock.notifyAll()
                return
            }
            addInterval(offset, end)
            recomputeContiguous()
            lock.notifyAll()
        }
    }

    fun markComplete() = synchronized(lock) { complete = true; lock.notifyAll() }
    fun markError(message: String) = synchronized(lock) { if (error == null) error = message; lock.notifyAll() }

    /** Wake waiters WITHOUT changing state — a seek-session finished its tail; readers re-evaluate. */
    fun notifyWaiters() = synchronized(lock) { lock.notifyAll() }

    /** Contiguous bytes available from 0. */
    fun available(): Long = synchronized(lock) { contiguous }

    /** Whether the session behind this buffer failed — a failed buffer must never be reused. */
    fun failed(): Boolean = synchronized(lock) { error != null }

    /** Whether the WHOLE stream is covered from 0 (the spool replay-cache retention condition). */
    fun completeFromZero(): Boolean = synchronized(lock) { contiguous >= expectedLength }

    /** Whether [position] lies inside a covered interval. */
    fun coveredAt(position: Long): Boolean = synchronized(lock) { coveredAtLocked(position) }

    /** End (exclusive) of the covered interval containing [position], or [position] when uncovered. */
    fun coverageEndFrom(position: Long): Long = synchronized(lock) {
        val e = intervals.floorEntry(position)
        if (e != null && e.value > position) e.value else position
    }

    private fun coveredAtLocked(position: Long): Boolean {
        val e = intervals.floorEntry(position) ?: return false
        return e.value > position
    }

    /**
     * Non-blocking read: serve up to [length] bytes at [position] from the covered region (returns the
     * count and advances the demand watermark), 0 when [position] is not covered yet, or throw when the
     * stream has FAILED and cannot cover [position] (covered bytes still serve first — the error
     * surfaces exactly at the gap, never discarding reassembled audio behind the read position).
     */
    fun readCovered(position: Long, into: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        synchronized(lock) {
            if (coveredAtLocked(position)) {
                val end = intervals.floorEntry(position).value
                val n = minOf(length.toLong(), end - position).toInt()
                if (closed) throw IOException("SABR spool closed")
                raf.seek(position)
                var done = 0
                while (done < n) {
                    val r = raf.read(into, offset + done, n - done)
                    if (r <= 0) throw IOException("SABR spool short read at $position")
                    done += r
                }
                val readEnd = position + n
                if (readEnd > lastReadEnd) lastReadEnd = readEnd
                lock.notifyAll() // wake a demand-paced session
                return n
            }
            error?.let { throw IOException("SABR session failed: $it") }
            return 0
        }
    }

    /** Block up to [timeoutMs] for any state change (new coverage / error / completion / demand). */
    fun awaitChange(timeoutMs: Long) = synchronized(lock) {
        if (error == null && !complete) lock.wait(timeoutMs)
    }

    /**
     * Blocking sequential read (the download path): waits until [position] is covered, the stream
     * completes, or errors. Returns the count, -1 at end of stream, throws on error at a gap.
     */
    fun read(position: Long, into: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        while (true) {
            val n = readCovered(position, into, offset, length)
            if (n > 0) return n
            synchronized(lock) {
                if (!coveredAtLocked(position)) {
                    error?.let { throw IOException("SABR session failed: $it") }
                    if (complete) return -1
                    lock.wait()
                    error?.let { throw IOException("SABR session failed: $it") }
                    if (complete && !coveredAtLocked(position)) return -1
                }
            }
        }
    }

    /** Bytes buffered ahead of the reader's watermark (the demand-pacing signal). */
    fun demandGap(): Long = synchronized(lock) {
        val e = intervals.floorEntry(lastReadEnd)
        val covEnd = if (e != null && e.value > lastReadEnd) e.value else lastReadEnd
        covEnd - lastReadEnd
    }

    /**
     * The demand-pacing gate: block while the drain is more than [aheadBytes] ahead of the reader
     * (checked against the coverage the READER is consuming). Returns when demand catches up, the
     * stream errors/completes, or [cancelled] flips — a PLAYBACK session calls this between POSTs so
     * a skipped/paused track stops costing data (proven live: the server keeps serving after long
     * idle gaps).
     */
    fun awaitDemand(aheadBytes: Long, cancelled: () -> Boolean) {
        synchronized(lock) {
            while (!cancelled() && error == null && !complete) {
                val gap = run {
                    val e = intervals.floorEntry(lastReadEnd)
                    val covEnd = if (e != null && e.value > lastReadEnd) e.value else lastReadEnd
                    covEnd - lastReadEnd
                }
                if (gap <= aheadBytes) return
                lock.wait(250)
            }
        }
    }

    /** Release the file handle; [deleteFile] removes the spool from disk (not for promoted cache files). */
    fun release(deleteFile: Boolean) {
        synchronized(lock) {
            closed = true
            runCatching { raf.close() }
            lock.notifyAll()
        }
        if (deleteFile) runCatching { file.delete() }
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
        // Disk-backed spool: the ceiling only refuses an absurd/bogus contentLength (multi-hour audio
        // and 2160p video tracks all fit far below it; RAM is untouched either way).
        internal const val MAX_BUFFER_BYTES = 4L * 1024 * 1024 * 1024

        /** Whether [len] is a contentLength this buffer can hold — pre-check before constructing. */
        fun lengthValid(len: Long): Boolean = len in 1..MAX_BUFFER_BYTES

        /** A read-only buffer over an already-COMPLETE spool file (the replay cache) — no session. */
        fun completeFrom(file: File, length: Long): SabrBuffer = SabrBuffer(length, file, preComplete = true)
    }
}
