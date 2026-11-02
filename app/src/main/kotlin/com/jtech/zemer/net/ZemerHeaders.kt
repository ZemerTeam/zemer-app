package com.jtech.zemer.net

import com.jtech.zemer.BuildConfig
import com.jtech.zemer.sig.InstallRegistration
import com.jtech.zemer.sig.ZemerSig
import io.ktor.client.call.HttpClientCall
import io.ktor.client.plugins.api.Send
import io.ktor.client.plugins.api.SendingRequest
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.isSaved
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.statement.bodyAsText
import io.ktor.http.content.OutgoingContent
import kotlinx.coroutines.cancel
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.Response
import okio.Buffer

/**
 * The one hook every client to a Zemer server installs: on a Zemer-host request ([ZemerHosts]) it adds the
 * build's versionCode ([BUILD_HEADER]), the environment signals when any is set ([EnvSignals]) and the request
 * signature (builds with a signing key only), and it takes the server clock from the response's `Date` so the
 * signature time is right on a device with a wrong clock. A request the server refuses for the clock
 * ([ZemerRefusal.CLOCK_SKEW]) is resent once, re-signed with the corrected clock; refused again, it fails with
 * [ZemerRefusal.ClockSkew] (a network failure). Every HOP is judged on its own: a redirect is re-signed
 * for its own path when it stays on a Zemer host and carries none of these headers when it leaves one. Requests to any other host pass through untouched. Nothing else here
 * fails a request, except that once [UnofficialCopy] has refused this copy (it is fed every Zemer-host response here)
 * every Zemer-host request fails locally with [UnofficialCopy.Refused].
 */
object ZemerHeaders {
    const val BUILD_HEADER = "X-Zemer-Build"
    private val build = BuildConfig.VERSION_CODE.toString()

    /** Everything this hook adds, all removed on a hop to any other host. */
    private val OWN_HEADERS = listOf(ZemerSig.HEADER, BUILD_HEADER, EnvSignals.HEADER)

    /** Enough of a refusal body for its message; never the whole of a large one. */
    private const val PEEK_BYTES = 4_096L

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
            if (UnofficialCopy.refused) throw UnofficialCopy.Refused()
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
            if (!ZemerHosts.isZemerHost(response.call.request.url.host)) return@onResponse
            ZemerSig.onServerDate(response.headers["Date"])
            val body = refusalBody(response.call)
            UnofficialCopy.onResponse(response.status.value, response.headers[ZemerRefusal.HEADER], response.headers["Content-Type"]) { body }
            onRefusal(response.status.value, response.call.request.headers[ZemerSig.HEADER], response.headers[ZemerRefusal.HEADER], response.headers["Content-Type"]) { body }
        }
        // the resend runs SendingRequest again: a fresh nonce and the clock onResponse just corrected
        on(Send) { request ->
            val call = proceed(request)
            val resendable = request.body is OutgoingContent.ByteArrayContent || request.body is OutgoingContent.NoContent
            if (!resendable || !isClockSkew(call)) return@on call
            call.response.cancel()
            val retry = proceed(HttpRequestBuilder().takeFrom(request))
            ZemerRefusal.logResend(retry.response.status.value)
            if (!isClockSkew(retry)) return@on retry
            retry.response.cancel()
            throw ZemerRefusal.ClockSkew()
        }
    }

    /** A refusal of a request signed with this install's own key self-heals that key ([InstallRegistration.onRefused]). */
    private fun onRefusal(status: Int, sentSignature: String?, refusalHeader: String?, contentType: String?, body: () -> String?) {
        if (status != 403 || sentSignature == null) return
        ZemerRefusal.parse(status, refusalHeader, contentType, body)?.let { InstallRegistration.onRefused(sentSignature, it.error) }
    }

    /** A 403's body when it can be read without taking it from the caller (a saved, non-streaming one). */
    private suspend fun refusalBody(call: HttpClientCall): String? =
        if (call.response.status.value == 403 && call.response.isSaved) runCatching { call.response.bodyAsText() }.getOrNull() else null

    private suspend fun isClockSkew(call: HttpClientCall): Boolean {
        val response = call.response
        if (response.status.value != 403 || !ZemerHosts.isZemerHost(call.request.url.host)) return false
        val body = refusalBody(call)
        return ZemerRefusal.parse(403, response.headers[ZemerRefusal.HEADER], response.headers["Content-Type"]) { body }?.error ==
            ZemerRefusal.CLOCK_SKEW
    }

    /** Resends a clock-skew refusal once (an application interceptor: the network one re-signs the resend). */
    private object OkHttpClockSkewRetry : okhttp3.Interceptor {
        override fun intercept(chain: okhttp3.Interceptor.Chain): Response {
            val response = chain.proceed(chain.request())
            if (!isClockSkew(response) || chain.request().body?.isOneShot() == true) return response
            response.close()
            val retry = chain.proceed(chain.request())
            ZemerRefusal.logResend(retry.code)
            if (!isClockSkew(retry)) return retry
            retry.close()
            throw ZemerRefusal.ClockSkew()
        }

        private fun isClockSkew(response: Response): Boolean =
            response.code == 403 && ZemerHosts.isZemerHost(response.request.url.host) &&
                ZemerRefusal.parse(403, response.header(ZemerRefusal.HEADER), response.header("Content-Type")) {
                    runCatching { response.peekBody(PEEK_BYTES).string() }.getOrNull()
                }?.error == ZemerRefusal.CLOCK_SKEW
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
            if (UnofficialCopy.refused) throw UnofficialCopy.Refused()
            val builder = request.newBuilder().header(BUILD_HEADER, build)
            EnvSignals.mask.takeIf { it != 0 }?.let { builder.header(EnvSignals.HEADER, it.toString()) }
            ZemerSig.headerFor(request.method, request.url.encodedPath, request.url.encodedQuery, bodyBytes(request.body))
                ?.let { builder.header(ZemerSig.HEADER, it) }
            return chain.proceed(builder.build()).also { response ->
                ZemerSig.onServerDate(response.header("Date"))
                val body = { runCatching { response.peekBody(PEEK_BYTES).string() }.getOrNull() }
                UnofficialCopy.onResponse(response.code, response.header(ZemerRefusal.HEADER), response.header("Content-Type"), body)
                onRefusal(response.code, response.request.header(ZemerSig.HEADER), response.header(ZemerRefusal.HEADER), response.header("Content-Type"), body)
            }
        }

        /** The bytes a body writes (none = empty); null for a body that can be written only once (signed version 1). */
        private fun bodyBytes(body: RequestBody?): ByteArray? = when {
            body == null -> ByteArray(0)
            body.isOneShot() || body.isDuplex() -> null
            else -> runCatching { Buffer().also(body::writeTo).readByteArray() }.getOrNull()
        }
    }

    /** Adds the hook to an OkHttp client: signing as a network interceptor (per redirect hop), the clock-skew resend around it. */
    fun OkHttpClient.Builder.withZemerHeaders(): OkHttpClient.Builder =
        addInterceptor(OkHttpClockSkewRetry).addNetworkInterceptor(OkHttpNetworkInterceptor)
}
