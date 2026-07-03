package com.jtech.zemer.playback

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import com.jtech.zemer.utils.reportException
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.fcast.sender_sdk.DeviceInfo
import org.fcast.sender_sdk.ProtocolType
import timber.log.Timber
import java.net.InetAddress
import kotlin.coroutines.resume

/**
 * Click-time NSD (re-)resolve for cast devices.
 *
 * sender-sdk 0.4.0 publishes a device to the picker the moment mDNS *finds* it — before Android has
 * resolved its host — and on API < 34 the legacy single-flight `resolveService` loses the race whenever
 * several services resolve at once, fails with no retry, and leaves the entry address-less forever.
 * Tapping such an entry threw `MissingAddresses` (swallowed into Crashlytics) — the "tap connect and
 * nothing happens" bug. Beyond that, an address resolved at discovery time can go stale (the receiver
 * takes a new DHCP lease), so [refreshAddresses] re-resolves on every tap where that makes sense
 * (CastConnect.shouldReResolve) and writes the result back into the (mutable) [DeviceInfo] — the same
 * instance held in the picker's device map, so later taps benefit too.
 */
class CastDeviceAddressResolver(context: Context) {
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager

    /**
     * True when [deviceInfo] is worth a connect attempt: freshly re-resolved addresses, or the
     * previously known ones as a fallback when the re-resolve fails (a resolve hiccup must not fail a
     * connect that might still work — the budget is shorter when a fallback exists, see the constants).
     */
    suspend fun refreshAddresses(deviceInfo: DeviceInfo): Boolean {
        val hadAddresses = deviceInfo.addresses.isNotEmpty()
        if (!CastConnect.shouldReResolve(deviceInfo.protocol, hadAddresses)) return hadAddresses
        val budgetMs = if (hadAddresses) CastConnect.ADDRESS_REFRESH_TIMEOUT_MS else CastConnect.ADDRESS_RESOLVE_TIMEOUT_MS
        val resolved = resolveWithRetry(deviceInfo.name, deviceInfo.protocol, budgetMs) ?: return hadAddresses
        val addresses = hostAddresses(resolved).mapNotNull { CastConnect.toIpAddr(it) }
        if (addresses.isEmpty()) return hadAddresses
        deviceInfo.addresses = addresses
        if (resolved.port != 0) deviceInfo.port = resolved.port.toUShort()
        Timber.d("Cast NSD re-resolve refreshed %s: %d address(es), port %d", deviceInfo.name, addresses.size, resolved.port)
        return true
    }

    /**
     * Resolve the service instance [name] of [protocol], retrying while the legacy (single-flight,
     * API < 34) resolver is busy, bounded by [budgetMs]. Also used per-service by [CastDeviceRefresher].
     */
    suspend fun resolveWithRetry(name: String, protocol: ProtocolType, budgetMs: Long): NsdServiceInfo? =
        withTimeoutOrNull(budgetMs) {
            var info = resolveOnce(name, protocol)
            while (info == null) {
                delay(CastConnect.ADDRESS_RESOLVE_RETRY_DELAY_MS)
                info = resolveOnce(name, protocol)
            }
            info
        }

    /** The resolved host address(es) — one on the legacy API, possibly several on API 34+. */
    fun hostAddresses(info: NsdServiceInfo): List<InetAddress> =
        if (Build.VERSION.SDK_INT >= 34) info.hostAddresses else listOfNotNull(info.host)

    private suspend fun resolveOnce(name: String, protocol: ProtocolType): NsdServiceInfo? =
        suspendCancellableCoroutine { cont ->
            val request = NsdServiceInfo().apply {
                serviceName = name
                serviceType = CastConnect.nsdServiceType(protocol)
            }
            // A fresh listener per attempt: NsdManager rejects a listener that is already in use.
            val listener = object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    // FAILURE_ALREADY_ACTIVE (the single-flight resolver is busy) and transient
                    // failures are both handled the same way: the caller retries until its budget.
                    Timber.d("Cast NSD resolve failed for %s (error %d)", name, errorCode)
                    if (cont.isActive) cont.resume(null)
                }

                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    if (cont.isActive) cont.resume(serviceInfo)
                }
            }
            try {
                nsdManager.resolveService(request, listener)
            } catch (e: Exception) {
                reportException(e, "Cast NSD resolve")
                if (cont.isActive) cont.resume(null)
            }
        }
}
