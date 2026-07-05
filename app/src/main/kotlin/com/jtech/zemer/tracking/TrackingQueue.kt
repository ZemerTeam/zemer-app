package com.jtech.zemer.tracking

import java.io.File

/**
 * The durable event queue: one JSON event per line (JSONL) in a small file under `filesDir`, so
 * queued telemetry survives process death without touching the Room schema. Capped at [maxSize]
 * events, dropping the OLDEST on overflow — telemetry must never grow unbounded, and losing old
 * events is fine by contract.
 *
 * Not thread-safe by itself: [Tracker] confines all access to a single dispatcher. Every mutation
 * persists via an atomic tmp-file rename; a corrupt/partial file degrades to the lines that parse
 * as JSON objects (never a crash, never a poisoned queue).
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
        while (events.size > maxSize) events.removeFirst()
        persist()
    }

    /** The oldest [max] events, in chronological (insertion) order, left in place until [removeFirst]. */
    fun peekBatch(max: Int = BATCH_SIZE): List<String> {
        ensureLoaded()
        return events.take(max)
    }

    fun removeFirst(count: Int) {
        ensureLoaded()
        repeat(count.coerceAtMost(events.size)) { events.removeFirst() }
        persist()
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

    private fun persist() {
        runCatching {
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.writeText(events.joinToString("\n"))
            if (!tmp.renameTo(file)) {
                file.writeText(events.joinToString("\n"))
                tmp.delete()
            }
        }
    }

    companion object {
        const val MAX_SIZE = 500
        const val BATCH_SIZE = 100
    }
}
