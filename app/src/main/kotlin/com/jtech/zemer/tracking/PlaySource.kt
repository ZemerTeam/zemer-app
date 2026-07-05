package com.jtech.zemer.tracking

import java.util.concurrent.ConcurrentHashMap

/**
 * The `source` taxonomy of the tracking spec — where a play started. Set when a queue is built;
 * items beyond the originally-chosen context resolve to [RADIO].
 */
object PlaySource {
    const val SEARCH = "search"
    const val NEW = "new"
    const val RADIO = "radio"
    const val OTHER = "other"

    fun artist(id: String) = "artist:$id"
    fun album(id: String) = "album:$id"
    fun playlist(id: String) = "playlist:$id"
    fun community(id: String) = "community:$id"
    fun zemer(id: String) = "zemer:$id"
}

/**
 * Resolves a played mediaId to its [PlaySource]. The player service registers ids as queues are
 * built: the user-chosen context items carry the queue's source, radio-continuation items carry
 * [PlaySource.RADIO], and anything unregistered (manually queued items, a queue restored from disk)
 * reads as [PlaySource.OTHER]. A new queue replaces the whole registry, so the map stays bounded by
 * the current queue's size.
 *
 * Thread-safe: registrations come from the service's coroutines, lookups from the analytics thread.
 */
class PlaySourceResolver {
    private val sources = ConcurrentHashMap<String, String>()

    /** A new queue started: forget the old context entirely, register its chosen items. */
    fun onQueueStarted(source: String, contextIds: List<String>) {
        sources.clear()
        registerContext(source, contextIds)
    }

    /** Late-loaded items that still belong to the current queue's chosen context. */
    fun registerContext(source: String, contextIds: List<String>) {
        contextIds.forEach { if (it.isNotEmpty()) sources[it] = source }
    }

    /** Items appended by autoplay/radio continuation beyond the original context. */
    fun registerRadio(ids: List<String>) {
        // Never demote a context item: a radio fetch can re-suggest a song already in the context.
        ids.forEach { if (it.isNotEmpty()) sources.putIfAbsent(it, PlaySource.RADIO) }
    }

    fun sourceFor(mediaId: String): String = sources[mediaId] ?: PlaySource.OTHER
}
