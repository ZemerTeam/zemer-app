package com.jtech.zemer.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the shared [zemerResponseJson] config the client decodes every response with — specifically
 * `coerceInputValues`. kotlinx applies a field's default only for an ABSENT key, so without coercion an
 * explicit JSON `null` on a non-null defaulted field throws and fails the WHOLE response (the strict-
 * deserialization "No results" trap). These prove a sparse/odd payload degrades to defaults instead.
 */
class ZemerSearchJsonTest {

    @Test
    fun `explicit null on a non-null defaulted field decodes to the default, not an exception`() {
        val payload =
            """{"q":"x","count":0,"categories":{"songs":null,"community":null,""" +
                """"playlists":[{"id":"p1","title":"PL","artist":"A","thumbnail":null,"whitelisted":null}]}}"""

        val resp = zemerResponseJson.decodeFromString(ZemerSearchResponse.serializer(), payload)

        assertTrue(resp.categories.songs.isEmpty())       // null list → default empty, no throw
        assertTrue(resp.categories.community.isEmpty())
        assertEquals(listOf("p1"), resp.categories.playlists.map { it.id })
        assertEquals(null, resp.categories.playlists.single().songCount) // nullable stays null
    }

    @Test
    fun `explicit null categories object decodes to the default empty categories`() {
        val resp = zemerResponseJson.decodeFromString(
            ZemerSearchResponse.serializer(),
            """{"q":"x","categories":null}""",
        )

        assertTrue(resp.categories.songs.isEmpty())
        assertTrue(resp.categories.playlists.isEmpty())
        assertTrue(resp.categories.community.isEmpty())
    }

    @Test
    fun `playlist response decodes tracks and the filter-aware header`() {
        val payload =
            """{"playlist":{"id":"PL1","title":"Pesach","artist":"Curator",""" +
                """"thumbnail":"https://i.ytimg.com/vi/vid0/mqdefault.jpg"},""" +
                """"tracks":[{"videoId":"vid0","title":"T","artist":"A","explicit":false}],""" +
                """"total":28,"whitelisted":1}"""

        val resp = zemerResponseJson.decodeFromString(ZemerPlaylistResponse.serializer(), payload)

        assertEquals("Pesach", resp.playlist.title)
        assertEquals("https://i.ytimg.com/vi/vid0/mqdefault.jpg", resp.playlist.thumbnail)
        assertEquals(listOf("vid0"), resp.tracks.map { it.videoId })
        assertEquals(1, resp.whitelisted)
        assertEquals(28, resp.total)
    }

    @Test
    fun `album response decodes null durations and unknown fields, not an exception`() {
        // Mirrors the live /album payload: extra header fields the app doesn't model (type,
        // releaseDate, trackCount, totalDurationSec) and an explicit-null durationSec on a track.
        val payload =
            """{"album":{"id":"MPRE1","title":"Journeys, Vol. 1","artist":"Abie Rotenberg","year":2010,""" +
                """"type":"album","releaseDate":"2015-10-05","trackCount":9,"totalDurationSec":257,"thumbnail":null},""" +
                """"tracks":[{"videoId":"v1","title":"T","artist":"A","explicit":false,""" +
                """"durationSec":null,"trackNumber":1,"releaseDate":"2015-10-05"}]}"""

        val resp = zemerResponseJson.decodeFromString(ZemerAlbumResponse.serializer(), payload)

        assertEquals("Journeys, Vol. 1", resp.album.title)
        assertEquals(2010, resp.album.year)
        assertEquals(null, resp.album.thumbnail)
        assertEquals(listOf("v1"), resp.tracks.map { it.videoId })
        assertEquals(null, resp.tracks.single().durationSec) // nullable stays null
        assertEquals(1, resp.tracks.single().trackNumber)
    }

    @Test
    fun `sparse playlist response degrades to defaults, not an exception`() {
        // A null header / null tracks (older or odd payload) must not fail the whole response.
        val resp = zemerResponseJson.decodeFromString(
            ZemerPlaylistResponse.serializer(),
            """{"playlist":null,"tracks":null}""",
        )

        assertTrue(resp.tracks.isEmpty())
        assertEquals("", resp.playlist.title)
    }
}
