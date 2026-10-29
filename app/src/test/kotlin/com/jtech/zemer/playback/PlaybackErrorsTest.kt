package com.jtech.zemer.playback

import androidx.media3.common.ParserException
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.upstream.Loader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.EOFException
import java.io.IOException

class PlaybackErrorsTest {
    private val mkvVarint = IOException("Source error", Loader.UnexpectedLoaderException(IllegalStateException("No valid varint length mask found")))

    // Its constructor needs an android.net.Uri (a stub on the JVM), so the exception is allocated without it and only
    // the status set: the classifier reads nothing else.
    private fun http(code: Int): IOException {
        val unsafe = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null)
        val e = unsafe.javaClass.getMethod("allocateInstance", Class::class.java)
            .invoke(unsafe, HttpDataSource.InvalidResponseCodeException::class.java) as HttpDataSource.InvalidResponseCodeException
        HttpDataSource.InvalidResponseCodeException::class.java.getField("responseCode").apply { isAccessible = true }.setInt(e, code)
        return IOException("Source error", e)
    }

    @Test
    fun `the HTTP status is read from anywhere in the cause chain`() {
        assertEquals(403, PlaybackErrors.httpStatus(http(403)))
        assertNull(PlaybackErrors.httpStatus(IOException("down")))
        assertTrue(PlaybackErrors.isExpiredStreamUrl(http(403)))
        assertTrue(PlaybackErrors.isExpiredStreamUrl(http(410)))
        assertFalse(PlaybackErrors.isExpiredStreamUrl(http(404)))
    }

    @Test
    fun `extractor failures on bad bytes count as corrupt media`() {
        assertTrue(PlaybackErrors.isCorruptMedia(mkvVarint))
        assertTrue(PlaybackErrors.isCorruptMedia(IOException("Source error", ParserException.createForMalformedContainer("bad", null))))
        assertTrue(PlaybackErrors.isCorruptMedia(IOException("Source error", EOFException())))
        assertTrue(PlaybackErrors.isCorruptMedia(Loader.UnexpectedLoaderException(ArrayIndexOutOfBoundsException())))
        assertFalse(PlaybackErrors.isCorruptMedia(http(403)))
        // the loader also wraps the resolver's own failures (a bot check): not corrupt bytes
        assertFalse(PlaybackErrors.isCorruptMedia(Loader.UnexpectedLoaderException(RuntimeException("sign in"))))
    }

    @Test
    fun `one refetch per item in a row, then the normal error handling`() {
        val retry = CorruptMediaRetry()
        assertTrue(retry.shouldRetry("a", mkvVarint))
        assertFalse(retry.shouldRetry("a", mkvVarint))
        assertTrue(retry.shouldRetry("b", mkvVarint))
        assertFalse(retry.shouldRetry("c", http(403)))
        // a range past the end of a changed resource is refetched the same way
        assertTrue(retry.shouldRetry("c", http(416)))
        assertFalse(retry.shouldRetry("c", http(416)))
    }
}
