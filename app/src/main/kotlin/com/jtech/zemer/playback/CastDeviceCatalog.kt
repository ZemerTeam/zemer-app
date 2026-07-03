package com.jtech.zemer.playback

import org.fcast.sender_sdk.DeviceInfo
import org.fcast.sender_sdk.IpAddr
import org.fcast.sender_sdk.ProtocolType

/**
 * Pure logic for rebuilding the picker's device list from a refresh burst (no Android — unit-testable).
 * The Android side (the NSD burst + resolves) is [CastDeviceRefresher]; the naming rules here mirror
 * sender-sdk 0.4.0's discoverer exactly, so refreshed entries merge onto the SDK's own map keys:
 * FCast devices are named by their mDNS instance name, Chromecast devices by their TXT `fn`
 * (friendly name) — the raw instance name (`Chromecast-<hash>`) is only used before resolution.
 */
object CastDeviceCatalog {
    /** What a refresh burst learned about one advertised service (null resolve data = resolve failed). */
    data class ResolvedService(
        val addresses: List<IpAddr>,
        val port: UShort,
        val txt: Map<String, ByteArray?>,
    )

    /** The display/map name the SDK would use for this service — see the class KDoc. */
    fun displayName(serviceName: String, protocol: ProtocolType, txt: Map<String, ByteArray?>): String =
        if (protocol == ProtocolType.CHROMECAST) {
            txt["fn"]?.decodeToString()?.takeIf { it.isNotBlank() } ?: serviceName
        } else {
            serviceName
        }

    /**
     * The [DeviceInfo] a burst-found service contributes to the fresh list, or null when it can't be
     * represented: an *unresolved* Chromecast has no friendly name yet, and adding it under the raw
     * instance name would duplicate (and, worse, out-prune) the SDK's `fn`-named entry.
     */
    fun freshEntry(serviceName: String, protocol: ProtocolType, resolved: ResolvedService?): DeviceInfo? {
        if (resolved == null && protocol == ProtocolType.CHROMECAST) return null
        return DeviceInfo(
            name = displayName(serviceName, protocol, resolved?.txt ?: emptyMap()),
            protocol = protocol,
            addresses = resolved?.addresses ?: emptyList(),
            port = resolved?.port ?: 0.toUShort(),
        )
    }

    /**
     * Merge a refresh burst's findings into the current device map.
     *
     * - Every fresh entry is upserted. An existing entry keeps its **instance** (the mutable
     *   [DeviceInfo] other code may hold) and takes the fresh addresses/port only when the burst
     *   actually resolved some — a failed resolve must not wipe addresses that still work.
     * - A current entry absent from the burst is pruned **only** when its protocol is in
     *   [authoritativeProtocols] — the burst's discovery started and every found service resolved, so
     *   the burst's view of that protocol is complete. A partially failed burst merges additively:
     *   wrongly keeping a dead entry costs one failed tap; wrongly pruning a live one hides a device.
     */
    fun merge(
        current: Map<String, DeviceInfo>,
        fresh: List<DeviceInfo>,
        authoritativeProtocols: Set<ProtocolType>,
    ): Map<String, DeviceInfo> {
        val merged = LinkedHashMap<String, DeviceInfo>()
        for ((name, existing) in current) {
            if (existing.protocol !in authoritativeProtocols || fresh.any { it.name == name }) {
                merged[name] = existing
            }
        }
        for (freshInfo in fresh) {
            val existing = merged[freshInfo.name]
            if (existing != null) {
                if (freshInfo.addresses.isNotEmpty()) {
                    existing.addresses = freshInfo.addresses
                    existing.port = freshInfo.port
                }
            } else {
                merged[freshInfo.name] = freshInfo
            }
        }
        return merged
    }
}
