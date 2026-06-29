package com.jtech.zemer.search

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin HTTP client for the deployed Zemer search service (search.zemer.io). One request shape —
 * `GET /search` — feeds every screen; the caller picks `k` (per-category result cap) to suit the
 * summary, a single filter, or the as-you-type dropdown.
 *
 * Content-type is not assumed: the body is read as text and decoded with a lenient,
 * unknown-key-tolerant [Json], so a missing/odd `Content-Type` or a new server field never breaks it.
 */
/**
 * The lenient reader for every Zemer response. Pulled out of the client so its exact config is
 * unit-testable. `ignoreUnknownKeys` forward-compats new server fields; `isLenient` tolerates an
 * odd/missing content-type; `coerceInputValues` falls an explicit JSON `null` on a non-null defaulted
 * field back to its default — kotlinx applies defaults only for ABSENT keys, so without this a
 * `"categories": null` / `"videos": null` would throw and fail the WHOLE response (the strict-
 * deserialization "No results" trap), instead of degrading gracefully.
 */
internal val zemerResponseJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
}

@Singleton
class ZemerSearchClient @Inject constructor() {

    private val client = HttpClient(CIO) {
        install(HttpTimeout) {
            requestTimeoutMillis = REQUEST_TIMEOUT_MS
            connectTimeoutMillis = CONNECT_TIMEOUT_MS
        }
    }

    suspend fun search(
        query: String,
        allowFemale: Boolean,
        blockVideos: Boolean,
        k: Int,
    ): ZemerSearchResponse {
        val response: HttpResponse = client.get("$BASE_URL/search") {
            parameter("q", query)
            parameter("allowFemale", if (allowFemale) "1" else "0")
            if (blockVideos) parameter("blockVideos", "1")
            parameter("k", k)
            // The Community chip asks for a large k (a few hundred rows); give that heavier response more
            // headroom than the default ceiling, while the as-you-type / filter calls keep the tighter
            // default so a genuinely hung request still fails fast.
            if (k > LARGE_REQUEST_K) timeout { requestTimeoutMillis = LARGE_REQUEST_TIMEOUT_MS }
        }
        // CIO does not throw on non-2xx; guard so an error page (HTML/5xx) is a clean failure rather
        // than a confusing JSON parse error fed from the error body.
        if (!response.status.isSuccess()) {
            throw IOException("Zemer search returned HTTP ${response.status.value}")
        }
        return zemerResponseJson.decodeFromString(ZemerSearchResponse.serializer(), response.bodyAsText())
    }

    companion object {
        const val BASE_URL = "https://search.zemer.io"
        private const val REQUEST_TIMEOUT_MS = 8_000L
        private const val CONNECT_TIMEOUT_MS = 5_000L
        // Requests above this k (the Community chip's K_COMMUNITY) get the larger ceiling below.
        private const val LARGE_REQUEST_K = 100
        private const val LARGE_REQUEST_TIMEOUT_MS = 20_000L
    }
}
