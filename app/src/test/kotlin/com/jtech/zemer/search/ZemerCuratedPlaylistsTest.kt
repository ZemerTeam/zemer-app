package com.jtech.zemer.search

import com.jtech.zemer.ui.component.ZemerRuntimeLabel
import com.jtech.zemer.ui.component.zemerCuratedPlaylistRuntime
import com.jtech.zemer.utils.ContentFilterConfig
import com.jtech.zemer.viewmodels.zemerOptionsStillCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the `/zemer-playlists` (curated "Zemer Playlists") integration contract:
 * - send-always / fail-closed flags — the server is default-OPEN, so ALL THREE content flags must be
 *   emitted on every request (list AND detail) regardless of value;
 * - lenient wire decoding — sparse/null/unknown fields degrade instead of failing the whole response;
 * - the runtime-label rule — null hides it, a known sub-minute runtime never shows "0 minutes".
 */
class ZemerCuratedPlaylistsTest {

    // ---- query-parameter contract ----

    @Test
    fun `list request always carries all three content flags, even when all-open`() {
        val params = zemerCuratedPlaylistsParameters(id = null, allowFemale = true, blockVideos = false)

        assertEquals(listOf("allowFemale", "blockVideos", "kidZone"), params.map { it.first })
        assertEquals("1", params.toMap()["allowFemale"])
        assertEquals("0", params.toMap()["blockVideos"])
        assertEquals("0", params.toMap()["kidZone"])
    }

    @Test
    fun `detail request carries the id plus the same three flags`() {
        val params = zemerCuratedPlaylistsParameters(id = "shabbos", allowFemale = false, blockVideos = true)

        assertEquals(listOf("id", "allowFemale", "blockVideos", "kidZone"), params.map { it.first })
        assertEquals("shabbos", params.toMap()["id"])
        // The crux: a restricted user's flags are explicit, never left to the server default.
        assertEquals("0", params.toMap()["allowFemale"])
        assertEquals("1", params.toMap()["blockVideos"])
    }

    // ---- wire decoding ----

    @Test
    fun `list response decodes the doc'd payload, keeping editorial order`() {
        val payload =
            """{"count":2,"playlists":[""" +
                """{"id":"shabbos","title":"Shabbos","thumbnail":"https://i.ytimg.com/vi/v1/mqdefault.jpg","trackCount":42,"totalDurationSec":9840},""" +
                """{"id":"kumzitz","title":"Kumzitz","thumbnail":null,"trackCount":7,"totalDurationSec":null}]}"""

        val resp = zemerResponseJson.decodeFromString(ZemerCuratedPlaylistsResponse.serializer(), payload)

        // The wire `count` is deliberately unmodeled — playlists.size is the truth.
        assertEquals(listOf("shabbos", "kumzitz"), resp.playlists.map { it.id })
        assertEquals(9840, resp.playlists.first().totalDurationSec)
        assertNull(resp.playlists.last().totalDurationSec) // null runtime survives as null (label hidden)
        assertNull(resp.playlists.last().thumbnail)
    }

    @Test
    fun `empty list and null playlists array are both the normal hidden-section state`() {
        val empty = zemerResponseJson.decodeFromString(
            ZemerCuratedPlaylistsResponse.serializer(),
            """{"count":0,"playlists":[]}""",
        )
        val nulled = zemerResponseJson.decodeFromString(
            ZemerCuratedPlaylistsResponse.serializer(),
            """{"count":0,"playlists":null}""",
        )

        assertTrue(empty.playlists.isEmpty())
        assertTrue(nulled.playlists.isEmpty())
    }

    @Test
    fun `detail response decodes doc'd track fields and ignores the extra per-track fields`() {
        val payload =
            """{"playlist":{"id":"shabbos","title":"Shabbos","thumbnail":"t","trackCount":1,"totalDurationSec":214},""" +
                """"albums":[{"id":"MPRE1","playlistId":"OLAK1","title":"Alb","artist":"A","year":2024,"thumbnail":"at"}],""" +
                """"tracks":[{"videoId":"zVRL5bTbDwk","title":"T","artist":"A","explicit":false,""" +
                """"isVideo":false,"durationSec":214,"playCount":75,"releaseDate":"2026-05-17T07:33:33-07:00","fromAlbum":true}]}"""

        val resp = zemerResponseJson.decodeFromString(ZemerCuratedPlaylistResponse.serializer(), payload)

        assertEquals("Shabbos", resp.playlist.title)
        assertEquals(listOf("zVRL5bTbDwk"), resp.tracks.map { it.videoId })
        assertEquals(214, resp.tracks.single().durationSec)
        assertEquals(true, resp.tracks.single().fromAlbum)
        assertEquals(listOf("MPRE1"), resp.albums.map { it.id })
    }

