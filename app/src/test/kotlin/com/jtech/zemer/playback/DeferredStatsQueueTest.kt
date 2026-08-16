package com.jtech.zemer.playback

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * The deferred queue's capture + reconnect-flush orchestration (with an Unconfined scope so every
 * `scope.launch` runs synchronously): SUCCESS/DROP remove the record, RETRY keeps it, a stale record
 * is dropped without a push, and nothing flushes while offline.
 */
class DeferredStatsQueueTest {

    private lateinit var file: File
    private val scope = CoroutineScope(Dispatchers.Unconfined)
    private var nowMs = 1_000_000L
    private var connected = true
    private val pushed = mutableListOf<String>()

    private val record = DeferredStatsRecord("v1", "0.0", "30.0", "30.0", "30.0", endedAtMs = 1_000_000L)

    @Before
    fun setUp() {
        file = File.createTempFile("deferred-stats-test", ".jsonl").apply { delete() }
    }

    @After
    fun tearDown() {
        scope.cancel() // drop any pending self-rescheduled retry coroutine
        file.delete()
    }

    private fun queue(outcome: (DeferredStatsRecord) -> DeferredPushOutcome) = DeferredStatsQueue(
        file = file,
        scope = scope,
        isConnected = { connected },
        push = { rec -> pushed.add(rec.videoId); outcome(rec) },
        now = { nowMs },
    )

    private fun queuedLines() = if (file.exists()) file.readLines().filter { it.startsWith("{") } else emptyList()

    @Test
    fun `a SUCCESS push removes the record`() {
        queue { DeferredPushOutcome.SUCCESS }.enqueue(record)

        assertEquals(listOf("v1"), pushed)
        assertEquals(0, queuedLines().size)
    }

    @Test
    fun `a DROP push removes the record`() {
        queue { DeferredPushOutcome.DROP }.enqueue(record)

        assertEquals(listOf("v1"), pushed)
        assertEquals(0, queuedLines().size)
    }

    @Test
    fun `a RETRY push keeps the record for the next flush`() {
        val q = queue { DeferredPushOutcome.RETRY }
        q.enqueue(record)

        assertEquals("attempted once", 1, pushed.size)
        assertEquals("kept in the queue", 1, queuedLines().size)
    }

    @Test
    fun `nothing is pushed while offline`() {
        connected = false
        queue { DeferredPushOutcome.SUCCESS }.enqueue(record)

        assertEquals(emptyList<String>(), pushed)
        assertEquals("stays queued until reconnect", 1, queuedLines().size)
    }

    @Test
    fun `a stale record is dropped without a push`() {
        val q = queue { DeferredPushOutcome.SUCCESS }
        connected = false
        q.enqueue(record) // queued, not pushed (offline)
        assertEquals(1, queuedLines().size)

        nowMs = record.endedAtMs + DeferredStatsQueue.MAX_AGE_MS + 1 // now stale
        connected = true
        q.onFlushTrigger()

        assertEquals("never pushed", emptyList<String>(), pushed)
        assertEquals("dropped as too old", 0, queuedLines().size)
    }

    @Test
    fun `reconnect flushes a record captured while offline`() {
        val q = queue { DeferredPushOutcome.SUCCESS }
        connected = false
        q.enqueue(record)
        assertEquals(1, queuedLines().size)

        connected = true
        q.onFlushTrigger()

        assertEquals(listOf("v1"), pushed)
        assertEquals(0, queuedLines().size)
    }

    // --- soak: multi-record cycles, ordering, retry-then-succeed, batch draining ---

    private fun recordFor(id: String) = record.copy(videoId = id, endedAtMs = nowMs)

    @Test
    fun `many records queued offline all drain on reconnect, in FIFO order`() {
        val q = queue { DeferredPushOutcome.SUCCESS }
        connected = false
        val ids = (0 until 8).map { "v$it" }
        ids.forEach { q.enqueue(recordFor(it)) }
        assertEquals(8, queuedLines().size)

        connected = true
        q.onFlushTrigger()

        assertEquals("all drained, oldest-first", ids, pushed)
        assertEquals(0, queuedLines().size)
    }

    @Test
    fun `a record that RETRYs then succeeds drains once the backoff expires`() {
        var outcome = DeferredPushOutcome.RETRY
        val q = queue { outcome }
        q.enqueue(record)

        assertEquals("attempted, kept after RETRY", 1, pushed.size)
        assertEquals(1, queuedLines().size)

        // Server recovers; before the backoff expires a trigger is a no-op...
        outcome = DeferredPushOutcome.SUCCESS
        q.onFlushTrigger()
        assertEquals("still within the backoff window", 1, pushed.size)
        assertEquals(1, queuedLines().size)

        // ...and once it expires the record drains.
        nowMs += 60_000
        q.onFlushTrigger()
        assertEquals(2, pushed.size)
        assertEquals(0, queuedLines().size)
    }

    @Test
    fun `a queue larger than one batch drains across successive triggers`() {
        val q = queue { DeferredPushOutcome.SUCCESS }
        connected = false
        val total = DeferredStatsQueue.BATCH_SIZE + 5
        repeat(total) { q.enqueue(recordFor("v$it")) }
        assertEquals(total, queuedLines().size)

        connected = true
        q.onFlushTrigger() // one batch
        assertEquals(DeferredStatsQueue.BATCH_SIZE, pushed.size)
        assertEquals(5, queuedLines().size)

        q.onFlushTrigger() // the remainder
        assertEquals(total, pushed.size)
        assertEquals(0, queuedLines().size)
    }

    @Test
    fun `a backlog larger than one batch fully drains on a stable connection`() {
        // delayFn is a no-op so the paced self-reschedule runs synchronously (no wall-clock wait).
        val q = DeferredStatsQueue(
            file = file,
            scope = scope,
            isConnected = { connected },
            push = { rec -> pushed.add(rec.videoId); DeferredPushOutcome.SUCCESS },
            now = { nowMs },
            delayFn = { },
        )
        connected = false
        val total = DeferredStatsQueue.BATCH_SIZE * 3 + 7 // 67, well past one batch
        repeat(total) { q.enqueue(recordFor("v$it")) }
        assertEquals(total, queuedLines().size)

        connected = true
        q.onFlushTrigger() // ONE trigger; the paced reschedule must drain the whole backlog

        assertEquals("no records stall past the first batch", total, pushed.size)
        assertEquals(0, queuedLines().size)
    }

    @Test
    fun `a mix of stale and fresh records drops the stale and pushes the fresh`() {
        val q = queue { DeferredPushOutcome.SUCCESS }
        connected = false
        // one old record, then advance the clock so it is stale, then a fresh one
        q.enqueue(recordFor("old"))
        nowMs += DeferredStatsQueue.MAX_AGE_MS + 1
        q.enqueue(recordFor("fresh"))
        assertEquals(2, queuedLines().size)

        connected = true
        q.onFlushTrigger()

        assertEquals("only the fresh record is pushed", listOf("fresh"), pushed)
        assertEquals(0, queuedLines().size)
    }
}
