package com.jtech.zemer.utils

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure coverage of the conditional id-override cache. It is a process-global singleton, so each test
 * resets it afterward.
 */
class BlockedIdsCacheTest {

    private val femaleFiltering = ContentFilterConfig(filtersEnabled = true, allowFemaleSingers = false)
    private val femaleAllowed = ContentFilterConfig(filtersEnabled = true, allowFemaleSingers = true)
    private val filtersOff = ContentFilterConfig(filtersEnabled = false, allowFemaleSingers = false)

    @After
    fun reset() = BlockedIdsCache.updateAll(emptyMap())

    @Test
    fun `female override hides only when filtering out female, shows otherwise`() {
        BlockedIdsCache.updateAll(mapOf("femSong" to BlockedIdsCache.REASON_FEMALE))

        assertTrue(BlockedIdsCache.isBlocked("femSong", femaleFiltering)) // hidden for female-filterers
        assertFalse(BlockedIdsCache.isBlocked("femSong", femaleAllowed)) // shown when female is allowed
        assertFalse(BlockedIdsCache.isBlocked("femSong", filtersOff))    // shown when filtering is off
    }

    @Test
    fun `global override hides for everyone with filtering on, never when filtering is off`() {
        BlockedIdsCache.updateAll(mapOf("bad" to BlockedIdsCache.REASON_GLOBAL))

        assertTrue(BlockedIdsCache.isBlocked("bad", femaleFiltering))
        assertTrue(BlockedIdsCache.isBlocked("bad", femaleAllowed)) // global ignores the female pref
        assertFalse(BlockedIdsCache.isBlocked("bad", filtersOff))   // master switch off => inert
    }

    @Test
    fun `unknown reason defaults to global, ids are trimmed, blanks and null are never blocked`() {
        BlockedIdsCache.updateAll(mapOf("x" to "weird", "  y  " to "global", "" to "global"))

        assertTrue(BlockedIdsCache.isBlocked("x", femaleAllowed)) // unknown reason -> global
        assertTrue(BlockedIdsCache.isBlocked("y", femaleAllowed)) // id trimmed on the way in
        assertFalse(BlockedIdsCache.isBlocked("other", femaleFiltering))
        assertFalse(BlockedIdsCache.isBlocked(null, femaleFiltering))
        assertFalse(BlockedIdsCache.isBlocked("", femaleFiltering))
    }

    @Test
    fun `serialize then parse round-trips id and reason`() {
        val entries = mapOf("a" to "female", "b" to "global")
        assertEquals(entries, BlockedIdsCache.parse(BlockedIdsCache.serialize(entries)))
    }

    @Test
    fun `parse defaults a reasonless line to global and skips blank lines`() {
        val parsed = BlockedIdsCache.parse("onlyid\n\n  \nwithreason\tfemale")
        assertEquals(mapOf("onlyid" to "global", "withreason" to "female"), parsed)
    }
}
