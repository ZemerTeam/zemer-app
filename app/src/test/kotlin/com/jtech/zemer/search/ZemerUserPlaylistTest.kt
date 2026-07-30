package com.jtech.zemer.search

import com.jtech.zemer.search.ZemerResultMapper.toSongItems
import com.jtech.zemer.tracking.PlaySource
import com.jtech.zemer.utils.BlockedIdsCache
import com.jtech.zemer.utils.ContentFilterConfig
import com.jtech.zemer.utils.ContentFilterState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * User playlist sharing (issue #176) wire layer: the handoff payloads decode with the shared
 * lenient reader, and the receiver-side mapping applies the standard Zemer defense-in-depth
 * (sparse drop, dedup, hide-explicit, the surgical blocked-id overrides, derived-art fallback).
 */
class ZemerUserPlaylistTest {

    @After
    fun reset() {
        BlockedIdsCache.updateAll(emptyMap())
        ContentFilterState.current = ContentFilterConfig()
    }

    @Test
    fun `create response decodes kept and dropped`() {
        val json = """{ "id": "Rtwwz3ZEA5Bzik", "url": "https://search.zemer.io/user_playlist/Rtwwz3ZEA5Bzik", "kept": 37, "dropped": 2 }"""
        val resp = zemerResponseJson.decodeFromString(ZemerUserPlaylistCreateResponse.serializer(), json)
        assertEquals("Rtwwz3ZEA5Bzik", resp.id)
        assertEquals("https://search.zemer.io/user_playlist/Rtwwz3ZEA5Bzik", resp.url)
        assertEquals(37, resp.kept)
        assertEquals(2, resp.dropped)
    }

    @Test
    fun `open response decodes the handoff payload shape`() {
        val json = """
            { "playlist": { "id": "abc", "title": "Simchas", "sharedBy": "Avi G.", "createdAt": 1785400000000,
                            "trackCount": 2, "thumbnail": "https://art/cover.jpg", "totalDurationSec": 8144 },
              "tracks": [ { "videoId": "v1", "title": "T1", "artist": "A1", "artistId": "UCx",
                            "thumbnail": "https://art", "durationSec": 224, "explicit": false, "isVideo": false } ],
              "source": "zemer-user" }
        """.trimIndent()
        val resp = zemerResponseJson.decodeFromString(ZemerUserPlaylistResponse.serializer(), json)
        assertEquals("Simchas", resp.playlist.title)
        assertEquals("Avi G.", resp.playlist.sharedBy)
        assertEquals(2, resp.playlist.trackCount)
        assertEquals(8144, resp.playlist.totalDurationSec)
        assertEquals("zemer-user", resp.source)
        assertEquals("v1", resp.tracks.single().videoId)
    }

    @Test
    fun `open response without sharer fields decodes with nulls`() {
        // Pre-addition payloads (and screened names) omit sharedBy/thumbnail/totalDurationSec.
        val json = """{ "playlist": { "id": "abc", "title": "Simchas", "createdAt": 1, "trackCount": 0 },
              "tracks": [], "source": "zemer-user" }"""
        val resp = zemerResponseJson.decodeFromString(ZemerUserPlaylistResponse.serializer(), json)
        assertNull(resp.playlist.sharedBy)
        assertNull(resp.playlist.totalDurationSec)
    }

    @Test
    fun `receiver mapping - blocked ids and sparse rows drop, dupes collapse, art falls back`() {
        BlockedIdsCache.updateAll(mapOf("blockedTrack" to "global"))
        ContentFilterState.current = ContentFilterConfig(filtersEnabled = true)
        val resp = ZemerUserPlaylistResponse(
            tracks = listOf(
                ZemerTrack(videoId = "ok", title = "OK", artist = "A"),
                ZemerTrack(videoId = "blockedTrack", title = "B", artist = "A"),
                ZemerTrack(videoId = "", title = "sparse", artist = "A"),
                ZemerTrack(videoId = "ok", title = "dup", artist = "A"),
            ),
        )
        val songs = resp.toSongItems(hideExplicit = false, blockVideos = false)
        assertEquals(listOf("ok"), songs.map { it.id })
        assertEquals("https://i.ytimg.com/vi/ok/hqdefault.jpg", songs.single().thumbnail)
    }

    @Test
    fun `hide-explicit drops explicit snapshot members`() {
        val resp = ZemerUserPlaylistResponse(
            tracks = listOf(
                ZemerTrack(videoId = "clean", title = "C", artist = "A", explicit = false),
                ZemerTrack(videoId = "dirty", title = "D", artist = "A", explicit = true),
            ),
        )
        assertEquals(listOf("clean"), resp.toSongItems(hideExplicit = true, blockVideos = false).map { it.id })
    }

    @Test
    fun `the shared play-source wire value is pinned`() {
        assertEquals("shared:abc123", PlaySource.shared("abc123"))
    }

    @Test
    fun `blockVideos backstop drops isVideo tracks client-side`() {
        // The client second gate for the sender-chosen surface: even if the server regressed the
        // query flag, isVideo tracks never render for a blockVideos receiver.
        val resp = ZemerUserPlaylistResponse(
            tracks = listOf(
                ZemerTrack(videoId = "song1", title = "S", artist = "A"),
                ZemerTrack(videoId = "vid1", title = "V", artist = "A", isVideo = true),
            ),
        )
        assertEquals(listOf("song1"), resp.toSongItems(hideExplicit = false, blockVideos = true).map { it.id })
        assertEquals(listOf("song1", "vid1"), resp.toSongItems(hideExplicit = false, blockVideos = false).map { it.id })
    }

    @Test
    fun `share ids are constrained to the server slug alphabet`() {
        // The id comes off an untrusted deep link DECODED - path/query/fragment metacharacters
        // must never reach the interpolated request path.
        assertTrue(isValidUserPlaylistShareId("Rtwwz3ZEA5Bzik"))
        assertTrue(isValidUserPlaylistShareId("a_B-9"))
        assertFalse(isValidUserPlaylistShareId(""))
        assertFalse(isValidUserPlaylistShareId("abc/../etc"))
        assertFalse(isValidUserPlaylistShareId("abc?x=1"))
        assertFalse(isValidUserPlaylistShareId("abc#frag"))
        assertFalse(isValidUserPlaylistShareId("abc def"))
        assertFalse(isValidUserPlaylistShareId("a".repeat(65)))
    }
}
