package com.jtech.zemer.net

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.SocketException

/**
 * The servers' "this copy is not official" answer: HTTP 403 with JSON `{"error":"unofficial_copy","message":…}`, or
 * (the relay) a plain-text message with `X-Zemer-Refusal: unofficial_copy`. Two such answers in a row from Zemer hosts
 * set [message], which the activity shows full screen; from then on [ZemerHeaders] refuses every Zemer-host request
 * locally ([Refused]) for the rest of this process, so background syncs and retries stop reaching the servers.
 *
 * Kept in memory only: every launch checks again, so a server-side mistake can never lock an install for good. Any
 * other refusal (`update_required`, …) and any other error never counts.
 */
object UnofficialCopy {
    const val ERROR = "unofficial_copy"
    const val SITE_URL = "https://zemer.io"
    private const val ANSWERS_TO_SHOW = 2
    private var inARow = 0
    private val _message = MutableStateFlow<String?>(null)

    /** Non-null once the copy has been refused: the text to show, blank when the server sent none (show the built-in one). */
    val message: StateFlow<String?> = _message.asStateFlow()

    val refused: Boolean get() = _message.value != null

    /** Thrown for a Zemer-host request after the copy was refused: no network is used. */
    class Refused : SocketException("refused locally: unofficial copy")

    /**
     * One Zemer-host response. [body] is read only for a 403 that may carry the answer. A clock-skew refusal is
     * neither counted nor resets the count: the request is resent and that answer counts.
     */
    @Synchronized
    fun onResponse(status: Int, refusalHeader: String?, contentType: String?, body: () -> String?) {
        if (refused) return
        val refusal = ZemerRefusal.parse(status, refusalHeader, contentType, body)
        if (refusal?.error == ZemerRefusal.CLOCK_SKEW) return
        if (refusal?.error != ERROR) {
            inARow = 0
            return
        }
        if (++inARow >= ANSWERS_TO_SHOW) _message.value = refusal.message
    }

    /** The answer's message (blank = none given) when this response is an unofficial-copy refusal, else null. */
    fun unofficialMessage(status: Int, refusalHeader: String?, contentType: String?, body: () -> String?): String? =
        ZemerRefusal.parse(status, refusalHeader, contentType, body)?.takeIf { it.error == ERROR }?.message

    /** Tests only. */
    @Synchronized
    internal fun reset() {
        inARow = 0
        _message.value = null
    }
}
