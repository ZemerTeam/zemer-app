package com.jtech.zemer.search

import com.metrolist.innertube.YouTube.SearchFilter
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.SongItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM coverage of the Zemer → YTItem adaptation that lets the existing search UI render Zemer
 * results unchanged. Guards the contracts the screens depend on: derived thumbnails, null endpoints
 * (playback falls back to the videoId), albums+singles merging, videos-as-SongItem, both playlist
 * chips, hide-explicit, and the summary section order.
 */
class ZemerResultMapperTest {

    private val titles = SectionTitles(
        songs = "Songs",
        videos = "Videos",
        albums = "Albums",
        artists = "Artists",
        playlists = "Playlists",
    )

    @Test
    fun `song maps to playable SongItem with derived thumbnail and null endpoint`() {
        val resp = ZemerSearchResponse(
            categories = ZemerCategories(songs = listOf(ZemerTrack("vid123", "Title", "Artist"))),
        )

        val song = ZemerResultMapper.summaryPage(resp, titles, hideExplicit = false)
            .summaries.single().items.single() as SongItem

        assertEquals("vid123", song.id)
        assertEquals("Title", song.title)
        assertEquals("Artist", song.artists.single().name)
        assertNull(song.artists.single().id)
        assertNull(song.endpoint)
        assertEquals("https://i.ytimg.com/vi/vid123/hqdefault.jpg", song.thumbnail)
    }

    @Test
    fun `hideExplicit drops only explicit songs`() {
        val resp = ZemerSearchResponse(
            categories = ZemerCategories(
                songs = listOf(
                    ZemerTrack("a", "Clean", "X", explicit = false),
                    ZemerTrack("b", "Dirty", "Y", explicit = true),
                ),
            ),
        )

        val kept = ZemerResultMapper.summaryPage(resp, titles, hideExplicit = true)
            .summaries.single().items
        assertEquals(1, kept.size)
        assertEquals("a", kept.single().id)

        val all = ZemerResultMapper.summaryPage(resp, titles, hideExplicit = false)
            .summaries.single().items
        assertEquals(2, all.size)
    }

    @Test
    fun `albums and singles merge under one Albums section with playlistId fallback`() {
        val resp = ZemerSearchResponse(
            categories = ZemerCategories(
                albums = listOf(ZemerAlbum(id = "al1", title = "Album", artist = "A", year = 2020)),
                singles = listOf(ZemerAlbum(id = "si1", playlistId = "PLsi1", title = "Single", artist = "")),
            ),
        )

        val section = ZemerResultMapper.summaryPage(resp, titles, hideExplicit = false).summaries.single()
        assertEquals("Albums", section.title)
        assertEquals(2, section.items.size)

        val album = section.items[0] as AlbumItem
        assertEquals("al1", album.browseId)
        assertEquals("al1", album.playlistId) // null playlistId falls back to the browseId
        assertEquals(2020, album.year)

        val single = section.items[1] as AlbumItem
        assertEquals("PLsi1", single.playlistId)
        assertNull(single.artists) // blank artist => no artist list
    }

    @Test
    fun `summary omits empty sections and keeps a stable order`() {
        val resp = ZemerSearchResponse(
            categories = ZemerCategories(
                artists = listOf(ZemerArtist("UC1", "An Artist", "thumb")),
                songs = listOf(ZemerTrack("s1", "Song", "A")),
                albums = listOf(ZemerAlbum(id = "al1", title = "Album", artist = "A")),
                videos = listOf(ZemerTrack("v1", "Video", "A")),
                playlists = listOf(ZemerPlaylist("pl1", "Playlist", "A", "t")),
            ),
        )

        val page = ZemerResultMapper.summaryPage(resp, titles, hideExplicit = false)
        // No "Videos" section: videos render as SongItem and the shared OnlineSearchResult resolves a
        // section's filter by item type (SongItem -> Songs), so a Videos section would mis-navigate.
        assertEquals(listOf("Songs", "Artists", "Albums", "Playlists"), page.summaries.map { it.title })
    }

    @Test
    fun `suggestions de-dupe ids shared across categories`() {
        // The same videoId appears as both a song and a video — the id-keyed dropdown must not get a dupe.
        val resp = ZemerSearchResponse(
            categories = ZemerCategories(
                songs = listOf(ZemerTrack("dup", "Track", "A")),
                videos = listOf(ZemerTrack("dup", "Track", "A")),
            ),
        )

        val items = ZemerResultMapper.suggestions(resp, hideExplicit = false).recommendedItems
        assertEquals(1, items.size)
        assertEquals(items.size, items.distinctBy { it.id }.size)
    }

