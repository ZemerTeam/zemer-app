package com.jtech.zemer.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the login-capture contract behind issue #140: a personal Google login that persists a cookie
 * but never captures DATASYNC_ID lands in the pooled/anonymous state ("Not logged in to YouTube" on
 * playlist sync). The extraction JS must be throw-proof, and the login must not complete without the
 * dataSyncId.
 */
class LoginCaptureTest {

    @Test
    fun `extraction scripts are guarded and always call the bridge back`() {
        for (js in listOf(LoginCapture.EXTRACT_VISITOR_DATA_JS, LoginCapture.EXTRACT_DATA_SYNC_ID_JS)) {
            assertTrue(js.startsWith("javascript:"))
            // The #140 regression was an unguarded `window.yt.config_.X` chain that THREW when yt
            // wasn't ready, so the bridge callback never fired. Pin the guard + null fallback.
            assertTrue("must guard yt.config_ existence", js.contains("window.yt&&yt.config_"))
            assertTrue("must be wrapped in try/catch", js.contains("try{") && js.contains("catch"))
            assertTrue("must fall back to null so the retry loop observes progress", js.contains("(null)"))
        }
        assertTrue(LoginCapture.EXTRACT_VISITOR_DATA_JS.contains("Android.onRetrieveVisitorData"))
        assertTrue(LoginCapture.EXTRACT_DATA_SYNC_ID_JS.contains("Android.onRetrieveDataSyncId"))
    }

    @Test
    fun `cleanDataSyncId strips the double-pipe suffix and rejects null or blank`() {
        assertEquals("id123", LoginCapture.cleanDataSyncId("id123||suffix"))
        assertEquals("id123", LoginCapture.cleanDataSyncId("id123"))
        assertNull(LoginCapture.cleanDataSyncId(null))
        assertNull(LoginCapture.cleanDataSyncId(""))
        assertNull(LoginCapture.cleanDataSyncId("   "))
        assertNull(LoginCapture.cleanDataSyncId("||suffix")) // empty id part -> null, never stored
    }

    @Test
    fun `capture is complete only with a signed-in cookie AND a dataSyncId`() {
        val signedInCookie = "VISITOR_INFO1_LIVE=x; SAPISID=abc; SID=y"
        assertTrue(LoginCapture.isCaptureComplete(signedInCookie, "dsid"))

        // The #140 state: cookie persisted, dataSyncId never captured -> must NOT complete.
        assertFalse(LoginCapture.isCaptureComplete(signedInCookie, ""))
        assertFalse(LoginCapture.isCaptureComplete(signedInCookie, null))

        // A pre-login cookie (no SAPISID) must not complete either.
        assertFalse(LoginCapture.isCaptureComplete("VISITOR_INFO1_LIVE=x", "dsid"))
        assertFalse(LoginCapture.isCaptureComplete(null, "dsid"))
        assertFalse(LoginCapture.isCaptureComplete("", ""))
    }

    @Test
    fun `an incomplete capture is a failure only after a real sign-in`() {
        assertTrue(LoginCapture.isCaptureFailure("SAPISID=abc; SID=y", ""))
        assertTrue(LoginCapture.isCaptureFailure("SAPISID=abc; SID=y", null))
        // the page was reached without signing in: nothing to report
        assertFalse(LoginCapture.isCaptureFailure("VISITOR_INFO1_LIVE=x", ""))
        assertFalse(LoginCapture.isCaptureFailure("", ""))
        assertFalse(LoginCapture.isCaptureFailure(null, null))
        assertFalse(LoginCapture.isCaptureFailure("SAPISID=abc", "dsid"))
    }

    @Test
    fun `retry budget allows the page several seconds to expose yt config`() {
        // 20 x 500 ms = 10 s. The victim device needed ~1-3 s; a single 500 ms shot was the bug.
        assertTrue(LoginCapture.MAX_CAPTURE_ATTEMPTS * LoginCapture.CAPTURE_RETRY_DELAY_MS >= 5_000L)
    }
}
