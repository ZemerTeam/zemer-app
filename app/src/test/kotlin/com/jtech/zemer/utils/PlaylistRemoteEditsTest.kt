package com.jtech.zemer.utils

import com.jtech.zemer.models.MediaMetadata
import com.metrolist.innertube.models.Album
import com.metrolist.innertube.models.Artist
import com.metrolist.innertube.models.SongItem
import com.jtech.zemer.models.toMediaMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaylistRemoteEditsTest {

    private fun metadata(id: String, setVideoId: String?) = MediaMetadata(
        id = id,
        title = "t-$id",
        artists = emptyList(),
        duration = 100,
        setVideoId = setVideoId,
    )

    // --- playlistSongMaps: the sync must persist setVideoId (its omission silently broke remote edits) ---

    @Test
    fun `sync rows carry setVideoId and remote order`() {
        val maps = playlistSongMaps(
            listOf(metadata("a", "SVID_A"), metadata("b", null), metadata("c", "SVID_C")),
            playlistId = "pl",
        )
        assertEquals(listOf("a", "b", "c"), maps.map { it.songId })
        assertEquals(listOf(0, 1, 2), maps.map { it.position })
        assertEquals(listOf("SVID_A", null, "SVID_C"), maps.map { it.setVideoId })
        assertEquals(listOf("pl", "pl", "pl"), maps.map { it.playlistId })
    }

    // --- remotePlaylistRemovalArgs: every gate that must skip the remote call ---

    @Test
    fun `removal skipped for anonymous account`() {
        assertNull(remotePlaylistRemovalArgs(false, "VL123", "vid", "SVID"))
    }

    @Test
    fun `removal skipped for local-only playlist`() {
        assertNull(remotePlaylistRemovalArgs(true, null, "vid", "SVID"))
    }

    @Test
    fun `removal skipped without a setVideoId`() {
        assertNull(remotePlaylistRemovalArgs(true, "VL123", "vid", null))
    }

    @Test
    fun `removal args pass through when all conditions hold`() {
        val args = remotePlaylistRemovalArgs(true, "VL123", "vid", "SVID")
        assertEquals(RemotePlaylistRemoval("VL123", "vid", "SVID"), args)
    }

    // --- setVideoId propagation from the innertube parser output into MediaMetadata ---

    @Test
    fun `SongItem toMediaMetadata preserves setVideoId`() {
        val item = SongItem(
            id = "vid",
            title = "song",
            artists = listOf(Artist(name = "a", id = "ch")),
            album = Album(name = "al", id = "alb"),
            duration = 200,
            thumbnail = "https://example.com/t.jpg",
            explicit = false,
            setVideoId = "SVID",
        )
        assertEquals("SVID", item.toMediaMetadata().setVideoId)
    }

    @Test
    fun `SongItem toMediaMetadata keeps null setVideoId null`() {
        val item = SongItem(
            id = "vid",
            title = "song",
            artists = emptyList(),
            album = null,
            duration = 200,
            thumbnail = "https://example.com/t.jpg",
            explicit = false,
        )
        assertNull(item.toMediaMetadata().setVideoId)
    }
}
