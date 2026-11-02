package com.jtech.zemer.utils

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CreditsPassTest {

    private val allowed: suspend (String) -> Boolean = { it == "UC_ok" }

    private fun pass(ids: List<String?>, filtersEnabled: Boolean = true, requireAll: Boolean = false, fallback: String? = null) =
        runBlocking { creditsPass(ids, filtersEnabled, requireAll, fallback, allowed) }

    @Test
    fun `filters off passes credits that carry no ids`() {
        assertTrue(pass(listOf(null), filtersEnabled = false))
        assertTrue(pass(emptyList(), filtersEnabled = false))
    }

    @Test
    fun `filters on needs a matching credit`() {
        assertTrue(pass(listOf("UC_other", "UC_ok")))
        assertFalse(pass(listOf("UC_other")))
        assertFalse(pass(listOf(null)))
    }

    @Test
    fun `require all needs every credit to match`() {
        assertFalse(pass(listOf("UC_ok", "UC_other"), requireAll = true))
        assertTrue(pass(listOf("UC_ok", null), requireAll = true))
    }

    @Test
    fun `no credits falls back to the page artist`() {
        assertTrue(pass(emptyList(), fallback = "UC_ok"))
        assertFalse(pass(emptyList(), fallback = "UC_other"))
        assertFalse(pass(emptyList()))
    }
}
