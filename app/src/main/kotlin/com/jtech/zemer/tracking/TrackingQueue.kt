package com.jtech.zemer.tracking

import java.io.File

/**
 * The durable event queue: one JSON event per line (JSONL) in a small file under `filesDir`, so
 * queued telemetry survives process death without touching the Room schema. Capped at [maxSize]
 * events, dropping the OLDEST on overflow — telemetry must never grow unbounded, and losing old
 * events is fine by contract.
 *
 * Not thread-safe by itself: [Tracker] confines all access to a single dispatcher. A plain append
 * is an O(1) file append; only evictions/removals rewrite the file (atomic tmp rename), so a bulk
 * burst never does O(n²) disk work. A corrupt/partial file degrades to the lines that parse as
 * JSON objects (never a crash, never a poisoned queue).
 */
internal class TrackingQueue(
    private val file: File,
    private val maxSize: Int = MAX_SIZE,
) {
    private val events = ArrayDeque<String>()
    private var loaded = false

    val size: Int
        get() {
            ensureLoaded()
            return events.size
        }

    fun append(eventLine: String) {
        ensureLoaded()
        events.addLast(eventLine)
        if (events.size > maxSize) {
            while (events.size > maxSize) events.removeFirst()
            rewrite()
        } else {
            runCatching {
                file.parentFile?.mkdirs()
                file.appendText(eventLine + "\n")
            }
        }
    }

    /** The oldest [max] events, in chronological (insertion) order, left in place until [removeBatch]. */
    fun peekBatch(max: Int = BATCH_SIZE): List<String> {
        ensureLoaded()
        return events.take(max)
    }

    /**
     * Removes exactly the events of an uploaded [batch] that are STILL at the head of the queue.
     * While an upload was in flight, cap-eviction may have already dropped the batch's oldest lines
     * — a plain remove-first-N would then delete newer, never-uploaded events. The walk aligns the
     * batch against the head (evictions only drop from the head, appends only go to the tail), so
     * an evicted batch line is skipped and unrelated events are never touched.
     */
    fun removeBatch(batch: List<String>) {
        ensureLoaded()
        var removed = false
        for (line in batch) {
            if (events.isEmpty()) break
            if (events.first() == line) {
                events.removeFirst()
                removed = true
            }
        }
        if (removed) rewrite()
    }

    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        runCatching {
            if (!file.exists()) return
            file.readLines()
                .filter { it.startsWith("{") && it.endsWith("}") }
                .takeLast(maxSize)
                .forEach { events.addLast(it) }
        }
    }

    private fun rewrite() {
        runCatching {
            file.parentFile?.mkdirs()
            val content = if (events.isEmpty()) "" else events.joinToString("\n", postfix = "\n")
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.writeText(content)
            if (!tmp.renameTo(file)) {
                file.writeText(content)
                tmp.delete()
            }
        }
    }

    companion object {
        const val MAX_SIZE = 500
        const val BATCH_SIZE = 100
    }
}
