package com.jtech.zemer.utils

import com.jtech.zemer.BuildConfig
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.pluginOrNull
import okhttp3.Interceptor
import okhttp3.Response
import java.net.URI
import java.net.URLConnection

/**
 * The app-version marker (`X-Zemer-App: <versionCode>`) on every request the app's own HTTP code makes
 * to Zemer's API hosts (search + content). Servers count requests per version and, from a cutoff date
 * the owner sets, refuse requests WITHOUT it - which only versions older than the marker can send.
 *
 * Applied once per client ([zemerAppMarker] for Ktor, [withZemerAppMarker] for `HttpURLConnection` /
 * OkHttp), host-gated so a client shared with third parties (the lyrics client) never leaks it, and so a
 * future endpoint on an existing client cannot miss it. Image/media loads (Coil, ExoPlayer) and the
 * tracking / stream / update hosts are exempt server-side and don't send it. Debug builds send it too.
 */
object ZemerAppMarker {
    const val HEADER = "X-Zemer-App"
    val value: String = BuildConfig.VERSION_CODE.toString()

    // The production API hosts plus whatever the build points the content mirror / lyrics resolver at
    // (a dev override keeps the marker).
    private val hosts: Set<String> = setOfNotNull(
        "search.zemer.io",
        "content.zemer.io",
        hostOf(BuildConfig.CONTENT_MIRROR_URL),
        hostOf(BuildConfig.ZEMER_LYRICS_BASE_URL),
    )

    fun appliesTo(host: String?): Boolean = host != null && host.lowercase() in hosts

    private fun hostOf(url: String): String? = runCatching { URI(url).host?.lowercase() }.getOrNull()
}

/** Ktor client plugin stamping [ZemerAppMarker] on every request to a Zemer API host. */
private val ZemerAppMarkerPlugin = createClientPlugin("ZemerAppMarker") {
    onRequest { request, _ ->
        if (ZemerAppMarker.appliesTo(request.url.host) && !request.headers.contains(ZemerAppMarker.HEADER)) {
            request.headers.append(ZemerAppMarker.HEADER, ZemerAppMarker.value)
        }
    }
}

/** Install the version marker on this Ktor client (inside `HttpClient(engine) { ... }`). */
fun HttpClientConfig<*>.zemerAppMarker() = install(ZemerAppMarkerPlugin)

/** Whether this client installs the marker - the per-client coverage check in tests. */
internal fun io.ktor.client.HttpClient.hasZemerAppMarker(): Boolean =
    pluginOrNull(ZemerAppMarkerPlugin) != null

/** Stamp the marker on an unconnected [URLConnection] when it targets a Zemer API host. */
fun <T : URLConnection> T.withZemerAppMarker(): T = apply {
    if (ZemerAppMarker.appliesTo(url.host)) setRequestProperty(ZemerAppMarker.HEADER, ZemerAppMarker.value)
}

/** OkHttp interceptor stamping the marker on every request to a Zemer API host. */
object ZemerAppMarkerInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!ZemerAppMarker.appliesTo(request.url.host)) return chain.proceed(request)
        return chain.proceed(request.newBuilder().header(ZemerAppMarker.HEADER, ZemerAppMarker.value).build())
    }
}
