package com.jtech.zemer.playback

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.fcast.sender_sdk.DeviceConnectionState
import org.fcast.sender_sdk.IpAddr
import org.fcast.sender_sdk.ProtocolType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress

/**
 * Pins the pure pieces of the user-initiated cast connect flow. The silent-failure bug this guards
 * against: sender-sdk 0.4.0 lists a device in the picker before mDNS resolution, a tap on an
 * address-less entry threw MissingAddresses into Crashlytics, and the user saw nothing. The fix
 * re-resolves at click time (needs these InetAddress→IpAddr / service-type mappings) and awaits the
 * connection outcome on remoteConnectionState (awaitOutcome) so failures surface in the UI.
 */
class CastConnectTest {

    private val someAddr = IpAddr.V4(192.toUByte(), 168.toUByte(), 0.toUByte(), 1.toUByte())
    private fun connected() = DeviceConnectionState.Connected(someAddr, someAddr)

    // --- awaitOutcome ---

    @Test
    fun `awaitOutcome reports CONNECTED when the receiver reports Connected`() = runBlocking {
        val states = MutableStateFlow<DeviceConnectionState>(DeviceConnectionState.Connecting)
        launch {
            delay(20)
            states.value = connected()
        }
        assertEquals(CastConnectOutcome.CONNECTED, CastConnect.awaitOutcome(states, timeoutMs = 2_000))
    }

    @Test
    fun `awaitOutcome reports FAILED when the attempt falls back to Disconnected`() = runBlocking {
        // A refused/unreachable receiver (e.g. a firewalled port 46899) surfaces as Disconnected.
        val states = MutableStateFlow<DeviceConnectionState>(DeviceConnectionState.Connecting)
        launch {
            delay(20)
            states.value = DeviceConnectionState.Disconnected
        }
        assertEquals(CastConnectOutcome.FAILED, CastConnect.awaitOutcome(states, timeoutMs = 2_000))
    }

    @Test
    fun `awaitOutcome times out when no terminal state ever arrives`() = runBlocking {
        val states = MutableStateFlow<DeviceConnectionState>(DeviceConnectionState.Connecting)
        assertEquals(CastConnectOutcome.TIMED_OUT, CastConnect.awaitOutcome(states, timeoutMs = 50))
    }

    @Test
    fun `awaitOutcome treats Reconnecting as non-terminal and keeps waiting`() = runBlocking {
        val states = MutableStateFlow<DeviceConnectionState>(DeviceConnectionState.Connecting)
        launch {
            delay(10)
            states.value = DeviceConnectionState.Reconnecting
            delay(10)
            states.value = connected()
        }
        assertEquals(CastConnectOutcome.CONNECTED, CastConnect.awaitOutcome(states, timeoutMs = 2_000))
    }

    // --- toIpAddr ---

    @Test
    fun `toIpAddr maps an IPv4 address octet-for-octet including high (unsigned) octets`() {
        // 182 and 192 are negative as signed bytes — a missing toUByte() would corrupt exactly these.
        val expected = IpAddr.V4(192.toUByte(), 168.toUByte(), 0.toUByte(), 182.toUByte())
        assertEquals(expected, CastConnect.toIpAddr(InetAddress.getByName("192.168.0.182")))
    }

    @Test
    fun `toIpAddr maps an IPv6 address byte-for-byte`() {
        val expected = IpAddr.V6(
            0x20.toUByte(), 0x01.toUByte(), 0x0d.toUByte(), 0xb8.toUByte(),
            0.toUByte(), 0.toUByte(), 0.toUByte(), 0.toUByte(),
            0.toUByte(), 0.toUByte(), 0.toUByte(), 0.toUByte(),
            0.toUByte(), 0.toUByte(), 0.toUByte(), 0x01.toUByte(),
            0u, // scope id (none for a global address)
        )
        assertEquals(expected, CastConnect.toIpAddr(InetAddress.getByName("2001:db8::1")))
    }

    // --- toInetAddress / relayTargetAddress ---

    @Test
    fun `toInetAddress round-trips both families through toIpAddr`() {
        // High octets are negative as signed bytes — the round-trip catches a missing toByte/toUByte.
        val v4 = InetAddress.getByName("192.168.0.182")
        assertEquals(v4, CastConnect.toInetAddress(CastConnect.toIpAddr(v4)!!))
        val v6 = InetAddress.getByName("2001:db8::1")
        assertEquals(v6, CastConnect.toInetAddress(CastConnect.toIpAddr(v6)!!))
    }

    @Test
    fun `relayTargetAddress prefers an IPv4 address for the relay route probe`() {
        val v6 = CastConnect.toIpAddr(InetAddress.getByName("2001:db8::1"))!!
        val v4 = IpAddr.V4(192.toUByte(), 168.toUByte(), 0.toUByte(), 7.toUByte())
        // v4 wins even when listed after v6 (mDNS-advertised v6 is often link-local, unusable in a URL).
        assertEquals(
            InetAddress.getByName("192.168.0.7"),
            CastConnect.relayTargetAddress(listOf(v6, v4)),
        )
        // v6-only receivers still get a target; an empty list gets none.
        assertEquals(InetAddress.getByName("2001:db8::1"), CastConnect.relayTargetAddress(listOf(v6)))
        assertNull(CastConnect.relayTargetAddress(emptyList()))
    }

    // --- shouldPruneDevice ---

    @Test
    fun `only a Failed connect prunes the tapped device from the picker`() {
        // Failed = the device is proven unreachable (stale mDNS entry, refused, timeout) — drop it.
        assertTrue(CastConnectResult.Failed("Ghost").shouldPruneDevice())
        // NoStream is OUR stream resolve failing; the device was never tried and must stay listed.
        assertFalse(CastConnectResult.NoStream.shouldPruneDevice())
        assertFalse(CastConnectResult.Connected.shouldPruneDevice())
    }

    // --- nsdServiceType ---

    @Test
    fun `nsdServiceType maps each protocol to the service type its receivers advertise`() {
        assertEquals("_fcast._tcp.", CastConnect.nsdServiceType(ProtocolType.F_CAST))
        assertEquals("_googlecast._tcp.", CastConnect.nsdServiceType(ProtocolType.CHROMECAST))
    }
}
