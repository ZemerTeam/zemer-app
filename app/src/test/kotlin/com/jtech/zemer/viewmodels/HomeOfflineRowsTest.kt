package com.jtech.zemer.viewmodels

import com.jtech.zemer.db.entities.Album
import com.jtech.zemer.db.entities.AlbumEntity
import com.jtech.zemer.db.entities.Artist
import com.jtech.zemer.db.entities.ArtistEntity
import com.jtech.zemer.db.entities.Song
import com.jtech.zemer.db.entities.SongEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * The offline-mode Home row math (manual offline mode, #366): the downloaded-only scoping of
 * Keep Listening / Forgotten Favorites and the Quick Picks shuffle cap. Pure functions — the
 * regression gate for the "offline mode must never surface something it can't play" rule.
 */
class HomeOfflineRowsTest {

    private fun song(
        id: String,
        downloaded: Boolean = true,
        isVideo: Boolean = false,
        isEpisode: Boolean = false,
    ) = Song(
        song = SongEntity(
            id = id,
            title = "title-$id",
            isDownloaded = downloaded,
            isVideo = isVideo,
            isEpisode = isEpisode,
        ),
        artists = emptyList(),
    )

    private fun artist(id: String) = Artist(
        artist = ArtistEntity(id = id, name = "artist-$id"),
        songCount = 1,
    )

    private fun album(id: String) = Album(
        album = AlbumEntity(id = id, title = "album-$id", songCount = 1, duration = 1),
    )

    @Test
    fun `keep listening keeps only downloaded songs albums and artists`() {
        val items = listOf(
            song("s1"), song("s2"),
            album("al1"), album("al2"),
            artist("ar1"), artist("ar2"),
        )
        val filtered = filterKeepListeningToDownloaded(
            items = items,
            downloadedSongIds = setOf("s1"),
            downloadedAlbumIds = setOf("al2"),
            downloadedArtistIds = setOf("ar1"),
        )
        assertEquals(listOf("s1", "al2", "ar1"), filtered.map { it.id })
    }

    @Test
    fun `keep listening drops everything when nothing is downloaded`() {
        val filtered = filterKeepListeningToDownloaded(
            items = listOf(song("s1"), album("al1"), artist("ar1")),
            downloadedSongIds = emptySet(),
            downloadedAlbumIds = emptySet(),
            downloadedArtistIds = emptySet(),
        )
        assertTrue(filtered.isEmpty())
    }

    @Test
    fun `offline quick picks caps at the max and keeps only given songs`() {
        val downloaded = (1..30).map { song("s$it") }
        val picks = offlineQuickPicks(downloaded, max = 20, random = Random(42))
        assertEquals(20, picks.size)
        assertTrue(picks.all { it.id in downloaded.map { d -> d.id } })
        assertEquals(picks.size, picks.distinctBy { it.id }.size)
    }

    @Test
    fun `offline quick picks returns everything when under the cap`() {
        val downloaded = (1..5).map { song("s$it") }
        assertEquals(5, offlineQuickPicks(downloaded, max = 20, random = Random(1)).size)
    }

    @Test
    fun `downloadedOnly drops non-downloaded episodes and gated videos`() {
        val songs = listOf(
            song("plain"),
            song("not-downloaded", downloaded = false),
            song("episode", isEpisode = true),
            song("video", isVideo = true),
        )
        assertEquals(
            listOf("plain", "video"),
            downloadedOnly(songs, includeVideos = true).map { it.id },
        )
        assertEquals(
            listOf("plain"),
            downloadedOnly(songs, includeVideos = false).map { it.id },
        )
    }
}
