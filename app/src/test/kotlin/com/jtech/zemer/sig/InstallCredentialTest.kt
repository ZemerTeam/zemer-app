package com.jtech.zemer.sig

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** The install credential's wire/stored form, and which key signs which request. */
class InstallCredentialTest {
    private val installKey = ByteArray(32) { (it + 100).toByte() }
    private val keyB64 = Base64.getEncoder().encodeToString(installKey)

    @After
    fun tearDown() {
        ZemerSig.signer = null
        ZemerSig.install = null
    }

    @Test
    fun `an answer is used only with a 32-byte key and an id issued for this build`() {
        val ok = InstallCredential.parse("""{"id":"i40.abcdEFGH12345678","key":"$keyB64"}""", "40")!!
        assertEquals("i40.abcdEFGH12345678", ok.id)
        assertTrue(ok.key.contentEquals(installKey))
        assertTrue(InstallCredential.parse("""{"id":"id40.x","key":"$keyB64"}""", "d40")!!.issuedFor("d40"))
        assertNull("another version", InstallCredential.parse("""{"id":"i39.x","key":"$keyB64"}""", "40"))
        assertNull("a release id in a debug build", InstallCredential.parse("""{"id":"i40.x","key":"$keyB64"}""", "d40"))
        assertNull("i400 is not i40", InstallCredential.parse("""{"id":"i400.x","key":"$keyB64"}""", "40"))
        assertNull("short key", InstallCredential.parse("""{"id":"i40.x","key":"AAEC"}""", "40"))
        assertNull("not JSON", InstallCredential.parse("<html>", "40"))
        assertNull(InstallCredential.parse("""{"id":"i40.x"}""", "40"))
    }

    @Test
    fun `the stored form reads back, and the key never shows in toString`() {
        val c = InstallCredential("i40.abc", installKey)
        val back = InstallCredential.parse(InstallCredential.encode(c), "40")!!
        assertEquals(c.id, back.id)
        assertTrue(back.key.contentEquals(installKey))
        assertFalse(c.toString().contains(keyB64))
    }

    @Test
    fun `version 2 signs with the install key, version 1 and the registration with the version key`() {
        ZemerSig.signer = { ByteArray(32) { 7 } }
        ZemerSig.install = InstallCredential("i${ZemerSig.keyId}.abc", installKey)
        fun fields(h: String) = h.split(';').associate { it.substringBefore('=') to it.substringAfter('=') }

        val v2 = fields(ZemerSig.headerFor("POST", "/v1/events", null, "{}".toByteArray(), nowMs = 5_000)!!)
        assertEquals("i${ZemerSig.keyId}.abc", v2["k"])
        val message = SigCanonical.message2("POST", "/v1/events", null, 5, v2.getValue("n"), v2.getValue("b"))
        val want = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(installKey, "HmacSHA256")) }.doFinal(message)
        assertEquals(Base64.getUrlEncoder().withoutPadding().encodeToString(want), v2["s"])

        val register = fields(ZemerSig.headerFor("POST", ZemerSig.INSTALL_PATH, null, ByteArray(0))!!)
        assertEquals(ZemerSig.keyId, register["k"])
        assertEquals("2", register["v"])
        assertEquals(ZemerSig.keyId, fields(ZemerSig.headerFor("GET", "/x", null, null)!!)["k"])

        ZemerSig.install = null
        assertEquals(ZemerSig.keyId, fields(ZemerSig.headerFor("GET", "/x", null, ByteArray(0))!!)["k"])
    }
}
