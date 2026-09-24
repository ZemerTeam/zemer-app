package com.jtech.zemer.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

/** The KidZone content-tab rules (the Home-selector pattern). */
class KidZoneTabTest {

    @Test
    fun `podcasts tab shows only when podcasts are not blocked`() {
        assertEquals(listOf(KidZoneTab.ARTISTS, KidZoneTab.PODCASTS), visibleKidZoneTabs(blockPodcasts = false))
        assertEquals(listOf(KidZoneTab.ARTISTS), visibleKidZoneTabs(blockPodcasts = true))
    }

    @Test
    fun `a selected tab that got blocked falls back to artists`() {
        val blocked = visibleKidZoneTabs(blockPodcasts = true)
        assertEquals(KidZoneTab.ARTISTS, effectiveKidZoneTab(KidZoneTab.PODCASTS, blocked))
    }

    @Test
    fun `a visible selection is honored`() {
        val open = visibleKidZoneTabs(blockPodcasts = false)
        assertEquals(KidZoneTab.PODCASTS, effectiveKidZoneTab(KidZoneTab.PODCASTS, open))
        assertEquals(KidZoneTab.ARTISTS, effectiveKidZoneTab(KidZoneTab.ARTISTS, open))
    }
}
