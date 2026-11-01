package com.jtech.zemer.net

import com.jtech.zemer.BuildConfig
import com.jtech.zemer.sig.ZemerSig
import io.ktor.client.plugins.api.SendingRequest
import io.ktor.client.plugins.api.createClientPlugin
import okhttp3.OkHttpClient
import okhttp3.Response

/**
 * The one hook every client to a Zemer server installs: on a Zemer-host request ([ZemerHosts]) it adds the
 * build's versionCode ([BUILD_HEADER]) and the request signature (builds with a signing key only), and it takes
 * the server clock from the response's `Date` so the signature time is right on a device with a wrong clock.
 * Every HOP is judged on its own: a redirect is re-signed for its own path when it stays on a Zemer host and
 * carries neither header when it leaves one. Requests to any other host pass through untouched. Nothing here can
 * fail a request.
 */
object ZemerHeaders {
    const val BUILD_HEADER = "X-Zemer-Build"
    private val build = BuildConfig.VERSION_CODE.toString()

    /**
     * Ktor clients. Signs at [SendingRequest], so the URL is final (query parameters included); Ktor's redirect
     * handling re-runs it for each hop with the previous hop's headers copied, so the headers are always replaced
     * (never appended) and removed for a non-Zemer hop.
     */
    val KtorPlugin = createClientPlugin("ZemerHeaders") {
        on(SendingRequest) { request, _ ->
            request.headers.remove(ZemerSig.HEADER)
            request.headers.remove(BUILD_HEADER)
            val url = request.url.build()
            if (!ZemerHosts.isZemerHost(url.host)) return@on
            request.headers[BUILD_HEADER] = build
            ZemerSig.headerFor(request.method.value, url.encodedPath, url.encodedQuery.takeIf { it.isNotEmpty() })
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
                if (request.header(ZemerSig.HEADER) == null && request.header(BUILD_HEADER) == null) {
                    return chain.proceed(request)
                }
                return chain.proceed(request.newBuilder().removeHeader(ZemerSig.HEADER).removeHeader(BUILD_HEADER).build())
            }
            val builder = request.newBuilder().header(BUILD_HEADER, build)
            ZemerSig.headerFor(request.method, request.url.encodedPath, request.url.encodedQuery)
                ?.let { builder.header(ZemerSig.HEADER, it) }
            return chain.proceed(builder.build()).also { ZemerSig.onServerDate(it.header("Date")) }
        }
    }

    /** Adds the hook to an OkHttp client as a network interceptor (per redirect hop). */
    fun OkHttpClient.Builder.withZemerHeaders(): OkHttpClient.Builder = addNetworkInterceptor(OkHttpNetworkInterceptor)
}
