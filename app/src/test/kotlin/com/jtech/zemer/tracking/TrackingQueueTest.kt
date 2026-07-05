package com.jtech.zemer.tracking

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Guards the durable JSONL queue (spec §2): 500-cap dropping OLDEST, ≤100-event batch slicing in
 * chronological order, disk persistence across instances, and corrupt-file tolerance — telemetry
 * must never grow unbounded, poison itself, or crash the app.
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

        q.removeFirst(3)
        assertEquals(listOf(event(4)), q.peekBatch(1))
        assertEquals(4, q.size)
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
    fun `a corrupt or partial file degrades to the parseable lines, never a crash`() {
        file().writeText("${event(1)}\ngarbage not json\n${event(2)}\n{\"trunc")

        val q = TrackingQueue(file())
        assertEquals(listOf(event(1), event(2)), q.peekBatch(100))
    }

    @Test
    fun `removeFirst beyond size clears without throwing`() {
        val q = TrackingQueue(file())
        q.append(event(1))
        q.removeFirst(10)
        assertEquals(0, q.size)
    }
}
