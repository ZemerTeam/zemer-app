package com.jtech.zemer.search

import com.jtech.zemer.search.ZemerResultMapper.toArtistPage
import com.jtech.zemer.search.ZemerResultMapper.toEpisodeItems
import com.jtech.zemer.search.ZemerResultMapper.toPodcastPage
import com.jtech.zemer.utils.BlockedIdsCache
import com.jtech.zemer.utils.ContentFilterConfig
import com.jtech.zemer.utils.ContentFilterState
import com.metrolist.innertube.models.EpisodeItem
import com.metrolist.innertube.models.PodcastItem
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Pure-JVM coverage of the podcast-discovery adaptation (Zemer server → the InnerTube podcast item/page
 * types the UI already renders). Guards the field mapping, paging cursor, host-channel sectioning, and
 * search folding the podcast screens depend on. Playback is out of scope (an episode plays by videoId).
 */
class ZemerPodcastMapperTest {

    @Before
    fun openFilters() {
        // No id-overrides, filters at defaults → dropBlocked keeps everything (tests the mapping, not the gate).
        BlockedIdsCache.updateAll(emptyMap())
        ContentFilterState.current = ContentFilterConfig()
    }

    @After
    fun reset() {
        BlockedIdsCache.updateAll(emptyMap())
        ContentFilterState.current = ContentFilterConfig()
    }

    private fun episode(videoId: String = "vid1") = ZemerPodcastEpisode(
        videoId = videoId,
        title = "Ep Title",
        podcastId = "MPSPshow",
        podcastName = "The Show",
        channelId = "UChost",
        thumbnail = "https://img/ep.jpg",
        durationSeconds = 2730,
        publishedAt = "2026-07-01",
    )

    @Test
    fun `episode maps to EpisodeItem carrying videoId, duration, show and host`() {
        val page = ZemerPodcastResponse(
            podcast = ZemerPodcastDetail(id = "MPSPshow", name = "The Show"),
            episodes = listOf(episode()),
            nextOffset = 30,
        ).toPodcastPage()!!
        val ep: EpisodeItem = page.episodes.single()
        assertEquals("vid1", ep.id)
        assertEquals(2730, ep.duration)
        assertEquals("2026-07-01", ep.publishDateText)
        assertEquals("https://img/ep.jpg", ep.thumbnail)
        // Owning show → EpisodeItem.podcast (powers "View podcast"); host channel → author id.
        assertEquals("MPSPshow", ep.podcast?.id)
        assertEquals("UChost", ep.author?.id)
        assertEquals("The Show", ep.author?.name)
    }

    @Test
    fun `zero duration becomes null and blank thumbnail falls back to the derived frame`() {
        val ep = ZemerPodcastResponse(
            podcast = ZemerPodcastDetail(id = "MPSPshow", name = "S"),
            episodes = listOf(episode().copy(durationSeconds = 0, thumbnail = null)),
        ).toPodcastPage()!!.episodes.single()
        assertNull(ep.duration)
        assertTrue(ep.thumbnail.contains("vid1"))
    }

    @Test
    fun `nextOffset becomes the page continuation, null at the end`() {
        val header = ZemerPodcastDetail(id = "MPSPshow", name = "S")
        assertEquals("30", ZemerPodcastResponse(header, listOf(episode()), nextOffset = 30).toPodcastPage()!!.continuation)
        assertNull(ZemerPodcastResponse(header, listOf(episode()), nextOffset = null).toPodcastPage()!!.continuation)
    }

    @Test
    fun `toPodcastPage is null when the show header is missing or blank`() {
        assertNull(ZemerPodcastResponse(podcast = null, episodes = listOf(episode())).toPodcastPage())
        assertNull(ZemerPodcastResponse(podcast = ZemerPodcastDetail(id = "", name = "x")).toPodcastPage())
    }

    @Test
    fun `blank-videoId episodes are dropped`() {
        val page = ZemerPodcastResponse(
            podcast = ZemerPodcastDetail(id = "MPSPshow", name = "S"),
            episodes = listOf(episode("good"), episode("")),
        ).toPodcastPage()!!
        assertEquals(listOf("good"), page.episodes.map { it.id })
    }

