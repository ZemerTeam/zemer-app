package com.jtech.zemer.utils

import com.metrolist.innertube.models.StreamProtocol
import com.metrolist.innertube.models.YouTubeClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamClientChainTest {

    private fun client(name: String, version: String = "1.0") = YouTubeClient(
        clientName = name,
        clientVersion = version,
        clientId = "1",
        userAgent = "ua",
    )

    private val main = client("WEB_REMIX")
    private val visionos = client("VISIONOS", "1.02")
    private val visionosOld = client("VISIONOS", "0.1")
    private val webCreator = client("WEB_CREATOR")
    private val androidVr = client("ANDROID_VR")
    private val fallbacks = listOf(visionos, visionosOld, webCreator, androidVr)

    @Test
    fun `nothing disabled keeps main and full fallback order`() {
        val chain = StreamClientChain.resolve(main, fallbacks, emptySet())!!
        assertEquals(main, chain.main)
        assertEquals(fallbacks, chain.fallbacks)
    }

    @Test
    fun `disabled fallback family drops out, order preserved`() {
        val chain = StreamClientChain.resolve(main, fallbacks, setOf("WEB_CREATOR"))!!
        assertEquals(main, chain.main)
        assertEquals(listOf(visionos, visionosOld, androidVr), chain.fallbacks)
    }

    @Test
    fun `disabled main promotes first enabled fallback and drops its same-name twin`() {
        val chain = StreamClientChain.resolve(main, fallbacks, setOf("WEB_REMIX"))!!
        assertEquals(visionos, chain.main)
        // VISIONOS_0_1 shares the promoted main's clientName, so it leaves the chain too
        // (the pre-extraction inline behavior, preserved deliberately).
        assertEquals(listOf(webCreator, androidVr), chain.fallbacks)
    }

    @Test
    fun `disabled main and first family promotes the next enabled fallback`() {
        val chain = StreamClientChain.resolve(main, fallbacks, setOf("WEB_REMIX", "VISIONOS"))!!
        assertEquals(webCreator, chain.main)
        assertEquals(listOf(androidVr), chain.fallbacks)
    }

    @Test
    fun `everything disabled resolves to null`() {
        assertNull(
            StreamClientChain.resolve(
                main, fallbacks, setOf("WEB_REMIX", "VISIONOS", "WEB_CREATOR", "ANDROID_VR"),
            ),
        )
    }

    @Test
    fun `only main enabled leaves an empty fallback list`() {
        val chain = StreamClientChain.resolve(
            main, fallbacks, setOf("VISIONOS", "WEB_CREATOR", "ANDROID_VR"),
        )!!
        assertEquals(main, chain.main)
        assertTrue(chain.fallbacks.isEmpty())
    }

    @Test
    fun `protocol derives the request-builder booleans`() {
        // The single-source rule: useSignatureTimestamp/useWebPoTokens must track the protocol.
        val web = client("X").copy(protocol = StreamProtocol.WEB_CIPHER_POT)
        assertTrue(web.useSignatureTimestamp)
        assertTrue(web.useWebPoTokens)
        val direct = client("Y")
        assertEquals(StreamProtocol.DIRECT, direct.protocol)
        assertTrue(!direct.useSignatureTimestamp && !direct.useWebPoTokens)
    }
}
