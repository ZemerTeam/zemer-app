package com.jtech.zemer.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What is known about a queue item's video rendition(s). */
data class VideoAvailability(
    /** The item's own music-video type (MUSIC_VIDEO_TYPE_ATV = audio song, _OMV/_UGC = a video), or null if unknown. */
    val musicVideoType: String? = null,
    /** A known video counterpart id for an audio song, or null (also null once resolved to "no counterpart"). */
    val counterpartVideoId: String? = null,
    /** True once a counterpart lookup has completed for this item (negative results included) — stops re-probing. */
    val counterpartResolved: Boolean = false,
)

/**
 * In-memory, bounded (LRU) cache of per-item [VideoAvailability] — the I7 "no Room change" store behind
 * the video-mode toggle. Thread-safe; filled from three sources (see the unified-video DESIGN §1):
 * playback resolution (music-video type, free), passive `next()` counterparts, and an on-demand `next()`
 * probe. Exposes a monotonically increasing [revision] so the controller's availability flow recomputes
 * when the table changes. Pure JVM (no `android.util.LruCache`) so it is unit-testable without Robolectric.
 */
class VideoAvailabilityCache(private val maxEntries: Int = 128) {
    private val lock = Any()

    private val map = object : LinkedHashMap<String, VideoAvailability>(16, 0.75f, /* accessOrder = */ true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, VideoAvailability>?): Boolean =
            size > maxEntries
    }

    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision.asStateFlow()

    fun get(mediaId: String): VideoAvailability? = synchronized(lock) { map[mediaId] }

    /** Record the item's own music-video type (from a playback resolution). Never clears a known type with null. */
    fun recordMusicVideoType(mediaId: String, musicVideoType: String?) {
        update(mediaId) { it.copy(musicVideoType = musicVideoType ?: it.musicVideoType) }
    }

    /**
     * Record the outcome of a counterpart lookup for [mediaId] (negative results too: [counterpartVideoId]
     * null with resolved = true, so the UI stops re-probing). Optionally updates the item's own type.
     */
    fun recordCounterpartResolution(mediaId: String, counterpartVideoId: String?, musicVideoType: String? = null) {
        update(mediaId) {
            it.copy(
                musicVideoType = musicVideoType ?: it.musicVideoType,
                counterpartVideoId = counterpartVideoId,
                counterpartResolved = true,
            )
        }
    }

    /** Passively fold in a `next()` response's song→video counterpart map (each entry marked resolved). */
    fun recordCounterparts(counterparts: Map<String, String>) {
        for ((songId, videoId) in counterparts) recordCounterpartResolution(songId, videoId)
    }

    private inline fun update(mediaId: String, transform: (VideoAvailability) -> VideoAvailability) {
        synchronized(lock) {
            val old = map[mediaId] ?: VideoAvailability()
            val new = transform(old)
            map[mediaId] = new // always touch (LRU recency); bump revision only on a real change
            if (new != old) _revision.value = _revision.value + 1
        }
    }
}
