package com.jtech.zemer.search

import com.metrolist.innertube.YouTube.SearchFilter
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.SongItem
import com.jtech.zemer.search.ZemerResultMapper.toAlbumPage
import com.jtech.zemer.search.ZemerResultMapper.toSongItems
import com.jtech.zemer.utils.BlockedIdsCache
import com.jtech.zemer.utils.ContentFilterConfig
import com.jtech.zemer.utils.ContentFilterState
import org.junit.After
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

    @Test
    fun `song maps to playable SongItem with derived thumbnail and null endpoint`() {
        val resp = ZemerSearchResponse(
            categories = ZemerCategories(songs = listOf(ZemerTrack("vid123", "Title", "Artist"))),
        )

        val song = ZemerResultMapper.summaryPage(resp, hideExplicit = false)
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

        val kept = ZemerResultMapper.summaryPage(resp, hideExplicit = true)
            .summaries.single().items
        assertEquals(1, kept.size)
        assertEquals("a", kept.single().id)

        val all = ZemerResultMapper.summaryPage(resp, hideExplicit = false)
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

        val section = ZemerResultMapper.summaryPage(resp, hideExplicit = false).summaries.single()
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
    fun `summary matches YouTube section order and folds videos into Songs`() {
        val resp = ZemerSearchResponse(
            categories = ZemerCategories(
                artists = listOf(ZemerArtist("UC1", "An Artist", "thumb")),
                songs = listOf(ZemerTrack("s1", "Song", "A")),
                albums = listOf(ZemerAlbum(id = "al1", title = "Album", artist = "A")),
                videos = listOf(ZemerTrack("v1", "Video", "A")),
                playlists = listOf(ZemerPlaylist("featured1", "Featured", "A", "t")),
                community = listOf(ZemerPlaylist("community1", "Community", "B", "t")),
            ),
        )

        val page = ZemerResultMapper.summaryPage(resp, hideExplicit = false)
        // Same titles/order as YouTube.searchSummary; no separate Videos section.
        assertEquals(listOf("Albums", "Songs", "Artists", "Playlists"), page.summaries.map { it.title })
        // Videos fold into the Songs section (videos are SongItems), exactly like YouTube.
        val songsSection = page.summaries.first { it.title == "Songs" }
        assertEquals(listOf("s1", "v1"), songsSection.items.map { it.id })
        // The "Playlists" section previews COMMUNITY playlists (its header drills into the Community
        // chip), so featured/artist-owned playlists are not shown here.
        val playlistsSection = page.summaries.first { it.title == "Playlists" }
        assertEquals(listOf("community1"), playlistsSection.items.map { it.id })
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
    fun `community chip returns community playlists, featured chip returns artist-owned playlists`() {
        val resp = ZemerSearchResponse(
            categories = ZemerCategories(
                playlists = listOf(ZemerPlaylist("featured1", "Featured", "A", "t")),
                community = listOf(ZemerPlaylist("community1", "Community", "B", "t")),
            ),
        )

        val community = ZemerResultMapper.filtered(resp, SearchFilter.FILTER_COMMUNITY_PLAYLIST, false).items
        val featured = ZemerResultMapper.filtered(resp, SearchFilter.FILTER_FEATURED_PLAYLIST, false).items
        // The two chips map to two distinct server categories — never the same list.
        assertEquals(listOf("community1"), community.map { it.id })
        assertEquals(listOf("featured1"), featured.map { it.id })
        assertTrue(community.single() is PlaylistItem)
        assertTrue(featured.single() is PlaylistItem)
    }

    @Test
    fun `community playlist surfaces its whitelisted song count`() {
        val resp = ZemerSearchResponse(
            categories = ZemerCategories(
                community = listOf(ZemerPlaylist("c1", "Mix", "Curator", "t", songCount = 12)),
            ),
        )

        val item = ZemerResultMapper
            .filtered(resp, SearchFilter.FILTER_COMMUNITY_PLAYLIST, hideExplicit = false) { "$it songs" }
            .items.single() as PlaylistItem
        assertEquals("12 songs", item.songCountText)
    }

    @Test
    fun `playlist with absent or zero count shows no count text`() {
        val resp = ZemerSearchResponse(
            categories = ZemerCategories(
                community = listOf(
                    ZemerPlaylist("c1", "No count", "Curator", "t"),               // count absent
                    ZemerPlaylist("c2", "Zero count", "Curator", "t", songCount = 0),
                ),
            ),
        )

        val items = ZemerResultMapper
            .filtered(resp, SearchFilter.FILTER_COMMUNITY_PLAYLIST, hideExplicit = false) { "$it songs" }
            .items.map { it as PlaylistItem }
        assertNull(items[0].songCountText) // absent → no count
        assertNull(items[1].songCountText) // zero → no count
    }

    @Test
    fun `summary Playlists section previews community only so its header drill-in is consistent`() {
        // Featured-only response: the "Playlists" section must NOT show featured playlists, because its
        // header routes to the Community chip — showing them would vanish on tap / yield "No results".
        val featuredOnly = ZemerSearchResponse(
            categories = ZemerCategories(playlists = listOf(ZemerPlaylist("featured1", "Featured", "A", "t"))),
        )
        assertTrue(
            ZemerResultMapper.summaryPage(featuredOnly, hideExplicit = false)
                .summaries.none { it.title == "Playlists" },
        )

        // With community present, the section shows exactly the community playlists (= the chip's rows).
        val withCommunity = ZemerSearchResponse(
            categories = ZemerCategories(
                playlists = listOf(ZemerPlaylist("featured1", "Featured", "A", "t")),
                community = listOf(ZemerPlaylist("community1", "Community", "B", "t")),
            ),
        )
        val section = ZemerResultMapper.summaryPage(withCommunity, hideExplicit = false)
            .summaries.first { it.title == "Playlists" }
        assertEquals(listOf("community1"), section.items.map { it.id })
    }

    @Test
    fun `summary caps each section to a compact preview`() {
        // The merged Songs section (songs + videos) would otherwise be a long scroll; the chip still
        // returns everything.
        val resp = ZemerSearchResponse(
            categories = ZemerCategories(songs = (1..20).map { ZemerTrack("s$it", "Song $it", "A") }),
        )

        val songsSection = ZemerResultMapper.summaryPage(resp, hideExplicit = false)
            .summaries.first { it.title == "Songs" }
        assertEquals(8, songsSection.items.size) // capped

        val songsChip = ZemerResultMapper.filtered(resp, SearchFilter.FILTER_SONG, hideExplicit = false).items
        assertEquals(20, songsChip.size) // chip is uncapped
    }

    @Test
    fun `Songs chip returns songs and videos so the summary Songs drill-in keeps the videos`() {
        // The summary folds videos into "Songs"; tapping that header (FILTER_SONG) must return both,
        // and a video-only query must NOT come back empty.
        val resp = ZemerSearchResponse(
            categories = ZemerCategories(
                songs = listOf(ZemerTrack("s1", "Song", "A")),
                videos = listOf(ZemerTrack("v1", "Video", "A")),
            ),
        )
        val songsChip = ZemerResultMapper.filtered(resp, SearchFilter.FILTER_SONG, hideExplicit = false).items
        assertEquals(listOf("s1", "v1"), songsChip.map { it.id })

        val videoOnly = ZemerSearchResponse(
            categories = ZemerCategories(videos = listOf(ZemerTrack("v1", "Video", "A"))),
        )
        val videoOnlySongsChip = ZemerResultMapper.filtered(videoOnly, SearchFilter.FILTER_SONG, hideExplicit = false).items
        assertEquals(listOf("v1"), videoOnlySongsChip.map { it.id }) // not empty
    }

    @Test
    fun `hideExplicit drops an explicit title from the as-you-type completions, not just the rows`() {
        val resp = ZemerSearchResponse(
            categories = ZemerCategories(
                songs = listOf(
                    ZemerTrack("a", "Clean Song", "X", explicit = false),
                    ZemerTrack("b", "Dirty Song", "Y", explicit = true),
                ),
            ),
        )

        val hidden = ZemerResultMapper.suggestions(resp, hideExplicit = true).queries
        assertEquals(listOf("Clean Song"), hidden) // explicit title not offered as a completion

        val shown = ZemerResultMapper.suggestions(resp, hideExplicit = false).queries
        assertEquals(listOf("Clean Song", "Dirty Song"), shown)
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

    @Test
    fun `playlist response maps every server track to a SongItem without a local whitelist re-filter`() {
        // The /playlist endpoint already whitelist-scoped the tracks, so opening keeps exactly what the
        // server returned (minus blanks/dupes) — re-running the local artist filter here is the bug that
        // made the opened count differ from the search card.
        val resp = ZemerPlaylistResponse(
            playlist = ZemerPlaylistHeader(id = "PL1", title = "Mix", artist = "Curator", thumbnail = "th"),
            tracks = listOf(
                ZemerTrack("a", "One", "Artist A"),
                ZemerTrack("b", "Two", "Artist B"),
                ZemerTrack("", "No id", "X"),        // blank id dropped, never crashes navigation
                ZemerTrack("a", "Dup", "Artist A"),  // duplicate id de-duped (id-keyed list)
            ),
            total = 10,
            whitelisted = 2,
        )

        val songs = resp.toSongItems(hideExplicit = false)
        assertEquals(listOf("a", "b"), songs.map { it.id })
        assertEquals("https://i.ytimg.com/vi/a/hqdefault.jpg", songs.first().thumbnail)
    }

    @Test
    fun `playlist response honors hideExplicit`() {
        val resp = ZemerPlaylistResponse(
            tracks = listOf(
                ZemerTrack("clean", "Clean", "A", explicit = false),
                ZemerTrack("dirty", "Dirty", "B", explicit = true),
            ),
        )
        assertEquals(listOf("clean"), resp.toSongItems(hideExplicit = true).map { it.id })
        assertEquals(listOf("clean", "dirty"), resp.toSongItems(hideExplicit = false).map { it.id })
    }

    @Test
    fun `album response maps to an AlbumPage ordered by track number`() {
        val resp = ZemerAlbumResponse(
            album = ZemerAlbumHeader(
                id = "MPRE1",
                title = "Journeys, Vol. 1",
                artist = "Abie Rotenberg",
                year = 2010,
                thumbnail = "https://art",
            ),
            tracks = listOf(
                // Out of order + a null duration (the live server really returns those), plus a
                // blank-videoId row that must be dropped rather than crash the id-keyed list.
                ZemerTrack("v2", "Second", "Abie Rotenberg", durationSec = null, trackNumber = 2),
                ZemerTrack("v1", "First", "Abie Rotenberg", durationSec = 257, trackNumber = 1),
                ZemerTrack("", "No id", "X", trackNumber = 3),
            ),
        )

        val page = resp.toAlbumPage(playlistId = "OLAK1")

        assertEquals("MPRE1", page.album.browseId)
        assertEquals("OLAK1", page.album.playlistId)
        assertEquals(2010, page.album.year)
        assertEquals("Abie Rotenberg", page.album.artists?.single()?.name)
        assertEquals(listOf("v1", "v2"), page.songs.map { it.id })
        assertEquals(257, page.songs.first().duration)
        assertNull(page.songs.last().duration)
        // Tracks carry the album back-reference + the square album art, not the derived video frame.
        assertEquals("MPRE1", page.songs.first().album?.id)
        assertEquals("https://art", page.songs.first().thumbnail)
    }

    @Test
    fun `album playlistId falls back to the browseId and untagged tracks keep server order`() {
        val resp = ZemerAlbumResponse(
            album = ZemerAlbumHeader(id = "MPRE1", title = "T", artist = ""),
            tracks = listOf(ZemerTrack("a", "A", "X"), ZemerTrack("b", "B", "X")),
        )

        val page = resp.toAlbumPage(playlistId = null)

        assertEquals("MPRE1", page.album.playlistId)
        assertNull(page.album.artists) // blank artist => no artist list
        assertEquals(listOf("a", "b"), page.songs.map { it.id })
        // No album art from the server -> the derived video thumbnail.
        assertEquals("https://i.ytimg.com/vi/a/hqdefault.jpg", page.songs.first().thumbnail)
    }

    @After
    fun clearBlockedIds() {
        BlockedIdsCache.updateAll(emptyMap())
        ContentFilterState.current = ContentFilterConfig()
    }

    @Test
    fun `blocked ids are dropped from album tracks`() {
        BlockedIdsCache.updateAll(mapOf("blockedTrack" to "global"))
        ContentFilterState.current = ContentFilterConfig(filtersEnabled = true)
        val resp = ZemerAlbumResponse(
            album = ZemerAlbumHeader(id = "MPRE1", title = "T", artist = "A"),
            tracks = listOf(ZemerTrack("ok", "OK", "A"), ZemerTrack("blockedTrack", "Blocked", "A")),
        )

        assertEquals(listOf("ok"), resp.toAlbumPage(playlistId = null).songs.map { it.id })
    }

    @Test
    fun `female-reason ids are dropped from zemer results only when filtering out female`() {
        BlockedIdsCache.updateAll(
            mapOf("blockedSong" to "female", "blockedPlaylist" to "female"),
        )
        val resp = ZemerSearchResponse(
            categories = ZemerCategories(
                songs = listOf(ZemerTrack("okSong", "OK", "A"), ZemerTrack("blockedSong", "Blocked", "A")),
                community = listOf(
                    ZemerPlaylist("okPlaylist", "OK", "A", "t"),
                    ZemerPlaylist("blockedPlaylist", "Blocked", "A", "t"),
                ),
            ),
        )

        // Female filtered out -> the female-reason ids are hidden everywhere.
        ContentFilterState.current = ContentFilterConfig(filtersEnabled = true, allowFemaleSingers = false)
        assertEquals(
            listOf("okSong"),
            ZemerResultMapper.filtered(resp, SearchFilter.FILTER_SONG, hideExplicit = false).items.map { it.id },
        )
        assertEquals(
            listOf("okPlaylist"),
            ZemerResultMapper.filtered(resp, SearchFilter.FILTER_COMMUNITY_PLAYLIST, hideExplicit = false)
                .items.map { it.id },
        )

        // Female allowed -> the same ids show again (the override is conditional).
        ContentFilterState.current = ContentFilterConfig(filtersEnabled = true, allowFemaleSingers = true)
        assertEquals(
            listOf("okSong", "blockedSong"),
            ZemerResultMapper.filtered(resp, SearchFilter.FILTER_SONG, hideExplicit = false).items.map { it.id },
        )
    }
}
