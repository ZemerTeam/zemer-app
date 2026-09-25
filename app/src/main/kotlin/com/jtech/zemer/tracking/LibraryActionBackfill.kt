package com.jtech.zemer.tracking

import android.content.Context
import androidx.datastore.preferences.core.edit
import com.jtech.zemer.constants.TrackingActionBackfillDoneKey
import com.jtech.zemer.constants.TrackingActionBackfillSentKey
import com.jtech.zemer.db.MusicDatabase
import com.jtech.zemer.db.entities.ActionSnapshotRow
import com.jtech.zemer.utils.dataStore
import com.jtech.zemer.utils.getSuspend
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicBoolean

/**
 * One snapshot row as a wire line, or null when the server would discard it: blank id, older than
 * the server's 10-year acceptance floor, or in the future. The server skips out-of-window rows
 * per-row (never failing a batch), so this filter is a bandwidth optimization, not a safety
 * requirement — but there is no point uploading rows the server will discard.
 */
internal fun actionBackfillLine(kind: String, id: String, timestampMillis: Long, nowMillis: Long): String? {
    if (id.isEmpty()) return null
    if (timestampMillis > nowMillis + FUTURE_TOLERANCE_MS) return null
    if (timestampMillis < nowMillis - MAX_ACTION_BACKFILL_AGE_MS) return null
    return TrackingEvents.actionBackfill(timestampMillis, kind, id).toString()
}

/**
 * The whole snapshot as uploadable wire lines, favorites first (the primary signal should land
 * first, and the resume-by-count prefix skip depends on this stable ordering). Timestamps
 * are device wall-clock (Room's UTC converter) and MUST convert via [historyEventEpochMillis]
 * with the device [zone] — the naive UTC reading shifts east-of-UTC users' freshest likes past
 * the server's now+5min bound (the review-confirmed play-backfill bug).
 */
internal fun actionBackfillLines(
    favorites: List<ActionSnapshotRow>,
    downloads: List<ActionSnapshotRow>,
    nowMillis: Long,
    zone: ZoneId,
): List<String> {
    fun convert(rows: List<ActionSnapshotRow>, kind: String) = rows.mapNotNull {
        actionBackfillLine(kind, it.id, historyEventEpochMillis(it.timestamp, zone), nowMillis)
    }
    return convert(favorites, TrackingActionKind.FAVORITE) + convert(downloads, TrackingActionKind.DOWNLOAD)
}

// Wider floor than plays: an old likedDate on a still-liked song is a long-standing favorite,
// not stale data. The age check only guards against garbage clock values.
private const val MAX_ACTION_BACKFILL_AGE_MS = 10L * 365 * 24 * 60 * 60 * 1000 // server accepts now−10y

/**
 * One-time upload of the currently-liked / currently-downloaded song snapshot as `action_backfill`
 * events (contract: handoff-docs/zemer-tracking-action-backfill-request.md — SETTLED, server-side
 * built). Mirrors [PlayHistoryBackfill] with the differences the contract settled:
 * - **Snapshot, not a log**: two snapshot queries over `song`, no id cursor or upper bound. Rows
 *   liked/downloaded after live action-tracking shipped are also live-tracked — a total, permanent
 *   overlap the server acknowledges and keeps segregated from live aggregates.
 * - **Resume by acked-line COUNT, not a row cursor**: the snapshot is recomputed on every attempt,
 *   and its timestamps are NOT stable — a device-zone change shifts every converted `t`, and
 *   `SyncUtils.syncLikedSongs` rewrites every synced song's `likedDate` to sync time — so the server's
 *   (device, kind, id, t) dedup canNOT be relied on to absorb a full-snapshot replay. Persisting
 *   the count of acked lines and skipping that prefix on restart bounds a replay to the unacked
 *   tail (the row order is stable: favorites then downloads, both `ORDER BY id`; a library
 *   mutation between launches can misalign the prefix by at most a few rows, which dedup or the
 *   segregated table absorbs). The count also advances past a server-rejected batch — the
 *   never-retry policy below.
 * - **Own start delay** (after the play backfill's) to spread first-launch load — it does NOT
 *   guarantee ordering (the play drain is still running at 90 s for histories over ~1500 rows);
 *   [Tracker.uploadBackfill]'s single-in-flight discipline is what serializes the two on the wire.
 * - Downloads include machine-initiated ones (auto-download-on-like, self-repair) — the snapshot
 *   cannot reconstruct `fromUser`, which is why the server weights backfilled `download` as weak
 *   corroboration of `favorite`, never equal-weight.
 */
object LibraryActionBackfill {
    private val started = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** [database] is a provider so the DB isn't opened unless the backfill actually has work. */
    fun maybeStart(context: Context, database: () -> MusicDatabase) {
        if (!started.compareAndSet(false, true)) return
        val appContext = context.applicationContext
        scope.launch {
            runCatching { run(appContext, database) }
                .onFailure { Timber.tag(TAG).d(it, "Action backfill aborted; will restart next launch") }
        }
    }

    private suspend fun run(context: Context, databaseProvider: () -> MusicDatabase) {
        if (context.dataStore.getSuspend(TrackingActionBackfillDoneKey, false)) return
        delay(START_DELAY_MS)

        val database = databaseProvider()
        val lines = actionBackfillLines(
            favorites = database.likedSongsForBackfill(),
            downloads = database.downloadedSongsForBackfill(),
            nowMillis = System.currentTimeMillis(),
            zone = ZoneId.systemDefault(),
        )

        var uploadedRows = 0
        var droppedRows = 0
        var ackedLines = context.dataStore.getSuspend(TrackingActionBackfillSentKey, 0L)
        val pending = lines.drop(ackedLines.coerceIn(0L, lines.size.toLong()).toInt())
        for ((index, batch) in pending.chunked(BACKFILL_BATCH_ROWS).withIndex()) {
            // Pace BETWEEN batches only: the done-flag must land immediately after the last ack —
            // a trailing sleep is a pure window for a process kill to discard a completed drain.
            if (index > 0) delay(BACKFILL_BATCH_PACE_MS)
            when (Tracker.uploadBackfill(batch)) {
                TrackingUploader.Result.Success -> uploadedRows += batch.size
                TrackingUploader.Result.DropBatch -> {
                    // Same policy as the play backfill — a malformed batch is never retried — but
                    // NEVER silent: this is real library state being dropped.
                    droppedRows += batch.size
                    Timber.tag(TAG).w("Server rejected an action-backfill batch; dropped ${batch.size} rows")
                }
                // Network/server trouble: stop quietly; a later launch resumes past the acked prefix.
                else -> return
            }
            ackedLines += batch.size
            context.dataStore.edit { it[TrackingActionBackfillSentKey] = ackedLines }
        }

        context.dataStore.edit { it[TrackingActionBackfillDoneKey] = true }
        Timber.tag(TAG).d(
            "Action backfill complete: $uploadedRows rows uploaded" +
                if (droppedRows > 0) ", $droppedRows rejected by the server" else ""
        )
    }

    private const val TAG = "Zemer_Tracker"

    // After the play backfill's 45 s to spread first-launch load. NOT an ordering guarantee — the
    // play drain (DB open at t≈0 for its bound, 100 rows / 3 s) is still running at 90 s for any
    // history over ~1500 rows; Tracker.uploadBackfill's single-in-flight discipline is what
    // actually serializes the two drains on the wire.
    private const val START_DELAY_MS = 90_000L
}
