package com.jtech.zemer.playback

/**
 * Process-wide registry of ids the CORPUS classified as video-songs ([com.metrolist.innertube.models.SongItem.isVideo],
 * set once by ZemerResultMapper). Marked at the SongItem → MediaMetadata boundary — the flag is
 * deliberately NOT persisted into playback metadata (downloads/library must treat video-songs as
 * ordinary audio), but the Song/Video toggle needs it: the corpus classification is authoritative,
 * so the toggle can show INSTANTLY instead of waiting a network round-trip for YouTube's own
 * musicVideoType (which, once learned, still governs — see VideoModeLogic.availability).
 *
 * Bounded LRU so a long session can't grow it unbounded; thread-safe (marked from UI threads,
 * read from the controller's main scope).
 */
object VideoSongIds {
    private const val MAX_ENTRIES = 512

    private val ids = object : LinkedHashMap<String, Unit>(MAX_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Unit>?): Boolean =
            size > MAX_ENTRIES
    }

    fun mark(id: String) = synchronized(ids) { ids.put(id, Unit) }

    fun contains(id: String): Boolean = synchronized(ids) { ids.containsKey(id) }
}
