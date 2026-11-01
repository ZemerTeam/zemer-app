package com.jtech.zemer.net

import com.jtech.zemer.BuildConfig
import com.jtech.zemer.sig.ZemerSig
import io.ktor.client.plugins.api.SendingRequest
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.http.content.OutgoingContent
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.Response
import okio.Buffer

/**
 * The one hook every client to a Zemer server installs: on a Zemer-host request ([ZemerHosts]) it adds the
 * build's versionCode ([BUILD_HEADER]), the environment signals when any is set ([EnvSignals]) and the request
 * signature (builds with a signing key only), and it takes the server clock from the response's `Date` so the
 * signature time is right on a device with a wrong clock. Every HOP is judged on its own: a redirect is re-signed
 * for its own path when it stays on a Zemer host and carries none of these headers when it leaves one. Requests to any other host pass through untouched. Nothing here can
 * fail a request.
 */
object ZemerHeaders {
    const val BUILD_HEADER = "X-Zemer-Build"
    private val build = BuildConfig.VERSION_CODE.toString()

    /** Everything this hook adds, all removed on a hop to any other host. */
    private val OWN_HEADERS = listOf(ZemerSig.HEADER, BUILD_HEADER, EnvSignals.HEADER)

    /**
     * Ktor clients. Signs at [SendingRequest], so the URL is final (query parameters included); Ktor's redirect
     * handling re-runs it for each hop with the previous hop's headers copied, so the headers are always replaced
     * (never appended) and removed for a non-Zemer hop. The body is the final content: a byte or text body is
     * signed with version 2, any other kind (a stream) with version 1.
     */
    val KtorPlugin = createClientPlugin("ZemerHeaders") {
        on(SendingRequest) { request, content ->
            OWN_HEADERS.forEach(request.headers::remove)
            val url = request.url.build()
            if (!ZemerHosts.isZemerHost(url.host)) return@on
            request.headers[BUILD_HEADER] = build
            EnvSignals.mask.takeIf { it != 0 }?.let { request.headers[EnvSignals.HEADER] = it.toString() }
            val body = when (content) {
                is OutgoingContent.ByteArrayContent -> content.bytes()
                is OutgoingContent.NoContent -> ByteArray(0)
                else -> null
            }
            ZemerSig.headerFor(request.method.value, url.encodedPath, url.encodedQuery.takeIf { it.isNotEmpty() }, body)
                ?.let { request.headers[ZemerSig.HEADER] = it }
        }
        onResponse { response ->
            if (ZemerHosts.isZemerHost(response.call.request.url.host)) ZemerSig.onServerDate(response.headers["Date"])
        }
    }

    /**
     * OkHttp clients (relay playback, relay downloads, images): install with [withZemerHeaders], never as an
     * application interceptor - only a NETWORK interceptor sees every redirect hop (each playback range is signed
     * on its own too).
     */
    private object OkHttpNetworkInterceptor : okhttp3.Interceptor {
        override fun intercept(chain: okhttp3.Interceptor.Chain): Response {
            val request = chain.request()
            if (!ZemerHosts.isZemerHost(request.url.host)) {
                if (OWN_HEADERS.none { request.header(it) != null }) return chain.proceed(request)
                return chain.proceed(request.newBuilder().apply { OWN_HEADERS.forEach(::removeHeader) }.build())
            }
            val builder = request.newBuilder().header(BUILD_HEADER, build)
            EnvSignals.mask.takeIf { it != 0 }?.let { builder.header(EnvSignals.HEADER, it.toString()) }
            ZemerSig.headerFor(request.method, request.url.encodedPath, request.url.encodedQuery, bodyBytes(request.body))
                ?.let { builder.header(ZemerSig.HEADER, it) }
            return chain.proceed(builder.build()).also { ZemerSig.onServerDate(it.header("Date")) }
        }

        /** The bytes a body writes (none = empty); null for a body that can be written only once (signed version 1). */
        private fun bodyBytes(body: RequestBody?): ByteArray? = when {
            body == null -> ByteArray(0)
            body.isOneShot() || body.isDuplex() -> null
            else -> runCatching { Buffer().also(body::writeTo).readByteArray() }.getOrNull()
        }
    }

    /** Adds the hook to an OkHttp client as a network interceptor (per redirect hop). */
    fun OkHttpClient.Builder.withZemerHeaders(): OkHttpClient.Builder = addNetworkInterceptor(OkHttpNetworkInterceptor)
}
