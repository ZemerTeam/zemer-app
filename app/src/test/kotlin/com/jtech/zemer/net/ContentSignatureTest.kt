package com.jtech.zemer.net

import com.jtech.zemer.net.ContentSignature.Verdict
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Content signatures against the server's pinned vectors (`content-sig-vectors.json`, copied from zemer-search,
 * signed with a TEST key; Ed25519 is deterministic), plus the apply/keep decision.
 */
class ContentSignatureTest {
    @Serializable
    private data class Vector(val path: String, val t: Long, val body: String, val header: String, val note: String? = null)

    @Serializable
    private data class Vectors(val keyId: String, val publicSpkiB64: String, val vectors: List<Vector>)

    private val file = Json { ignoreUnknownKeys = true }.decodeFromString(
        Vectors.serializer(),
        javaClass.classLoader!!.getResourceAsStream("contentsig/content-sig-vectors.json")!!.readBytes().decodeToString(),
    )
    private val keys = mapOf(file.keyId to ContentSignature.rawKey(file.publicSpkiB64))

    @Test
    fun `every pinned vector verifies`() {
        assertTrue(file.vectors.size >= 4)
        file.vectors.forEach { v ->
            assertEquals(v.note, Verdict.VALID, ContentSignature.verify(v.path, v.header, v.body.toByteArray(), keys))
        }
    }

    @Test
    fun `a changed body, path, time or key, or a malformed header, is invalid`() {
        val v = file.vectors.first { it.body.isNotEmpty() }
        val body = v.body.toByteArray()
        assertEquals(Verdict.INVALID, ContentSignature.verify(v.path, v.header, body + ' '.code.toByte(), keys))
        assertEquals(Verdict.INVALID, ContentSignature.verify(v.path + "/version", v.header, body, keys))
        assertEquals(Verdict.INVALID, ContentSignature.verify(v.path, v.header.replace("t=${v.t}", "t=${v.t + 1}"), body, keys))
        assertEquals(Verdict.INVALID, ContentSignature.verify(v.path, v.header.replace("k=${file.keyId}", "k=9"), body, keys))
        assertEquals("the production key never accepts a test signature", Verdict.INVALID, ContentSignature.verify(v.path, v.header, body))
        listOf("k=1", "k=1;t=x;s=abc", "garbage", "k=1;t=1;s=!!!").forEach {
            assertEquals(it, Verdict.INVALID, ContentSignature.verify(v.path, it, body, keys))
        }
        assertEquals(Verdict.MISSING, ContentSignature.verify(v.path, null, body, keys))
        assertEquals(Verdict.MISSING, ContentSignature.verify(v.path, " ", body, keys))
    }

    @Test
    fun `an invalid signature is never applied, a missing one only on a first sync`() {
        assertNull(ContentSignature.rejection(Verdict.VALID, firstSync = false))
        assertEquals("bad_sig", ContentSignature.rejection(Verdict.INVALID, firstSync = false))
        assertEquals("bad_sig", ContentSignature.rejection(Verdict.INVALID, firstSync = true))
        assertEquals("no_sig", ContentSignature.rejection(Verdict.MISSING, firstSync = false))
        assertNull(ContentSignature.rejection(Verdict.MISSING, firstSync = true))
    }

    @Test
    fun `the production key parses to a raw Ed25519 key`() {
        assertEquals(32, ContentSignature.rawKey("MCowBQYDK2VwAyEAFMojieqcnaStywSStpF2MOBuiR/HapbRay5oSAO70Eg=").size)
    }
}
