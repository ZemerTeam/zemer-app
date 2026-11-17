package com.jtech.zemer.playback

import androidx.media3.common.ParserException
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.upstream.Loader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import androidx.media3.datasource.FileDataSource
import java.io.EOFException
import java.io.FileNotFoundException
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

    @Test
    fun `a later play of a refetched item is refetched again, a repeat of it is not`() {
        val retry = CorruptMediaRetry()
        assertTrue(retry.shouldRetry("a", http(416)))
        // still on the same item (a repeat, a rendition swap): the limit holds
        retry.onItemChanged("a")
        assertFalse(retry.shouldRetry("a", http(416)))
        // another item played in between
        retry.onItemChanged("b")
        retry.onItemChanged("a")
        assertTrue(retry.shouldRetry("a", http(416)))
    }

    @Test
    fun `a read that ran off the cached spans is refetched once`() {
        val hole = IOException(
            "Source error",
            FileDataSource.FileDataSourceException(FileNotFoundException("zMCnjU3P4Is: open failed: ENOENT"), 2005),
        )
        assertTrue(PlaybackErrors.isCacheHole(hole))
        assertFalse(PlaybackErrors.isCacheHole(IOException("Source error", FileNotFoundException("gone"))))
        assertFalse(PlaybackErrors.isCacheHole(http(403)))
        val retry = CorruptMediaRetry()
        assertTrue(retry.shouldRetry("a", hole))
        assertFalse(retry.shouldRetry("a", hole))
    }

    @Test
    fun `bad bytes, a stale range and a cache hole are what a refetch fixes`() {
        val hole = IOException("Source error", FileDataSource.FileDataSourceException(FileNotFoundException("x"), 2005))
        assertTrue(PlaybackErrors.refetchFixes(mkvVarint))
        assertTrue(PlaybackErrors.refetchFixes(IOException("Source error", ParserException.createForMalformedContainer("bad", null))))
        assertTrue(PlaybackErrors.refetchFixes(http(416)))
        assertTrue(PlaybackErrors.refetchFixes(hole))
        // an expired URL is re-resolved, not refetched; a plain network failure is neither
        assertFalse(PlaybackErrors.refetchFixes(http(403)))
        assertFalse(PlaybackErrors.refetchFixes(IOException("down")))
    }

    @Test
    fun `the service reports what a refetch fixes, outside RELAY`() {
        assertTrue(PlaybackErrors.serviceReports(mkvVarint, relayMode = false))
        // the cold-start window (mode not read yet) is not RELAY
        assertTrue(PlaybackErrors.serviceReports(mkvVarint, relayMode = null))
        // RELAY: PlayerConnection already reports every error
        assertFalse(PlaybackErrors.serviceReports(mkvVarint, relayMode = true))
        // anything else is PlayerConnection's to report
        assertFalse(PlaybackErrors.serviceReports(http(403), relayMode = false))
    }

    @Test
    fun `only I-O and parsing error codes are source errors`() {
        // media3's ERROR_CODE_IO_UNSPECIFIED (an extractor's IOException, e.g. "No ogg page can be found.")
        assertTrue(PlaybackErrors.isSourceErrorCode(2000))
        assertTrue(PlaybackErrors.isSourceErrorCode(2005)) // IO_FILE_NOT_FOUND
        assertTrue(PlaybackErrors.isSourceErrorCode(3001)) // PARSING_CONTAINER_MALFORMED
        assertFalse(PlaybackErrors.isSourceErrorCode(1000)) // UNSPECIFIED
        assertFalse(PlaybackErrors.isSourceErrorCode(4001)) // DECODER_INIT_FAILED
        assertFalse(PlaybackErrors.isSourceErrorCode(5001)) // AUDIO_TRACK_INIT_FAILED
    }
}
