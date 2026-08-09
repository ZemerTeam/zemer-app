package com.jtech.zemer.playback.relay

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The pure RELAY download decisions (extracted from MediaStoreDownloadManager so they are testable without
 * Robolectric): container -> MediaStore-friendly extension, and non-2xx status -> handling.
 */
class RelayDownloadTest {

    private fun magic(vararg bytes: Int): Pair<ByteArray, Int> {
        // Emulate a 12-byte read buffer with only the given leading bytes populated.
        val buf = ByteArray(12)
        bytes.forEachIndexed { i, b -> buf[i] = b.toByte() }
        return buf to bytes.size
    }

    @Test
    fun `WebM EBML header maps to opus`() {
        val (head, n) = magic(0x1A, 0x45, 0xDF, 0xA3, 0x01, 0x02)
        assertEquals("opus", RelayDownload.audioExtensionFromMagic(head, n))
    }

    @Test
    fun `MP4 ftyp maps to m4a`() {
        // bytes 4..7 == "ftyp"
        val (head, n) = magic(0x00, 0x00, 0x00, 0x20, 'f'.code, 't'.code, 'y'.code, 'p'.code)
        assertEquals("m4a", RelayDownload.audioExtensionFromMagic(head, n))
    }

    @Test
    fun `Ogg header maps to opus`() {
        val (head, n) = magic('O'.code, 'g'.code, 'g'.code, 'S'.code)
        assertEquals("opus", RelayDownload.audioExtensionFromMagic(head, n))
    }

    @Test
    fun `unknown or too-short magic falls back to the default extension`() {
        val (head, n) = magic(0x00, 0x11)
        assertEquals(RelayDownload.DEFAULT_AUDIO_EXTENSION, RelayDownload.audioExtensionFromMagic(head, n))
        assertEquals("opus", RelayDownload.audioExtensionFromMagic(ByteArray(12), 0))
    }

    @Test
    fun `relay 404 is unavailable (fail fast)`() {
        assertEquals(RelayDownload.HttpErrorAction.UNAVAILABLE, RelayDownload.classifyHttpError(isRelay = true, code = 404))
    }

    @Test
    fun `relay 502 and 503 are transient (retry with message)`() {
        assertEquals(RelayDownload.HttpErrorAction.TRANSIENT, RelayDownload.classifyHttpError(isRelay = true, code = 502))
        assertEquals(RelayDownload.HttpErrorAction.TRANSIENT, RelayDownload.classifyHttpError(isRelay = true, code = 503))
    }

    @Test
    fun `other relay errors are generic`() {
        assertEquals(RelayDownload.HttpErrorAction.GENERIC, RelayDownload.classifyHttpError(isRelay = true, code = 500))
        assertEquals(RelayDownload.HttpErrorAction.GENERIC, RelayDownload.classifyHttpError(isRelay = true, code = 403))
    }

    @Test
    fun `non-relay errors are always generic (DIRECT path unchanged)`() {
        assertEquals(RelayDownload.HttpErrorAction.GENERIC, RelayDownload.classifyHttpError(isRelay = false, code = 404))
        assertEquals(RelayDownload.HttpErrorAction.GENERIC, RelayDownload.classifyHttpError(isRelay = false, code = 502))
    }
}
