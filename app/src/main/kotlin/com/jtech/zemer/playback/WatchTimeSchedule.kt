package com.jtech.zemer.playback

/**
 * The official WEB_REMIX watchtime-ping flush cadence. The
 * `/player` response carries `videostatsScheduledFlushWalltimeSeconds` + `videostatsDefaultFlushIntervalSeconds`
 * (verified live: `[10,20,30]` then `40`); the web client fires a watchtime ping at each scheduled
 * wall-clock second since playback start, then every default-interval seconds. Matching this replaces a
 * fixed interval (a timing fingerprint) with the real client's timing — the values come from YouTube,
 * nothing is guessed.
 *
 * Pure and JVM-tested. [scheduledSeconds] is the absolute wall-clock offsets; when the server omits
 * them the base.js `klA` default `[10,20,30]` then `40` is used (also verified from base.js).
 */
class WatchTimeSchedule(
    scheduledSeconds: List<Int>?,
    defaultIntervalSeconds: Int?,
) {
    private val scheduled: List<Long> =
        (scheduledSeconds?.takeIf { it.isNotEmpty() } ?: DEFAULT_SCHEDULE)
            .map { it.toLong() * 1000L }
            .sorted()
    private val intervalMs: Long =
        (defaultIntervalSeconds?.takeIf { it > 0 } ?: DEFAULT_INTERVAL_SECONDS).toLong() * 1000L

    /**
     * The wall-clock offset (ms since playback start) of the [index]-th flush (0-based): the scheduled
     * offsets first, then the last scheduled offset plus multiples of the default interval.
     */
    fun flushOffsetMs(index: Int): Long =
        if (index < scheduled.size) {
            scheduled[index]
        } else {
            scheduled.last() + (index - scheduled.size + 1) * intervalMs
        }

    companion object {
        // base.js `klA` default when the response omits the schedule: pings at 10s, 20s, 30s, then 40s.
        val DEFAULT_SCHEDULE = listOf(10, 20, 30)
        const val DEFAULT_INTERVAL_SECONDS = 40
    }
}
