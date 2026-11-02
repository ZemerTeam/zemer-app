package com.jtech.zemer.utils

import com.jtech.zemer.db.entities.ArtistEntity
import com.jtech.zemer.db.entities.Song
import com.jtech.zemer.db.entities.SongEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VisiblePlaylistSongsTest {

    @Test
    fun `the filter sees the stored song's local artists and episode flag`() {
        val song = Song(
            song = SongEntity(id = "v1", title = "Song", isEpisode = true),
            artists = listOf(ArtistEntity(id = "UC_a", name = "A"), ArtistEntity(id = "UC_b", name = "B")),
        )
        val item = song.asFilterItem()
        assertEquals("v1", item.id)
        assertEquals(listOf("UC_a", "UC_b"), item.artists.map { it.id })
        assertTrue(item.isEpisode)
    }

    @Test
    fun `with nothing hidden a row index is its position`() {
        assertEquals(1 to 3, storedMovePositions(listOf(0, 1, 2, 3), from = 1, to = 3))
    }

    @Test
    fun `a drag past hidden songs lands on the stored position of the target row`() {
        // Stored positions 1 and 3 are hidden; visible rows hold positions 0, 2, 4, 5.
        assertEquals(0 to 4, storedMovePositions(listOf(0, 2, 4, 5), from = 0, to = 2))
        assertEquals(5 to 2, storedMovePositions(listOf(0, 2, 4, 5), from = 3, to = 1))
    }
}
