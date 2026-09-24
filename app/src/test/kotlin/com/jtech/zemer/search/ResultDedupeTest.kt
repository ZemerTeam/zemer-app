package com.jtech.zemer.search

import com.metrolist.innertube.models.Artist
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.pages.SearchSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Pure coverage of the authoritative-only video dedupe (I3 / unified-video DESIGN §7): a video row is
 * dropped only on an identical videoId or a known counterpart mapping — never a title/artist/duration
 * heuristic — and a dropped video is always still reachable via the surviving song's toggle.
 */
class ResultDedupeTest {

    private fun song(id: String, title: String = "T") =
        SongItem(id = id, title = title, artists = listOf(Artist(name = "A", id = null)), album = null,
            duration = null, thumbnail = "", isVideo = false)

    private fun video(id: String, title: String = "T") = song(id, title).copy(isVideo = true)

    @Test
    fun `same videoId drops the video row (rule 1)`() {
        val songs = listOf(song("x"), song("y"))
        val videos = listOf(video("x"), video("z"))
        assertEquals(listOf("z"), ResultDedupe.dropDuplicateVideos(songs, videos).map { it.id })
    }

    @Test
    fun `a known counterpart drops the video row (rule 2)`() {
        val songs = listOf(song("s1"))
        val videos = listOf(video("v1"), video("v2"))
        val kept = ResultDedupe.dropDuplicateVideos(songs, videos, counterpartOf = { if (it == "s1") "v1" else null })
        assertEquals(listOf("v2"), kept.map { it.id })
    }

    @Test
    fun `same title but different id and no counterpart keeps both (no heuristic)`() {
        // The Zemer duplicate case: identical curated titles, different videoIds — must NOT be merged.
        val songs = listOf(song("s1", "לך אלי"))
        val videos = listOf(video("v1", "לך אלי"))
        assertEquals(listOf("v1"), ResultDedupe.dropDuplicateVideos(songs, videos).map { it.id })
    }

    @Test
    fun `empty songs or empty videos is a no-op`() {
        val videos = listOf(video("v1"))
        assertEquals(videos, ResultDedupe.dropDuplicateVideos(emptyList(), videos))
        assertEquals(emptyList<SongItem>(), ResultDedupe.dropDuplicateVideos(listOf(song("s1")), emptyList()))
    }

    @Test
    fun `dedupeSummaries filters the videos section against songs across all sections`() {
        val summaries = listOf(
            SearchSummary("Songs", listOf(song("a"), song("b"))),
            SearchSummary("Videos", listOf(video("a"), video("c"))),
        )
        val out = ResultDedupe.dedupeSummaries(summaries)
        assertEquals(listOf("Songs", "Videos"), out.map { it.title })
        assertEquals(listOf("c"), out.first { it.title == "Videos" }.items.map { it.id })
    }

    @Test
    fun `dedupeSummaries drops a section emptied by the dedupe`() {
        val summaries = listOf(
            SearchSummary("Songs", listOf(song("a"))),
            SearchSummary("Videos", listOf(video("a"))),
        )
        val out = ResultDedupe.dedupeSummaries(summaries)
        assertEquals(listOf("Songs"), out.map { it.title })
    }

    @Test
    fun `dedupeSummaries never touches non-song items or audio songs and preserves untouched sections`() {
        val playlists = SearchSummary("Playlists",
            listOf(PlaylistItem(id = "p1", title = "P", author = null, songCountText = null, thumbnail = "",
                playEndpoint = null, shuffleEndpoint = null, radioEndpoint = null)))
        val songsSection = SearchSummary("Songs", listOf(song("a")))
        val out = ResultDedupe.dedupeSummaries(listOf(songsSection, playlists))
        // No videos anywhere → identical section instances returned (no copies).
        assertSame(songsSection, out[0])
        assertSame(playlists, out[1])
    }
}
