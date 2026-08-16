package com.jtech.zemer.playback

import com.jtech.zemer.tracking.FlushSchedule
import com.jtech.zemer.tracking.TrackingQueue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/** What a single deferred push resolved to — drives keep-vs-remove on the queue. */
enum class DeferredPushOutcome {
    /** The beacons were accepted (2xx) — remove the record. */
    SUCCESS,

    /** Transient (offline, timeout, 5xx, video temporarily unresolvable) — keep and retry with backoff. */
    RETRY,

    /** Permanently unusable (malformed 4xx) — remove so it can't poison the queue forever. */
    DROP,
}

/**
 * The durable queue + reconnect flusher for offline/cached listens whose LIVE YouTube playback-stats
 * session could not fire (no network → no `/player` tracking URLs). Additive to [WatchTimeReporter];
 * the live DIRECT path is untouched — this only recovers the plays it deliberately drops, by pushing
 * a *deferred* stats session from a fresh `/player` once the device is back online.
 *
 * Fire-and-forget by contract (mirrors the telemetry `Tracker`): every entry point is a cheap
 * `scope.launch` onto a caller-provided single dispatcher, so all queue access is single-threaded and
 * nothing here can block playback. Durability reuses [TrackingQueue] (JSONL under `filesDir`, cap
 * [MAX_SIZE], drop-oldest, atomic rewrite — no Room, no migration); backoff reuses [FlushSchedule].
 *
 * The network push is injected ([push]) so the queue/scheduling/staleness logic is unit-testable
 * without YouTube; [MusicService] wires the real push ([pushDeferredStats]).
 */
class DeferredStatsQueue(
    file: File,
    private val scope: CoroutineScope,
    private val isConnected: () -> Boolean,
    private val push: suspend (DeferredStatsRecord) -> DeferredPushOutcome,
    private val now: () -> Long = System::currentTimeMillis,
    private val maxAgeMs: Long = MAX_AGE_MS,
) {
    private val queue = TrackingQueue(file, MAX_SIZE)
    private val schedule = FlushSchedule(now)
    private var inFlight = false
    private var retryScheduled = false

    /** Capture (from the reporter's offline branch): persist one qualifying listen, then try to flush. */
    fun enqueue(record: DeferredStatsRecord) {
        scope.launch {
            queue.append(record.encode())
            flush()
        }
    }

    /** An external flush trigger — wired to connectivity-available (the offline→online edge). */
    fun onFlushTrigger() {
        scope.launch { flush() }
    }

    private suspend fun flush() {
        if (inFlight || !isConnected() || queue.size == 0) return
        if (schedule.delayUntilAllowed() > 0) return
        inFlight = true
        var retryAfterMs = 0L
        try {
            // Bounded per flush (each record costs a /player round-trip) — a self-reschedule (below) and
            // the reconnect trigger drain any remainder.
            for (line in queue.peekBatch(BATCH_SIZE)) {
                if (!isConnected()) break
                val record = DeferredStatsRecord.decode(line)
                if (record == null || record.isStale(now(), maxAgeMs)) {
                    queue.removeBatch(listOf(line)) // corrupt or too old — drop, never report
                    continue
                }
                when (push(record)) {
                    DeferredPushOutcome.SUCCESS -> {
                        queue.removeBatch(listOf(line))
                        schedule.onSuccess()
                    }
                    DeferredPushOutcome.DROP -> queue.removeBatch(listOf(line))
                    DeferredPushOutcome.RETRY -> {
                        retryAfterMs = schedule.onFailure(rateLimited = false)
                        break // respect the backoff window; the reschedule below retries the rest
                    }
                }
            }
        } finally {
            inFlight = false
        }
        // Self-reschedule after the backoff: the ONLY external trigger is the reconnect edge, so a
        // record that RETRYs while the device stays online would otherwise sit until it goes stale. One
        // pending retry at a time (the guard); the delayUntilAllowed gate makes an early fire a no-op.
        if (retryAfterMs > 0 && queue.size > 0 && !retryScheduled) {
            retryScheduled = true
            scope.launch {
                delay(retryAfterMs)
                retryScheduled = false
                flush()
            }
        }
    }

    companion object {
        const val MAX_SIZE = 500

        /** Beyond this a deferred listen isn't worth reporting (matches the telemetry queue's 7-day cap). */
        const val MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000

        /** How many records one flush processes before yielding to the next trigger. */
        const val BATCH_SIZE = 20
    }
}
