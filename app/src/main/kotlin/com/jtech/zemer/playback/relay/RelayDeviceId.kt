package com.jtech.zemer.playback.relay

import android.content.Context
import androidx.datastore.preferences.core.edit
import com.jtech.zemer.BuildConfig
import com.jtech.zemer.constants.RelayDeviceIdKey
import com.jtech.zemer.utils.dataStore
import com.jtech.zemer.utils.getSuspend
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The relay-only device id (header [HEADER]) that lets `stream.zemer.io` count distinct relay devices per
 * content-filter (the relay pairs the id with the request's filter-egress IP).
 *
 * SEPARATE from the zemer-stats tracking id ([RelayDeviceIdKey] vs `TrackingDeviceIdKey`) and only ever sent
 * to the relay, so the pairing stays relay-only and never joins the PII-free listening history. Sent on
 * RELAY-mode requests only, never DIRECT. Debug builds send NO id ([resolve] returns null under
 * `BuildConfig.DEBUG`) and mark requests with `x-zemer-debug` instead so they serve but are not counted.
 * Contract: `handoff-docs/zemer-app-relay-device-id-request.md` (option B).
 */
object RelayDeviceId {
    /** The request header the relay reads (lowercase). A valid id is counted; anything else is ignored. */
    const val HEADER = "x-zemer-device"

    // The exact shape the relay validates; our ids are UUIDs, which always match.
    private val SHAPE = Regex("^[A-Za-z0-9_.:-]{4,64}$")

    // Serializes get-or-create so a fresh install's first concurrent playback + download can't mint two
    // divergent ids (which would miscount one device as two).
    private val mutex = Mutex()

    /** Pure shape check mirroring the relay's own validation (JVM-tested). */
    fun isValid(id: String?): Boolean = id != null && SHAPE.matches(id)

    /**
     * Pure resolution (JVM-tested), the single source of the id policy: no id in debug; the [stored] id if
     * valid; otherwise a freshly [mint]ed one. The caller persists the result when it differs from [stored].
     */
    fun resolve(stored: String?, debug: Boolean, mint: () -> String): String? = when {
        debug -> null
        isValid(stored) -> stored
        else -> mint()
    }

    // Durable get-or-create under the mutex: reads, resolves, and AWAITS the persist so the id is committed
    // before it is used (a process killed afterwards keeps the same id) and concurrent callers converge on
    // one value. Both entry points funnel through here so the playback and download paths can never diverge.
    private suspend fun getOrCreate(context: Context): String? = mutex.withLock {
        // A DataStore hiccup must NOT propagate: getSync runs inside the relay data-source resolver, so an
        // exception here would surface as an ExoPlayer load error and stop playback. Fail soft -> no header.
        runCatching {
            val stored = context.dataStore.getSuspend(RelayDeviceIdKey, "")
            resolve(stored, BuildConfig.DEBUG) { UUID.randomUUID().toString() }?.also { id ->
                if (id != stored) context.dataStore.edit { it[RelayDeviceIdKey] = id }
            }
        }.getOrNull()
    }

    /** Get-or-create the persisted relay id, or null in debug. Suspend: the download path. */
    suspend fun get(context: Context): String? = getOrCreate(context)

    /**
     * Get-or-create for the playback factory, which is built synchronously on ExoPlayer's loading thread
     * (a Media3 contract, never the main thread — the same off-main blocking site as the DIRECT resolver's
     * runBlocking). Debug short-circuits before touching DataStore (`resolve` would return null anyway).
     */
    fun getSync(context: Context): String? =
        if (BuildConfig.DEBUG) null else runBlocking { getOrCreate(context) }
}
