package com.jtech.zemer.tracking

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Guards the flush gate: EVERY trigger (threshold, timer, background) must wait out the failure
 * backoff — the review-confirmed bug was a ≥20-event queue firing one POST per new event straight
 * through an outage.
 */
class FlushScheduleTest {

    @Test
    fun `failures open a backoff window that delayUntilAllowed enforces`() {
        var now = 1_000L
        val s = FlushSchedule { now }

        assertEquals(0L, s.delayUntilAllowed()) // healthy: attempts allowed immediately

        assertEquals(30_000L, s.onFailure(rateLimited = false))
        assertEquals(30_000L, s.delayUntilAllowed()) // a threshold trigger right now must wait

        now += 10_000
        assertEquals(20_000L, s.delayUntilAllowed())

        now += 20_000
        assertEquals(0L, s.delayUntilAllowed()) // window elapsed
    }

    @Test
    fun `consecutive failures climb the ladder and a 429 floors at two minutes`() {
        var now = 0L
        val s = FlushSchedule { now }

        assertEquals(30_000L, s.onFailure(rateLimited = false))
        assertEquals(120_000L, s.onFailure(rateLimited = false))
        assertEquals(600_000L, s.onFailure(rateLimited = false))
        assertEquals(600_000L, s.onFailure(rateLimited = false))

        val fresh = FlushSchedule { now }
        assertEquals(120_000L, fresh.onFailure(rateLimited = true)) // first failure, but 429 waits ≥2 min
    }

    @Test
    fun `success resets the ladder and reopens immediate attempts`() {
        var now = 0L
        val s = FlushSchedule { now }
        s.onFailure(rateLimited = false)
        s.onFailure(rateLimited = false)

        s.onSuccess()

        assertEquals(0L, s.delayUntilAllowed())
        assertEquals(0, s.consecutiveFailures)
        assertEquals(30_000L, s.onFailure(rateLimited = false)) // ladder restarts from the bottom
    }
}
