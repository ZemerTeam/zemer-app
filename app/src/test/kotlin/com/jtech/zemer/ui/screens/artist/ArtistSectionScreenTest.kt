package com.jtech.zemer.ui.screens.artist

import com.metrolist.innertube.models.Artist
import com.metrolist.innertube.models.EpisodeItem
import org.junit.Assert.assertEquals
import org.junit.Test

class ArtistSectionScreenTest {

    private fun episode(id: String, title: String) = EpisodeItem(
        id = id,
        title = title,
        author = Artist(name = "a", id = null),
        thumbnail = "th",
    )

    // The channel-episode search: case-insensitive title contains, blank/whitespace query = the
    // plain paged list (never an accidental empty filter).
    @Test
    fun filterChannelEpisodes_matchesCaseInsensitive() {
        val episodes = listOf(
            episode("e1", "Greenspan on Supplements"),
            episode("e2", "Chosson Class"),
            episode("e3", "greenspan gizbor talk"),
        )
        assertEquals(listOf("e1", "e3"), filterChannelEpisodes(episodes, "greenspan").map { it.id })
        assertEquals(listOf("e2"), filterChannelEpisodes(episodes, "  chosson ").map { it.id })
    }

    @Test
    fun filterChannelEpisodes_blankQuery_passesEverything() {
        val episodes = listOf(episode("e1", "A"), episode("e2", "B"))
        assertEquals(episodes, filterChannelEpisodes(episodes, ""))
        assertEquals(episodes, filterChannelEpisodes(episodes, "   "))
    }
}
