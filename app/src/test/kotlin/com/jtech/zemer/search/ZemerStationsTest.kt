package com.jtech.zemer.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Zemer Stations wire layer: the handoff-doc sample payloads decode field-for-field with the shared
 * lenient reader, the card-shaping rules ([liveStations]) hold, and the §4 tune-in clock math is
 * pinned (skew, live-offset join, the dying-track and drift rules). The playback queue itself lands
 * in the follow-up PR — these contracts are what it builds on.
 */
class ZemerStationsTest {

    // --- wire decode (payload shapes straight from the handoff doc) ---

    @Test
    fun `stations catalog decodes with relative covers and nowPlaying`() {
        val json = """
            { "count": 3,
              "stations": [
                { "id": "chasidish", "title": "Chassidish Radio",
                  "thumbnail": "/stations/cover?id=chasidish",
                  "live": true,
                  "nowPlaying": { "title": "T", "artist": "A", "thumbnail": "https://i.ytimg.com/x.jpg" } }
              ],
              "serverTimeMs": 1785400000000 }
        """.trimIndent()

        val resp = zemerResponseJson.decodeFromString(ZemerStationsResponse.serializer(), json)
        assertEquals(3, resp.count)
        assertEquals(1785400000000, resp.serverTimeMs)
        val s = resp.stations.single()
        assertEquals("chasidish", s.id)
        assertEquals("Chassidish Radio", s.title)
        assertEquals("/stations/cover?id=chasidish", s.thumbnail)
        assertTrue(s.live)
        assertEquals("T", s.nowPlaying?.title)
        assertEquals("A", s.nowPlaying?.artist)
    }

    @Test
    fun `tune-in payload decodes - now carries offsetMs, next entries do not`() {
        val json = """
            { "station": { "id": "dj", "title": "DJ Radio", "thumbnail": "/stations/cover?id=dj" },
              "serverTimeMs": 1785400000000,
              "horizonMs": 172000000,
              "now": { "videoId": "v1", "title": "T1", "artist": "A1", "artistId": "UCx",
                       "thumbnail": null, "durationSec": 224,
                       "startMs": 1785399900000, "endMs": 1785400124000, "offsetMs": 100000 },
              "next": [ { "videoId": "v2", "title": "T2", "artist": "A2",
                          "startMs": 1785400124000, "endMs": 1785400300000 } ] }
        """.trimIndent()

        val resp = zemerResponseJson.decodeFromString(ZemerStationTuneInResponse.serializer(), json)
        assertEquals("dj", resp.station.id)
        assertEquals(172000000, resp.horizonMs)
        assertEquals("v1", resp.now?.videoId)
        assertEquals(100000L, resp.now?.offsetMs)
        assertNull("null thumbnail = coverless standalone", resp.now?.thumbnail)
        assertEquals("v2", resp.next.single().videoId)
        assertNull("next entries carry no offset", resp.next.single().offsetMs)
    }

    // --- card shaping ---

    @Test
    fun `only live stations with a real id render, deduped, covers resolved absolute`() {
        val resp = ZemerStationsResponse(
            stations = listOf(
                ZemerStation(id = "chasidish", title = "C", thumbnail = "/stations/cover?id=chasidish", live = true),
                ZemerStation(id = "offline", title = "O", live = false),
                ZemerStation(id = "", title = "sparse", live = true),
                ZemerStation(id = "chasidish", title = "dup", live = true),
            ),
        )

        val cards = resp.liveStations { url -> url?.let { "https://search.zemer.io$it" } }

        assertEquals(listOf("chasidish"), cards.map { it.id })
        assertEquals("https://search.zemer.io/stations/cover?id=chasidish", cards.single().thumbnail)
    }

    // --- §4 clock math ---

    private val entry = ZemerStationEntry(videoId = "v", startMs = 1_000_000, endMs = 1_224_000)

    @Test
    fun `join position is the live wall-clock offset inside the track`() {
        // Server is 2s ahead of the local clock; 100s into the track on the server clock.
        val skew = stationSkewMs(serverTimeMs = 1_100_000, localTimeMs = 1_098_000)
        assertEquals(2_000L, skew)
        assertEquals(100_000L, stationJoinPositionMs(entry, skew, localNowMs = 1_098_000))
    }

    @Test
    fun `a not-yet-started entry joins at 0 - the addendum's negative-offset case`() {
        // Local+skew is 1.5s BEFORE startMs: join position is negative, seek clamps to 0.
        val join = stationJoinPositionMs(entry, skewMs = 0, localNowMs = 998_500)
        assertEquals(-1_500L, join)
        assertEquals(0L, stationStartPositionMs(join))
        // A normal mid-track join passes through unchanged.
        assertEquals(100_000L, stationStartPositionMs(100_000L))
    }

    @Test
    fun `joining inside the last 5s skips the dying track`() {
        assertFalse(stationShouldSkipDyingTrack(entry, skewMs = 0, localNowMs = 1_218_999))
        assertTrue(stationShouldSkipDyingTrack(entry, skewMs = 0, localNowMs = 1_219_000))
        assertTrue("skew counts toward the wall", stationShouldSkipDyingTrack(entry, skewMs = 2_000, localNowMs = 1_217_500))
    }

    @Test
    fun `drift corrects only beyond 3s - and only compares against the live position`() {
        // Live position is 100s in; 2.9s of drift is tolerated, 3.1s is not.
        assertFalse(stationDriftExceedsLimit(playbackPositionMs = 97_100, entry = entry, skewMs = 0, localNowMs = 1_100_000))
        assertTrue(stationDriftExceedsLimit(playbackPositionMs = 96_900, entry = entry, skewMs = 0, localNowMs = 1_100_000))
        assertTrue("ahead drifts too", stationDriftExceedsLimit(playbackPositionMs = 103_100, entry = entry, skewMs = 0, localNowMs = 1_100_000))
    }
}
