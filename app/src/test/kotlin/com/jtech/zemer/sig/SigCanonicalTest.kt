package com.jtech.zemer.sig

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * The canonical message must match the server byte for byte: every pinned contract vector (`sig-vectors.json`,
 * copied from zemer-search) and 400 random URLs signed by the server's own `sig.mjs`
 * (`sig-vectors-generated.json`: Hebrew, emoji, `+` vs `%2B`, repeated / empty / key-only params, empty
 * segments, mixed-case methods).
 */
class SigCanonicalTest {
    @Serializable
    private data class Vector(val method: String, val url: String, val t: Long, val sig: String, val note: String? = null)

    @Serializable
    private data class Vectors(val keyB64: String, val vectors: List<Vector>)

    private fun load(name: String): Vectors = Json { ignoreUnknownKeys = true }.decodeFromString(
        Vectors.serializer(),
        javaClass.classLoader!!.getResourceAsStream("sig/$name")!!.readBytes().decodeToString(),
    )

    private fun sign(keyB64: String, v: Vector): String {
        val q = v.url.indexOf('?')
        val path = if (q < 0) v.url else v.url.substring(0, q)
        val query = if (q < 0) null else v.url.substring(q + 1)
        val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(Base64.getDecoder().decode(keyB64), "HmacSHA256")) }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(SigCanonical.message(v.method, path, query, v.t)))
    }

    private fun check(name: String, minCount: Int) {
        val file = load(name)
        assertTrue(file.vectors.size >= minCount)
        file.vectors.forEach { v -> assertEquals("${v.method} ${v.url} (${v.note})", v.sig, sign(file.keyB64, v)) }
    }

    @Test
    fun `every pinned contract vector matches the server`() = check("sig-vectors.json", 7)

    @Test
    fun `400 random URLs signed by the server match`() = check("sig-vectors-generated.json", 400)

    @Serializable
    private data class V2Vector(
        val method: String, val url: String, val t: Long, val n: String, val body: String, val bodyHash: String, val sig: String,
        val note: String? = null,
    )

    @Serializable
    private data class V2Vectors(val keyB64: String, val v2: List<V2Vector>)

    @Test
    fun `every pinned version 2 vector matches the server, body hash included`() {
        val file = Json { ignoreUnknownKeys = true }.decodeFromString(
            V2Vectors.serializer(),
            javaClass.classLoader!!.getResourceAsStream("sig/sig-vectors.json")!!.readBytes().decodeToString(),
        )
        assertTrue(file.v2.size >= 4)
        val b64 = Base64.getUrlEncoder().withoutPadding()
        val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(Base64.getDecoder().decode(file.keyB64), "HmacSHA256")) }
        file.v2.forEach { v ->
            assertEquals(v.note, v.bodyHash, b64.encodeToString(MessageDigest.getInstance("SHA-256").digest(v.body.toByteArray())))
            val q = v.url.indexOf('?')
            val message = SigCanonical.message2(
                v.method, if (q < 0) v.url else v.url.substring(0, q), if (q < 0) null else v.url.substring(q + 1), v.t, v.n, v.bodyHash,
            )
            assertEquals(v.note, v.sig, b64.encodeToString(mac.doFinal(message)))
        }
    }

    @Test
    fun `a version 2 header carries a fresh nonce and the body hash`() {
        ZemerSig.signer = { ByteArray(32) }
        try {
            fun fields(h: String) = h.split(';').associate { it.substringBefore('=') to it.substringAfter('=') }
            val a = fields(ZemerSig.headerFor("POST", "/v1/events", null, "{}".toByteArray())!!)
            val b = fields(ZemerSig.headerFor("POST", "/v1/events", null, "{}".toByteArray())!!)
            assertEquals(listOf("v", "k", "t", "n", "b", "s"), a.keys.toList())
            assertEquals(22, a.getValue("n").length)
            assertTrue("a nonce per call", a["n"] != b["n"])
            assertEquals("RBNvo1WzZ4oRRq0W9-hknpT7T8If536DEMBg9hyq_4o", a["b"])
            assertEquals("47DEQpj8HBSa-_TImW-5JCeuQeRkm5NMpJWZG3hSuFU", fields(ZemerSig.headerFor("GET", "/x", null, ByteArray(0))!!)["b"])
            assertTrue("no body bytes: version 1", !ZemerSig.headerFor("POST", "/x", null, null)!!.startsWith("v="))
        } finally {
            ZemerSig.signer = null
        }
    }

    @Test
    fun `the message layout is exactly the contract`() {
        assertEquals(
            "zsig1\u0000GET\u0000/a\u000042\u0000x\u00001\u0000x\u00002\u0000y\u0000\u0000",
            SigCanonical.message("get", "/a", "y=&x=2&x=1", 42).decodeToString(),
        )
        assertEquals("zsig1\u0000PUT\u0000/prefs\u00007\u0000", SigCanonical.message("PUT", "/prefs", null, 7).decodeToString())
        // a malformed path escape keeps the raw path, like decodeURIComponent throwing on the server
        assertEquals("/a%zz", SigCanonical.decodePath("/a%zz"))
        assertEquals("/a+b", SigCanonical.decodePath("/a+b"))
    }
}
