package com.jtech.zemer.search

import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.pages.SearchSummary

/**
 * The unified-video "one result per certainly-matched song" rule (I3 / unified-video DESIGN §7).
 *
 * When a rendered screen shows both audio song rows and video rows, a video row is dropped **only** when
 * an audio song row in the SAME screen is an AUTHORITATIVE match for it:
 *   1. identical videoId, or
 *   2. a known song→video counterpart mapping ([counterpartOf]) links them.
 *
 * There is deliberately **no** title / artist / duration heuristic: an unconfirmed audio/video pair stays
 * as two rows (the official audio release, and the video release playing its extracted audio). The
 * governing guarantee is that **no video ever becomes unreachable through dedupe** — a dropped video is
 * always still reachable via the surviving song row's in-player video toggle (rule 1 → same id is the
 * same item; rule 2 → the COUNTERPART toggle).
 *
 * The counterpart tier is dormant in the shipped app (step 3 found `next()` returns no counterparts for
 * the personal/anonymous accounts, so [counterpartOf] defaults to a no-op); rule 1 (same videoId) is the
 * only tier that currently fires. If counterpart data ever lights up, pass a real [counterpartOf].
 *
 * Callers must gate on `!blockVideos`: blocked mode is frozen byte-for-byte (spec §1(a)), so no dedupe
 * runs there.
 */
object ResultDedupe {

    /**
     * Returns [videos] with every row dropped whose audio counterpart is present in [songs] by an
     * authoritative link. [songs] are the audio rows and [videos] the video rows co-occurring on one
     * screen. Order is preserved; nothing is dropped when either list is empty.
     */
    fun dropDuplicateVideos(
        songs: List<SongItem>,
        videos: List<SongItem>,
        counterpartOf: (String) -> String? = { null },
    ): List<SongItem> {
        if (songs.isEmpty() || videos.isEmpty()) return videos
        val dropIds = dropIdsFor(songs.map { it.id }, counterpartOf)
        return videos.filterNot { it.id in dropIds }
    }

    /**
     * Applies [dropDuplicateVideos] across a grouped summary (the search "All" page, both engines). The
     * audio id set is gathered from every non-video [SongItem] in ALL sections, then each section's
     * video [SongItem]s are filtered against it; a section emptied by the drop is removed. Non-song
     * items and audio songs are never touched.
     */
    fun dedupeSummaries(
        summaries: List<SearchSummary>,
        counterpartOf: (String) -> String? = { null },
    ): List<SearchSummary> {
        val songIds = summaries.asSequence()
            .flatMap { it.items.asSequence() }
            .filterIsInstance<SongItem>()
            .filterNot { it.isVideo }
            .map { it.id }
            .toList()
        if (songIds.isEmpty()) return summaries
        val dropIds = dropIdsFor(songIds, counterpartOf)
        return summaries.mapNotNull { summary ->
            val kept = summary.items.filterNot { it is SongItem && it.isVideo && it.id in dropIds }
            when {
                kept.isEmpty() -> null
                kept.size == summary.items.size -> summary
                else -> summary.copy(items = kept)
            }
        }
    }

    /** The set of video ids a video row is dropped for: the song ids themselves (rule 1) plus their known counterparts (rule 2). */
    private fun dropIdsFor(songIds: List<String>, counterpartOf: (String) -> String?): Set<String> {
        val ids = HashSet<String>(songIds)
        for (id in songIds) counterpartOf(id)?.let { ids.add(it) }
        return ids
    }
}
