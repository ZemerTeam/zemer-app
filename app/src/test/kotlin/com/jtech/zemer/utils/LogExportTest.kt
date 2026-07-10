package com.jtech.zemer.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    private fun entry(timestamp: Long, message: String = "m", tag: String? = "Tag", priority: Int = 6) =
        LogBufferTree.LogEntry(timestamp, priority, tag, message, null)

    @Test
    fun `range filter is inclusive on both bounds`() {
        val entries = listOf(entry(99), entry(100), entry(150), entry(200), entry(201))

        val filtered = LogExport.filterRange(entries, 100, 200)

        assertEquals(listOf(100L, 150L, 200L), filtered.map { it.timestamp })
    }

    @Test
    fun `export text carries header and one logcat-style line per entry`() {
        val text = LogExport.buildLogText(
            listOf(entry(0, "boom", "YTPlayerUtils", 6), entry(1000, "ok", null, 4))
        )
        val lines = text.lines()

        assertEquals("# Zemer log export", lines[0])
        assertTrue(lines[2].startsWith("# Entries: 2"))
        assertTrue(lines[4].endsWith("E/YTPlayerUtils: boom"))
        assertTrue("null tag falls back to Zemer", lines[5].endsWith("I/Zemer: ok"))
    }

    @Test
    fun `export file name embeds both range bounds`() {
        val name = LogExport.exportFileName(0, 1000)

        assertTrue(name.startsWith("zemer_logs_"))
        assertTrue(name.endsWith(".txt"))
        assertTrue(name.contains("_to_"))
    }
}
