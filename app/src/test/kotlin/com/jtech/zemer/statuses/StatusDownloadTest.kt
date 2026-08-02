package com.jtech.zemer.statuses

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** JVM coverage of the saved-status record's JSON round-trip (the DataStore persistence format). */
class StatusDownloadTest {

    private fun sample(id: String, kind: String) = StatusDownload(
        id = id, kind = kind, creatorId = "c1", creatorName = "Shira Choir", creatorAvatar = "c1/a.jpg",
        postedAt = "2026-08-01T21:14:32+00:00", caption = "hi", textBody = if (kind == "text") "Shabbat" else null,
        mediaUri = "content://media/external/images/media/42", savedAt = 1_700_000_000_000L,
    )

    @Test
    fun `encode then decode round-trips every field`() {
        val items = listOf(sample("a", "video"), sample("b", "text"), sample("c", "image"))
        val decoded = StatusDownload.decodeList(StatusDownload.encodeList(items))
        assertEquals(items, decoded)
    }

    @Test
    fun `null caption and textBody survive the round-trip as null`() {
        val d = sample("a", "image").copy(caption = null, textBody = null)
        val decoded = StatusDownload.decodeList(StatusDownload.encodeList(listOf(d))).single()
        assertNull(decoded.caption)
        assertNull(decoded.textBody)
        assertEquals(d, decoded)
    }

    @Test
    fun `malformed or empty blob decodes to an empty list, never throws`() {
        assertTrue(StatusDownload.decodeList(null).isEmpty())
        assertTrue(StatusDownload.decodeList("").isEmpty())
        assertTrue(StatusDownload.decodeList("not json").isEmpty())
        assertTrue(StatusDownload.decodeList("{\"not\":\"an array\"}").isEmpty())
    }
}
