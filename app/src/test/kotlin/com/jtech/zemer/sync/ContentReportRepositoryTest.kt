package com.jtech.zemer.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class ContentReportRepositoryTest {

    @Test
    fun `payload carries reason, comment, status, reporter and timestamp`() {
        val timestamp = Any()
        val payload = ContentReportRepository.buildReportPayload(
            subject = emptyMap(),
            reason = "female",
            comment = "a comment",
            reporterUid = "uid-123",
            createdAt = timestamp,
        )
        assertEquals("female", payload["reason"])
        assertEquals("a comment", payload["comment"])
        assertEquals("pending", payload["status"])
        assertEquals("uid-123", payload["reporterUid"])
        assertEquals(timestamp, payload["createdAt"])
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
            createdAt = "ts",
        )
        assertEquals("abc123", payload["songId"])
        assertEquals("Some Song", payload["songTitle"])
        assertEquals("art1", payload["artistId"])
        assertEquals("Some Artist", payload["artistName"])
        // base fields still present alongside the subject
        assertEquals("other", payload["reason"])
        assertEquals(9, payload.size)
    }

    @Test
    fun `null subject values are preserved like the legacy per-menu payloads`() {
        // PlaylistMenu historically sent "playlistName" as a nullable value.
        val payload = ContentReportRepository.buildReportPayload(
            subject = mapOf("playlistId" to "p1", "playlistName" to null),
            reason = "bad_playlists",
            comment = "",
            reporterUid = "anon",
            createdAt = "ts",
        )
        assertEquals(true, payload.containsKey("playlistName"))
        assertEquals(null, payload["playlistName"])
    }

    @Test
    fun `subject cannot clobber repository-controlled fields`() {
        val payload = ContentReportRepository.buildReportPayload(
            subject = mapOf(
                "status" to "approved",
                "reporterUid" to "someone-else",
                "songId" to "abc",
            ),
            reason = "other",
            comment = "",
            reporterUid = "real-uid",
            createdAt = "ts",
        )
        // Reserved fields win regardless of what the subject map carries.
        assertEquals("pending", payload["status"])
        assertEquals("real-uid", payload["reporterUid"])
        // Legitimate subject fields still pass through.
        assertEquals("abc", payload["songId"])
    }
}
