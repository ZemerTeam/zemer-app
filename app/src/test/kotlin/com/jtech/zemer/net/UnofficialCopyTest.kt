package com.jtech.zemer.net

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The servers' unofficial-copy answer is recognized exactly, and only two in a row refuse the copy. */
class UnofficialCopyTest {
    private val json = "application/json; charset=utf-8"
    private val msg = "This copy of Zemer isn't official. Download Zemer from zemer.io."
    private val refusal = """{"error":"unofficial_copy","message":"$msg"}"""

    @After
    fun tearDown() = UnofficialCopy.reset()

    private fun of(status: Int, header: String? = null, type: String? = json, body: String? = refusal) =
        UnofficialCopy.unofficialMessage(status, header, type) { body }

    @Test
    fun `the JSON answer and the relay's header answer are recognized, with their message`() {
        assertEquals(msg, of(403))
        assertEquals(msg, of(403, header = "unofficial_copy", type = "text/plain; charset=utf-8", body = "$msg\n"))
        assertEquals("a header answer without a text body still counts", "", of(403, header = "unofficial_copy", type = null, body = null))
        assertEquals("no message: the built-in one is shown", "", of(403, body = """{"error":"unofficial_copy"}"""))
    }

    @Test
    fun `any other answer never counts`() {
        assertNull(of(403, body = """{"error":"update_required","message":"Update Zemer"}"""))
        assertNull(of(401))
        assertNull(of(200))
        assertNull(of(403, type = "text/html", body = refusal))
        assertNull(of(403, body = "not json"))
        assertNull(of(403, body = "[1]"))
        assertNull(of(403, body = null))
    }

    @Test
    fun `two refusals in a row refuse the copy, any other response in between resets the count`() {
        UnofficialCopy.onResponse(403, null, json) { refusal }
        UnofficialCopy.onResponse(200, null, json) { null }
        UnofficialCopy.onResponse(403, null, json) { refusal }
        assertFalse(UnofficialCopy.refused)
        UnofficialCopy.onResponse(403, "unofficial_copy", "text/plain") { msg }
        assertTrue(UnofficialCopy.refused)
        assertEquals(msg, UnofficialCopy.message.value)
    }

    @Test
    fun `a clock-skew refusal neither counts nor resets the count`() {
        assertNull(of(403, body = """{"error":"clock_skew","message":"Your phone's clock is off."}"""))
        UnofficialCopy.onResponse(403, null, json) { refusal }
        UnofficialCopy.onResponse(403, null, json) { """{"error":"clock_skew"}""" }
        UnofficialCopy.onResponse(403, "clock_skew", "text/plain") { "Your phone's clock is off." }
        assertFalse(UnofficialCopy.refused)
        UnofficialCopy.onResponse(403, null, json) { refusal }
        assertTrue(UnofficialCopy.refused)
    }
}
