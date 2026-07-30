package com.jtech.zemer.search

import android.content.Context
import com.jtech.zemer.constants.UserPlaylistSharedByKey
import com.jtech.zemer.db.MusicDatabase
import com.jtech.zemer.db.entities.SharedPlaylistSnapshot
import com.jtech.zemer.utils.dataStore
import com.jtech.zemer.utils.reportException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
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
    @ApplicationContext private val context: Context,
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
            database.get().sharedPlaylistSnapshots()
                .debounce(UPDATE_DEBOUNCE_MS)
                .collect { snapshots -> snapshots.forEach { push(it) } }
        }
    }

    private suspend fun push(snapshot: SharedPlaylistSnapshot) {
        val ids = snapshot.songIds
        if (ids.isEmpty()) return
        val fingerprint = sharedPlaylistFingerprint(snapshot.name, ids)
        if (fingerprint == snapshot.shareSyncedHash) return
        val sharedBy = context.dataStore.data.first()[UserPlaylistSharedByKey]?.takeIf { it.isNotBlank() }
        runCatching {
            repository.updateUserPlaylist(snapshot.shareId, snapshot.shareOwnerToken, snapshot.name, ids, sharedBy)
        }.onSuccess {
            database.get().query { updatePlaylistShareSyncedHash(snapshot.playlistId, fingerprint) }
        }.onFailure { e ->
            when {
                e is CancellationException -> throw e
                e is ZemerShareGoneException -> database.get().query {
                    updatePlaylistShare(snapshot.playlistId, null, null, null)
                }
                e.isZemerServerUnreachable() -> Timber.d("share auto-update deferred: server unreachable")
                else -> reportException(e)
            }
        }
    }

    companion object {
        /** Collapses an edit burst (multi-select removes, drag reorders) into one PUT. */
        private const val UPDATE_DEBOUNCE_MS = 10_000L
    }
}
