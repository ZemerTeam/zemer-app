package com.jtech.zemer.lyrics

import org.junit.Assert.assertEquals
import org.junit.Test

/** The priority dialog's order math: one row per toggle, enabled only; a drag keeps disabled providers behind the enabled ones. */
class LyricsProviderOrderingTest {
    private val groups = listOf(listOf("Zemer"), listOf("SimpMusic"), listOf("LrcLib"), listOf("YouTube"))
    private val all = groups.flatten().toSet()

    @Test
    fun `rows follow the saved order`() {
        val rows = LyricsProviderOrdering.enabledGroups("YouTube,Zemer", groups, all)
        assertEquals(listOf(listOf("YouTube"), listOf("Zemer"), listOf("SimpMusic"), listOf("LrcLib")), rows)
    }

    @Test
    fun `disabled toggles are not rows`() {
        val enabled = all - setOf("LrcLib", "YouTube")
        assertEquals(listOf(listOf("Zemer"), listOf("SimpMusic")), LyricsProviderOrdering.enabledGroups("", groups, enabled))
    }

    @Test
    fun `a drag rewrites the enabled order and keeps disabled providers behind it in their old order`() {
        val enabled = all - setOf("LrcLib", "YouTube")
        val dragged = listOf(listOf("SimpMusic"), listOf("Zemer"))
        assertEquals("SimpMusic,Zemer,LrcLib,YouTube", LyricsProviderOrdering.reordered("", dragged, enabled))
        // the disabled tail keeps a previously saved relative order
        assertEquals("SimpMusic,Zemer,YouTube,LrcLib", LyricsProviderOrdering.reordered("YouTube", dragged, enabled))
    }

    @Test
    fun `re-enabling a provider lands where it was`() {
        val saved = LyricsProviderOrdering.reordered("", listOf(listOf("SimpMusic"), listOf("Zemer")), setOf("Zemer", "SimpMusic"))
        assertEquals(listOf(listOf("SimpMusic"), listOf("Zemer"), listOf("LrcLib"), listOf("YouTube")), LyricsProviderOrdering.enabledGroups(saved, groups, all))
    }
}
