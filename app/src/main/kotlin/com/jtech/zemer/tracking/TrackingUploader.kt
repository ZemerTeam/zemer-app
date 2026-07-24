package com.jtech.zemer.tracking

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import timber.log.Timber

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
                response.status.value in 200..299 -> {
                    reportCounters(parseTrackingUploadCounters(response.bodyAsText()))
                    Result.Success
                }
                response.status == HttpStatusCode.BadRequest -> Result.DropBatch
                response.status == HttpStatusCode.TooManyRequests -> Result.RateLimited
                else -> Result.Retry
            }
        }.getOrDefault(Result.Retry)

    /**
     * `impressionsDropped` means the POST carried more impression rows than the server stores per
     * batch — silent data loss that we asked to be made visible precisely so it can't hide. It is
     * not an error and nothing retries on it: the fix is to send smaller batches, or to ask for the
     * ceiling to be raised, and neither decision belongs in a fire-and-forget upload path.
     */
    private fun reportCounters(counters: TrackingUploadCounters) {
        if (counters.impressionsDropped > 0) {
            Timber.tag(TAG).w(
                "Server dropped ${counters.impressionsDropped} impression row(s) — batch exceeded " +
                    "the per-POST cap (accepted ${counters.accepted})",
            )
        }
    }

    companion object {
        const val BASE_URL = "https://tracking.zemer.io"
        private const val TAG = "Zemer_Tracker"
        private const val REQUEST_TIMEOUT_MS = 10_000L
        private const val CONNECT_TIMEOUT_MS = 5_000L
    }
}

/**
 * The counters a 200 response carries: [accepted] rows actually stored, and [impressionsDropped]
 * impression rows refused by the per-POST cap (present only when truncation happened). Debug builds
 * run the real ingest inside a rolled-back transaction, so both are truthful there too — impression
 * batching can be validated on a debug device without a release build pointed at production.
 */
internal data class TrackingUploadCounters(val accepted: Int?, val impressionsDropped: Int)

private val LENIENT_JSON = Json { ignoreUnknownKeys = true; isLenient = true }

/**
 * Never throws and never affects the upload result: the counters are diagnostics, so an unparseable
 * body (proxy error page, future response shape, empty 204) degrades to "nothing to report".
 */
internal fun parseTrackingUploadCounters(body: String): TrackingUploadCounters = runCatching {
    val obj = LENIENT_JSON.parseToJsonElement(body).jsonObject
    TrackingUploadCounters(
        accepted = obj["accepted"]?.jsonPrimitive?.content?.toIntOrNull(),
        impressionsDropped = obj["impressionsDropped"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
    )
}.getOrDefault(TrackingUploadCounters(accepted = null, impressionsDropped = 0))

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
