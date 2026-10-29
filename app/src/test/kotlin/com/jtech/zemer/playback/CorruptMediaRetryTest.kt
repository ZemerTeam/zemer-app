package com.jtech.zemer.playback

import androidx.media3.common.ParserException
import androidx.media3.exoplayer.upstream.Loader
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.EOFException
import java.io.IOException

class CorruptMediaRetryTest {
    private val mkvVarint = IOException("Source error", Loader.UnexpectedLoaderException(IllegalStateException("No valid varint length mask found")))

    @Test
    fun `extractor failures on bad bytes count as corrupt media`() {
        assertTrue(CorruptMediaRetry.isCorruptMedia(mkvVarint))
        assertTrue(CorruptMediaRetry.isCorruptMedia(IOException("Source error", ParserException.createForMalformedContainer("bad", null))))
        assertTrue(CorruptMediaRetry.isCorruptMedia(IOException("Source error", EOFException())))
        assertTrue(CorruptMediaRetry.isCorruptMedia(Loader.UnexpectedLoaderException(ArrayIndexOutOfBoundsException())))
        assertFalse(CorruptMediaRetry.isCorruptMedia(IOException("Response code: 403")))
        // the loader also wraps the resolver's own failures (a bot check): not corrupt bytes
        assertFalse(CorruptMediaRetry.isCorruptMedia(Loader.UnexpectedLoaderException(RuntimeException("sign in"))))
    }

    @Test
    fun `one refetch per item in a row, then the normal error handling`() {
        val retry = CorruptMediaRetry()
        assertTrue(retry.shouldRetry("a", mkvVarint))
        assertFalse(retry.shouldRetry("a", mkvVarint))
        assertTrue(retry.shouldRetry("b", mkvVarint))
        assertFalse(retry.shouldRetry("c", IOException("Response code: 403")))
    }
}
