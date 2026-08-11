package com.jtech.zemer.ui.screens.search

import com.jtech.zemer.search.ZEMER_FILTER_EPISODE
import com.jtech.zemer.search.ZEMER_FILTER_PODCAST
import com.metrolist.innertube.YouTube.SearchFilter.Companion.FILTER_SONG
import com.metrolist.innertube.models.Artist
import com.metrolist.innertube.models.EpisodeItem
import com.metrolist.innertube.models.PodcastItem
import com.metrolist.innertube.models.SongItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Block Podcasts enforcement on search is CLIENT-ONLY (the flag never rides the server
 * request), so these two predicates are the whole gate: a selected podcast/episode chip must not
 * survive the flag, and podcast/episode rows must drop from result lists.
 */
class SearchFilterPolicyTest {

    private val show = PodcastItem(
        id = "MPSP1", title = "Show", author = null, episodeCountText = null,
        thumbnail = null, playEndpoint = null, shuffleEndpoint = null,
    )
    private val episode = EpisodeItem(
        id = "v1", title = "Ep", author = Artist(name = "Host", id = "UC1"), thumbnail = "t",
    )
    private val song = SongItem(
        id = "v2", title = "Song", artists = listOf(Artist(name = "A", id = "UC2")), thumbnail = "t",
    )

    @Test
    fun `podcast and episode chips are disallowed only while podcasts are blocked`() {
        assertFalse(searchFilterAllowed(ZEMER_FILTER_PODCAST, blockPodcasts = true))
        assertFalse(searchFilterAllowed(ZEMER_FILTER_EPISODE, blockPodcasts = true))
        assertTrue(searchFilterAllowed(ZEMER_FILTER_PODCAST, blockPodcasts = false))
        assertTrue(searchFilterAllowed(ZEMER_FILTER_EPISODE, blockPodcasts = false))
    }

    @Test
    fun `other chips and the summary view are always allowed`() {
        assertTrue(searchFilterAllowed(null, blockPodcasts = true))
        assertTrue(searchFilterAllowed(FILTER_SONG, blockPodcasts = true))
    }

    @Test
    fun `blocked podcasts drop shows and episodes from result lists, keep everything else`() {
        val items = listOf(show, episode, song)
        assertEquals(listOf(song), dropBlockedPodcastItems(items, blockPodcasts = true))
        assertEquals(items, dropBlockedPodcastItems(items, blockPodcasts = false))
    }
}
