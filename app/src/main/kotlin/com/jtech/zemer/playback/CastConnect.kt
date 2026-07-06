
package com.jtech.zemer.playback

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withTimeoutOrNull
import org.fcast.sender_sdk.DeviceConnectionState
import org.fcast.sender_sdk.IpAddr
import org.fcast.sender_sdk.ProtocolType
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

/** Terminal result of a cast connect attempt, observed on [FCastDiscoveryHandler.remoteConnectionState]. */
enum class CastConnectOutcome { CONNECTED, FAILED, TIMED_OUT }

/**
 * Pure logic for the user-initiated cast connect flow (no Android, no SDK runtime — unit-testable).
 * The stateful pieces live in [CastConnector] (orchestration) and [CastDeviceAddressResolver]
 * (the Android NSD re-resolve).
 */
object CastConnect {
    /**
     * Cap on waiting for the receiver to report Connected/Disconnected after connect() is issued.
     * The SDK keeps re-trying a dead address indefinitely (endless Connecting), so this cap is the
     * only exit for a stale entry; healthy receivers on a LAN accept within ~1–3 s.
     */
    const val CONNECT_TIMEOUT_MS = 10_000L

    /** Overall cap on the click-time NSD re-resolve of a device discovered without addresses. */
    const val ADDRESS_RESOLVE_TIMEOUT_MS = 5_000L

    /**
     * Cap on the click-time re-resolve of a device that already has (possibly stale) addresses —
     * shorter, because on failure the connect proceeds with the cached ones as a fallback.
     */
    const val ADDRESS_REFRESH_TIMEOUT_MS = 2_000L

    /** Delay between retries while the legacy (single-flight, API < 34) NSD resolver is busy. */
    const val ADDRESS_RESOLVE_RETRY_DELAY_MS = 300L

    /** How long a refresh's NSD discovery burst listens before its found-set is treated as complete. */
    const val DISCOVERY_BURST_MS = 3_000L

    /** Per-service resolve budget inside a refresh burst (several may run back-to-back). */
    const val BURST_RESOLVE_TIMEOUT_MS = 2_000L

    /**
     * TCP connect budget for the refresh burst's per-address reachability probe. mDNS caches answer
     * resolves for a dead receiver until the records' TTL runs out (a force-closed receiver sends no
     * goodbye), so only an actual connection attempt separates alive from lingering; live LAN
     * receivers accept in a few ms.
     */
    const val REACHABILITY_PROBE_TIMEOUT_MS = 750L

    /**
     * Whether a tapped device's addresses should be re-resolved before connecting. FCast entries are
     * named by their mDNS instance name, so a re-resolve always works and picks up a new DHCP lease.
     * Chromecast entries are named by their TXT friendly name (`fn`) — not a resolvable instance name —
     * so re-resolving an already-resolved one just burns the budget; only address-less ones (still
     * named by the raw service name) are worth resolving.
     */
    fun shouldReResolve(protocol: ProtocolType, hasAddresses: Boolean): Boolean =
        protocol == ProtocolType.F_CAST || !hasAddresses

    /** The mDNS service type a cast protocol is advertised under (what the SDK's discoverer browses). */
    fun nsdServiceType(protocol: ProtocolType): String = when (protocol) {
        ProtocolType.F_CAST -> "_fcast._tcp."
        ProtocolType.CHROMECAST -> "_googlecast._tcp."
    }

    /** JVM address → SDK address, or null for kinds the SDK can't represent. */
    fun toIpAddr(address: InetAddress): IpAddr? {
        val b = address.address
        return when {
            address is Inet4Address && b.size == 4 -> IpAddr.V4(
                b[0].toUByte(), b[1].toUByte(), b[2].toUByte(), b[3].toUByte(),
            )
            address is Inet6Address && b.size == 16 -> IpAddr.V6(
                b[0].toUByte(), b[1].toUByte(), b[2].toUByte(), b[3].toUByte(),
                b[4].toUByte(), b[5].toUByte(), b[6].toUByte(), b[7].toUByte(),
                b[8].toUByte(), b[9].toUByte(), b[10].toUByte(), b[11].toUByte(),
                b[12].toUByte(), b[13].toUByte(), b[14].toUByte(), b[15].toUByte(),
                // The interface scope id: required to route link-local (fe80::) addresses.
                address.scopeId.toUInt(),
            )
            else -> null
        }
    }

    /** SDK address → JVM address (the reverse of [toIpAddr]), or null when it can't be materialised. */
    fun toInetAddress(address: IpAddr): InetAddress? = runCatching {
        when (address) {
            is IpAddr.V4 -> InetAddress.getByAddress(
                byteArrayOf(address.o1.toByte(), address.o2.toByte(), address.o3.toByte(), address.o4.toByte()),
            )
            is IpAddr.V6 -> Inet6Address.getByAddress(
                null,
                byteArrayOf(
                    address.o1.toByte(), address.o2.toByte(), address.o3.toByte(), address.o4.toByte(),
                    address.o5.toByte(), address.o6.toByte(), address.o7.toByte(), address.o8.toByte(),
                    address.o9.toByte(), address.o10.toByte(), address.o11.toByte(), address.o12.toByte(),
                    address.o13.toByte(), address.o14.toByte(), address.o15.toByte(), address.o16.toByte(),
                ),
                address.scopeId.toInt(),
            )
        }
    }.getOrNull()

    /**
     * The receiver address the stream relay derives its URL host toward ([CastStreamRelay.receiverAddress]).
     * Prefer IPv4: the relay URL host must be reachable from the receiver, and v4 LAN addressing is the
     * common denominator — mDNS-advertised v6 entries are often link-local, which can't be expressed as
     * a URL host the receiver can resolve.
     */
    fun relayTargetAddress(addresses: List<IpAddr>): InetAddress? =
        (addresses.firstOrNull { it is IpAddr.V4 } ?: addresses.firstOrNull())?.let { toInetAddress(it) }

    /**
     * Waits for the attempt started by [FCastDiscoveryHandler.connectTo] to reach a terminal state:
     * Connected → CONNECTED, Disconnected → FAILED (refused/dropped), neither within [timeoutMs] →
     * TIMED_OUT. Connecting/Reconnecting are non-terminal and keep the wait alive. connectTo presets
     * the flow to Connecting, so the previous session's Disconnected can never be read as this
     * attempt's failure.
     */
    suspend fun awaitOutcome(
        states: Flow<DeviceConnectionState>,
        timeoutMs: Long = CONNECT_TIMEOUT_MS,
    ): CastConnectOutcome = withTimeoutOrNull(timeoutMs) {
        states.mapNotNull { state ->
            when (state) {
                is DeviceConnectionState.Connected -> CastConnectOutcome.CONNECTED
                is DeviceConnectionState.Disconnected -> CastConnectOutcome.FAILED
                else -> null
            }
        }.first()
    } ?: CastConnectOutcome.TIMED_OUT
}
