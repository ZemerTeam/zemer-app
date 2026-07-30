package com.jtech.zemer.search

import com.jtech.zemer.db.MusicDatabase
import com.jtech.zemer.db.entities.PlaylistContentSnapshot
import com.jtech.zemer.utils.reportException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps every ACTIVE share tracking its local playlist (issue #176 live-updating shares): the
 * DataStore credential map ([ShareCredentialStore]) is combined with one Room flow over every
 * playlist's title + ordered members (re-emits on rename, add, remove, reorder); after a
 * debounce, any SHARED playlist whose [sharedPlaylistFingerprint] differs from the last-pushed
 * `syncedHash` is `PUT` to the server in place - same id, same URL, so the link already sitting
 * in a chat shows the current state on its next open.
 *
 * Invariants:
 * - The hash is written only AFTER a successful PUT, so an edit made just before the app died is
 *   still pending on next start (the first emission re-checks everything).
 * - Updates send the PER-SHARE stored name ([ShareCredentials.sharedBy]), never the device-wide
 *   name preference - a share created anonymous must never be retroactively de-anonymized.
 * - A credential entry whose playlist no longer exists locally (deleted playlist, logout wiping
 *   synced rows) is an ORPHAN: the share is withdrawn server-side and the entry removed - the
 *   generic replacement for per-site delete/logout hooks.
 * - A transient failure is left alone (retried on the next edit or app start); a definitive HTTP
 *   error ([ZemerShareHttpException]) is REPORTED - a server answering 400s must not silently
 *   stall every live share; [ZemerShareGoneException] (403/404) removes the entry so the next
 *   Share tap mints a fresh link. An emptied playlist is not pushed (the server 400s empty
 *   videoIds); the share keeps its last state until songs exist again.
 * - The collector itself is crash-proof: a DataStore/SQLite error surfacing through the flows is
 *   reported and re-subscribed after a backoff, never left to kill the process from a background
 *   reconciler.
 */
@Singleton
class SharedPlaylistAutoUpdater @Inject constructor(
    private val repository: ZemerSearchRepository,
    private val credentialStore: ShareCredentialStore,
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
            while (true) {
                try {
                    combine(credentialStore.shares, database.get().playlistContentSnapshots()) { shares, playlists ->
                        shares to playlists
                    }
                        .distinctUntilChanged()
                        .debounce(UPDATE_DEBOUNCE_MS)
                        .collect { (shares, playlists) -> reconcile(shares, playlists) }
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

    private suspend fun reconcile(shares: Map<String, ShareCredentials>, playlists: List<PlaylistContentSnapshot>) {
        val byId = playlists.associateBy { it.playlistId }
        shares.forEach { (playlistId, credentials) ->
            val content = byId[playlistId]
            if (content == null) withdrawOrphan(playlistId, credentials) else push(playlistId, credentials, content)
        }
    }

    private suspend fun withdrawOrphan(playlistId: String, credentials: ShareCredentials) {
        runCatching { repository.deleteUserPlaylist(credentials.shareId, credentials.ownerToken) }
            .onSuccess { credentialStore.remove(playlistId) }
            .onFailure { e ->
                when {
                    e is CancellationException -> throw e
                    e is ZemerShareGoneException -> credentialStore.remove(playlistId) // already gone = goal state
                    e.isZemerServerUnreachable() -> Timber.d("orphan share withdrawal deferred: server unreachable")
                    else -> reportException(e)
                }
            }
    }

    private suspend fun push(playlistId: String, credentials: ShareCredentials, content: PlaylistContentSnapshot) {
        val ids = content.songIds
        if (ids.isEmpty()) return
        val fingerprint = sharedPlaylistFingerprint(content.name, ids)
        if (fingerprint == credentials.syncedHash) return
        runCatching {
            repository.updateUserPlaylist(credentials.shareId, credentials.ownerToken, content.name, ids, credentials.sharedBy)
        }.onSuccess {
            credentialStore.updateSyncedHash(playlistId, fingerprint)
        }.onFailure { e ->
            when {
                e is CancellationException -> throw e
                e is ZemerShareGoneException -> credentialStore.remove(playlistId)
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
