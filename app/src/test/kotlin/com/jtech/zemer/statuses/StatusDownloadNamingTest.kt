package com.jtech.zemer.statuses

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId

/**
 * JVM coverage of the gallery path pieces: the posted-date filename stamp (zone-injected for
 * determinism) and the creator folder-segment sanitizer.
 */
class StatusDownloadNamingTest {

    private val utc = ZoneId.of("UTC")

    @Test
    fun `stamp is the posted date and time, hyphenated`() {
        assertEquals("2026-08-01 21-14-32", statusDownloadStamp("2026-08-01T21:14:32+00:00", utc))
    }

    @Test
    fun `stamp is rendered in the given zone`() {
        // 23:30 UTC is the next calendar day in +05:30.
        assertEquals("2026-08-02 05-00-00", statusDownloadStamp("2026-08-01T23:30:00+00:00", ZoneId.of("Asia/Kolkata")))
    }

    @Test
    fun `unparseable posted time falls back without throwing`() {
        assertEquals("unknown-date", statusDownloadStamp("nonsense", utc))
    }

    @Test
    fun `creator folder segment is sanitized for the filesystem`() {
        assertEquals("ACDC", sanitizeCreatorForFile("AC/DC"))
        assertEquals("a b", sanitizeCreatorForFile("  a   b  "))
        assertEquals("clean", sanitizeCreatorForFile("""cl:*?"<>|ean"""))
        assertEquals("Status", sanitizeCreatorForFile("   "))
        assertEquals("Status", sanitizeCreatorForFile("///"))
    }
}
