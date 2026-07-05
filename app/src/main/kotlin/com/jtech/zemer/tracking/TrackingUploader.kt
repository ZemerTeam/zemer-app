package com.jtech.zemer.tracking

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType

/**
 * One POST of one batch to `tracking.zemer.io/v1/events`, mapped to the spec's retry semantics:
 * 2xx → [Result.Success]; 400 → [Result.DropBatch] (malformed — never poison-pill the queue);
 * 429 → [Result.RateLimited] (wait ≥ 2 min); anything else (5xx, network) → [Result.Retry] with
 * backoff. Fire-and-forget by contract: callers never surface failures to the user.
 */
internal class TrackingUploader(private val baseUrl: String = BASE_URL) {

    sealed interface Result {
        data object Success : Result
        data object DropBatch : Result
        data object RateLimited : Result
        data object Retry : Result
    }

    private val client = HttpClient(CIO) {
        install(HttpTimeout) {
            requestTimeoutMillis = REQUEST_TIMEOUT_MS
            connectTimeoutMillis = CONNECT_TIMEOUT_MS
        }
        expectSuccess = false
    }

    suspend fun upload(device: String, appVer: String, debug: Boolean, eventLines: List<String>): Result =
        runCatching {
            val response = client.post("$baseUrl/v1/events") {
                contentType(ContentType.Application.Json)
                setBody(trackingBatchBody(device, appVer, debug, eventLines))
            }
            when {
                response.status.value in 200..299 -> Result.Success
                response.status == HttpStatusCode.BadRequest -> Result.DropBatch
                response.status == HttpStatusCode.TooManyRequests -> Result.RateLimited
                else -> Result.Retry
            }
        }.getOrDefault(Result.Retry)

    companion object {
        const val BASE_URL = "https://tracking.zemer.io"
        private const val REQUEST_TIMEOUT_MS = 10_000L
        private const val CONNECT_TIMEOUT_MS = 5_000L
    }
}

/**
 * The retry-delay ladder for failed flushes (pure, unit-tested): consecutive failures back off
 * 30 s → 2 min → 10 min; a 429 always waits at least 2 min; success resets the counter.
 */
internal fun trackingRetryDelayMs(consecutiveFailures: Int, rateLimited: Boolean): Long {
    val backoff = when {
        consecutiveFailures <= 1 -> 30_000L
        consecutiveFailures == 2 -> 120_000L
        else -> 600_000L
    }
    return if (rateLimited) maxOf(backoff, 120_000L) else backoff
}
