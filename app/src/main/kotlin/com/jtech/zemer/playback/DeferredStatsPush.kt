package com.jtech.zemer.playback

import com.metrolist.innertube.models.response.PlayerResponse

/**
 * Push ONE deferred offline listen as a late playback-stats session — fresh `/player` for the real
 * tracking URLs (their `ei`/`plid`/`vm` tokens are fresh, so there is no expiry problem, which is why
 * deferral works), one fresh `cpn`, then the playback ping (`cmt=0`, `final=0`) and a single `final=1`
 * watchtime ping carrying the STORED real ranges — nothing re-derived, nothing fabricated.
 *
 * Network I/O is injected ([fetchTracking]/[sendPlayback]/[sendWatchtime], each returning the HTTP
 * status or null on throw) so the keep-vs-drop classification is unit-testable without YouTube.
 * Status classification, applied to both beacons:
 *  - both 2xx        → [DeferredPushOutcome.SUCCESS] (accepted; remove)
 *  - either exactly 400 → [DeferredPushOutcome.DROP] (malformed; remove, never poison the queue)
 *  - anything else (null/5xx/429, or no resolvable tracking) → [DeferredPushOutcome.RETRY] (keep)
 */
suspend fun pushDeferredStats(
    record: DeferredStatsRecord,
    fetchTracking: suspend (videoId: String) -> PlayerResponse.PlaybackTracking?,
    cpn: String,
    sendPlayback: suspend (url: String, cpn: String) -> Int?,
    sendWatchtime: suspend (url: String, cpn: String, record: DeferredStatsRecord) -> Int?,
): DeferredPushOutcome {
    // Video temporarily unresolvable (bot-gate, transient error) or no usable tracking block — keep and
    // retry; the queue's staleness cap eventually drops a permanently-gone id.
    val tracking = fetchTracking(record.videoId) ?: return DeferredPushOutcome.RETRY
    val playbackUrl = tracking.videostatsPlaybackUrl?.baseUrl ?: return DeferredPushOutcome.RETRY
    val watchtimeUrl = tracking.videostatsWatchtimeUrl?.baseUrl ?: return DeferredPushOutcome.RETRY

    val playbackStatus = sendPlayback(playbackUrl, cpn)
    val watchtimeStatus = sendWatchtime(watchtimeUrl, cpn, record)

    return when {
        playbackStatus.is2xx() && watchtimeStatus.is2xx() -> DeferredPushOutcome.SUCCESS
        playbackStatus == 400 || watchtimeStatus == 400 -> DeferredPushOutcome.DROP
        else -> DeferredPushOutcome.RETRY
    }
}

private fun Int?.is2xx(): Boolean = this != null && this in 200..299
