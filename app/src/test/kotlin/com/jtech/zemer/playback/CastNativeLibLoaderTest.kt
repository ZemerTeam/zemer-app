package com.jtech.zemer.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the pure metadata for the on-demand FCast native lib: ABI selection and the per-ABI download
 * coordinates / checksums. The actual download + verify needs a device (network + storage), but the
 * choice of which lib to fetch and the integrity anchors are pure and must not silently drift.
 */
class CastNativeLibLoaderTest {

    @Test
    fun `pickAbi follows the device's ABI preference order`() {
        assertEquals("arm64-v8a", CastNativeLib.pickAbi(listOf("arm64-v8a", "armeabi-v7a"))?.abi)
        assertEquals("armeabi-v7a", CastNativeLib.pickAbi(listOf("armeabi-v7a"))?.abi)
        // A 32-bit-only device (armeabi-v7a) must not be handed the arm64 lib.
        assertEquals("armeabi-v7a", CastNativeLib.pickAbi(listOf("armeabi-v7a", "armeabi"))?.abi)
        // Unsupported ABIs listed first fall through to the first one we ship.
        assertEquals("arm64-v8a", CastNativeLib.pickAbi(listOf("x86_64", "x86", "arm64-v8a"))?.abi)
    }

    @Test
    fun `pickAbi returns null when no shipped ABI matches`() {
        assertNull(CastNativeLib.pickAbi(listOf("x86", "x86_64")))
        assertNull(CastNativeLib.pickAbi(emptyList()))
    }

    @Test
    fun `every ABI entry has a 64-char hex sha256 and a zemer-cast release url for its abi`() {
        assertEquals(2, CastNativeLib.ABIS.size)
        CastNativeLib.ABIS.forEach { lib ->
            assertEquals("sha256 must be 64 hex chars for ${lib.abi}", 64, lib.sha256.length)
            assertTrue("sha256 must be lowercase hex", lib.sha256.all { it in "0123456789abcdef" })
            assertTrue(
                "url must point at the zemer-cast sdk release",
                lib.url.startsWith("https://github.com/ZemerTeam/zemer-cast/releases/download/sdk-${CastNativeLib.SDK_VERSION}/"),
            )
            assertTrue("url must end with the abi-tagged asset", lib.url.endsWith("-${lib.abi}.so"))
        }
    }

    @Test
    fun `override property is uniffi's libraryOverride hook for the fcast component`() {
        assertEquals("uniffi.component.fcast_sender_sdk.libraryOverride", CastNativeLib.OVERRIDE_PROPERTY)
    }

    @Test
    fun `downloadProgress is a clamped fraction, and null while the total size is unknown`() {
        // No usable Content-Length (chunked/absent → -1, or a bogus 0): indeterminate, never divide by it.
        assertNull(CastNativeLib.downloadProgress(bytesReceived = 1024, totalBytes = -1))
        assertNull(CastNativeLib.downloadProgress(bytesReceived = 1024, totalBytes = 0))

        assertEquals(0f, CastNativeLib.downloadProgress(0, 100)!!, 0f)
        assertEquals(0.5f, CastNativeLib.downloadProgress(50, 100)!!, 1e-6f)
        assertEquals(1f, CastNativeLib.downloadProgress(100, 100)!!, 0f)

        // A server under-reporting the length must never push the bar past 100%.
        assertEquals(1f, CastNativeLib.downloadProgress(150, 100)!!, 0f)
    }

    @Test
    fun `cacheIsValid only trusts a present lib whose recorded sha matches the expected sha`() {
        val expected = CastNativeLib.ABIS.first().sha256

        // Happy path: present + recorded sha matches the pinned sha (case-insensitive).
        assertTrue(CastNativeLib.cacheIsValid(libExists = true, storedSha = expected, expectedSha = expected))
        assertTrue(CastNativeLib.cacheIsValid(true, expected.uppercase(), expected))

        // Stale after an SDK_VERSION bump: a different expected sha must reject the old cached lib.
        assertFalse(CastNativeLib.cacheIsValid(true, expected, expectedSha = "deadbeef"))

        // Truncated/partial copy: the marker is written only after the file lands, so a missing marker
        // (null storedSha) must force a re-download even though the file exists.
        assertFalse("missing marker must not be trusted", CastNativeLib.cacheIsValid(true, null, expected))

        // No file on disk, or no shipped ABI for this device: never valid.
        assertFalse(CastNativeLib.cacheIsValid(libExists = false, storedSha = expected, expectedSha = expected))
        assertFalse(CastNativeLib.cacheIsValid(true, expected, expectedSha = null))
    }
}
