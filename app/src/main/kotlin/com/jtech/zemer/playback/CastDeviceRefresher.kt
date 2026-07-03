package com.jtech.zemer.playback

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.jtech.zemer.utils.reportException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import org.fcast.sender_sdk.DeviceInfo
import org.fcast.sender_sdk.ProtocolType
import timber.log.Timber
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean

/**
 * On-demand rebuild of the picker's device list ("reload devices").
 *
 * The SDK's discoverer never re-checks a device once found: a receiver that closed or changed IP stays
 * listed forever (its `NsdDeviceDiscoverer` can't even be restarted — no stop API). A refresh runs a
 * short discovery **burst** with our own NSD listeners — a fresh listener is immediately told about
 * every service currently advertised, which the long-running SDK listener is not — resolves each found
 * service, and merges the result over the current map via [CastDeviceCatalog.merge]: fresh addresses
 * win, vanished entries are pruned (only when the burst's view of that protocol is complete), and
 * still-alive entries keep their identity.
 */
class CastDeviceRefresher(
    context: Context,
    private val handler: FCastDiscoveryHandler,
) {
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val resolver = CastDeviceAddressResolver(context)
    private val running = AtomicBoolean(false)

    /** True while a refresh is in flight — drives the picker's refresh-button spinner. */
    val refreshing = MutableStateFlow(false)

    /** Runs one refresh; concurrent calls are no-ops (the in-flight one already serves them). */
    suspend fun refresh() {
        if (!running.compareAndSet(false, true)) return
        refreshing.value = true
        try {
            // Both protocol bursts listen concurrently (3 s total, not 6); resolves then run
            // sequentially because the legacy resolver is single-flight anyway.
            val bursts = coroutineScope {
                ProtocolType.entries.map { protocol ->
                    async { protocol to discoverBurst(protocol) }
                }.awaitAll()
            }
            val fresh = mutableListOf<DeviceInfo>()
            val authoritative = mutableSetOf<ProtocolType>()
            for ((protocol, services) in bursts) {
                if (services == null) continue // discovery didn't start — no knowledge, prune nothing
                var allResolved = true
                for (service in services.distinctBy { it.serviceName }) {
                    val info = resolver.resolveWithRetry(
                        service.serviceName, protocol, CastConnect.BURST_RESOLVE_TIMEOUT_MS,
                    )
                    if (info == null) {
                        allResolved = false
                        CastDeviceCatalog.freshEntry(service.serviceName, protocol, resolved = null)?.let { fresh += it }
                        continue
                    }
                    // A resolve can be answered by a stale mDNS cache for a receiver that force-closed
                    // (no goodbye sent) — the records outlive the process by their TTL. Probe the port:
                    // nothing listening is *definitive* knowledge, so contribute no entry and leave the
                    // burst authoritative — the merge then prunes the lingering row.
                    if (info.port != 0 && !probeReachable(resolver.hostAddresses(info), info.port)) {
                        Timber.d("Cast refresh: %s resolved but unreachable — pruning", service.serviceName)
                        continue
                    }
                    val resolved = CastDeviceCatalog.ResolvedService(
                        addresses = resolver.hostAddresses(info).mapNotNull { CastConnect.toIpAddr(it) },
                        port = info.port.toUShort(),
                        txt = info.attributes ?: emptyMap(),
                    )
                    CastDeviceCatalog.freshEntry(service.serviceName, protocol, resolved)?.let { fresh += it }
                }
                if (allResolved) authoritative += protocol
            }
            handler.applyRefreshedDevices(fresh, authoritative)
            Timber.d(
                "Cast refresh: %d device(s) found, authoritative for %s", fresh.size, authoritative,
            )
        } finally {
            refreshing.value = false
            running.set(false)
        }
    }

    /**
     * True when any of [addresses] accepts a TCP connection on [port] within the probe budget.
     * I/O-bound ground truth (no unit test without a live socket): live LAN receivers accept in a few
     * ms; a stale cache entry's host refuses or times out.
     */
    private suspend fun probeReachable(addresses: List<InetAddress>, port: Int): Boolean =
        withContext(Dispatchers.IO) {
            addresses.any { address ->
                runCatching {
                    Socket().use {
                        it.connect(InetSocketAddress(address, port), CastConnect.REACHABILITY_PROBE_TIMEOUT_MS.toInt())
                    }
                    true
                }.getOrDefault(false)
            }
        }

    /**
     * Listens for [CastConnect.DISCOVERY_BURST_MS] and returns the services advertised right now, or
     * null when discovery could not start (no knowledge — the caller must not prune on that protocol).
     */
    private suspend fun discoverBurst(protocol: ProtocolType): List<NsdServiceInfo>? {
        val found = Collections.synchronizedMap(LinkedHashMap<String, NsdServiceInfo>())
        val started = CompletableDeferred<Boolean>()
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {
                started.complete(true)
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Timber.d("Cast refresh discovery failed to start for %s (error %d)", serviceType, errorCode)
                started.complete(false)
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                found[serviceInfo.serviceName] = serviceInfo
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                // Lost mid-burst = gone; keeping it would resurrect a just-vanished device.
                found.remove(serviceInfo.serviceName)
            }

            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
        }
        try {
            nsdManager.discoverServices(
                CastConnect.nsdServiceType(protocol), NsdManager.PROTOCOL_DNS_SD, listener,
            )
        } catch (e: Exception) {
            reportException(e, "Cast refresh discovery")
            return null
        }
        try {
            if (!started.await()) return null
            delay(CastConnect.DISCOVERY_BURST_MS)
        } finally {
            runCatching { nsdManager.stopServiceDiscovery(listener) }
        }
        return synchronized(found) { found.values.toList() }
    }
}
