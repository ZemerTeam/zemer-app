package com.jtech.zemer.sync

import com.jtech.zemer.BuildConfig
import com.jtech.zemer.sync.models.DeviceContentFilters
import com.jtech.zemer.sync.models.DevicePreferencesEntity
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.encodeURLPathPart
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A non-2xx answer from the content server; [status] is the HTTP code (401 = bad/expired token).
 * [challenge] = Cloudflare answered with a bot challenge (`cf-mitigated: challenge`), not the origin.
 */
class ContentServerException(
    val status: Int,
    message: String,
    val challenge: Boolean = false,
    /** The server's `Retry-After` (seconds) on a 429, when sent. */
    val retryAfterSec: Long? = null,
) : IOException(message)

internal fun HttpResponse.isCloudflareChallenge(): Boolean = headers["cf-mitigated"].equals("challenge", ignoreCase = true)

/** `GET /prefs/device/<deviceId>`: the device's filters plus the owning account's email (the restore label). */
@Serializable
data class DeviceRestore(
    // Required: a 200 without filters (e.g. a proxy's JSON error page) must fail, never restore defaults.
    val contentFilters: DeviceContentFilters,
    val userEmail: String? = null,
)

/**
 * The content server's ([BuildConfig.CONTENT_MIRROR_URL], content.zemer.io) write + account endpoints:
 * content reports and the synced content-filter preferences. Account calls carry the current user's
 * Firebase ID token. Every non-2xx throws [ContentServerException] except the documented 404s, which
 * return null.
 */
@Singleton
class ContentServerApi(
    baseUrl: String,
    private val client: HttpClient,
) {
    @Inject
    constructor() : this(BuildConfig.CONTENT_MIRROR_URL, defaultClient())

    private val baseUrl = baseUrl.trimEnd('/')

    /** `POST /reports`; returns the server's report id. */
    suspend fun submitReport(payload: JsonObject): String {
        val response = client.post("$baseUrl/reports") { jsonBody(payload.toString()) }
        val body = response.checked("/reports")
        return json.decodeFromString(JsonObject.serializer(), body)["id"]?.jsonPrimitive?.content.orEmpty()
    }

    /** `GET /prefs` for the token's user, or null when they have none yet. */
    suspend fun getPrefs(idToken: String): DevicePreferencesEntity? {
        val response = client.get("$baseUrl/prefs") { bearerAuth(idToken) }
        if (response.status == HttpStatusCode.NotFound) return null
        return json.decodeFromString(DevicePreferencesEntity.serializer(), response.checked("/prefs"))
    }

    /** `PUT /prefs`: replaces the token's user document (the server forces `userId` to the token uid). */
    suspend fun putPrefs(idToken: String, doc: DevicePreferencesEntity) {
        client.put("$baseUrl/prefs") {
            bearerAuth(idToken)
            jsonBody(json.encodeToString(DevicePreferencesEntity.serializer(), doc))
        }.checked("/prefs")
    }

    /** `GET /prefs/device/<deviceId>` (no auth), or null when no account lists this device. */
    suspend fun devicePrefs(deviceId: String): DeviceRestore? {
        val response = client.get("$baseUrl/prefs/device/${deviceId.encodeURLPathPart()}")
        if (response.status == HttpStatusCode.NotFound) return null
        return json.decodeFromString(DeviceRestore.serializer(), response.checked("/prefs/device"))
    }

    private fun HttpRequestBuilder.jsonBody(text: String) {
        contentType(ContentType.Application.Json)
        setBody(text)
    }

    private suspend fun HttpResponse.checked(path: String): String {
        val body = bodyAsText()
        if (!status.isSuccess()) {
            throw ContentServerException(
                status.value,
                "content server $path -> HTTP ${status.value}: ${body.take(200)}",
                isCloudflareChallenge(),
                headers["Retry-After"]?.trim()?.toLongOrNull(),
            )
        }
        return body
    }

    companion object {
        // encodeDefaults + explicit nulls: send every field like the Firestore set() of the same entity did
        // (e.g. a null femalePasscodeHash / firstSeen goes out as null, not omitted). Lenient reads.
        internal val json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            encodeDefaults = true
        }

        private fun defaultClient() = HttpClient(CIO) {
            install(HttpTimeout) {
                connectTimeoutMillis = 5_000
                requestTimeoutMillis = 10_000
            }
        }
    }
}

/**
 * The wait before retrying after [failure]: [backoffMs], or the server's Retry-After when longer,
 * never more than [MAX_RETRY_AFTER_MS] (a bogus header must not park the caller for hours).
 */
internal fun retryDelayMs(backoffMs: Long, failure: Throwable?): Long {
    val retryAfterMs = ((failure as? ContentServerException)?.retryAfterSec ?: 0L).coerceIn(0L, MAX_RETRY_AFTER_MS / 1000) * 1000
    return maxOf(backoffMs, retryAfterMs)
}

private const val MAX_RETRY_AFTER_MS = 10 * 60_000L

/** Backoff between retries of a write the server could not take (prefs upload, queued report): 5s doubling, capped at 5 minutes. */
internal fun writeRetryDelayMs(attempt: Int): Long = minOf(5_000L shl attempt.coerceIn(0, 6), 300_000L)
