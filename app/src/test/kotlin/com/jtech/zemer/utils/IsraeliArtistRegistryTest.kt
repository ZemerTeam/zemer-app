package com.jtech.zemer.utils

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Issue #535: "loaded" was keyed off a non-empty set, so a successfully loaded EMPTY registry (the live
 * list is empty) re-fetched on every ensureLoaded() - one network round-trip per filtered artist. A
 * success, empty or not, must be loaded once; a failure must still be retried (after a cooldown).
 */
class IsraeliArtistRegistryTest {

    @After
    fun reset() = IsraeliArtistRegistry.resetForTest()

    @Test
    fun `a successfully loaded empty registry is not re-fetched`() = runBlocking {
        var fetches = 0
        repeat(3) { IsraeliArtistRegistry.ensureLoaded { fetches++; emptySet() } }
        assertEquals(1, fetches)
        assertFalse(IsraeliArtistRegistry.isIsraeli("UC1"))
    }

    @Test
    fun `a failed load is retried once the cooldown has passed, not on every call`() = runBlocking {
        var fetches = 0
        var clock = 1_000L
        IsraeliArtistRegistry.ensureLoaded(now = { clock }) { fetches++; throw java.io.IOException("offline") }
        // inside the cooldown: no fetch (an outage must not add a network wait to every filter pass)
        IsraeliArtistRegistry.ensureLoaded(now = { clock }) { fetches++; setOf("UC1") }
        assertEquals(1, fetches)
        assertFalse(IsraeliArtistRegistry.isIsraeli("UC1"))
        clock += IsraeliArtistRegistry.RETRY_COOLDOWN_MS
        IsraeliArtistRegistry.ensureLoaded(now = { clock }) { fetches++; setOf("UC1") }
        IsraeliArtistRegistry.ensureLoaded(now = { clock }) { fetches++; emptySet() }
        assertEquals(2, fetches)
        assertTrue(IsraeliArtistRegistry.isIsraeli("UC1"))
    }
}
