package com.jtech.zemer.models

import com.jtech.zemer.db.entities.ArtistEntity
import com.jtech.zemer.db.entities.Song
import com.jtech.zemer.db.entities.SongEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * [withResolvedNavIds] — the player's navigation-id fill for name-only Zemer queue items (curated
 * playlists, community playlists, search rows), so the title/artist taps and the player menu's
 * view rows stop being silent no-ops there (issue #519).
 */
class MediaMetadataNavResolutionTest {
    private fun metadata(
        artists: List<MediaMetadata.Artist> = listOf(MediaMetadata.Artist(id = null, name = "Ben Zur")),
        album: MediaMetadata.Album? = null,
    ) = MediaMetadata(id = "vid1", title = "Song", artists = artists, duration = 200, album = album)

    private fun song(
        artists: List<ArtistEntity> = emptyList(),
        albumId: String? = null,
        albumName: String? = null,
    ) = Song(
        song = SongEntity(id = "vid1", title = "Song", albumId = albumId, albumName = albumName),
        artists = artists,
    )

    @Test
    fun `blank artist id fills from a name-matched real channel row`() {
        val resolved = metadata().withResolvedNavIds(
            song(artists = listOf(ArtistEntity(id = "UCben", name = "Ben Zur"))),
        )
        assertEquals("UCben", resolved.artists.single().id)
        assertEquals("Ben Zur", resolved.artists.single().name)
    }

    @Test
    fun `generated local artist row never fills - a dead artist page is worse than the no-op`() {
        val resolved = metadata().withResolvedNavIds(
            song(artists = listOf(ArtistEntity(id = "LAABCDEFGH", name = "Ben Zur"))),
        )
        assertNull(resolved.artists.single().id)
    }

    @Test
    fun `name mismatch does not fill`() {
        val resolved = metadata().withResolvedNavIds(
            song(artists = listOf(ArtistEntity(id = "UCother", name = "Someone Else"))),
        )
        assertNull(resolved.artists.single().id)
    }

    @Test
    fun `existing wire id is never overwritten`() {
        val resolved = metadata(artists = listOf(MediaMetadata.Artist(id = "UCwire", name = "Ben Zur")))
            .withResolvedNavIds(song(artists = listOf(ArtistEntity(id = "UCdb", name = "Ben Zur"))))
        assertEquals("UCwire", resolved.artists.single().id)
    }

    @Test
    fun `missing album fills from the song row`() {
        val resolved = metadata().withResolvedNavIds(song(albumId = "MPREb_x", albumName = "Album X"))
        assertEquals(MediaMetadata.Album(id = "MPREb_x", title = "Album X"), resolved.album)
    }

    @Test
    fun `existing album is never overwritten`() {
        val album = MediaMetadata.Album(id = "MPREb_wire", title = "Wire Album")
        val resolved = metadata(album = album).withResolvedNavIds(song(albumId = "MPREb_db"))
        assertEquals(album, resolved.album)
    }

    @Test
    fun `null song and no-op resolution return the same instance`() {
        val m = metadata()
        assertSame(m, m.withResolvedNavIds(null))
        assertSame(m, m.withResolvedNavIds(song()))
    }
}
