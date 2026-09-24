package com.jtech.zemer.lyrics.zemer

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression: "Report wrong lyrics" dismissed the menu sheet and then launched the POST on the sheet's own
 * scope, which was cancelled on the next frame, so the report never reached the server. The feedback now
 * rides a scope the caller does not own; cancelling the caller's scope must not cancel the call.
 */
class LyricsFeedbackTest {
    @Test
    fun `report completes even after the calling scope is cancelled`() {
        val owner = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val sent = mutableListOf<Pair<String, String>>()
        var accepted = false
        val feedback = LyricsFeedback(owner, deviceId = { "dev-1" }, report = { id, device -> sent += id to device; true }, mainDispatcher = Dispatchers.Unconfined)
        val caller = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        feedback.reportWrong("v1") { accepted = true }
        caller.cancel()
        assertEquals(listOf("v1" to "dev-1"), sent)
        assertTrue(accepted)
    }

    @Test
    fun `no device id means no request, a rejected report shows nothing`() {
        val owner = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        var calls = 0
        var accepted = false
        LyricsFeedback(owner, deviceId = { null }, report = { _, _ -> calls++; true }, mainDispatcher = Dispatchers.Unconfined).reportWrong("v1") { accepted = true }
        assertEquals(0, calls)
        LyricsFeedback(owner, deviceId = { "dev" }, report = { _, _ -> calls++; false }, mainDispatcher = Dispatchers.Unconfined).reportWrong("v1") { accepted = true }
        assertEquals(1, calls)
        assertFalse(accepted)
    }

    @Test
    fun `submit carries the edited text and only toasts on acceptance`() {
        val owner = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        var submitted: Triple<String, String, String>? = null
        var accepted = false
        LyricsFeedback(owner, deviceId = { "dev" }, submit = { id, text, device -> submitted = Triple(id, text, device); true }, mainDispatcher = Dispatchers.Unconfined)
            .submitEdit("v1", "corrected") { accepted = true }
        assertEquals(Triple("v1", "corrected", "dev"), submitted)
        assertTrue(accepted)
    }

    @Test
    fun `a throwing client call is swallowed, never crashes the owner scope`() {
        val owner = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        var accepted = false
        LyricsFeedback(owner, deviceId = { "dev" }, report = { _, _ -> throw IllegalStateException("network") }, mainDispatcher = Dispatchers.Unconfined).reportWrong("v1") { accepted = true }
        assertFalse(accepted)
        assertTrue(owner.isActive)
    }
}
