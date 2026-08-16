package com.jtech.zemer.playback

import com.metrolist.innertube.YouTube

/**
 * One client playback nonce (`cpn`) per in-flight listen, shared between the media (googlevideo)
 * request and the watch-time beacon session — exactly what the official WEB_REMIX client does
 * (base.js `cpn=${videoData.clientPlaybackNonce}` on the media URL, the same cpn on its stats
 * beacons). Correlating the two lets YouTube tie the reported watch time to real byte delivery.
 *
 * Keyed by BASE videoId (see [VideoRendition.baseVideoId]) so a listen's audio, video-mode and
 * merge-audio renditions all resolve to ONE cpn. [getOrCreate] is called from BOTH the stream
 * resolver (a background thread) and the [WatchTimeReporter] session (the service main scope), so
 * every access is `synchronized`. The reporter [release]s the id when its listen ends, so the next
 * play of the same song mints a fresh cpn (matching the client's fresh-cpn-per-playback model, which
 * keeps view counts incrementing).
 *
 * Bounded WITHOUT ever wiping the live listen's cpn: an excluded/skipped resolve (cast, relay,
 * preload that never plays) can leave a stray, so the store is an access-ordered LRU that evicts the
 * LEAST-recently-used entry past [MAX_ENTRIES]. The active listen is [pin]ned by the reporter and is
 * never evicted while it plays — the earlier wholesale `clear()` could drop the currently-playing
 * cpn mid-stream, silently breaking the very byte↔beacon correlation this class exists for.
 */
class PlaybackNonceRegistry(
    private val generate: () -> String = YouTube::generateCpn,
) {
    private val lock = Any()

    /** Ids the reporter has pinned as actively playing — never evicted regardless of LRU age. */
    private val pinned = HashSet<String>()

    // Access-ordered (accessOrder = true): getOrPut touches an entry to youngest, so the eldest is
    // genuinely the least-recently-used. removeEldestEntry evicts it past the cap, but never a pinned
    // (currently-playing) id — so the live listen's cpn survives no matter how many strays accrue.
    private val nonces = object : LinkedHashMap<String, String>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>): Boolean =
            size > MAX_ENTRIES && eldest.key !in pinned
    }

    fun getOrCreate(videoId: String): String = synchronized(lock) {
        nonces.getOrPut(videoId) { generate() }
    }

    /** Protect the active listen's cpn from LRU eviction for the life of the listen. */
    fun pin(videoId: String): Unit = synchronized(lock) {
        pinned.add(videoId)
        Unit
    }

    fun release(videoId: String): Unit = synchronized(lock) {
        nonces.remove(videoId)
        pinned.remove(videoId)
        Unit
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
