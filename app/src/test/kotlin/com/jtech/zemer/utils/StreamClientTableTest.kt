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
    ) = StreamClientParser.StreamClientDef(
        key = key,
        clientName = key,
        clientVersion = "1.0",
        clientId = "67",
        userAgent = "ua",
        protocol = protocol,
        family = key,
    )

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
    }
}
