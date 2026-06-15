package com.jtech.zemer.recognition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the recognition-history whitelist gate: history is only ever shown/played for entries whose
 * artists are *currently* whitelisted, and the gate fails closed. This is the guarantee that a song
 * whose artist was later removed from the whitelist can never be replayed from history.
 */
class RecognitionHistoryFilterTest {

    @Test
    fun `allowed when a stored artist is currently whitelisted`() {
        assertTrue(RecognitionHistoryFilter.isAllowed("UC_a,UC_b", setOf("UC_b")))
    }

    @Test
    fun `blocked when no stored artist is whitelisted (de-whitelisted since recognition)`() {
        assertFalse(RecognitionHistoryFilter.isAllowed("UC_a,UC_b", setOf("UC_other")))
    }

    @Test
    fun `fails closed for an entry with no stored artist ids`() {
        assertFalse(RecognitionHistoryFilter.isAllowed("", setOf("UC_a")))
    }

    @Test
    fun `fails closed against an empty whitelist`() {
        assertFalse(RecognitionHistoryFilter.isAllowed("UC_a", emptySet()))
    }

    @Test
    fun `blank segments are ignored`() {
        assertFalse(RecognitionHistoryFilter.isAllowed(",, ,", setOf("UC_a")))
        assertTrue(RecognitionHistoryFilter.isAllowed("UC_a,,", setOf("UC_a")))
    }

    @Test
    fun `joinIds drops nulls and blanks and round-trips through isAllowed`() {
        val joined = RecognitionHistoryFilter.joinIds(listOf("UC_a", null, "", "UC_b"))
        assertEquals("UC_a,UC_b", joined)
        assertTrue(RecognitionHistoryFilter.isAllowed(joined, setOf("UC_b")))
    }
}
