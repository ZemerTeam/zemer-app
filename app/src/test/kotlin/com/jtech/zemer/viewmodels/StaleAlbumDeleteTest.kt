package com.jtech.zemer.viewmodels

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The stale-album delete rule: a `/album` 404 also fires for an album that is merely FULLY BLOCKED
 * under the user's content flags, and deleting the local row for a flag-hide would destroy library
 * data the user gets back by flipping the filter.
 */
class StaleAlbumDeleteTest {

    @Test
    fun `open flags make the 404 unconditional - delete without a probe`() {
        assertTrue(staleAlbumGoneForEveryone(flagsRestrictive = false, openFlags404 = null))
    }

    @Test
    fun `restrictive flags delete only when the open-flags probe also 404s`() {
        assertTrue(staleAlbumGoneForEveryone(flagsRestrictive = true, openFlags404 = true))
        assertFalse("album exists unfiltered → it's a flag-hide, keep the row", staleAlbumGoneForEveryone(flagsRestrictive = true, openFlags404 = false))
    }

    @Test
    fun `a failed probe keeps the row - never delete on uncertainty`() {
        assertFalse(staleAlbumGoneForEveryone(flagsRestrictive = true, openFlags404 = null))
    }
}
