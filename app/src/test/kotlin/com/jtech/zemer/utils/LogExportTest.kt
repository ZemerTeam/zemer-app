package com.jtech.zemer.utils

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

class LogExportTest {

    private val newYork = ZoneId.of("America/New_York")
    private val jerusalem = ZoneId.of("Asia/Jerusalem")

    private fun millisOf(zdt: ZonedDateTime) = zdt.toInstant().toEpochMilli()

    @Test
    fun `combining a picked UTC day with local time stays on the picked day west of UTC`() {
        // Material3 hands back UTC-midnight of the picked day (July 10).
        val pickedUtcDay = millisOf(ZonedDateTime.of(2026, 7, 10, 0, 0, 0, 0, ZoneOffset.UTC))

        val result = LogExport.localInstantMillis(pickedUtcDay, 14, 0, newYork)

        // The old default-zone Calendar decomposition landed on July 9 14:00 in New York.
        assertEquals(millisOf(ZonedDateTime.of(2026, 7, 10, 14, 0, 0, 0, newYork)), result)
    }

    @Test
    fun `combining a picked UTC day with local time stays on the picked day east of UTC`() {
        val pickedUtcDay = millisOf(ZonedDateTime.of(2026, 7, 10, 0, 0, 0, 0, ZoneOffset.UTC))

        val result = LogExport.localInstantMillis(pickedUtcDay, 14, 0, jerusalem)

        assertEquals(millisOf(ZonedDateTime.of(2026, 7, 10, 14, 0, 0, 0, jerusalem)), result)
    }

    @Test
    fun `seeding the picker preserves the local day shortly after local midnight east of UTC`() {
        // 00:30 July 10 in Israel is still July 9 in UTC; the raw instant would pre-select July 9.
        val localInstant = millisOf(ZonedDateTime.of(2026, 7, 10, 0, 30, 0, 0, jerusalem))

        val seed = LogExport.utcDayMillis(localInstant, jerusalem)

        assertEquals(millisOf(ZonedDateTime.of(2026, 7, 10, 0, 0, 0, 0, ZoneOffset.UTC)), seed)
    }

    @Test
    fun `seeding the picker preserves the local day late in the evening west of UTC`() {
        // 22:30 July 10 in New York is already July 11 in UTC; the raw instant would pre-select July 11.
        val localInstant = millisOf(ZonedDateTime.of(2026, 7, 10, 22, 30, 0, 0, newYork))

        val seed = LogExport.utcDayMillis(localInstant, newYork)

        assertEquals(millisOf(ZonedDateTime.of(2026, 7, 10, 0, 0, 0, 0, ZoneOffset.UTC)), seed)
    }

    @Test
    fun `seed then combine round-trips a local instant to the same day and wall-clock time`() {
        val original = ZonedDateTime.of(2026, 7, 10, 1, 15, 0, 0, jerusalem)

        val seed = LogExport.utcDayMillis(millisOf(original), jerusalem)
        val roundTripped = LogExport.localInstantMillis(seed, original.hour, original.minute, jerusalem)

        assertEquals(millisOf(original), roundTripped)
    }
}
