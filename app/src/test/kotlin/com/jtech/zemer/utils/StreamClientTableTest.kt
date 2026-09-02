package com.jtech.zemer.utils

import com.jtech.zemer.utils.StreamClientTable.toStreamClient
import com.metrolist.innertube.models.StreamProtocol
import com.zemer.cipher.StreamClientParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamClientTableTest {

    private fun def(
        key: String,
        protocol: StreamClientParser.StreamClientDef.Protocol =
            StreamClientParser.StreamClientDef.Protocol.DIRECT,
        sabr: StreamClientParser.StreamClientDef.SabrInfo? = null,
    ) = StreamClientParser.StreamClientDef(
        key = key,
        clientName = key,
        clientVersion = "1.0",
        clientId = "67",
        userAgent = "ua",
        protocol = protocol,
        family = key,
        sabr = sabr,
    )

    @Test
    fun `entry key and sabr info ride along onto the table entry`() {
        val info = StreamClientParser.StreamClientDef.SabrInfo(osName = "Windows")
        val mapped = def("MAIN", sabr = info).toStreamClient()
        assertEquals("MAIN", mapped.key)
        assertEquals(info, mapped.sabr)
        assertEquals(null, def("PLAIN").toStreamClient().sabr)
    }

    @Test
    fun `sabrRoster is the sabr-capable entries in table order, main first`() {
        val info = StreamClientParser.StreamClientDef.SabrInfo()
        val table = StreamClientTable.fromConfig(
            StreamClientParser.StreamClientConfig(
                clients = listOf(def("MAIN", sabr = info), def("A"), def("B", sabr = info), def("C")),
                families = emptyMap(),
            ),
        )
        assertEquals(listOf("MAIN", "B"), table.sabrRoster.map { it.key })
        assertTrue(StreamClientTable.fromConfig(
            StreamClientParser.StreamClientConfig(listOf(def("MAIN"), def("A")), emptyMap()),
        ).sabrRoster.isEmpty())
        // A benched SABR capability drops the entry from the roster but not from the chain.
        val benched = StreamClientTable.fromConfig(
            StreamClientParser.StreamClientConfig(
                listOf(def("MAIN", sabr = info), def("B", sabr = StreamClientParser.StreamClientDef.SabrInfo(enabled = false))),
                emptyMap(),
            ),
        )
        assertEquals(listOf("MAIN"), benched.sabrRoster.map { it.key })
        assertEquals(listOf("B"), benched.fallbacks.map { it.key })
    }

    @Test
    fun `def maps onto the innertube client field for field`() {
        val def = StreamClientParser.StreamClientDef(
            key = "MWEB",
            clientName = "MWEB",
            clientVersion = "2.0",
            clientId = "2",
            userAgent = "ua",
            protocol = StreamClientParser.StreamClientDef.Protocol.WEB_CIPHER_POT,
            family = "MWEB",
            osName = "iPadOS",
            osVersion = "16",
            deviceMake = "Apple",
            deviceModel = "iPad",
            androidSdkVersion = "32",
            loginSupported = true,
            loginRequired = true,
            isEmbedded = false,
            skipHeadValidation = true,
        )
        val mapped = def.toStreamClient()
        assertEquals("MWEB", mapped.family)
        with(mapped.client) {
            assertEquals("MWEB", clientName)
            assertEquals("2.0", clientVersion)
            assertEquals("2", clientId)
            assertEquals("iPadOS", osName)
            assertEquals("Apple", deviceMake)
            assertEquals("32", androidSdkVersion)
            assertEquals(StreamProtocol.WEB_CIPHER_POT, protocol)
            assertTrue(loginSupported && loginRequired && skipHeadValidation && !isEmbedded)
            // The protocol drives the request-builder booleans.
            assertTrue(useSignatureTimestamp && useWebPoTokens)
        }
    }

    @Test
    fun `fromConfig splits entry 0 as main and keeps fallback order`() {
        val config = StreamClientParser.StreamClientConfig(
            clients = listOf(def("MAIN"), def("A"), def("B")),
            families = emptyMap(),
        )
        val table = StreamClientTable.fromConfig(config)
        assertEquals("MAIN", table.main.client.clientName)
        assertEquals(listOf("A", "B"), table.fallbacks.map { it.client.clientName })
    }

    @Test
    fun `compiled floor matches the bundled chain shape`() {
        val table = StreamClientTable.COMPILED_TABLE
        assertEquals("WEB_REMIX", table.main.client.clientName)
        assertTrue(table.main.client.skipHeadValidation)
        assertEquals(
            listOf("VISIONOS", "VISIONOS", "WEB_CREATOR", "TVHTML5_SIMPLY"),
            table.fallbacks.map { it.client.clientName },
        )
        assertEquals(
            listOf("VISIONOS", "VISIONOS", "WEB_CREATOR", "TVHTML5"),
            table.fallbacks.map { it.family },
        )
        assertEquals(
            listOf("WEB_REMIX", "VISIONOS", "VISIONOS_0_1", "WEB_CREATOR", "TVHTML5_SIMPLY"),
            (listOf(table.main) + table.fallbacks).map { it.key },
        )
        // The SABR roster the resolvers ran before the table existed, and WEB_REMIX's SABR identity.
        assertEquals(listOf("WEB_REMIX", "VISIONOS", "TVHTML5_SIMPLY"), table.sabrRoster.map { it.key })
        assertEquals(
            StreamClientParser.StreamClientDef.SabrInfo(osName = "Windows", osVersion = "10.0"),
            table.main.sabr,
        )
    }
}
