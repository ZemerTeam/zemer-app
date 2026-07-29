package com.jtech.zemer.offline

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.jtech.zemer.constants.OfflineSubsetEnabledKey
import com.jtech.zemer.constants.OfflineSubsetLastSyncedAtKey
import com.jtech.zemer.constants.OfflineSubsetWifiOnlyKey
import com.jtech.zemer.utils.dataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import androidx.datastore.preferences.core.edit
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

/** What one [OfflineSubsetSyncer.sync] attempt did. */
enum class SubsetSyncOutcome {
    /** Offline search is off and the caller did not force — nothing done. */
    DISABLED,

    /** WiFi-only is set and the active network is metered/absent — deferred, not an error. */
    SKIPPED_METERED,

    /** Local snapshot already matches the server manifest. */
    UP_TO_DATE,

    /** Shards were downloaded/removed and the new manifest committed. */
    UPDATED,

    /** A network/verification error occurred; the previous snapshot (if any) is left intact. */
    FAILED,
}

/** Observable snapshot state for settings UI. */
data class SubsetSyncStatus(
    val running: Boolean = false,
    /** Version of the committed local manifest, or null when nothing is downloaded. */
    val localVersion: Int? = null,
    val sizeOnDisk: Long = 0L,
    val lastSyncedAt: Long = 0L,
    val lastError: String? = null,
    /**
     * The last attempt was skipped by the WiFi-only gate (not an error). Rendered by the settings
     * screen — without it an explicit "Download now" on cellular was a silent no-op that left the
     * user believing a backup exists.
     */
    val waitingForWifi: Boolean = false,
)

/**
 * Downloads and incrementally updates the on-device subset snapshot (see [SubsetManifest]).
 *
 * A single sync runs at a time ([mutex]). Each run diffs the local manifest against the server's,
 * downloads only changed shards, **verifies every shard's content hash before writing it**, removes
 * stale shards, and commits the new manifest last — so a failed or interrupted run never leaves a
 * half-updated snapshot (the previous one keeps serving). Failure is expected when offline, so it is
 * logged (not reported as a crash) and surfaces as [SubsetSyncOutcome.FAILED] / [SubsetSyncStatus.lastError].
 */
@Singleton
class OfflineSubsetSyncer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: SubsetSyncClient,
) {
    private val store = SubsetStore(context)
    private val mutex = Mutex()

    private val _status = MutableStateFlow(SubsetSyncStatus())
    val status: StateFlow<SubsetSyncStatus> = _status.asStateFlow()

    /** Repopulates [status] from what is currently on disk / in prefs (call before showing settings). */
    suspend fun refresh() = withContext(Dispatchers.IO) {
        val lastSyncedAt = context.dataStore.data.first()[OfflineSubsetLastSyncedAtKey] ?: 0L
        _status.update {
            it.copy(
                localVersion = store.localManifest()?.v,
                sizeOnDisk = store.sizeOnDisk(),
                lastSyncedAt = lastSyncedAt,
            )
        }
    }

    /**
     * Brings the local snapshot up to the server manifest. [force] runs even when the opt-in is off
     * (used by the "download now" settings action, which enables + forces the first sync).
     *
     * Runs on [Dispatchers.IO]: multi-MB SHA-256 hashing and blocking file writes must never execute
     * on the caller's (often Main) dispatcher. Downloads are STAGED and promoted only after every
     * shard verified — overwriting live shards one at a time left a silently mixed-version corpus
     * whenever a later download failed.
     */
    suspend fun sync(force: Boolean = false): SubsetSyncOutcome = mutex.withLock {
        withContext(Dispatchers.IO) {
            val prefs = context.dataStore.data.first()
            val enabled = prefs[OfflineSubsetEnabledKey] ?: false
            if (!enabled && !force) return@withContext SubsetSyncOutcome.DISABLED
            val wifiOnly = prefs[OfflineSubsetWifiOnlyKey] ?: true
            if (wifiOnly && isMeteredOrOffline()) {
                // Deferred, not an error — but it MUST be visible: the settings screen renders
                // waitingForWifi so an explicit "Download now" on cellular isn't a silent dead press.
                _status.update { it.copy(waitingForWifi = true) }
                return@withContext SubsetSyncOutcome.SKIPPED_METERED
            }

            _status.update { it.copy(running = true, lastError = null, waitingForWifi = false) }
            try {
                val remote = client.fetchManifest()
                if (remote.schema != SUPPORTED_SUBSET_SCHEMA) {
                    // Unknown wire-format generation (cipher-schemaVersion precedent): shard rows are
                    // positional, so decoding them would silently mis-map — keep the last-good snapshot.
                    throw IOException("unsupported subset schema ${remote.schema} (supported: $SUPPORTED_SUBSET_SCHEMA)")
                }
                val plan = subsetSyncPlan(store.localManifest(), remote)

                store.clearStaged()
                for (shard in plan.toDownload) {
                    val bytes = client.downloadShard(shard.name)
                    val actual = subsetShardHash(bytes)
                    if (actual != shard.hash) {
                        throw IOException("shard ${shard.name} hash mismatch (expected ${shard.hash}, got $actual)")
                    }
                    store.stageShard(shard.name, bytes)
                }
                // Every download verified — only now touch the live set.
                plan.toDownload.forEach { store.promoteStagedShard(it.name) }
                plan.toDelete.forEach(store::deleteShard)
                store.pruneOrphans(remote.shards.mapTo(HashSet()) { it.name })
                store.commitManifest(remote)

                val now = System.currentTimeMillis()
                context.dataStore.edit { it[OfflineSubsetLastSyncedAtKey] = now }
                _status.update {
                    it.copy(
                        running = false,
                        localVersion = remote.v,
                        sizeOnDisk = store.sizeOnDisk(),
                        lastSyncedAt = now,
                        lastError = null,
                    )
                }
                if (plan.isNoOp) SubsetSyncOutcome.UP_TO_DATE else SubsetSyncOutcome.UPDATED
            } catch (e: CancellationException) {
                _status.update { it.copy(running = false) }
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Offline subset sync failed")
                runCatching { store.clearStaged() }
                _status.update { it.copy(running = false, lastError = e.message ?: e.javaClass.simpleName) }
                SubsetSyncOutcome.FAILED
            }
        }
    }

    /**
     * App-start auto-update: sync only when offline search is enabled and the last successful sync is
     * older than [AUTO_UPDATE_INTERVAL_MS] (or never). A cheap no-op otherwise; [sync] still applies the
     * enabled + WiFi-only gates, so an enabled-but-metered device simply defers to the next start.
     */
    suspend fun maybeSync() {
        val prefs = context.dataStore.data.first()
        if (prefs[OfflineSubsetEnabledKey] != true) return
        val last = prefs[OfflineSubsetLastSyncedAtKey] ?: 0L
        if (System.currentTimeMillis() - last < AUTO_UPDATE_INTERVAL_MS) return
        sync()
    }

    /** Wipes the downloaded snapshot (called when the user turns offline search off). */
    suspend fun clear() = mutex.withLock {
        withContext(Dispatchers.IO) {
            store.clear()
            context.dataStore.edit { it.remove(OfflineSubsetLastSyncedAtKey) }
            _status.value = SubsetSyncStatus()
        }
    }

    private fun isMeteredOrOffline(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return true
        val caps = cm.activeNetwork?.let(cm::getNetworkCapabilities) ?: return true
        return !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    companion object {
        private const val AUTO_UPDATE_INTERVAL_MS = 24L * 60 * 60 * 1000 // daily
    }
}
