package com.jtech.zemer.lyrics

import org.junit.Assert.assertEquals
import org.junit.Test

/** The priority dialog's order math: one row per toggle, enabled only; a drag keeps disabled providers behind the enabled ones. */
class LyricsProviderOrderingTest {
    private val groups = listOf(listOf("Zemer"), listOf("SimpMusic"), listOf("Musixmatch"), listOf("LrcLib"), listOf("YouTubeSubtitle", "YouTube"))
    private val all = groups.flatten().toSet()

    @Test
    fun `YouTube's two registry entries collapse into one row placed at the first-listed id`() {
        val rows = LyricsProviderOrdering.enabledGroups("YouTube,Zemer,YouTubeSubtitle", groups, all)
        assertEquals(listOf(listOf("YouTubeSubtitle", "YouTube"), listOf("Zemer"), listOf("SimpMusic"), listOf("Musixmatch"), listOf("LrcLib")), rows)
    }

    @Test
    fun `disabled toggles are not rows`() {
        val enabled = all - setOf("LrcLib", "YouTubeSubtitle", "YouTube")
        assertEquals(listOf(listOf("Zemer"), listOf("SimpMusic"), listOf("Musixmatch")), LyricsProviderOrdering.enabledGroups("", groups, enabled))
    }

    @Test
    fun `a drag rewrites the enabled order and keeps disabled providers behind it in their old order`() {
        val enabled = all - setOf("LrcLib", "YouTubeSubtitle", "YouTube")
        val dragged = listOf(listOf("Musixmatch"), listOf("Zemer"), listOf("SimpMusic"))
        assertEquals("Musixmatch,Zemer,SimpMusic,LrcLib,YouTubeSubtitle,YouTube", LyricsProviderOrdering.reordered("", dragged, enabled))
        // the disabled tail keeps a previously saved relative order
        assertEquals("Musixmatch,Zemer,SimpMusic,YouTube,LrcLib,YouTubeSubtitle", LyricsProviderOrdering.reordered("YouTube,LrcLib", dragged, enabled))
    }

    @Test
    fun `re-enabling a provider lands where it was`() {
        val saved = LyricsProviderOrdering.reordered("", listOf(listOf("SimpMusic"), listOf("Zemer")), setOf("Zemer", "SimpMusic"))
        assertEquals(listOf(listOf("SimpMusic"), listOf("Zemer"), listOf("Musixmatch"), listOf("LrcLib"), listOf("YouTubeSubtitle", "YouTube")), LyricsProviderOrdering.enabledGroups(saved, groups, all))
    }
}
