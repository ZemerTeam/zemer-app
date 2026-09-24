package com.jtech.zemer.playback.sabr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the attestation-cap detection: a client that can't satisfy stream protection (MWEB/IOS-class)
 * gets a free window, then only STREAM_PROTECTION_STATUS>=2 with no media — counted so the session bails
 * fast (proven live in tests/probe-mweb-sabr.mjs) instead of grinding to the dry cap.
 */
class SabrProtectionTest {

    @Test
    fun `no-progress responses under active protection accumulate, and cap at the limit`() {
        var stalls = 0
        // Delivering segments under protection resets the counter (protection alone is not a cap).
        stalls = SabrProtection.nextStalls(protStatus = 2, madeProgress = true, prev = stalls)
        assertEquals(0, stalls)
        // Then media stops while protection stays >= 2: each no-media response counts.
        stalls = SabrProtection.nextStalls(2, madeProgress = false, prev = stalls); assertEquals(1, stalls)
        assertFalse(SabrProtection.capped(stalls))
        stalls = SabrProtection.nextStalls(2, madeProgress = false, prev = stalls); assertEquals(2, stalls)
        assertFalse(SabrProtection.capped(stalls))
        stalls = SabrProtection.nextStalls(3, madeProgress = false, prev = stalls); assertEquals(3, stalls)
        assertTrue("three no-media responses under protection is the cap", SabrProtection.capped(stalls))
    }

    @Test
    fun `protection status below pending never counts, and progress clears the count`() {
        // OK (status 1) never counts, even with no new media (that's a normal between-segment poll).
        assertEquals(0, SabrProtection.nextStalls(protStatus = 1, madeProgress = false, prev = 2))
        // A no-status response (0) likewise never counts.
        assertEquals(0, SabrProtection.nextStalls(protStatus = 0, madeProgress = false, prev = 2))
        // Any progress resets, even while protection is pending.
        assertEquals(0, SabrProtection.nextStalls(protStatus = 2, madeProgress = true, prev = 2))
    }
}
