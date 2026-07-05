package com.jtech.zemer.tracking

/**
 * The flush-gating state (pure, unit-tested): tracks the backoff ladder and the earliest moment the
 * next upload attempt is ALLOWED. Every flush trigger — threshold, 60 s timer, background, retry —
 * must honor [delayUntilAllowed], so a failing/rate-limiting server is never hammered by
 * threshold-triggered flushes while the queue sits above 20 events (spec §2's 30 s → 2 min → 10 min
 * ladder and the ≥ 2 min wait after a 429).
 */
internal class FlushSchedule(private val now: () -> Long) {
    var consecutiveFailures = 0
        private set
    private var nextAllowedAt = 0L

    fun onSuccess() {
        consecutiveFailures = 0
        nextAllowedAt = 0L
    }

    /** Records a failed attempt; returns the backoff delay before the next allowed attempt. */
    fun onFailure(rateLimited: Boolean): Long {
        consecutiveFailures++
        val delay = trackingRetryDelayMs(consecutiveFailures, rateLimited)
        nextAllowedAt = now() + delay
        return delay
    }

    /** 0 when an attempt may run now; otherwise how long a trigger must wait. */
    fun delayUntilAllowed(): Long = (nextAllowedAt - now()).coerceAtLeast(0L)
}
