package com.jtech.zemer.viewmodels

import com.jtech.zemer.search.ZemerResultMapper
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.EpisodeItem
import com.metrolist.innertube.pages.ArtistPage
import com.metrolist.innertube.pages.ArtistSection
import org.junit.Assert.assertEquals
import org.junit.Test

/** The channel-wide episodes paging append (appendChannelEpisodes): order, dedup, section isolation. */
class ArtistChannelEpisodesTest {

    private fun episode(id: String) = EpisodeItem(
        id = id,
        title = "ep $id",
        author = null,
        thumbnail = "https://example.com/$id.jpg",
    )

    private fun page(vararg sections: ArtistSection) = ArtistPage(
        artist = ArtistItem(
            id = "UCx",
            title = "Channel",
            thumbnail = null,
            shuffleEndpoint = null,
            radioEndpoint = null,
        ),
        sections = sections.toList(),
        description = null,
    )

    private fun episodesSection(vararg ids: String) =
        ArtistSection(ZemerResultMapper.TITLE_EPISODES, ids.map { episode(it) }, null)

    @Test
    fun `appends in order after the existing episodes`() {
        val out = appendChannelEpisodes(page(episodesSection("a", "b")), listOf(episode("c"), episode("d")))
        assertEquals(listOf("a", "b", "c", "d"), out.sections.single().items.map { it.id })
    }

    @Test
    fun `drops ids already in the section (offset pages can overlap after serve-time drops)`() {
        val out = appendChannelEpisodes(page(episodesSection("a", "b")), listOf(episode("b"), episode("c")))
        assertEquals(listOf("a", "b", "c"), out.sections.single().items.map { it.id })
    }

    @Test
    fun `other sections are untouched`() {
        val shows = ArtistSection("Podcasts", listOf(episode("show1")), null)
        val out = appendChannelEpisodes(page(shows, episodesSection("a")), listOf(episode("b")))
        assertEquals(listOf("show1"), out.sections[0].items.map { it.id })
        assertEquals(listOf("a", "b"), out.sections[1].items.map { it.id })
    }

    @Test
    fun `an empty page is a no-op and a missing section gains one only when items arrive`() {
        val untouched = page(episodesSection("a"))
        assertEquals(untouched, appendChannelEpisodes(untouched, emptyList()))

        val noSection = page()
        assertEquals(noSection, appendChannelEpisodes(noSection, emptyList()))
        val grown = appendChannelEpisodes(noSection, listOf(episode("a")))
        assertEquals(ZemerResultMapper.TITLE_EPISODES, grown.sections.single().title)
        assertEquals(listOf("a"), grown.sections.single().items.map { it.id })
    }
}
