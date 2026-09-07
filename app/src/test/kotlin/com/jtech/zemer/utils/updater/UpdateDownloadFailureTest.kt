package com.jtech.zemer.utils.updater

import io.ktor.client.plugins.HttpRequestTimeoutException
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.FileNotFoundException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.zip.ZipException

class UpdateDownloadFailureTest {

    @Test
    fun `ktor request timeout is a timeout`() {
        val e = HttpRequestTimeoutException("https://nightly.link/x", 15_000L)
        assertEquals(UpdateDownloadFailure.TIMEOUT, classifyUpdateDownloadFailure(e))
    }

    @Test
    fun `socket timeout wrapped in an io exception is a timeout`() {
        val e = IOException("wrapped", SocketTimeoutException("read timed out"))
        assertEquals(UpdateDownloadFailure.TIMEOUT, classifyUpdateDownloadFailure(e))
    }

    @Test
    fun `dns and generic io failures are network`() {
        assertEquals(UpdateDownloadFailure.NETWORK, classifyUpdateDownloadFailure(UnknownHostException("nightly.link")))
        assertEquals(UpdateDownloadFailure.NETWORK, classifyUpdateDownloadFailure(IOException("connection reset")))
    }

    @Test
    fun `full disk and missing cache dir are storage`() {
        assertEquals(UpdateDownloadFailure.STORAGE, classifyUpdateDownloadFailure(IOException("write failed: ENOSPC (No space left on device)")))
        assertEquals(UpdateDownloadFailure.STORAGE, classifyUpdateDownloadFailure(FileNotFoundException("/cache/zemer-update.apk")))
    }

    @Test
    fun `broken artifact is corrupt`() {
        assertEquals(UpdateDownloadFailure.CORRUPT_ARTIFACT, classifyUpdateDownloadFailure(ZipException("not a zip")))
        assertEquals(UpdateDownloadFailure.CORRUPT_ARTIFACT, classifyUpdateDownloadFailure(IllegalStateException("No APK found in the nightly archive")))
    }

    @Test
    fun `anything else is unknown`() {
        assertEquals(UpdateDownloadFailure.UNKNOWN, classifyUpdateDownloadFailure(RuntimeException("?")))
    }
}
