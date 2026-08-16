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
 *
 * The watchtime ping is fired ONLY after the playback (session-open) ping is accepted — a watchtime
 * with no preceding playback ping is the orphan shape a real client never produces, AND on a partial
 * failure (playback ok, watchtime not) the whole record is re-pushed under a fresh cpn next time, so
 * beaconing the watchtime before the playback succeeds would double-count on retry. Classification:
 *  - playback not 2xx → 400 ⇒ DROP (malformed), else ⇒ RETRY (watchtime NOT sent)
 *  - playback 2xx, then watchtime: 2xx ⇒ SUCCESS, 400 ⇒ DROP, else ⇒ RETRY
 */
suspend fun pushDeferredStats(
    record: DeferredStatsRecord,
    fetchTracking: suspend (videoId: String) -> PlayerResponse.PlaybackTracking?,
    cpn: String,
    sendPlayback: suspend (url: String, cpn: String, cmt: String) -> Int?,
    sendWatchtime: suspend (url: String, cpn: String, record: DeferredStatsRecord) -> Int?,
): DeferredPushOutcome {
    // Video temporarily unresolvable (bot-gate, transient error) or no usable tracking block — keep and
    // retry; the queue's staleness cap eventually drops a permanently-gone id.
    val tracking = fetchTracking(record.videoId) ?: return DeferredPushOutcome.RETRY
    val playbackUrl = tracking.videostatsPlaybackUrl?.baseUrl ?: return DeferredPushOutcome.RETRY
    val watchtimeUrl = tracking.videostatsWatchtimeUrl?.baseUrl ?: return DeferredPushOutcome.RETRY

    // Open the session first; do NOT beacon watch time until it is accepted. The open ping's cmt is the
    // listen's REAL start position (first watched range), not 0 — a resumed/offline listen begins at a
    // nonzero media time, and an open-at-0 + watchtime-at-60 mismatch is an inconsistency the live path
    // never produces.
    val playbackStatus = sendPlayback(playbackUrl, cpn, record.openCmt())
    if (!playbackStatus.is2xx()) {
        return if (playbackStatus == 400) DeferredPushOutcome.DROP else DeferredPushOutcome.RETRY
    }

    val watchtimeStatus = sendWatchtime(watchtimeUrl, cpn, record)
    return when {
        watchtimeStatus.is2xx() -> DeferredPushOutcome.SUCCESS
        watchtimeStatus == 400 -> DeferredPushOutcome.DROP
        else -> DeferredPushOutcome.RETRY
    }
}

private fun Int?.is2xx(): Boolean = this != null && this in 200..299
