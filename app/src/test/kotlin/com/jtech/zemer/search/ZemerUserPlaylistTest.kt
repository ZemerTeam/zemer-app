package com.jtech.zemer.search

import com.jtech.zemer.search.ZemerResultMapper.toSongItems
import com.jtech.zemer.tracking.PlaySource
import com.jtech.zemer.utils.BlockedIdsCache
import com.jtech.zemer.utils.ContentFilterConfig
import com.jtech.zemer.utils.ContentFilterState
import org.junit.After
import org.junit.Assert.assertEquals
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
            { "playlist": { "id": "abc", "title": "Simchas", "createdAt": 1785400000000, "trackCount": 2 },
              "tracks": [ { "videoId": "v1", "title": "T1", "artist": "A1", "artistId": "UCx",
                            "thumbnail": "https://art", "durationSec": 224, "explicit": false, "isVideo": false } ],
              "source": "zemer-user" }
        """.trimIndent()
        val resp = zemerResponseJson.decodeFromString(ZemerUserPlaylistResponse.serializer(), json)
        assertEquals("Simchas", resp.playlist.title)
        assertEquals(2, resp.playlist.trackCount)
        assertEquals("zemer-user", resp.source)
        assertEquals("v1", resp.tracks.single().videoId)
    }

    @Test
    fun `receiver mapping - blocked ids and sparse rows drop, dupes collapse, art falls back`() {
        BlockedIdsCache.updateAll(mapOf("blockedTrack" to "global"))
        ContentFilterState.current = ContentFilterConfig(filtersEnabled = true)
        val resp = ZemerUserPlaylistResponse(
            tracks = listOf(
                ZemerUserPlaylistTrack(videoId = "ok", title = "OK", artist = "A"),
                ZemerUserPlaylistTrack(videoId = "blockedTrack", title = "B", artist = "A"),
                ZemerUserPlaylistTrack(videoId = "", title = "sparse", artist = "A"),
                ZemerUserPlaylistTrack(videoId = "ok", title = "dup", artist = "A"),
            ),
        )
        val songs = resp.toSongItems(hideExplicit = false)
        assertEquals(listOf("ok"), songs.map { it.id })
        assertEquals("https://i.ytimg.com/vi/ok/hqdefault.jpg", songs.single().thumbnail)
    }

    @Test
    fun `hide-explicit drops explicit snapshot members`() {
        val resp = ZemerUserPlaylistResponse(
            tracks = listOf(
                ZemerUserPlaylistTrack(videoId = "clean", title = "C", artist = "A", explicit = false),
                ZemerUserPlaylistTrack(videoId = "dirty", title = "D", artist = "A", explicit = true),
            ),
        )
        assertEquals(listOf("clean"), resp.toSongItems(hideExplicit = true).map { it.id })
    }

    @Test
    fun `the shared play-source wire value is pinned`() {
        assertEquals("shared:abc123", PlaySource.shared("abc123"))
    }
}
