package com.jtech.zemer.net

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import timber.log.Timber
import java.net.SocketException

/**
 * A Zemer server's refusal: HTTP 403 with JSON `{"error":…,"message":…}`, or (the relay) a plain-text message with
 * `X-Zemer-Refusal: <error>`.
 */
object ZemerRefusal {
    const val HEADER = "X-Zemer-Refusal"

    /** The request's signature time was too far off the server clock; resent once with the corrected clock. */
    const val CLOCK_SKEW = "clock_skew"

    private const val MAX_MESSAGE_CHARS = 500
    private val json = Json { ignoreUnknownKeys = true }

    /** [message] is blank when the server sent none. */
    class Refusal(val error: String, val message: String)

    /** Thrown when a resent request is refused for the clock again: handled like any network failure. */
    class ClockSkew : SocketException("refused: clock skew")

    /** One line per clock-skew resend with its answer (a breadcrumb in crash reports). */
    fun logResend(status: Int) = Timber.tag("ZemerRefusal").i("clock skew: resent, answer %d", status)

    /** The refusal this response carries, else null. [body] is read only when needed. */
    fun parse(status: Int, header: String?, contentType: String?, body: () -> String?): Refusal? {
        if (status != 403) return null
        if (!header.isNullOrBlank()) {
            val text = if (contentType?.startsWith("text/plain") == true) body()?.trim()?.take(MAX_MESSAGE_CHARS).orEmpty() else ""
            return Refusal(header.trim().lowercase(), text)
        }
        if (contentType?.contains("json", ignoreCase = true) != true) return null
        val obj = runCatching { json.parseToJsonElement(body() ?: return null) as? JsonObject }.getOrNull() ?: return null
        val error = (obj["error"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return null
        val message = (obj["message"] as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim()?.take(MAX_MESSAGE_CHARS).orEmpty()
        return Refusal(error, message)
    }
}
