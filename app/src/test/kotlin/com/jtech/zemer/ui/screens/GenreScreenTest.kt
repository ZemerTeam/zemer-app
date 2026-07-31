package com.jtech.zemer.ui.screens

import com.jtech.zemer.search.ZemerGenreHeader
import com.jtech.zemer.search.ZemerResultMapper
import com.jtech.zemer.search.ZemerResultMapper.ZemerGenrePage
import com.jtech.zemer.search.ZemerResultMapper.headerCovers
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.Artist
import com.metrolist.innertube.models.SongItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The tracklist's near-edge prefetch threshold: fires while [TRACKLIST_PREFETCH_ROWS] rows of
 * runway remain (so the next page lands before the user reaches the end), never before, and never
 * when nothing is laid out yet.
 */
class GenreTracklistPrefetchTest {

    @Test
    fun `fires exactly at the rows-from-end threshold and past it`() {
        // 100 items, last index 99: threshold is 99 - 10 = 89.
        assertFalse(shouldPrefetchNearEnd(lastVisibleIndex = 88, totalItemsCount = 100))
        assertTrue(shouldPrefetchNearEnd(lastVisibleIndex = 89, totalItemsCount = 100))
        assertTrue(shouldPrefetchNearEnd(lastVisibleIndex = 99, totalItemsCount = 100))
    }

    @Test
    fun `no last visible item means no fetch`() {
        assertFalse(shouldPrefetchNearEnd(lastVisibleIndex = null, totalItemsCount = 100))
        assertFalse(shouldPrefetchNearEnd(lastVisibleIndex = null, totalItemsCount = 0))
    }

    @Test
    fun `a page shorter than the runway fires immediately so short first pages chain`() {
        assertTrue(shouldPrefetchNearEnd(lastVisibleIndex = 0, totalItemsCount = 5))
    }

    @Test
    fun `custom runway is respected`() {
        assertFalse(shouldPrefetchNearEnd(lastVisibleIndex = 96, totalItemsCount = 100, prefetchRows = 2))
        assertTrue(shouldPrefetchNearEnd(lastVisibleIndex = 97, totalItemsCount = 100, prefetchRows = 2))
    }
}

/**
 * The header mosaic's cover selection: albums first, then singles, then song art to fill, blanks
 * dropped, de-duped, capped.
 */
class GenreHeaderCoversTest {

    private fun album(id: String, thumb: String) = AlbumItem(
        browseId = id, playlistId = id, title = id, artists = null, year = null, thumbnail = thumb,
    )

    private fun song(id: String, thumb: String) = SongItem(
        id = id, title = id, artists = listOf(Artist(name = "a", id = null)), thumbnail = thumb,
    )

    private fun page(albums: List<AlbumItem>, singles: List<AlbumItem>, songs: List<SongItem>) =
        ZemerGenrePage(
            header = ZemerGenreHeader(id = "g"),
            artists = emptyList(), albums = albums, singles = singles,
            songs = songs, videos = emptyList(), nextOffset = null,
        )

    @Test
    fun `albums lead, singles then songs fill, blanks and duplicates drop, cap respected`() {
        val covers = page(
                albums = listOf(album("a1", "A1"), album("a2", "")),
                singles = listOf(album("s1", "S1"), album("s2", "A1")),
                songs = (1..6).map { song("v$it", "V$it") },
            ).headerCovers()

        assertEquals(listOf("A1", "S1", "V1", "V2", "V3"), covers)
    }

    @Test
    fun `no art at all is a normal empty mosaic`() {
        assertTrue(page(emptyList(), emptyList(), emptyList()).headerCovers().isEmpty())
    }

    @Test
    fun `three or four unique covers still render (they tile to fill the width)`() {
        // 4 unique covers: enough to tile evenly — the common case (songs reuse album art, so
        // genres often have only a handful of unique covers). Shown, not hidden.
        val covers = page(
            albums = listOf(album("a1", "A1"), album("a2", "A2")),
            singles = listOf(album("s1", "S1")),
            songs = listOf(song("v1", "V1")),
        ).headerCovers()

        assertEquals(listOf("A1", "A2", "S1", "V1"), covers)
    }

    @Test
    fun `fewer than the minimum renders NO mosaic - a lone or paired cover breaks the flow`() {
        val covers = page(
            albums = listOf(album("a1", "A1"), album("a2", "A2")),
            singles = emptyList(),
            songs = emptyList(),
        ).headerCovers()

        assertTrue(covers.isEmpty()) // only 2 unique < min 3
    }

    @Test
    fun `two renditions of one image can never count as different covers`() {
        // Same videoId art via hqdefault and mqdefault URLs: mosaicVariant normalizes BOTH to
        // hqdefault BEFORE distinct, so they collapse to one cover — the strip has 4 unique, not 5.
        val hq = "https://i.ytimg.com/vi/abc/hqdefault.jpg"
        val mq = "https://i.ytimg.com/vi/abc/mqdefault.jpg"
        val covers = page(
            albums = listOf(album("a1", hq), album("a2", mq)),
            singles = emptyList(),
            songs = (1..3).map { song("v$it", "V$it") },
        ).headerCovers()

        // hqdefault appears exactly once (the two renditions collapsed), plus V1..V3 = 4 covers.
        assertEquals(listOf("https://i.ytimg.com/vi/abc/hqdefault.jpg", "V1", "V2", "V3"), covers)
    }

    @Test
    fun `mosaic variant sizes ytimg to hqdefault and rewrites every FIFE size, not just w`() {
        // ytimg: small variants bump up to hqdefault; hqdefault stays.
        assertEquals(
            "https://i.ytimg.com/vi/abc/hqdefault.jpg",
            ZemerResultMapper.mosaicVariant("https://i.ytimg.com/vi/abc/mqdefault.jpg"),
        )
        assertEquals(
            "https://i.ytimg.com/vi/abc/hqdefault.jpg",
            ZemerResultMapper.mosaicVariant("https://i.ytimg.com/vi/abc/hqdefault.jpg"),
        )
        // googleusercontent =w suffix replaced.
        assertEquals(
            "https://lh3.googleusercontent.com/xyz=w480-h480-l90-rj",
            ZemerResultMapper.mosaicVariant("https://lh3.googleusercontent.com/xyz=w544-h544-l90-rj"),
        )
        // The bug this fixes: an =s-sized URL must be REPLACED, not get a second =param appended.
        assertEquals(
            "https://lh3.googleusercontent.com/xyz=w480-h480-l90-rj",
            ZemerResultMapper.mosaicVariant("https://lh3.googleusercontent.com/xyz=s120-c"),
        )
        // No FIFE suffix -> appended.
        assertEquals(
            "https://lh3.googleusercontent.com/xyz=w480-h480-l90-rj",
            ZemerResultMapper.mosaicVariant("https://lh3.googleusercontent.com/xyz"),
        )
        // Unknown host passes through untouched.
        assertEquals("https://cdn.example.com/a.jpg", ZemerResultMapper.mosaicVariant("https://cdn.example.com/a.jpg"))
    }
}
