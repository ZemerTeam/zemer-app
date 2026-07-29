package com.jtech.zemer.playback.queues

import android.content.Context
import androidx.media3.common.MediaItem
import com.jtech.zemer.di.ZemerSearchRepositoryEntryPoint
import com.jtech.zemer.extensions.toMediaItem
import com.jtech.zemer.models.MediaMetadata
import com.jtech.zemer.search.ZemerStationEntry
import com.jtech.zemer.search.stationJoinPositionMs
import com.jtech.zemer.search.stationShouldSkipDyingTrack
import com.jtech.zemer.search.stationSkewMs
import com.jtech.zemer.search.stationStartPositionMs
import com.jtech.zemer.utils.BlockedIdsCache
import com.jtech.zemer.utils.ContentFilterState
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * A **Zemer Station** — synchronized broadcast radio (handoff `zemer-app-stations.md`): one shared,
 * server-programmed wall-clock schedule; every listener hears the SAME track at the SAME moment.
 * The queue's whole job is §4 of the contract: join `now` at its live offset, keep the `next`
 * runway loaded, and let MusicService correct drift at track boundaries only.
 *
 * Broadcast semantics riding on this class (enforced by MusicService + the session player mask):
 * no skip/prev/scrub, pause = stop-and-rejoin-live, never persisted to disk. The tapped-station
 * failure path is the standard playQueue one (surfaced toast, previous queue restored).
 *
 * Content: pools are pre-filtered server-side to the strictest common denominator, so no flags are
 * sent; the app's blocked-ids table still runs as the third defense layer ([entryIsBlocked] — the
 * since-blocked race, ~10 min server-side). A same-track re-broadcast hours later is deduped away
 * by the central append filter (the queue accumulates played items); the wall-clock resync at the
 * following boundary absorbs that rare skipped slot.
 *
 * Tracking: every station play tags `station:<id>` ([playSource]); both context flags are true so
 * the whole broadcast reports under that source (the fill-vs-context split is meaningless for a
 * broadcast — the user chose the station, the station chose everything else).
 */
class StationQueue(
    val stationId: String,
    context: Context,
) : Queue {
    override val preloadItem: MediaMetadata? = null
    override val playSource: String = "station:$stationId"
    override val initialItemsAreContext: Boolean = true
    override val continuationIsContext: Boolean = true

    // MusicService retains currentQueue for the whole session — hold only the application context.
    private val context = context.applicationContext

    private val repository = EntryPointAccessors
        .fromApplication(context.applicationContext, ZemerSearchRepositoryEntryPoint::class.java)
        .zemerSearchRepository()

    /** mediaId → its scheduled slot, so boundary handlers can recompute the live position. */
    private val schedule = LinkedHashMap<String, ZemerStationEntry>()
    private val scheduleLock = Any()

    @Volatile
    private var skewMs = 0L

    var stationTitle: String? = null
        private set

    override suspend fun getInitialStatus(): Queue.Status = withContext(IO) {
        val tuneIn = repository.stationTuneIn(stationId)
            ?: throw IOException("Zemer station $stationId is offline")
        val receivedAt = System.currentTimeMillis()
        skewMs = stationSkewMs(tuneIn.serverTimeMs, receivedAt)
        stationTitle = tuneIn.station.title

        // §4: don't join a dying track — start from the first `next` entry (at/near its own start).
        val joinNow = tuneIn.now
            ?.takeUnless { stationShouldSkipDyingTrack(it, skewMs, receivedAt) }
            ?.takeUnless { entryIsBlocked(it) }
        val entries = listOfNotNull(joinNow) + tuneIn.next.filterNot { entryIsBlocked(it) }
        if (entries.isEmpty()) throw IOException("Zemer station $stationId served no playable entries")
        remember(entries)

        Queue.Status(
            title = tuneIn.station.title,
            items = entries.map { it.toMediaItem() },
            mediaItemIndex = 0,
            // Join the on-air track at its live offset (0 for a not-yet-started addendum entry, and
            // for a first-`next` join). Computed here, at the moment playback starts, so the fetch
            // latency is absorbed by the seek.
            position = if (joinNow != null) {
                stationStartPositionMs(stationJoinPositionMs(joinNow, skewMs, System.currentTimeMillis()))
            } else {
                0L
            },
        )
    }

    // A broadcast never ends; a failed top-up throws (SilentHandler) and the next boundary retries.
    override fun hasNextPage(): Boolean = true

    override suspend fun nextPage(): List<MediaItem> = withContext(IO) {
        val tuneIn = repository.stationTuneIn(stationId) ?: return@withContext emptyList()
        skewMs = stationSkewMs(tuneIn.serverTimeMs, System.currentTimeMillis())
        val entries = (listOfNotNull(tuneIn.now) + tuneIn.next).filterNot { entryIsBlocked(it) }
        remember(entries)
        // MusicService's central append filter drops whatever is already queued (incl. the current
        // on-air track) — only genuinely upcoming slots are added.
        entries.map { it.toMediaItem() }
    }

    /**
     * Where the broadcast is right now inside the queued [mediaId], or null when it isn't a known
     * slot. MusicService uses it for boundary drift correction and the pause→resume live rejoin.
     */
    fun livePositionMs(mediaId: String, localNowMs: Long): Long? {
        val entry = synchronized(scheduleLock) { schedule[mediaId] } ?: return null
        return stationStartPositionMs(stationJoinPositionMs(entry, skewMs, localNowMs))
    }

    /** The slot's wall-clock end (server clock, comparable via [livePositionMs]'s math). */
    fun entryDurationMs(mediaId: String): Long? =
        synchronized(scheduleLock) { schedule[mediaId] }?.let { it.endMs - it.startMs }

    private fun remember(entries: List<ZemerStationEntry>) {
        synchronized(scheduleLock) {
            entries.forEach { schedule[it.videoId] = it }
            // The schedule map only needs the runway, not the whole listening history.
            while (schedule.size > SCHEDULE_MEMORY) {
                val eldest = schedule.keys.firstOrNull() ?: break
                schedule.remove(eldest)
            }
        }
    }

    private fun entryIsBlocked(entry: ZemerStationEntry): Boolean =
        BlockedIdsCache.isBlocked(entry.videoId, ContentFilterState.current)

    private fun ZemerStationEntry.toMediaItem(): MediaItem =
        MediaMetadata(
            id = videoId,
            title = title,
            artists = listOf(MediaMetadata.Artist(id = artistId, name = artist)),
            duration = durationSec ?: -1,
            thumbnailUrl = thumbnail,
        ).toMediaItem()

    private companion object {
        const val SCHEDULE_MEMORY = 24
    }
}
