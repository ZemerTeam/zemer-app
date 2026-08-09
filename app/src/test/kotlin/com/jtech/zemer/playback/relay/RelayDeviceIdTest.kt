package com.jtech.zemer.playback.relay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

/**
 * The pure relay-id logic: the shape check ([RelayDeviceId.isValid]) mirrors the relay's validation regex,
 * and [RelayDeviceId.resolve] is the single source of the get-or-create/debug-null policy (the DataStore
 * persistence + mutex durability around it needs Robolectric, which the project does not have).
 */
class RelayDeviceIdTest {

    // ---- isValid ----

    @Test
    fun `a random uuid is a valid relay id`() {
        assertTrue(RelayDeviceId.isValid(UUID.randomUUID().toString()))
    }

    @Test
    fun `the shortest and longest allowed lengths are valid`() {
        assertTrue(RelayDeviceId.isValid("abcd")) // 4 = min
        assertTrue(RelayDeviceId.isValid("a".repeat(64))) // 64 = max
    }

    @Test
    fun `all allowed punctuation is accepted`() {
        assertTrue(RelayDeviceId.isValid("A-z_0.9:-"))
    }

    @Test
    fun `too short or too long is rejected`() {
        assertFalse(RelayDeviceId.isValid("abc")) // 3 < min
        assertFalse(RelayDeviceId.isValid("a".repeat(65))) // 65 > max
    }

    @Test
    fun `disallowed characters are rejected`() {
        assertFalse(RelayDeviceId.isValid("has space"))
        assertFalse(RelayDeviceId.isValid("bad/slash"))
        assertFalse(RelayDeviceId.isValid("bad@at"))
    }

    @Test
    fun `null and blank are rejected`() {
        assertFalse(RelayDeviceId.isValid(null))
        assertFalse(RelayDeviceId.isValid(""))
    }

    // ---- resolve (get-or-create / debug-null policy) ----

    @Test
    fun `debug returns null and never mints`() {
        var minted = false
        assertNull(RelayDeviceId.resolve(null, debug = true) { minted = true; "x" })
        assertFalse(minted)
    }

    @Test
    fun `debug returns null even when a valid id is stored`() {
        assertNull(RelayDeviceId.resolve(UUID.randomUUID().toString(), debug = true) { "x" })
    }

    @Test
    fun `a valid stored id is returned as-is and does not mint`() {
        val stored = UUID.randomUUID().toString()
        var minted = false
        assertEquals(stored, RelayDeviceId.resolve(stored, debug = false) { minted = true; "x" })
        assertFalse(minted)
    }

    @Test
    fun `an absent or invalid stored id mints a fresh one`() {
        assertEquals("fresh", RelayDeviceId.resolve(null, debug = false) { "fresh" })
        assertEquals("fresh", RelayDeviceId.resolve("", debug = false) { "fresh" })
        assertEquals("fresh", RelayDeviceId.resolve("bad space", debug = false) { "fresh" })
    }
}
