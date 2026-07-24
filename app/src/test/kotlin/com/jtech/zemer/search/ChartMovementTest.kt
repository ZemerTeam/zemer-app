package com.jtech.zemer.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Guards the chart-movement contract (tracking thread, RESPONSE 3). The precedence and the
 * absent-means-NOTHING rule are the two places a plausible-looking implementation goes wrong: a
 * fallback to zero would render "no change" for songs that have no baseline at all, and reading
 * `delta` before `new` would show "▲ 0" on a debut.
 */
class ChartMovementTest {

    private fun track(
        delta: Int? = null,
        prevRank: Int? = null,
        isNew: Boolean = false,
        isReentry: Boolean = false,
    ) = ZemerTrack(
        videoId = "dQw4w9WgXcQ",
        delta = delta,
        prevRank = prevRank,
        isNew = isNew,
        isReentry = isReentry,
    )

    @Test
    fun `positive delta is a CLIMB - the server sends prevRank minus currentRank`() {
        assertEquals(ChartMovement.Up(3), chartMovementOf(track(delta = 3, prevRank = 5)))
        assertEquals(ChartMovement.Down(1), chartMovementOf(track(delta = -1, prevRank = 2)))
        assertEquals(ChartMovement.Unchanged, chartMovementOf(track(delta = 0, prevRank = 1)))
    }

    @Test
    fun `new and reentry win over delta - a debut has no meaningful delta to render`() {
        assertEquals(ChartMovement.New, chartMovementOf(track(isNew = true)))
        assertEquals(ChartMovement.Reentry, chartMovementOf(track(isReentry = true)))
        // Defensive: if a payload ever carried both, the flags still beat the number.
        assertEquals(ChartMovement.New, chartMovementOf(track(delta = 4, isNew = true)))
        assertEquals(ChartMovement.Reentry, chartMovementOf(track(delta = 4, isReentry = true)))
    }

    @Test
    fun `rank is parsed off the wire and is independent of the movement fields`() {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val response = json.decodeFromString<ZemerCuratedPlaylistResponse>(
            """
            {"playlist":{"id":"auto-top-50","title":"Top 50","anchorDate":"2026-07-19"},
             "tracks":[
               {"videoId":"oRTnBuM5H_I","rank":31,"prevRank":34,"delta":3},
               {"videoId":"uXhQc8v6Jic","rank":32,"new":true},
               {"videoId":"kK7YfL2pQ1s","rank":34}
             ]}
            """.trimIndent(),
        )

        assertEquals("2026-07-19", response.playlist.anchorDate)
        // A position exists even with no movement at all: `rank` — never anchorDate, and never the
        // row index — is what decides whether this is a ranked chart.
        assertEquals(listOf(31, 32, 34), response.tracks.map { it.rank })
        assertEquals(
            listOf(ChartMovement.Up(3), ChartMovement.New, null),
            response.tracks.map(::chartMovementOf),
        )
        // 3 rows whose last position is 34: a filtered chart's row count is NOT its last position.
        assertEquals(3, response.tracks.size)
    }

    @Test
    fun `absent movement is null, never Unchanged - no baseline must render NO badge`() {
        // Curated playlists, a too-young rank history, auto-year-<YYYY> (a dynamic rule, never a
        // ranked chart), and the window after a ranking-formula change all look like this. Rendering
        // a dash here would claim "held its position" about a song that was never ranked.
        assertNull(chartMovementOf(track()))
        assertNull(chartMovementOf(track(prevRank = 7)))
    }
}