    @Test
    fun `fromAlbum and albums default on an old server that doesn't send them`() {
        val resp = zemerResponseJson.decodeFromString(
            ZemerCuratedPlaylistResponse.serializer(),
            """{"playlist":{"id":"p","title":"P"},"tracks":[{"videoId":"v1","title":"T","artist":"A"}]}""",
        )
        assertEquals(false, resp.tracks.single().fromAlbum)
        assertTrue(resp.albums.isEmpty())
    }

    @Test
    fun `stale-flag guard - a response only publishes while its options match the live config`() {
        val options = ZemerSearchOptions(allowFemale = true, blockVideos = false, hideExplicit = false)

        assertEquals(
            true,
            zemerOptionsStillCurrent(options, ContentFilterConfig(allowFemaleSingers = true, blockVideos = false)),
        )
        // The crux: a fetch issued before a flag flip must be dropped, not shown.
        assertEquals(
            false,
            zemerOptionsStillCurrent(options, ContentFilterConfig(allowFemaleSingers = false, blockVideos = false)),
        )
        // blockVideos is deliberately NOT compared: ZemerSearchOptions.blockVideos is a pinned
        // constant (false), so a live blockVideos=true config must still publish - comparing it would
        // permanently drop every response for a "Block videos" user (the regression this guards).
        assertEquals(
            true,
            zemerOptionsStillCurrent(options, ContentFilterConfig(allowFemaleSingers = true, blockVideos = true)),
        )
    }

    @Test
    fun `curated albums map with blank-id drop and browseId dedup`() {
        val resp = ZemerCuratedPlaylistResponse(
            albums = listOf(
                ZemerAlbum(id = "MPRE1", title = "A1", artist = "X"),
                ZemerAlbum(id = "", title = "sparse"),
                ZemerAlbum(id = "MPRE1", title = "dup"),
                ZemerAlbum(id = "MPRE2", title = "A2", artist = "Y"),
            ),
        )

        val items = with(ZemerResultMapper) { resp.toAlbumItems() }

        assertEquals(listOf("MPRE1", "MPRE2"), items.map { it.browseId })
    }

    // ---- mapping ----

    @Test
    fun `curated tracks map to playable SongItems in curated order with durations`() {
        val resp = ZemerCuratedPlaylistResponse(
            playlist = ZemerCuratedPlaylist(id = "shabbos", title = "Shabbos", trackCount = 3),
            tracks = listOf(
                ZemerTrack(videoId = "v1", title = "One", artist = "A", durationSec = 214),
                ZemerTrack(videoId = "", title = "sparse row"), // dropped, must not fail the rest
                ZemerTrack(videoId = "v2", title = "Two", artist = "B", durationSec = null),
            ),
        )

        val songs = with(ZemerResultMapper) { resp.toSongItems(hideExplicit = false) }

        assertEquals(listOf("v1", "v2"), songs.map { it.id })
        assertEquals(214, songs.first().duration)
        assertNull(songs.last().duration)
    }

    @Test
    fun `server-relative cover paths resolve against the base URL, absolute URLs pass through`() {
        assertEquals(
            "${ZemerSearchClient.BASE_URL}/zemer-playlists/cover?id=acapella",
            resolveZemerUrl("/zemer-playlists/cover?id=acapella"),
        )
        assertEquals(
            "https://i.ytimg.com/vi/v1/mqdefault.jpg",
            resolveZemerUrl("https://i.ytimg.com/vi/v1/mqdefault.jpg"),
        )
        assertNull(resolveZemerUrl(null))
    }

    // ---- runtime label ----

    @Test
    fun `runtime label - null hides, short reads in minutes, two hours and up reads in rounded hours`() {
        assertNull(zemerCuratedPlaylistRuntime(null))
        // Sub-minute rounds up to 1 minute, never "0 minutes".
        assertEquals(ZemerRuntimeLabel(1, isHours = false), zemerCuratedPlaylistRuntime(30))
        assertEquals(ZemerRuntimeLabel(119, isHours = false), zemerCuratedPlaylistRuntime(119 * 60))
        assertEquals(ZemerRuntimeLabel(2, isHours = true), zemerCuratedPlaylistRuntime(120 * 60))
        // The truncation case: 145601s must read "40 hours", not "2426 minutes".
        assertEquals(ZemerRuntimeLabel(40, isHours = true), zemerCuratedPlaylistRuntime(145601))
    }
}
