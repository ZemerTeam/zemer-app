package com.jtech.zemer.playback

import com.metrolist.innertube.models.response.PlayerResponse
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The deferred push's keep-vs-drop classification: both beacons 2xx => SUCCESS (remove); a 400 =>
 * DROP (remove, never poison the queue); anything else — null/5xx, or no resolvable tracking =>
 * RETRY (keep for the next reconnect).
 */
class DeferredStatsPushTest {

    private val record = DeferredStatsRecord("v", "0.0", "30.0", "30.0", "30.0", 0L)

    private fun tracking(playback: String? = "https://s.youtube.com/api/stats/playback?b=1",
                         watchtime: String? = "https://s.youtube.com/api/stats/watchtime?b=1") =
        PlayerResponse.PlaybackTracking(
            videostatsPlaybackUrl = playback?.let { PlayerResponse.PlaybackTracking.VideostatsPlaybackUrl(it) },
            videostatsWatchtimeUrl = watchtime?.let { PlayerResponse.PlaybackTracking.VideostatsWatchtimeUrl(it) },
            atrUrl = null,
        )

    private fun push(
        tracking: PlayerResponse.PlaybackTracking?,
        playbackStatus: Int?,
        watchtimeStatus: Int?,
    ): DeferredPushOutcome = runBlocking {
        pushDeferredStats(
            record = record,
            fetchTracking = { tracking },
            cpn = "CPN",
            sendPlayback = { _, _ -> playbackStatus },
            sendWatchtime = { _, _, _ -> watchtimeStatus },
        )
    }

    @Test
    fun `both beacons 2xx is SUCCESS`() {
        assertEquals(DeferredPushOutcome.SUCCESS, push(tracking(), 204, 204))
        assertEquals(DeferredPushOutcome.SUCCESS, push(tracking(), 200, 200))
    }

    @Test
    fun `a 400 on either beacon is DROP`() {
        assertEquals(DeferredPushOutcome.DROP, push(tracking(), 400, 204))
        assertEquals(DeferredPushOutcome.DROP, push(tracking(), 204, 400))
    }

    @Test
    fun `a 5xx or null status is RETRY`() {
        assertEquals(DeferredPushOutcome.RETRY, push(tracking(), 503, 204))
        assertEquals(DeferredPushOutcome.RETRY, push(tracking(), 204, null))
        assertEquals(DeferredPushOutcome.RETRY, push(tracking(), 429, 429))
    }

    @Test
    fun `no resolvable tracking is RETRY, and no beacon is attempted`() {
        assertEquals(DeferredPushOutcome.RETRY, push(tracking = null, 204, 204))
        assertEquals(DeferredPushOutcome.RETRY, push(tracking(playback = null), 204, 204))
        assertEquals(DeferredPushOutcome.RETRY, push(tracking(watchtime = null), 204, 204))
    }

    @Test
    fun `watchtime is NOT sent when the playback open ping fails`() {
        var watchtimeSent = false
        val outcome = runBlocking {
            pushDeferredStats(
                record = record,
                fetchTracking = { tracking() },
                cpn = "CPN",
                sendPlayback = { _, _ -> 503 },
                sendWatchtime = { _, _, _ -> watchtimeSent = true; 204 },
            )
        }
        assertEquals(DeferredPushOutcome.RETRY, outcome)
        // A watchtime with no accepted playback ping is the orphan shape; re-pushing under a fresh cpn
        // next time would double-count the ranges. The open ping must succeed first.
        org.junit.Assert.assertFalse("watchtime must not ride a failed session-open", watchtimeSent)
    }
}
