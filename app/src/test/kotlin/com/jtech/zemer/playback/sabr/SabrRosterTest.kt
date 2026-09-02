package com.jtech.zemer.playback.sabr

import com.jtech.zemer.utils.StreamClient
import com.metrolist.innertube.models.StreamProtocol
import com.metrolist.innertube.models.YouTubeClient
import com.zemer.cipher.StreamClientParser.StreamClientDef.SabrInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SabrRosterTest {

    private fun entry(
        key: String,
        family: String = key,
        protocol: StreamProtocol = StreamProtocol.DIRECT,
        sabr: SabrInfo? = SabrInfo(),
        osName: String? = null,
        androidSdkVersion: String? = null,
    ) = StreamClient(
        YouTubeClient(
            clientName = key, clientVersion = "1.0", clientId = "67", userAgent = "ua",
            osName = osName, androidSdkVersion = androidSdkVersion, protocol = protocol,
        ),
        family, key = key, sabr = sabr,
    )

    @Test
    fun `spec derives web from the protocol and keys by the table entry`() {
        val web = SabrRoster.spec(entry("WEB_REMIX", protocol = StreamProtocol.WEB_CIPHER_POT))
        assertTrue(web.web)
        assertEquals("WEB_REMIX", web.key)
        assertEquals("WEB_REMIX (SABR)", web.label)
        assertFalse(SabrRoster.spec(entry("VISIONOS")).web)
    }

    @Test
    fun `sabr identity overrides the client's own, null inherits`() {
        val overridden = SabrRoster.spec(
            entry("WEB_REMIX", sabr = SabrInfo(osName = "Windows", osVersion = "10.0")),
        )
        assertEquals("Windows", overridden.osName)
        assertEquals("10.0", overridden.osVersion)
        assertNull(overridden.deviceMake)

        val inherited = SabrRoster.spec(entry("VR", osName = "Android", androidSdkVersion = "32"))
        assertEquals("Android", inherited.osName)
        assertEquals(32, inherited.androidSdk)

        val sdkOverride = SabrRoster.spec(entry("VR", androidSdkVersion = "32", sabr = SabrInfo(androidSdkVersion = "33")))
        assertEquals(33, sdkOverride.androidSdk)
    }

    @Test
    fun `order keeps table order over enabled families, stalled keys last`() {
        val roster = listOf(
            entry("WEB_REMIX"), entry("VISIONOS"), entry("VISIONOS_0_1", family = "VISIONOS"), entry("TVHTML5_SIMPLY", family = "TVHTML5"),
        ).map { SabrRoster.spec(it) }
        assertEquals(
            listOf("WEB_REMIX", "VISIONOS", "VISIONOS_0_1", "TVHTML5_SIMPLY"),
            SabrRoster.order(roster, setOf("WEB_REMIX", "VISIONOS", "TVHTML5"), emptySet()).map { it.key },
        )
        // A disabled FAMILY drops every entry of that family; a stalled KEY moves behind the rest.
        assertEquals(
            listOf("TVHTML5_SIMPLY", "WEB_REMIX"),
            SabrRoster.order(roster, setOf("WEB_REMIX", "TVHTML5"), setOf("WEB_REMIX")).map { it.key },
        )
        assertTrue(SabrRoster.order(roster, emptySet(), emptySet()).isEmpty())
    }
}
