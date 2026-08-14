package com.jtech.zemer.playback

import com.metrolist.innertube.YouTube
import java.util.concurrent.ConcurrentHashMap

/**
 * One client playback nonce (`cpn`) per in-flight listen, shared between the media (googlevideo)
 * request and the watch-time beacon session — exactly what the official WEB_REMIX client does
 * (base.js `cpn=${videoData.clientPlaybackNonce}` on the media URL, the same cpn on its stats
 * beacons). Correlating the two lets YouTube tie the reported watch time to real byte delivery.
 *
 * Keyed by BASE videoId (see [VideoRendition.baseVideoId]) so a listen's audio, video-mode and
 * merge-audio renditions all resolve to ONE cpn. [getOrCreate] is called from BOTH the stream
 * resolver (a background thread) and the [WatchTimeReporter] session (the service main scope), so the
 * store is a [ConcurrentHashMap] and creation is atomic. The reporter [release]s the id when its
 * listen ends, so the next play of the same song mints a fresh cpn (matching the client's
 * fresh-cpn-per-playback model, which keeps view counts incrementing).
 *
 * Bounded: a `cpn` is tiny and a released listen frees its entry, but an excluded play (cast) can
 * leave a stray, so the map is cleared wholesale past [MAX_ENTRIES] — a stray only means one future
 * play reuses a live cpn until its own resolve, never a correctness problem.
 */
class PlaybackNonceRegistry(
    private val generate: () -> String = YouTube::generateCpn,
) {
    private val nonces = ConcurrentHashMap<String, String>()

    fun getOrCreate(videoId: String): String {
        if (nonces.size > MAX_ENTRIES) nonces.clear()
        return nonces.computeIfAbsent(videoId) { generate() }
    }

    fun release(videoId: String) {
        nonces.remove(videoId)
    }

    companion object {
        const val MAX_ENTRIES = 64

        /**
         * Append `&cpn=<cpn>` to a media URL (googlevideo URLs already carry a query, so `&`; the `?`
         * branch is defensive). Pure so it is unit-tested without a player.
         */
        fun appendCpn(url: String, cpn: String): String =
            url + (if ('?' in url) '&' else '?') + "cpn=" + cpn
    }
}
