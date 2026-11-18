package com.jtech.zemer.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Zemer Stations wire layer: the handoff-doc sample payloads decode field-for-field with the shared
 * lenient reader, the card-shaping rules ([liveStations]) hold, and the §4 tune-in clock math is
 * pinned (skew, live-offset join, the dying-track rule, and the on-air window the bidirectional
 * resync is built on). [StationQueue]/MusicService wiring has no JVM seam (Hilt EntryPoint +
 * Media3) — these pure contracts are what it builds on.
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
    fun `on-air offset is the live position only while the slot is broadcasting`() {
        // Mid-slot: 100s in.
        assertEquals(100_000L, stationOnAirOffsetMs(entry, skewMs = 0, localNowMs = 1_100_000))
        // Before startMs (we are AHEAD - wait, don't play): null.
        assertNull(stationOnAirOffsetMs(entry, skewMs = 0, localNowMs = 999_000))
        // At/after endMs (the slot ended - we are BEHIND, move on): null.
        assertNull(stationOnAirOffsetMs(entry, skewMs = 0, localNowMs = 1_224_000))
        // Exactly at startMs joins at 0.
        assertEquals(0L, stationOnAirOffsetMs(entry, skewMs = 0, localNowMs = 1_000_000))
    }

    // --- the boundary resync decision ---

    private fun slot(onAir: Long? = null, untilStart: Long? = null) = StationSlotState(onAir, untilStart)

    @Test
    fun `a dead on-air slot waits for the next queued slot instead of re-tuning`() {
        // The on-air slot failed here (offset null, start unknown); the next one starts in 90 s.
        val slots = listOf(slot(), slot(untilStart = 90_000), slot(untilStart = 300_000))
        assertEquals(StationResync.WaitFor(1, 90_000), stationResync(slots, currentIndex = 0, currentPositionMs = 0))
        // The same after skipOnError already moved to that slot.
        assertEquals(StationResync.WaitFor(1, 90_000), stationResync(slots, currentIndex = 1, currentPositionMs = 0))
    }

    @Test
    fun `an on-air slot is kept within the drift and seeked to beyond it`() {
        val slots = listOf(slot(onAir = 60_000))
        assertEquals(StationResync.Stay, stationResync(slots, currentIndex = 0, currentPositionMs = 58_000))
        assertEquals(StationResync.Seek(0, 60_000), stationResync(slots, currentIndex = 0, currentPositionMs = 10_000))
        // Behind the broadcast: the on-air slot is a later one.
        val behind = listOf(slot(untilStart = -200_000), slot(onAir = 5_000))
        assertEquals(StationResync.Seek(1, 5_000), stationResync(behind, currentIndex = 0, currentPositionMs = 0))
    }

    @Test
    fun `ahead of the broadcast waits on the current slot, or plays on when it starts within the drift`() {
        assertEquals(StationResync.WaitFor(0, 20_000), stationResync(listOf(slot(untilStart = 20_000)), 0, 0))
        assertEquals(StationResync.Stay, stationResync(listOf(slot(untilStart = 2_000)), 0, 0))
    }

    @Test
    fun `re-tunes only when nothing queued is on-air or still to come`() {
        // Every queued slot has ended (paused past the runway), or the only upcoming slot is unplayable.
        val ended = listOf(slot(untilStart = -500_000), slot(untilStart = -200_000))
        assertEquals(StationResync.Retune, stationResync(ended, 0, 0))
        assertEquals(StationResync.Retune, stationResync(listOf(slot(), slot()), 0, 0))
        assertEquals(StationResync.Retune, stationResync(emptyList(), -1, 0))
    }

    @Test
    fun `re-tunes are at least the minimum interval apart`() {
        assertEquals(0L, stationRetuneDelayMs(lastRetuneAtMs = null, nowMs = 1_000))
        assertEquals(4_000L, stationRetuneDelayMs(lastRetuneAtMs = 10_000, nowMs = 11_000))
        assertEquals(STATION_RETUNE_MIN_INTERVAL_MS, stationRetuneDelayMs(lastRetuneAtMs = 10_000, nowMs = 10_000))
        assertEquals(0L, stationRetuneDelayMs(lastRetuneAtMs = 10_000, nowMs = 16_000))
        // A burst of decisions 12 ms apart (the field report) can start at most one re-tune per window.
        var last: Long? = null
        var retunes = 0
        for (now in 0L until 60_000L step 12) {
            if (stationRetuneDelayMs(last, now) == 0L) { retunes++; last = now }
        }
        assertEquals(12, retunes)
    }

    @Test
    fun `tune-in leaves out a dying, blocked or already failed now and failed next entries`() {
        val now = ZemerStationEntry(videoId = "dead", startMs = 1_000_000, endMs = 1_224_000)
        val next = listOf(
            ZemerStationEntry(videoId = "n1", startMs = 1_224_000, endMs = 1_400_000),
            ZemerStationEntry(videoId = "n2", startMs = 1_400_000, endMs = 1_600_000),
        )
        val plain = stationTuneInPlan(now, next, skewMs = 0, localNowMs = 1_100_000) { false }
        assertEquals(now, plain.joinNow)
        assertEquals(listOf("dead", "n1", "n2"), plain.entries.map { it.videoId })

        val failed = stationTuneInPlan(now, next, skewMs = 0, localNowMs = 1_100_000) { it.videoId == "dead" }
        assertNull(failed.joinNow)
        assertEquals(listOf("n1", "n2"), failed.entries.map { it.videoId })

        val dying = stationTuneInPlan(now, next, skewMs = 0, localNowMs = 1_220_000) { it.videoId == "n2" }
        assertNull(dying.joinNow)
        assertEquals(listOf("n1"), dying.entries.map { it.videoId })
    }
}
