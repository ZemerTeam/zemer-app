package com.jtech.zemer.utils

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM guards for the content-mirror cutover: the [mirrorFirst] resilience contract (worst case ==
 * Firebase) and the wire-model shapes the two mappings depend on. The HTTP/Firestore paths need
 * instrumentation the project doesn't have (no Robolectric), so those are not unit-tested here.
 */
class ZemerContentClientTest {

    // Same lenient config ZemerContentClient decodes with.
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    @Test
    fun `mirrorFirst returns the mirror result and never touches firebase when the mirror succeeds`() = runBlocking {
        var firebaseRan = false
        val result = mirrorFirst("t", mirror = { "mirror" }, firebase = { firebaseRan = true; "firebase" })
        assertEquals("mirror", result)
        assertFalse("firebase fallback must not run when the mirror succeeds", firebaseRan)
    }

    @Test
    fun `mirrorFirst falls back to firebase on ANY mirror throw`() = runBlocking {
        val result = mirrorFirst("t", mirror = { error("mirror down") }, firebase = { "firebase" })
        assertEquals("firebase", result)
    }

    @Test
    fun `whitelist doc keeps absent booleans null (both mappings depend on this), present ones exact`() {
        val doc = json.decodeFromString(
            ContentWhitelistDoc.serializer(),
            """{"id":"UC1","name":"A","isAmerican":true,"isFemale":false}""",
        )
        assertEquals("UC1", doc.id)
        assertEquals(true, doc.isAmerican)   // present true
        assertEquals(false, doc.isFemale)    // present false
        assertNull(doc.isKids)               // absent -> null, NOT false (HomeArtistProfile needs the distinction)
        assertNull(doc.isChasid)             // absent -> null
        assertNull(doc.isGenZ)               // absent -> null
    }

    @Test
    fun `whitelist doc reads the thumbnail when present, null when absent`() {
        val withThumb = json.decodeFromString(
            ContentWhitelistDoc.serializer(),
            """{"id":"UC1","name":"A","thumbnail":"https://yt3.googleusercontent.com/abc"}""",
        )
        assertEquals("https://yt3.googleusercontent.com/abc", withThumb.thumbnail)

        val noThumb = json.decodeFromString(
            ContentWhitelistDoc.serializer(),
            """{"id":"UC2","name":"B"}""",
        )
        assertNull(noThumb.thumbnail)        // the ~3 artists without a server thumb -> null -> default avatar
    }

    @Test
    fun `whitelist doc tolerates the artistId or artistName fallback fields`() {
        val doc = json.decodeFromString(
            ContentWhitelistDoc.serializer(),
            """{"artistId":"UC2","artistName":"B"}""",
        )
        assertEquals("", doc.id)             // primary id absent -> default empty (caller falls back to artistId)
        assertEquals("UC2", doc.artistId)
        assertEquals("B", doc.artistName)
    }

    @Test
    fun `blocked dto exposes global and female buckets, a missing key defaults to empty`() {
        val dto = json.decodeFromString(
            ContentBlockedDto.serializer(),
            """{"female":["a","b"]}""",
        )
        assertEquals(listOf("a", "b"), dto.female)
        assertTrue(dto.global.isEmpty())     // absent key -> empty, no throw
    }

    @Test
    fun `version dto exposes the monotonic gate the app version-gates on`() {
        val dto = json.decodeFromString(
            ContentVersionDto.serializer(),
            """{"update":"9","updatedAt":"2025-12-03T17:39:52.201Z","updatedAtMs":1764783592201,"gate":1764783592201}""",
        )
        assertEquals(1764783592201L, dto.gate)
    }
}
