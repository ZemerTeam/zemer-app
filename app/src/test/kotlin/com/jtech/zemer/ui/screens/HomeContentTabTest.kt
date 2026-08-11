package com.jtech.zemer.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Home content-tab selection rules — content-filter behavior, so it gets a regression gate:
 * PODCASTS is the one tab Block Podcasts removes (and falls back to MUSIC), while VIDEO is ALWAYS
 * shown (blocked-video users get it relabeled, never hidden — a visibility gate is a regression).
 */
class HomeContentTabTest {

    @Test
    fun `all four tabs visible with no filters`() {
        assertEquals(
            listOf(HomeContentTab.MUSIC, HomeContentTab.RADIO, HomeContentTab.PODCASTS, HomeContentTab.VIDEO),
            visibleHomeTabs(blockPodcasts = false),
        )
    }

    @Test
    fun `block podcasts drops only the podcasts tab`() {
        val tabs = visibleHomeTabs(blockPodcasts = true)
        assertFalse(HomeContentTab.PODCASTS in tabs)
        assertEquals(listOf(HomeContentTab.MUSIC, HomeContentTab.RADIO, HomeContentTab.VIDEO), tabs)
    }

    @Test
    fun `video tab is never hidden`() {
        assertTrue(HomeContentTab.VIDEO in visibleHomeTabs(blockPodcasts = false))
        assertTrue(HomeContentTab.VIDEO in visibleHomeTabs(blockPodcasts = true))
    }

    @Test
    fun `persisted podcasts tab falls back to music when blocked`() {
        assertEquals(HomeContentTab.MUSIC, effectiveHomeTab(HomeContentTab.PODCASTS, blockPodcasts = true))
        assertEquals(HomeContentTab.PODCASTS, effectiveHomeTab(HomeContentTab.PODCASTS, blockPodcasts = false))
    }

    @Test
    fun `other persisted tabs are untouched by the podcast filter`() {
        for (tab in listOf(HomeContentTab.MUSIC, HomeContentTab.RADIO, HomeContentTab.VIDEO)) {
            assertEquals(tab, effectiveHomeTab(tab, blockPodcasts = true))
            assertEquals(tab, effectiveHomeTab(tab, blockPodcasts = false))
        }
    }
}
