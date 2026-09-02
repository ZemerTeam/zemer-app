package com.jtech.zemer.utils

import com.metrolist.innertube.models.StreamProtocol
import com.metrolist.innertube.models.YouTubeClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamClientChainTest {

    private fun client(name: String, family: String = name, version: String = "1.0") = StreamClient(
        client = YouTubeClient(
            clientName = name,
            clientVersion = version,
            clientId = "1",
            userAgent = "ua",
        ),
        family = family,
    )

    private val main = client("WEB_REMIX")
    private val visionos = client("VISIONOS", version = "1.02")
    private val visionosOld = client("VISIONOS", version = "0.1")
    private val webCreator = client("WEB_CREATOR")
    private val tvhtml5 = client("TVHTML5_SIMPLY", family = "TVHTML5")
    private val fallbacks = listOf(visionos, visionosOld, webCreator, tvhtml5)

    @Test
    fun `nothing disabled keeps main and full fallback order`() {
        val chain = StreamClientChain.resolve(main, fallbacks, emptySet())!!
        assertEquals(main.client, chain.main)
        assertEquals(fallbacks.map { it.client }, chain.fallbacks)
    }

    @Test
    fun `disabling a family drops its entries, order preserved`() {
        val chain = StreamClientChain.resolve(main, fallbacks, setOf("VISIONOS"))!!
        assertEquals(main.client, chain.main)
        assertEquals(listOf(webCreator.client, tvhtml5.client), chain.fallbacks)
    }

    @Test
    fun `a family disable keys on family, not clientName`() {
        // TVHTML5_SIMPLY's toggle family is "TVHTML5" — disabling the family must drop it.
        val chain = StreamClientChain.resolve(main, fallbacks, setOf("TVHTML5"))!!
        assertEquals(listOf(visionos.client, visionosOld.client, webCreator.client), chain.fallbacks)
        // Disabling by the clientName string does nothing (not a family id).
        val byName = StreamClientChain.resolve(main, fallbacks, setOf("TVHTML5_SIMPLY"))!!
        assertEquals(fallbacks.map { it.client }, byName.fallbacks)
    }

    @Test
    fun `disabled main promotes first enabled fallback and drops its same-name twin`() {
        val chain = StreamClientChain.resolve(main, fallbacks, setOf("WEB_REMIX"))!!
        assertEquals(visionos.client, chain.main)
        // VISIONOS_0_1 shares the promoted main's clientName, so it leaves the chain too.
        assertEquals(listOf(webCreator.client, tvhtml5.client), chain.fallbacks)
    }

    @Test
    fun `disabled main and first family promotes the next enabled fallback`() {
        val chain = StreamClientChain.resolve(main, fallbacks, setOf("WEB_REMIX", "VISIONOS"))!!
        assertEquals(webCreator.client, chain.main)
        assertEquals(listOf(tvhtml5.client), chain.fallbacks)
    }

    @Test
    fun `everything disabled resolves to null`() {
        assertNull(
            StreamClientChain.resolve(
                main, fallbacks, setOf("WEB_REMIX", "VISIONOS", "WEB_CREATOR", "TVHTML5"),
            ),
        )
    }

    @Test
    fun `only main enabled leaves an empty fallback list`() {
        val chain = StreamClientChain.resolve(
            main, fallbacks, setOf("VISIONOS", "WEB_CREATOR", "TVHTML5"),
        )!!
        assertEquals(main.client, chain.main)
        assertTrue(chain.fallbacks.isEmpty())
    }

    @Test
    fun `protocol derives the request-builder booleans`() {
        // The single-source rule: useSignatureTimestamp/useWebPoTokens must track the protocol.
        val web = client("X").client.copy(protocol = StreamProtocol.WEB_CIPHER_POT)
        assertTrue(web.useSignatureTimestamp)
        assertTrue(web.useWebPoTokens)
        val direct = client("Y").client
        assertEquals(StreamProtocol.DIRECT, direct.protocol)
        assertTrue(!direct.useSignatureTimestamp && !direct.useWebPoTokens)
    }
}
