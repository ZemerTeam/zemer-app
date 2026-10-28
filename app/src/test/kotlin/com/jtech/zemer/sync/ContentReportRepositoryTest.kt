package com.jtech.zemer.sync

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ContentReportRepositoryTest {

    @Test
    fun `payload carries reason, comment and reporter, never server-owned fields`() {
        val payload = ContentReportRepository.buildReportPayload(
            subject = emptyMap(),
            reason = "female",
            comment = "a comment",
            reporterUid = "uid-123",
        )
        assertEquals("female", payload["reason"]?.jsonPrimitive?.content)
        assertEquals("a comment", payload["comment"]?.jsonPrimitive?.content)
        assertEquals("uid-123", payload["reporterUid"]?.jsonPrimitive?.content)
        assertFalse(payload.containsKey("status"))
        assertFalse(payload.containsKey("createdAt"))
    }

    @Test
    fun `subject fields are merged into the payload`() {
        val payload = ContentReportRepository.buildReportPayload(
            subject = mapOf(
                "songId" to "abc123",
                "songTitle" to "Some Song",
                "artistId" to "art1",
                "artistName" to "Some Artist",
            ),
            reason = "other",
            comment = "",
            reporterUid = "anon",
        )
        assertEquals("abc123", payload["songId"]?.jsonPrimitive?.content)
        assertEquals("Some Song", payload["songTitle"]?.jsonPrimitive?.content)
        assertEquals("art1", payload["artistId"]?.jsonPrimitive?.content)
        assertEquals("Some Artist", payload["artistName"]?.jsonPrimitive?.content)
        assertEquals("other", payload["reason"]?.jsonPrimitive?.content)
        assertEquals(7, payload.size)
        assertEquals(
            """{"songId":"abc123","songTitle":"Some Song","artistId":"art1","artistName":"Some Artist","reason":"other","comment":"","reporterUid":"anon"}""",
            payload.toString(),
        )
    }

    @Test
    fun `null subject values are sent as JSON null like the legacy per-menu payloads`() {
        // PlaylistMenu historically sent "playlistName" as a nullable value.
        val payload = ContentReportRepository.buildReportPayload(
            subject = mapOf("playlistId" to "p1", "playlistName" to null),
            reason = "bad_playlists",
            comment = "",
            reporterUid = "anon",
        )
        assertEquals(JsonNull, payload["playlistName"])
    }

    @Test
    fun `numbers and booleans keep their JSON type`() {
        val payload = ContentReportRepository.buildReportPayload(
            subject = mapOf("count" to 3, "flag" to true),
            reason = "other",
            comment = "",
            reporterUid = "anon",
        )
        assertEquals(JsonPrimitive(3), payload["count"])
        assertEquals(JsonPrimitive(true), payload["flag"])
    }

    @Test
    fun `subject cannot clobber repository-controlled fields`() {
        val payload = ContentReportRepository.buildReportPayload(
            subject = mapOf(
                "reason" to "spoofed",
                "reporterUid" to "someone-else",
                "songId" to "abc",
            ),
            reason = "other",
            comment = "",
            reporterUid = "real-uid",
        )
        assertEquals("other", payload["reason"]?.jsonPrimitive?.content)
        assertEquals("real-uid", payload["reporterUid"]?.jsonPrimitive?.content)
        assertEquals("abc", payload["songId"]?.jsonPrimitive?.content)
    }
}
