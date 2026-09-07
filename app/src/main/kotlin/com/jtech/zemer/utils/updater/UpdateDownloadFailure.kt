package com.jtech.zemer.utils.updater

import java.io.FileNotFoundException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.zip.ZipException
import javax.net.ssl.SSLException

/**
 * Why an update download failed, at the granularity the dialog can act on. The raw exception
 * message (a Ktor timeout carries the full CDN URL) is logged, never shown.
 */
enum class UpdateDownloadFailure { TIMEOUT, NETWORK, STORAGE, CORRUPT_ARTIFACT, UNKNOWN }

/** Classifies [error] by walking its cause chain; the most specific class wins. */
fun classifyUpdateDownloadFailure(error: Throwable): UpdateDownloadFailure {
    val chain = generateSequence(error) { it.cause?.takeIf { cause -> cause !== it } }.take(8).toList()
    return when {
        chain.any { it.isTimeout() } -> UpdateDownloadFailure.TIMEOUT
        chain.any { it.isStorage() } -> UpdateDownloadFailure.STORAGE
        chain.any { it is ZipException || it is IllegalStateException } -> UpdateDownloadFailure.CORRUPT_ARTIFACT
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
