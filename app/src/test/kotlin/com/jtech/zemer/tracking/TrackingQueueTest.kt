package com.jtech.zemer.tracking

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Guards the durable JSONL queue (spec §2): 500-cap dropping OLDEST, ≤100-event batch slicing in
 * chronological order, eviction-safe batch removal, disk persistence across instances, and
 * corrupt-file tolerance — telemetry must never grow unbounded, poison itself, lose never-uploaded
 * events, or crash the app.
 */
class TrackingQueueTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun file(): File = File(tmp.root, "events.jsonl")

    private fun event(n: Int) = """{"type":"open","t":$n}"""

    @Test
    fun `cap drops the OLDEST events, never the newest`() {
        val q = TrackingQueue(file(), maxSize = 5)
        (1..8).forEach { q.append(event(it)) }

        assertEquals(5, q.size)
        assertEquals(listOf(event(4), event(5), event(6), event(7), event(8)), q.peekBatch(100))
    }

    @Test
    fun `peekBatch slices the oldest N in insertion order and leaves them queued until removed`() {
        val q = TrackingQueue(file())
        (1..7).forEach { q.append(event(it)) }

        assertEquals(listOf(event(1), event(2), event(3)), q.peekBatch(3))
        assertEquals(7, q.size)

        q.removeBatch(q.peekBatch(3))
        assertEquals(listOf(event(4)), q.peekBatch(1))
        assertEquals(4, q.size)
    }

    @Test
    fun `removeBatch after cap-eviction mid-upload never deletes events that were not uploaded`() {
        val q = TrackingQueue(file(), maxSize = 5)
        (1..5).forEach { q.append(event(it)) }
        val batch = q.peekBatch(3) // "uploading" events 1..3

        // While the upload is in flight, two new events evict the two oldest (1 and 2) — both were
        // part of the uploaded batch.
        q.append(event(6))
        q.append(event(7))
        assertEquals(listOf(event(3), event(4), event(5), event(6), event(7)), q.peekBatch(100))

        // Upload succeeded: only the batch's SURVIVING line (3) may be removed — 4..7 were never
        // uploaded and must stay queued (the old remove-first-N deleted 3,4,5 here).
        q.removeBatch(batch)
        assertEquals(listOf(event(4), event(5), event(6), event(7)), q.peekBatch(100))
    }

    @Test
    fun `queue persists to disk and reloads in a fresh instance`() {
        TrackingQueue(file()).apply {
            append(event(1))
            append(event(2))
        }

        val reloaded = TrackingQueue(file())
        assertEquals(listOf(event(1), event(2)), reloaded.peekBatch(100))
    }

    @Test
    fun `eviction and removal rewrite the file consistently for the next instance`() {
        val q = TrackingQueue(file(), maxSize = 3)
        (1..5).forEach { q.append(event(it)) } // evicts 1,2
        q.removeBatch(listOf(event(3)))

        val reloaded = TrackingQueue(file(), maxSize = 3)
        assertEquals(listOf(event(4), event(5)), reloaded.peekBatch(100))
    }

    @Test
    fun `a corrupt or partial file degrades to the parseable lines, never a crash`() {
        file().writeText("${event(1)}\ngarbage not json\n${event(2)}\n{\"trunc")

        val q = TrackingQueue(file())
        assertEquals(listOf(event(1), event(2)), q.peekBatch(100))
    }

    @Test
    fun `removeBatch with an already-emptied queue is a no-op`() {
        val q = TrackingQueue(file())
        q.append(event(1))
        q.removeBatch(listOf(event(1), event(2), event(3)))
        assertEquals(0, q.size)
    }
}