    @Test
    fun `host channel maps to an ArtistPage with a Podcasts shows section and an Episodes section`() {
        val page = ZemerPodcastChannelResponse(
            channel = ZemerPodcastChannelHeader(id = "UChost", name = "Kosherdotcom", thumbnail = "https://img/av.jpg"),
            shows = listOf(ZemerPodcastShow(id = "MPSPa", name = "Show A", channelId = "UChost", thumbnail = "t")),
            episodes = listOf(episode()),
        ).toArtistPage()!!
        assertEquals("UChost", page.artist.id)
        assertEquals("Kosherdotcom", page.artist.title)
        val podcastsSection = page.sections.first { it.title == "Podcasts" }
        assertTrue(podcastsSection.items.single() is PodcastItem)
        assertEquals("MPSPa", podcastsSection.items.single().id)
        val episodesSection = page.sections.first { it.title == "Episodes" }
        assertTrue(episodesSection.items.single() is EpisodeItem)
    }

    @Test
    fun `podcast channel is null when the channel header is missing`() {
        assertNull(ZemerPodcastChannelResponse(channel = null, shows = emptyList()).toArtistPage())
    }

    @Test
    fun `new episodes response maps its episodes`() {
        val eps = ZemerNewEpisodesResponse(episodes = listOf(episode("a"), episode("b"))).toEpisodeItems()
        assertEquals(listOf("a", "b"), eps.map { it.id })
    }

    private fun show(id: String, name: String = "Show $id") =
        ZemerPodcastShow(id = id, name = name, channelId = "UC$id", thumbnail = "t")

    @Test
    fun `podcast home rows map each row to its item type`() {
        val rows = ZemerResultMapper.podcastHomeRows(
            ZemerPodcastHomeRowsResponse(
                featured = listOf(show("MPSPf")),
                topPodcasts = listOf(show("MPSPa"), show("MPSPb")),
                trendingEpisodes = listOf(episode("ep1"), episode("ep2")),
            )
        )
        assertEquals(listOf("MPSPf"), rows.featured.map { it.id })
        assertEquals(listOf("MPSPa", "MPSPb"), rows.topPodcasts.map { it.id })
        assertTrue(rows.topPodcasts.all { it is PodcastItem })
        assertEquals(listOf("ep1", "ep2"), rows.trendingEpisodes.map { it.id })
        assertTrue(rows.trendingEpisodes.all { it is EpisodeItem })
    }

    @Test
    fun `podcast home rows drop blank ids and dedupe within a row`() {
        val rows = ZemerResultMapper.podcastHomeRows(
            ZemerPodcastHomeRowsResponse(
                topPodcasts = listOf(show("MPSPa"), show(""), show("MPSPa")),
                trendingEpisodes = listOf(episode("ep1"), episode(""), episode("ep1")),
            )
        )
        assertEquals(listOf("MPSPa"), rows.topPodcasts.map { it.id })
        assertEquals(listOf("ep1"), rows.trendingEpisodes.map { it.id })
    }

    @Test
    fun `an empty podcast-home-rows response yields empty rows`() {
        val rows = ZemerResultMapper.podcastHomeRows(ZemerPodcastHomeRowsResponse())
        assertTrue(rows.featured.isEmpty())
        assertTrue(rows.topPodcasts.isEmpty())
        assertTrue(rows.trendingEpisodes.isEmpty())
    }

    @Test
    fun `search folds podcast shows and episodes into their own summary sections`() {
        val resp = ZemerSearchResponse(
            categories = ZemerCategories(
                podcasts = listOf(ZemerPodcastShow(id = "MPSPa", name = "Show A", thumbnail = "t")),
                episodes = listOf(episode("ep1")),
            ),
        )
        val titles = ZemerResultMapper.summaryPage(resp, hideExplicit = false).summaries.map { it.title }
        assertTrue("Podcasts" in titles)
        assertTrue("Episodes" in titles)
    }
}
