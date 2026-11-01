package com.jtech.zemer.utils

import org.junit.Assert.assertEquals
import org.junit.Test

/** [artistsToDelete]: an artist's local data is deleted ONLY when the server names it removed. */
class ArtistRemovalTest {

    private val local = setOf("UCa", "UCb", "UCc")

    @Test
    fun `an artist merely missing from the fetched whitelist is never deleted`() {
        assertEquals(emptyList<String>(), artistsToDelete(serverRemoved = emptySet(), whitelisted = setOf("UCa"), local = local))
    }

    @Test
    fun `a failed removed read deletes nothing`() {
        assertEquals(emptyList<String>(), artistsToDelete(serverRemoved = null, whitelisted = emptySet(), local = local))
    }

    @Test
    fun `a named removed artist stored on the device is deleted`() {
        assertEquals(listOf("UCb"), artistsToDelete(serverRemoved = setOf("UCb"), whitelisted = setOf("UCa"), local = local))
    }

    @Test
    fun `a named artist the whitelist lists again, or one not on the device, is kept out`() {
        assertEquals(
            emptyList<String>(),
            artistsToDelete(serverRemoved = setOf("UCa", "UCzz"), whitelisted = setOf("UCa"), local = local),
        )
    }
}
