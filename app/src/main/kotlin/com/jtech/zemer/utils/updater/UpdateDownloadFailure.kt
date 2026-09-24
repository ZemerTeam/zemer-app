package com.jtech.zemer.utils.updater

import java.io.FileNotFoundException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * Why an update download failed, at the granularity the dialog can act on. The raw exception
 * message (a Ktor timeout carries the full CDN URL) is logged, never shown.
 */
enum class UpdateDownloadFailure { TIMEOUT, NETWORK, STORAGE, CORRUPT_ARTIFACT, UNKNOWN }

/**
 * The downloaded artifact was not the build that was announced (a nightly whose size or SHA-256
 * does not match the mirror's record). A dedicated type so the classifier can report
 * [UpdateDownloadFailure.CORRUPT_ARTIFACT] without swallowing every unrelated
 * [IllegalStateException] as a corrupt download.
 */
class CorruptUpdateArtifactException(message: String) : Exception(message)

/** Classifies [error] by walking its cause chain; the most specific class wins. */
fun classifyUpdateDownloadFailure(error: Throwable): UpdateDownloadFailure {
    val chain = generateSequence(error) { it.cause?.takeIf { cause -> cause !== it } }.take(8).toList()
    return when {
        chain.any { it.isTimeout() } -> UpdateDownloadFailure.TIMEOUT
        chain.any { it.isStorage() } -> UpdateDownloadFailure.STORAGE
        // Only the dedicated type - never a bare IllegalStateException, which is a common runtime
        // type an unrelated failure would carry.
        chain.any { it is CorruptUpdateArtifactException } -> UpdateDownloadFailure.CORRUPT_ARTIFACT
        chain.any { it.isNetwork() } -> UpdateDownloadFailure.NETWORK
        else -> UpdateDownloadFailure.UNKNOWN
    }
}

private fun Throwable.isTimeout(): Boolean =
    this is SocketTimeoutException ||
        // Ktor's HttpRequestTimeoutException / ConnectTimeoutException / SocketTimeoutException,
        // matched by name so this stays engine-agnostic.
        this::class.simpleName?.contains("Timeout") == true

private fun Throwable.isStorage(): Boolean =
    this is FileNotFoundException ||
        message?.contains("ENOSPC") == true ||
        message?.contains("No space left", ignoreCase = true) == true

private fun Throwable.isNetwork(): Boolean =
    this is UnknownHostException || this is ConnectException || this is SSLException || this is IOException
