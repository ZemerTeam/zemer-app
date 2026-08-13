package com.jtech.zemer.search

import android.content.Context
import androidx.datastore.preferences.core.edit
import com.jtech.zemer.constants.UserPlaylistSharesKey
import com.jtech.zemer.utils.dataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A playlist's active share (issue #176): the server share id, the owner secret (the sole
 * capability for PUT/DELETE), the fingerprint of the state last successfully pushed, and the
 * sharer name THIS share was created with (null = anonymous - updates must keep sending it, never
 * the device-wide name preference).
 */
@Serializable
data class ShareCredentials(
    val shareId: String,
    val ownerToken: String,
    val syncedHash: String? = null,
    val sharedBy: String? = null,
)

/**
 * The device's share credentials, as one JSON map in DataStore keyed by local playlist id.
 * DELIBERATELY not Room columns: the feature then needs no schema migration, and DataStore edits
 * are serialized, so read-modify-write updates cannot interleave (the split-mutation race class
 * the download doctrine warns about is structurally impossible here). An entry whose playlist no
 * longer exists locally (deleted playlist, logout wiping synced rows) is an ORPHAN - the
 * auto-updater withdraws it and removes the entry.
 */
@Singleton
class ShareCredentialStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** playlistId -> credentials; emits on every change. */
    val shares: Flow<Map<String, ShareCredentials>> = context.dataStore.data
        .map { decodeShareCredentials(it[UserPlaylistSharesKey]) }
        .distinctUntilChanged()

    suspend fun get(playlistId: String): ShareCredentials? =
        decodeShareCredentials(context.dataStore.data.first()[UserPlaylistSharesKey])[playlistId]

    suspend fun set(playlistId: String, credentials: ShareCredentials) = mutate { it + (playlistId to credentials) }

    suspend fun remove(playlistId: String) = mutate { it - playlistId }

    suspend fun updateSyncedHash(playlistId: String, syncedHash: String) = mutate { map ->
        map[playlistId]?.let { map + (playlistId to it.copy(syncedHash = syncedHash)) } ?: map
    }

    private suspend fun mutate(transform: (Map<String, ShareCredentials>) -> Map<String, ShareCredentials>) {
        context.dataStore.edit { prefs ->
            prefs[UserPlaylistSharesKey] = encodeShareCredentials(transform(decodeShareCredentials(prefs[UserPlaylistSharesKey])))
        }
    }
}

private val shareCredentialsJson = Json { ignoreUnknownKeys = true }

internal fun encodeShareCredentials(map: Map<String, ShareCredentials>): String =
    shareCredentialsJson.encodeToString(map)

/** A corrupt/absent blob decodes to empty - losing credentials beats crashing a background path. */
internal fun decodeShareCredentials(json: String?): Map<String, ShareCredentials> =
    json?.let { runCatching { shareCredentialsJson.decodeFromString<Map<String, ShareCredentials>>(it) }.getOrNull() }.orEmpty()
