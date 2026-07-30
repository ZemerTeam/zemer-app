package com.jtech.zemer.search

import com.jtech.zemer.db.MusicDatabase
import com.jtech.zemer.db.entities.SharedPlaylistSnapshot
import com.jtech.zemer.utils.reportException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps every ACTIVE share tracking its local playlist (issue #176 live-updating shares): one Room
 * flow over the shared playlists + their ordered members re-emits on rename, add, remove and
 * reorder; after a debounce, any playlist whose [sharedPlaylistFingerprint] differs from the
 * last-pushed `shareSyncedHash` is `PUT` to the server in place - same id, same URL, so the link
 * already sitting in a chat shows the current state on its next open.
 *
 * Invariants:
 * - The hash column is written only AFTER a successful PUT, so an edit made just before the app
 *   died is still pending on next start (the first emission re-checks everything).
 * - A transient failure is left alone: the hash stays stale and the next edit or app start
 *   retries. [ZemerShareGoneException] (403/404 - token rejected or link taken down) clears the
 *   stored credentials; the next Share tap mints a fresh link.
 * - An emptied playlist is NOT pushed (the server 400s an empty videoIds); the share keeps its
 *   last state until songs exist again.
 * - Failures never surface to the user - background reconciliation, telemetry-style silence.
 */
@Singleton
class SharedPlaylistAutoUpdater @Inject constructor(
    private val repository: ZemerSearchRepository,
    private val database: dagger.Lazy<MusicDatabase>,
) {
    // Singleton-owned scope: updates must survive whatever screen triggered the edit.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var started = false

    @OptIn(FlowPreview::class)
    fun start() {
        if (started) return
        started = true
        scope.launch {
            // The collector itself must be crash-proof: a DataStore/SQLite error surfacing
            // through the flow would otherwise escape into a scope with no handler and kill the
            // process from a background reconciler. Report, back off, re-subscribe.
            while (true) {
                try {
                    database.get().sharedPlaylistSnapshots()
                        .distinctUntilChanged()
                        .debounce(UPDATE_DEBOUNCE_MS)
                        .collect { snapshots -> snapshots.forEach { push(it) } }
                    break
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    reportException(e)
                    delay(COLLECTOR_RETRY_MS)
                }
            }
        }
    }

    private suspend fun push(snapshot: SharedPlaylistSnapshot) {
        val ids = snapshot.songIds
        if (ids.isEmpty()) return
        val fingerprint = sharedPlaylistFingerprint(snapshot.name, ids)
        if (fingerprint == snapshot.shareSyncedHash) return
        runCatching {
            // The name is the PER-SHARE stored one (null = anonymous), never the device-wide
            // preference: a share created anonymous must stay anonymous whatever name the user
            // later types for a different playlist.
            repository.updateUserPlaylist(snapshot.shareId, snapshot.shareOwnerToken, snapshot.name, ids, snapshot.shareSharedBy)
        }.onSuccess {
            database.get().query { updatePlaylistShareSyncedHash(snapshot.playlistId, fingerprint) }
        }.onFailure { e ->
            when {
                e is CancellationException -> throw e
                e is ZemerShareGoneException -> database.get().query {
                    updatePlaylistShare(snapshot.playlistId, null, null, null, null)
                }
                // A definitive HTTP error (contract drift, rate limit) must be VISIBLE - a server
                // that answers with 400s would otherwise silently stop every live share forever.
                e is ZemerShareHttpException -> reportException(e)
                e.isZemerServerUnreachable() -> Timber.d("share auto-update deferred: server unreachable")
                else -> reportException(e)
            }
        }
    }

    companion object {
        /** Collapses an edit burst (multi-select removes, drag reorders) into one PUT. */
        private const val UPDATE_DEBOUNCE_MS = 10_000L

        /** Backoff before re-subscribing after a flow-level failure (corrupt prefs, DB error). */
        private const val COLLECTOR_RETRY_MS = 60_000L
    }
}
