package com.jtech.zemer.utils

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BlockedAlbumsTest {

    private val femaleBlocked = ContentFilterConfig(filtersEnabled = true, allowFemaleSingers = false)
    private val femaleAllowed = ContentFilterConfig(filtersEnabled = true, allowFemaleSingers = true)
    private val filtersOff = ContentFilterConfig(filtersEnabled = false)

    @Before
    fun setUp() = BlockedIdsCache.updateAll(mapOf("g1" to "global", "g2" to "global", "f1" to "female"))

    @After
    fun tearDown() = BlockedIdsCache.updateAll(emptyMap())

    private fun hidden(songs: List<String>, config: ContentFilterConfig) =
        albumFullyBlocked(songs) { BlockedIdsCache.isBlocked(it, config) }

    @Test
    fun `every song global-blocked hides the album for every filtering user`() {
        assertTrue(hidden(listOf("g1", "g2"), femaleBlocked))
        assertTrue(hidden(listOf("g1", "g2"), femaleAllowed))
    }

    @Test
    fun `global plus female songs hide the album only when female singers are blocked`() {
        assertTrue(hidden(listOf("g1", "f1"), femaleBlocked))
        assertFalse(hidden(listOf("g1", "f1"), femaleAllowed))
    }

    @Test
    fun `one unblocked song keeps the album`() {
        assertFalse(hidden(listOf("g1", "f1", "ok"), femaleBlocked))
    }

    @Test
    fun `an album with no stored songs is kept`() {
        assertFalse(hidden(emptyList(), femaleBlocked))
    }

    @Test
    fun `filters off hides nothing and skips the lookup`() {
        assertFalse(hidden(listOf("g1", "g2"), filtersOff))
        assertFalse(BlockedIdsCache.anyApplies(filtersOff))
        assertTrue(BlockedIdsCache.anyApplies(femaleBlocked))
        BlockedIdsCache.updateAll(emptyMap())
        assertFalse("an empty table hides nothing", BlockedIdsCache.anyApplies(femaleBlocked))
    }
}
