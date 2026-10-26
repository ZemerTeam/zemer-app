package com.jtech.zemer.pass

import com.jtech.zemer.BuildConfig
import io.ktor.client.plugins.api.createClientPlugin
import okhttp3.Response
import java.net.URI

/**
 * The genuine-app pass: a short-lived token the content server issues after this install proves (Android key
 * attestation) it is the real, release-signed app (see [PassManager]). It rides as [HEADER] on every request
 * to the Zemer servers, and ONLY to them ([hostGetsPass]) - several shared clients also reach third-party
 * hosts, which must never see it. No pass (no hardware attestation, offline, refused) sends nothing and the
 * app works exactly as without it.
 */
object ZemerPass {
    const val HEADER = "X-Zemer-Pass"

    @Volatile
    var current: String? = null

    private val hosts: Set<String> = buildSet {
        add("search.zemer.io")
        add("content.zemer.io")
        add("stream.zemer.io")
        // a build pointed at another server (local testing) sends the pass there too
        listOf(BuildConfig.CONTENT_MIRROR_URL, BuildConfig.ZEMER_LYRICS_BASE_URL).forEach { url ->
            runCatching { URI(url).host }.getOrNull()?.takeIf { it.isNotBlank() }?.let(::add)
        }
    }

    fun hostGetsPass(host: String): Boolean = host.lowercase() in hosts

    /** The header value for a request to [host], or null when it must not carry the pass (or there is none). */
    fun headerFor(host: String): String? = current?.takeIf { hostGetsPass(host) }

    /** Ktor clients: adds the header to Zemer-host requests. */
    val KtorPlugin = createClientPlugin("ZemerPass") {
        onRequest { request, _ ->
            headerFor(request.url.host)?.let { request.headers.append(HEADER, it) }
        }
    }

    /** OkHttp clients (relay playback/downloads, images): adds the header to Zemer-host requests. */
    object OkHttpInterceptor : okhttp3.Interceptor {
        override fun intercept(chain: okhttp3.Interceptor.Chain): Response {
            val request = chain.request()
            val pass = headerFor(request.url.host) ?: return chain.proceed(request)
            return chain.proceed(request.newBuilder().header(HEADER, pass).build())
        }
    }
}
