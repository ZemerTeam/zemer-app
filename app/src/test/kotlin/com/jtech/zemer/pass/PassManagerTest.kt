package com.jtech.zemer.pass

import com.jtech.zemer.pass.PassManager.Step
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class PassManagerTest {
    private val now = 1_000_000_000L
    private val hour = 3_600_000L

    @After
    fun reset() {
        ZemerPass.current = null
    }

    @Test
    fun `a pass with more than the margin left is kept, even while a retry is pending`() {
        assertEquals(Step.KEEP, PassManager.nextStep(now, "p", now + 3 * hour, "k", retryAfter = now + hour))
    }

    @Test
    fun `a pass close to expiry is renewed with the existing key`() {
        assertEquals(Step.RENEW, PassManager.nextStep(now, "p", now + hour, "k", retryAfter = 0))
        assertEquals(Step.RENEW, PassManager.nextStep(now, null, 0, "k", retryAfter = 0))
    }

    @Test
    fun `no key means a fresh attestation, and a pending backoff waits`() {
        assertEquals(Step.ATTEST, PassManager.nextStep(now, null, 0, null, retryAfter = 0))
        assertEquals(Step.WAIT, PassManager.nextStep(now, null, 0, null, retryAfter = now + 1))
        assertEquals(Step.ATTEST, PassManager.nextStep(now, null, 0, null, retryAfter = now))
    }

    @Test
    fun `a refusal backs off a day, a network failure an hour`() {
        assertEquals(PassManager.REFUSED_BACKOFF_MS, PassManager.backoffFor(PassRefusedException(403, "security level software")))
        assertEquals(PassManager.REFUSED_BACKOFF_MS, PassManager.backoffFor(PassRefusedException(400, "unknown or expired nonce")))
        assertEquals(PassManager.FAILURE_BACKOFF_MS, PassManager.backoffFor(PassRefusedException(429, "too many")))
        assertEquals(PassManager.FAILURE_BACKOFF_MS, PassManager.backoffFor(PassRefusedException(503, "down")))
        assertEquals(PassManager.FAILURE_BACKOFF_MS, PassManager.backoffFor(IOException("offline")))
        // a device without keystore attestation support
        assertEquals(PassManager.REFUSED_BACKOFF_MS, PassManager.backoffFor(java.security.ProviderException("no attestation")))
    }

    @Test
    fun `the pass rides only the Zemer hosts`() {
        ZemerPass.current = "tok"
        for (host in listOf("search.zemer.io", "content.zemer.io", "stream.zemer.io", "SEARCH.ZEMER.IO")) {
            assertEquals(host, "tok", ZemerPass.headerFor(host))
        }
        for (host in listOf("tracking.zemer.io", "nightly.zemer.io", "lrclib.net", "zemer.io", "search.zemer.io.evil.com", "music.youtube.com")) {
            assertNull(host, ZemerPass.headerFor(host))
            assertFalse(ZemerPass.hostGetsPass(host))
        }
        ZemerPass.current = null
        assertNull(ZemerPass.headerFor("search.zemer.io"))
        assertTrue(ZemerPass.hostGetsPass("search.zemer.io"))
    }

    @Test
    fun `the server nonce is decoded as unpadded base64url`() {
        val raw = ByteArray(32) { (it * 7 + 250).toByte() } // includes bytes that map to - and _
        val text = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(raw)
        assertTrue(raw.contentEquals(PassClient.Nonce(text).bytes))
    }
}
