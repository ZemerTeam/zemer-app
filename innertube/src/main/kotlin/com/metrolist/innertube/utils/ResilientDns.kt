package com.metrolist.innertube.utils

import java.net.InetAddress
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps

/**
 * DNS that prefers system resolution but falls back to public DoH
 * when the hostname is looped back (e.g., adblock rewriting to 127.0.0.1).
 */
class ResilientDns : Dns {
    private val bootstrapGoogle = listOf(
        "8.8.8.8",
        "8.8.4.4",
        "2001:4860:4860::8888",
        "2001:4860:4860::8844"
    ).map { InetAddress.getByName(it) }

    private val bootstrapCloudflare = listOf(
        "1.1.1.1",
        "1.0.0.1",
        "2606:4700:4700::1111",
        "2606:4700:4700::1001"
    ).map { InetAddress.getByName(it) }

    private val bootstrapQuad9 = listOf(
        "9.9.9.9",
        "149.112.112.112",
        "2620:fe::fe",
        "2620:fe::9"
    ).map { InetAddress.getByName(it) }

    private val dohClients = listOf(
        DnsOverHttps.Builder()
            .client(
                OkHttpClient.Builder()
                    .dns(Dns.SYSTEM) // Use system for the DoH bootstrap only.
                    .build()
            )
            .url("https://dns.google/dns-query".toHttpUrl())
            .bootstrapDnsHosts(bootstrapGoogle)
            .includeIPv6(true)
            .build(),
        DnsOverHttps.Builder()
            .client(
                OkHttpClient.Builder()
                    .dns(Dns.SYSTEM)
                    .build()
            )
            .url("https://cloudflare-dns.com/dns-query".toHttpUrl())
            .bootstrapDnsHosts(bootstrapCloudflare)
            .includeIPv6(true)
            .build(),
        DnsOverHttps.Builder()
            .client(
                OkHttpClient.Builder()
                    .dns(Dns.SYSTEM)
                    .build()
            )
            .url("https://dns.quad9.net/dns-query".toHttpUrl())
            .bootstrapDnsHosts(bootstrapQuad9)
            .includeIPv6(true)
            .build(),
    )

    override fun lookup(hostname: String): List<InetAddress> {
        val systemAddresses = runCatching { Dns.SYSTEM.lookup(hostname) }.getOrDefault(emptyList())

        val usable = systemAddresses.filterNot { it.isLoopbackAddress || it.isAnyLocalAddress }
        if (usable.isNotEmpty()) return usable

        // Fallback to DoH to bypass local hosts overrides.
        dohClients.forEach { doh ->
            val dohResult = runCatching { doh.lookup(hostname) }.getOrNull().orEmpty()
            val dohUsable = dohResult.filterNot { it.isLoopbackAddress || it.isAnyLocalAddress }
            if (dohUsable.isNotEmpty()) return dohUsable
        }

        // As a last resort, return system results (even if empty) to avoid throwing.
        return systemAddresses
    }
}