    @Test
    fun `rows missing an id are dropped, not crashing the whole response`() {
        val resp = ZemerSearchResponse(
            categories = ZemerCategories(
                songs = listOf(ZemerTrack("", "No id", "A"), ZemerTrack("ok", "Good", "A")),
                artists = listOf(ZemerArtist("", "No id"), ZemerArtist("UC1", "Good")),
            ),
        )

        val songs = ZemerResultMapper.filtered(resp, SearchFilter.FILTER_SONG, false).items
        assertEquals(listOf("ok"), songs.map { it.id })
        val artists = ZemerResultMapper.filtered(resp, SearchFilter.FILTER_ARTIST, false).items
        assertEquals(listOf("UC1"), artists.map { it.id })
    }

    @Test
    fun `filtered FILTER_ALBUM includes singles and has no continuation`() {
        val resp = ZemerSearchResponse(
            categories = ZemerCategories(
                albums = listOf(ZemerAlbum(id = "al1", title = "Album", artist = "A")),
                singles = listOf(ZemerAlbum(id = "si1", title = "Single", artist = "A")),
            ),
        )

        val result = ZemerResultMapper.filtered(resp, SearchFilter.FILTER_ALBUM, hideExplicit = false)
        assertEquals(2, result.items.size)
        assertNull(result.continuation)
    }

    @Test
    fun `filtered FILTER_VIDEO maps videos to SongItem`() {
        val resp = ZemerSearchResponse(
            categories = ZemerCategories(videos = listOf(ZemerTrack("v1", "Live", "A"))),
        )

        val item = ZemerResultMapper.filtered(resp, SearchFilter.FILTER_VIDEO, hideExplicit = false).items.single()
        assertTrue(item is SongItem)
        assertEquals("v1", item.id)
    }

    @Test
    fun `both playlist chips return the single Zemer playlist category`() {
        val resp = ZemerSearchResponse(
            categories = ZemerCategories(playlists = listOf(ZemerPlaylist("pl1", "PL", "A", "t"))),
        )

        val community = ZemerResultMapper.filtered(resp, SearchFilter.FILTER_COMMUNITY_PLAYLIST, false).items
        val featured = ZemerResultMapper.filtered(resp, SearchFilter.FILTER_FEATURED_PLAYLIST, false).items
        assertEquals(1, community.size)
        assertEquals(1, featured.size)
        assertTrue(community.single() is PlaylistItem)
        assertEquals((community.single() as PlaylistItem).id, (featured.single() as PlaylistItem).id)
    }

    @Test
    fun `artist maps to ArtistItem preserving id and thumbnail`() {
        val resp = ZemerSearchResponse(
            categories = ZemerCategories(artists = listOf(ZemerArtist("UC1", "Name", "th"))),
        )

        val artist = ZemerResultMapper.filtered(resp, SearchFilter.FILTER_ARTIST, false).items.single() as ArtistItem
        assertEquals("UC1", artist.id)
        assertEquals("Name", artist.title)
        assertEquals("th", artist.thumbnail)
    }

    @Test
    fun `suggestions give text completions then all-category result rows`() {
        val resp = ZemerSearchResponse(
            categories = ZemerCategories(
                artists = listOf(ZemerArtist("UC1", "Name")),
                songs = listOf(ZemerTrack("s1", "Song", "A")),
                albums = listOf(ZemerAlbum(id = "al1", title = "Album", artist = "A")),
                videos = listOf(ZemerTrack("v1", "Video", "A")),
                playlists = listOf(ZemerPlaylist("pl1", "PL", "A", "t")),
            ),
        )

        val suggestions = ZemerResultMapper.suggestions(resp, hideExplicit = false)

        // Part 1: text completions — artist names first, then song titles.
        assertEquals(listOf("Name", "Song"), suggestions.queries)

        // Part 2: result rows in the summary order: songs, artists, albums, videos, playlists.
        val types = suggestions.recommendedItems.map { it::class }
        assertEquals(
            listOf(
                SongItem::class,   // song
                ArtistItem::class, // artist
                AlbumItem::class,  // album
                SongItem::class,   // video maps to SongItem
                PlaylistItem::class, // playlist
            ),
            types,
        )
    }

    @Test
    fun `suggestion completions are deduped case-insensitively and capped`() {
        val resp = ZemerSearchResponse(
            categories = ZemerCategories(
                artists = (1..6).map { ZemerArtist("UC$it", "Artist $it") },
                songs = listOf(ZemerTrack("s1", "ARTIST 1", "x")), // dupe of "Artist 1" by case
            ),
        )

        val queries = ZemerResultMapper.suggestions(resp, hideExplicit = false).queries
        assertEquals(5, queries.size) // capped at MAX_QUERY_SUGGESTIONS
        assertEquals(queries.size, queries.distinctBy { it.lowercase() }.size) // no case-dupes
    }
}
