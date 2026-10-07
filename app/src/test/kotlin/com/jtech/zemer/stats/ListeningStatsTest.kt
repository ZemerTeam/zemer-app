package com.jtech.zemer.stats

import com.jtech.zemer.db.entities.Artist
import com.jtech.zemer.db.entities.ArtistEntity
import com.jtech.zemer.db.entities.Song
import com.jtech.zemer.db.entities.SongEntity
import com.jtech.zemer.db.entities.SongPlayStats
import org.junit.Assert.assertEquals
import org.junit.Test

class ListeningStatsTest {
    private fun song(id: String, plays: Int, ms: Long) =
        SongPlayStats(Song(song = SongEntity(id = id, title = id), artists = emptyList()), plays, ms)

    private fun artist(id: String) = Artist(artist = ArtistEntity(id = id, name = id), songCount = 1)

    @Test
    fun `summary totals the ranked songs and counts the artists`() {
        val stats = buildListeningStats(
            songs = listOf(song("a", 3, 120_000), song("b", 2, 30_000)),
            artists = listOf(artist("UC1"), artist("UC2"), artist("UC3")),
        )
        assertEquals(StatsSummary(timeListenedMs = 150_000, plays = 5, songs = 2, artists = 3), stats.summary)
        assertEquals(listOf("a", "b"), stats.songs.map { it.song.id })
    }

    @Test
    fun `an empty window summarizes to zero`() {
        assertEquals(StatsSummary(0, 0, 0, 0), buildListeningStats(emptyList(), emptyList()).summary)
    }
}
