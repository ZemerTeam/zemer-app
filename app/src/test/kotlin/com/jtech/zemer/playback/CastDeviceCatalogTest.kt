package com.jtech.zemer.playback

import org.fcast.sender_sdk.DeviceInfo
import org.fcast.sender_sdk.IpAddr
import org.fcast.sender_sdk.ProtocolType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the refresh-burst merge rules ("reload devices"). The failure modes these guard against:
 * a stale receiver staying listed forever (the SDK never re-checks a found device), a flaky resolve
 * wrongly pruning a live device (only an authoritative burst may prune), a refresh wiping addresses
 * that still work, and Chromecast naming drift (the SDK keys Chromecast entries by TXT `fn`, so a
 * raw-instance-name entry would duplicate and out-prune the real one).
 */
class CastDeviceCatalogTest {

    private val addrA = IpAddr.V4(192.toUByte(), 168.toUByte(), 0.toUByte(), 5.toUByte())
    private val addrB = IpAddr.V4(192.toUByte(), 168.toUByte(), 0.toUByte(), 99.toUByte())

    private fun device(
        name: String,
        protocol: ProtocolType = ProtocolType.F_CAST,
        addresses: List<IpAddr> = listOf(addrA),
        port: Int = 46899,
    ) = DeviceInfo(name, protocol, addresses, port.toUShort())

    private fun resolved(
        addresses: List<IpAddr> = listOf(addrB),
        port: Int = 46899,
        txt: Map<String, ByteArray?> = emptyMap(),
    ) = CastDeviceCatalog.ResolvedService(addresses, port.toUShort(), txt)

    // --- displayName / freshEntry naming ---

    @Test
    fun `fcast entries are always named by the mDNS instance name`() {
        val txt = mapOf<String, ByteArray?>("fn" to "Friendly".toByteArray())
        assertEquals("FCast-thinkpad", CastDeviceCatalog.displayName("FCast-thinkpad", ProtocolType.F_CAST, txt))
    }

    @Test
    fun `chromecast entries are named by the fn TXT record, falling back to the instance name`() {
        val txt = mapOf<String, ByteArray?>("fn" to "Living Room TV".toByteArray())
        assertEquals("Living Room TV", CastDeviceCatalog.displayName("Chromecast-abc123", ProtocolType.CHROMECAST, txt))
        assertEquals("Chromecast-abc123", CastDeviceCatalog.displayName("Chromecast-abc123", ProtocolType.CHROMECAST, emptyMap()))
        val blank = mapOf<String, ByteArray?>("fn" to "".toByteArray())
        assertEquals("Chromecast-abc123", CastDeviceCatalog.displayName("Chromecast-abc123", ProtocolType.CHROMECAST, blank))
    }

    @Test
    fun `an unresolved chromecast contributes no fresh entry`() {
        // Without a resolve there is no fn: an entry under the raw instance name would duplicate —
        // and via pruning even replace — the SDK's fn-named entry for the same device.
        assertNull(CastDeviceCatalog.freshEntry("Chromecast-abc123", ProtocolType.CHROMECAST, resolved = null))
    }

    @Test
    fun `an unresolved fcast service still contributes an address-less entry`() {
        // Its name is stable (the instance name), so it is safe to list; the click-time
        // re-resolve fills the addresses on tap.
        val entry = CastDeviceCatalog.freshEntry("FCast-thinkpad", ProtocolType.F_CAST, resolved = null)!!
        assertEquals("FCast-thinkpad", entry.name)
        assertTrue(entry.addresses.isEmpty())
    }

    // --- merge ---

    @Test
    fun `merge prunes an entry missing from an authoritative burst`() {
        val current = mapOf("Gone-receiver" to device("Gone-receiver"), "Alive" to device("Alive"))
        val merged = CastDeviceCatalog.merge(
            current,
            fresh = listOf(device("Alive")),
            authoritativeProtocols = setOf(ProtocolType.F_CAST),
        )
        assertEquals(setOf("Alive"), merged.keys)
    }

    @Test
    fun `merge keeps a missing entry when the burst was not authoritative for its protocol`() {
        val current = mapOf("Maybe-alive" to device("Maybe-alive"))
        val merged = CastDeviceCatalog.merge(
            current,
            fresh = emptyList(),
            authoritativeProtocols = emptySet(),
        )
        assertEquals(setOf("Maybe-alive"), merged.keys)
    }

    @Test
    fun `merge prunes per protocol`() {
        val current = mapOf(
            "Dead-fcast" to device("Dead-fcast", ProtocolType.F_CAST),
            "TV" to device("TV", ProtocolType.CHROMECAST),
        )
        // FCast burst fully resolved (authoritative), Chromecast burst had a failure — its
        // absent entry must survive.
        val merged = CastDeviceCatalog.merge(
            current,
            fresh = emptyList(),
            authoritativeProtocols = setOf(ProtocolType.F_CAST),
        )
        assertEquals(setOf("TV"), merged.keys)
    }

    @Test
    fun `merge keeps the existing instance and refreshes its addresses`() {
        val existing = device("FCast-thinkpad", addresses = listOf(addrA), port = 46899)
        val merged = CastDeviceCatalog.merge(
            mapOf(existing.name to existing),
            fresh = listOf(device("FCast-thinkpad", addresses = listOf(addrB), port = 46898)),
            authoritativeProtocols = setOf(ProtocolType.F_CAST),
        )
        // Same mutable instance (other code may hold it) with the fresh addresses written in.
        assertSame(existing, merged["FCast-thinkpad"])
        assertEquals(listOf(addrB), existing.addresses)
        assertEquals(46898.toUShort(), existing.port)
    }

    @Test
    fun `merge does not wipe working addresses with an address-less fresh entry`() {
        val existing = device("FCast-thinkpad", addresses = listOf(addrA))
        val merged = CastDeviceCatalog.merge(
            mapOf(existing.name to existing),
            fresh = listOf(device("FCast-thinkpad", addresses = emptyList(), port = 0)),
            authoritativeProtocols = setOf(ProtocolType.F_CAST),
        )
        assertSame(existing, merged["FCast-thinkpad"])
        assertEquals(listOf(addrA), existing.addresses)
    }

    @Test
    fun `merge adds a newly discovered device`() {
        val merged = CastDeviceCatalog.merge(
            current = emptyMap(),
            fresh = listOf(device("Brand-new")),
            authoritativeProtocols = setOf(ProtocolType.F_CAST),
        )
        assertEquals(setOf("Brand-new"), merged.keys)
    }
}
