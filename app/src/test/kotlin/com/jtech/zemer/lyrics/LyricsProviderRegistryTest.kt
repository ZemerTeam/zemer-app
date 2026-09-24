package com.jtech.zemer.lyrics

import org.junit.Assert.assertEquals
import org.junit.Test

/** The order preference round-trips, drops unknown names, and appends providers an old preference never listed. */
class LyricsProviderRegistryTest {
    @Test
    fun `blank preference is the default accuracy order`() {
        assertEquals(listOf("Zemer", "SimpMusic", "Musixmatch", "LrcLib", "YouTubeSubtitle", "YouTube"), LyricsProviderRegistry.deserializeProviderOrder(""))
        assertEquals(LyricsProviderRegistry.providerNames, LyricsProviderRegistry.getDefaultProviderOrder())
    }

    @Test
    fun `a saved order is honoured and providers it never listed keep their default slot at the end`() {
        assertEquals(listOf("LrcLib", "Zemer", "SimpMusic", "Musixmatch", "YouTubeSubtitle", "YouTube"), LyricsProviderRegistry.deserializeProviderOrder("LrcLib, Zemer"))
    }

    @Test
    fun `unknown names are dropped on read and write, duplicates collapse`() {
        assertEquals(LyricsProviderRegistry.providerNames, LyricsProviderRegistry.deserializeProviderOrder("Genius,Nope"))
        assertEquals("Zemer,LrcLib", LyricsProviderRegistry.serializeProviderOrder(listOf("Zemer", "Genius", "LrcLib", "Zemer")))
    }

    @Test
    fun `serialise then deserialise is the identity for a full order`() {
        val full = listOf("YouTube", "YouTubeSubtitle", "LrcLib", "Musixmatch", "SimpMusic", "Zemer")
        assertEquals(full, LyricsProviderRegistry.deserializeProviderOrder(LyricsProviderRegistry.serializeProviderOrder(full)))
        assertEquals(full, LyricsProviderRegistry.getOrderedProviders(full.joinToString(",")).map { it.name.let { n -> LyricsProviderRegistry.providerNames.first { key -> LyricsProviderRegistry.getProviderByName(key)!!.name == n } } })
    }
}
