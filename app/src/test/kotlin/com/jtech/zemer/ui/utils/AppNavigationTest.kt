package com.jtech.zemer.ui.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The blank-id guard that prevents the "artist/" / "album/" crash: a real id builds the route, a
 * null/blank/whitespace id yields null (navigation is then skipped instead of navigating to a dead
 * route). A valid id must build the exact same string the call sites used to interpolate by hand,
 * so the swap is behavior-identical for real ids.
 */
class AppNavigationTest {
    @Test
    fun artistRoute_realId_buildsRoute() {
        assertEquals("artist/UC123", artistRoute("UC123"))
        assertEquals("artist/UCabc_DEF-45", artistRoute("UCabc_DEF-45"))
    }

    @Test
    fun kidZoneContext_ridesTheRoutes() {
        // The KidZone navigation flag (drill-in discipline, issue-free leak path): shows opened
        // from the KidZone grid, and channels opened from those shows, keep kidZone=true.
        assertEquals("online_podcast/MPSP1?kidZone=true", podcastRoute("MPSP1", kidZone = true))
        assertEquals("online_podcast/MPSP1", podcastRoute("MPSP1"))
        assertEquals(
            "artist/UC1?isPodcastChannel=true&kidZone=true",
            artistRoute("UC1", isPodcastChannel = true, kidZone = true),
        )
        // kidZone is a podcast-context flag: it never changes a music artist route.
        assertEquals("artist/UC1", artistRoute("UC1", isPodcastChannel = false, kidZone = true))
        assertNull(podcastRoute("", kidZone = true))
    }

    @Test
    fun artistRoute_nullOrBlank_isNull() {
        assertNull(artistRoute(null))
        assertNull(artistRoute(""))
        assertNull(artistRoute("   "))
        assertNull(artistRoute("\t"))
    }

    @Test
    fun artistRoute_podcastChannel_addsFlag() {
        assertEquals("artist/UC123?isPodcastChannel=true", artistRoute("UC123", isPodcastChannel = true))
        // The flag never resurrects a blank id (still a no-op route).
        assertNull(artistRoute("", isPodcastChannel = true))
    }

    @Test
    fun albumRoute_realId_buildsRoute() {
        assertEquals("album/MPREb_abc", albumRoute("MPREb_abc"))
    }

    @Test
    fun albumRoute_nullOrBlank_isNull() {
        assertNull(albumRoute(null))
        assertNull(albumRoute(""))
        assertNull(albumRoute("  "))
    }

    @Test
    fun podcastRoute_realId_buildsRoute() {
        assertEquals("online_podcast/MPSPabc123", podcastRoute("MPSPabc123"))
    }

    @Test
    fun podcastRoute_nullOrBlank_isNull() {
        assertNull(podcastRoute(null))
        assertNull(podcastRoute(""))
        assertNull(podcastRoute("   "))
    }

    @Test
    fun whitelistedPodcastRoute_prefersChannelWhenKnown() {
        // A known channel opens the host CHANNEL page (with the podcast-channel flag)...
        assertEquals(
            "artist/UChost?isPodcastChannel=true",
            whitelistedPodcastRoute("MPSPshow", "UChost"),
        )
    }

    @Test
    fun whitelistedPodcastRoute_fallsBackToShowWithoutChannel() {
        // ...and falls back to the show's episode list when there is no channel.
        assertEquals("online_podcast/MPSPshow", whitelistedPodcastRoute("MPSPshow", null))
        assertEquals("online_podcast/MPSPshow", whitelistedPodcastRoute("MPSPshow", "  "))
    }

    @Test
    fun whitelistedPodcastRoute_bothBlankIsNull() {
        assertNull(whitelistedPodcastRoute(null, null))
        assertNull(whitelistedPodcastRoute("", ""))
    }

    @Test
    fun channelDeepLink_artistWhitelistWinsThenPodcastThenNothing() {
        assertEquals("artist/UC1", channelDeepLinkRoute("UC1", artistWhitelisted = true, podcastWhitelisted = true))
        assertEquals(
            "artist/UC1?isPodcastChannel=true",
            channelDeepLinkRoute("UC1", artistWhitelisted = false, podcastWhitelisted = true),
        )
        assertNull(channelDeepLinkRoute("UC1", artistWhitelisted = false, podcastWhitelisted = false))
        assertNull(channelDeepLinkRoute("", artistWhitelisted = true, podcastWhitelisted = true))
    }
}
