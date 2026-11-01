package com.jtech.zemer.net

import com.jtech.zemer.BuildConfig
import java.net.URI

/**
 * The Zemer servers that receive the request signature and build header. Several shared clients (lyrics pages,
 * images) also reach third-party hosts, which must never see them.
 */
object ZemerHosts {
    private val hosts: Set<String> = buildSet {
        add("search.zemer.io")
        add("content.zemer.io")
        add("stream.zemer.io")
        add("tracking.zemer.io")
        // a build pointed at another server (local testing) sends them there too
        listOf(BuildConfig.CONTENT_MIRROR_URL, BuildConfig.ZEMER_LYRICS_BASE_URL).forEach { url ->
            runCatching { URI(url).host }.getOrNull()?.takeIf { it.isNotBlank() }?.let(::add)
        }
    }

    fun isZemerHost(host: String): Boolean = host.lowercase() in hosts
}
