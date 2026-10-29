package com.jtech.zemer.playback

import androidx.media3.common.ParserException
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.upstream.Loader
import java.io.EOFException

/** Pure classification of player errors, shared by [MusicService]'s recovery and [PlayerConnection]'s reporting. */
object PlaybackErrors {
    /** The HTTP status in [error]'s cause chain, or null when no HTTP response caused it. */
    fun httpStatus(error: Throwable): Int? =
        generateSequence(error) { it.cause }.take(MAX_CAUSES)
            .filterIsInstance<HttpDataSource.InvalidResponseCodeException>()
            .firstOrNull()?.responseCode

    /** HTTP 403/410: an expired or address-bound stream URL, which [MusicService] re-resolves itself. */
    fun isExpiredStreamUrl(error: Throwable): Boolean = httpStatus(error).let { it == 403 || it == 410 }

    /**
     * The extractor rejected or choked on the bytes (MKV varint, peek-buffer overrun, unknown container, early end).
     * Only an extractor-style runtime error counts inside a loader wrapper: the loader also wraps the stream
     * resolver's own failures (a bot check, an unplayable id), which a refetch would not fix.
     */
    fun isCorruptMedia(error: Throwable): Boolean =
        generateSequence(error) { it.cause }.take(MAX_CAUSES).any {
            it is ParserException || it is EOFException ||
                (it is Loader.UnexpectedLoaderException &&
                    (it.cause is IllegalStateException || it.cause is IndexOutOfBoundsException))
        }

    /**
     * HTTP 416: a range at or past the end of the resource, i.e. cached spans or a resume position that no longer
     * match the stream (a re-resolved URL for another format/size). Fixed the same way as corrupt bytes.
     */
    fun isStaleRange(error: Throwable): Boolean = httpStatus(error) == 416

    private const val MAX_CAUSES = 10
}

/**
 * A stream whose bytes do not parse (a torn cache span, a truncated or garbled response) is re-fetched once, from
 * scratch, before the track is failed: the extractor cannot recover from bad bytes in place, but a fresh fetch
 * almost always plays. [shouldRetry] allows one retry per item in a row, so a source that is really broken still
 * reaches the normal error handling instead of looping.
 */
class CorruptMediaRetry {
    private var lastRetriedMediaId: String? = null

    fun shouldRetry(mediaId: String, error: Throwable): Boolean {
        val refetchFixes = PlaybackErrors.isCorruptMedia(error) || PlaybackErrors.isStaleRange(error)
        if (!refetchFixes || mediaId == lastRetriedMediaId) return false
        lastRetriedMediaId = mediaId
        return true
    }
}
