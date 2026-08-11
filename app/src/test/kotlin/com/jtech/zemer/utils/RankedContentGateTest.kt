package com.jtech.zemer.utils

import com.jtech.zemer.utils.RankedContentGate.Flags
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The shared ranked-row content gate (female-when-blocked + Israeli + kids-only; NEVER the
 * famous/american proxy). Both HomeViewModel and VideoHomeRowsViewModel resolve through this one
 * rule — these tests pin it so the two surfaces cannot drift.
 */
class RankedContentGateTest {

    private val flags = mapOf(
        "UCfem" to Flags(isFemale = true, isKids = false),
        "UCkid" to Flags(isFemale = false, isKids = true),
        "UCok" to Flags(isFemale = false, isKids = false),
    )
    private fun gate(ids: List<String>, allowFemale: Boolean, israeli: Set<String> = emptySet()) =
        RankedContentGate.isBlockedRanked(ids, allowFemale, flags::get, isIsraeli = { it in israeli })

    @Test
    fun `female artist blocks only when female is blocked`() {
        assertTrue(gate(listOf("UCfem"), allowFemale = false))
        assertFalse(gate(listOf("UCfem"), allowFemale = true))
    }

    @Test
    fun `kids-only artist blocks regardless of the female flag`() {
        assertTrue(gate(listOf("UCkid"), allowFemale = true))
        assertTrue(gate(listOf("UCkid"), allowFemale = false))
    }

    @Test
    fun `israeli id blocks even with no flags known`() {
        assertTrue(gate(listOf("UCunknown"), allowFemale = true, israeli = setOf("UCunknown")))
    }

    @Test
    fun `unknown ids fail open and a clean artist passes`() {
        assertFalse(gate(listOf("UCunknown"), allowFemale = false))
        assertFalse(gate(listOf("UCok"), allowFemale = false))
        assertFalse(gate(emptyList(), allowFemale = false))
    }

    @Test
    fun `any blocked credit blocks the item`() {
        assertTrue(gate(listOf("UCok", "UCfem"), allowFemale = false))
        assertFalse(gate(listOf("UCok", "UCfem"), allowFemale = true))
    }
}
