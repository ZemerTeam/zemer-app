package com.jtech.zemer.models

import com.jtech.zemer.db.entities.Album
import com.jtech.zemer.db.entities.AlbumEntity
import com.jtech.zemer.db.entities.Artist
import com.jtech.zemer.db.entities.ArtistEntity
import com.jtech.zemer.db.entities.Song
import com.jtech.zemer.db.entities.SongEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the shared Room-entity → YTItem mapping ([toArtistItem]/[toAlbumItem]/[toSongItem]) — the
 * extraction from OnlineSearchViewModel's hand-rolled local search results, now also feeding the
 * offline-mode Home rows. A field drift here mis-renders or mis-routes local cards on BOTH surfaces.
 */
class LocalItemMappingTest {

    @Test
    fun `artist maps id title and thumbnail with no endpoints`() {
        val item = Artist(
            artist = ArtistEntity(id = "UCx", name = "Some Artist", thumbnailUrl = "https://t"),
            songCount = 3,
        ).toArtistItem()
        assertEquals("UCx", item.id)
        assertEquals("Some Artist", item.title)
        assertEquals("https://t", item.thumbnail)
        assertNull(item.shuffleEndpoint)
        assertNull(item.radioEndpoint)
    }

    @Test
    fun `album maps browseId playlistId artists and year`() {
        val item = Album(
            album = AlbumEntity(
                id = "MPREx", playlistId = "OLAKx", title = "An Album", year = 2024,
                thumbnailUrl = "https://a", songCount = 10, duration = 600,
            ),
            artists = listOf(ArtistEntity(id = "UCy", name = "The Artist")),
        ).toAlbumItem()
        assertEquals("MPREx", item.browseId)
        assertEquals("OLAKx", item.playlistId)
        assertEquals("An Album", item.title)
        assertEquals(2024, item.year)
        assertEquals("https://a", item.thumbnail)
        assertEquals(listOf("UCy"), item.artists?.map { it.id })
    }

    @Test
    fun `album with no playlistId falls back to the browseId`() {
        val item = Album(
            album = AlbumEntity(id = "MPREy", title = "No Playlist", songCount = 1, duration = 60),
        ).toAlbumItem()
        assertEquals("MPREy", item.playlistId)
    }

    @Test
    fun `song maps identity artists album and the classification flags`() {
        val item = Song(
            song = SongEntity(
                id = "vid1", title = "A Song", duration = 180, thumbnailUrl = "https://s",
                isVideo = true, isEpisode = false, explicit = true,
            ),
            artists = listOf(ArtistEntity(id = "UCz", name = "Singer")),
            album = AlbumEntity(id = "MPREz", title = "Their Album", songCount = 1, duration = 180),
        ).toSongItem()
        assertEquals("vid1", item.id)
        assertEquals("A Song", item.title)
        assertEquals(180, item.duration)
        assertEquals("https://s", item.thumbnail)
        assertEquals(listOf("UCz"), item.artists.map { it.id })
        assertEquals("MPREz", item.album?.id)
        assertTrue(item.isVideo)
        assertTrue(item.explicit)
        assertEquals(false, item.isEpisode)
    }

    @Test
    fun `song with no album and no thumbnail maps to null album and empty thumbnail`() {
        val item = Song(
            song = SongEntity(id = "vid2", title = "Bare"),
            artists = emptyList(),
        ).toSongItem()
        assertNull(item.album)
        assertEquals("", item.thumbnail)
    }
}
