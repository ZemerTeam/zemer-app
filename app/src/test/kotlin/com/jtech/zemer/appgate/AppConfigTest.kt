package com.jtech.zemer.appgate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class AppConfigTest {

    private val now = Instant.parse("2026-10-15T12:00:00Z")

    @Test
    fun `the all-null live config parses and gates nothing`() {
        val config = parseAppConfig(
            """{"minVersion":null,"latestVersion":null,"graceUntil":null,"message":null,"updateUrl":null,"updatedAt":"2026-10-15T00:00:00Z"}""",
        )
        assertEquals(AppConfig(), config)
        assertEquals(AppGate.None, appGate(config, 39, now))
    }

    @Test
    fun `garbage, blank and non-object bodies are no config (fail open)`() {
        listOf(null, "", "  ", "not json", "[1,2]", "{\"error\":", "<html>503</html>").forEach {
            assertNull(it, parseAppConfig(it))
        }
        assertEquals(AppGate.None, appGate(null, 39, now))
    }

    @Test
    fun `missing and ill-typed fields are simply absent`() {
        val config = parseAppConfig(
            """{"minVersion":"40","latestVersion":39.5,"graceUntil":"next week","message":"  ","updateUrl":"javascript:x"}""",
        )!!
        assertEquals(AppConfig(), config)
        assertEquals(AppGate.None, appGate(config, 1, now))
        assertEquals(AppConfig(), parseAppConfig("{}"))
    }

    @Test
    fun `below minVersion is blocked, with the server message and link`() {
        val config = parseAppConfig(
            """{"minVersion":40,"latestVersion":41,"graceUntil":"2026-11-01T00:00:00Z","message":"Update now","updateUrl":"https://zemer.io/download"}""",
        )
        assertEquals(AppGate.Blocked("Update now", "https://zemer.io/download"), appGate(config, 39, now))
        // At or above minVersion the block lifts (and here the grace window applies instead).
        assertTrue(appGate(config, 40, now) is AppGate.Grace)
    }

    @Test
    fun `below latestVersion inside the grace window prompts, outside it does nothing`() {
        val config = parseAppConfig("""{"latestVersion":40,"graceUntil":"2026-11-01T00:00:00+00:00"}""")
        assertEquals(AppGate.Grace(Instant.parse("2026-11-01T00:00:00Z"), null, null), appGate(config, 39, now))
        // Deadline passed, no deadline, or already current -> nothing.
        assertEquals(AppGate.None, appGate(config, 39, Instant.parse("2026-11-02T00:00:00Z")))
        assertEquals(AppGate.None, appGate(config!!.copy(graceUntil = null), 39, now))
        assertEquals(AppGate.None, appGate(config, 40, now))
    }

    @Test
    fun `the grace prompt shows at most once per day`() {
        assertTrue(gracePromptDue(null, "2026-10-15"))
        assertTrue(gracePromptDue("", "2026-10-15"))
        assertFalse(gracePromptDue("2026-10-15", "2026-10-15"))
        assertTrue(gracePromptDue("2026-10-15", "2026-10-16"))
    }
}
