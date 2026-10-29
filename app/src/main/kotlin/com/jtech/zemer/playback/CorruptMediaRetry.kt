package com.jtech.zemer.playback

import androidx.media3.common.ParserException
import androidx.media3.exoplayer.upstream.Loader
import java.io.EOFException

/**
 * A stream whose bytes do not parse (a torn cache span, a truncated or garbled response) is re-fetched once, from
 * scratch, before the track is failed: the extractor cannot recover from bad bytes in place, but a fresh fetch
 * almost always plays. [shouldRetry] allows one retry per item in a row, so a source that is really broken still
 * reaches the normal error handling instead of looping.
 */
class CorruptMediaRetry {
    private var lastRetriedMediaId: String? = null

    fun shouldRetry(mediaId: String, error: Throwable): Boolean {
        if (!isCorruptMedia(error) || mediaId == lastRetriedMediaId) return false
        lastRetriedMediaId = mediaId
        return true
    }

    companion object {
        /**
         * The extractor rejected or choked on the bytes (MKV varint, peek-buffer overrun, unknown container, early end).
         * Only an extractor-style runtime error counts inside a loader wrapper: the loader also wraps the stream
         * resolver's own failures (a bot check, an unplayable id), which a refetch would not fix.
         */
        fun isCorruptMedia(error: Throwable): Boolean =
            generateSequence(error) { it.cause }.take(10).any {
                it is ParserException || it is EOFException ||
                    (it is Loader.UnexpectedLoaderException &&
                        (it.cause is IllegalStateException || it.cause is IndexOutOfBoundsException))
            }
    }
}
