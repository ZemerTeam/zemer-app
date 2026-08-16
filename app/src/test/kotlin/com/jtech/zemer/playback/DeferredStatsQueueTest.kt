package com.jtech.zemer.playback

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
}
