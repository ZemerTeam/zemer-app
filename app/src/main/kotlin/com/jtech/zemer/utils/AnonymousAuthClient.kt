package com.jtech.zemer.utils

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout

/**
 * HTTP client for the anonymous ("pooled account") credential fetch used by the login gate and the
 * Account settings screen.
 *
 * A bare `HttpClient()` relies on the engine's short default timeout (~5-15 s), which fails on slow
 * or spotty connections — issue #284: the anonymous login "times out too soon" for a user on poor
 * service. This installs deliberately generous timeouts so the one-shot credential GET has time to
 * complete before giving up. The credential endpoint returns a small JSON payload, so a long ceiling
 * costs nothing on a healthy connection (the request finishes as soon as the body arrives) while
 * giving a weak connection room to succeed.
 */
object AnonymousAuthClient {
    /** Time allowed to establish the TCP/TLS connection. */
    const val CONNECT_TIMEOUT_MS = 30_000L

    /** Overall ceiling for the whole request/response. */
    const val REQUEST_TIMEOUT_MS = 60_000L

    /** Max idle time between data packets once connected. */
    const val SOCKET_TIMEOUT_MS = 60_000L

    fun create(): HttpClient = HttpClient {
        install(HttpTimeout) {
            connectTimeoutMillis = CONNECT_TIMEOUT_MS
            requestTimeoutMillis = REQUEST_TIMEOUT_MS
            socketTimeoutMillis = SOCKET_TIMEOUT_MS
        }
    }
}
