package com.jtech.zemer.statuses

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.jtech.zemer.utils.dataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persisted index of statuses the user saved to their device (the gallery holds the actual media; this
 * is the metadata that lets the Status library list, group, filter and re-open them offline). Stored as
 * one JSON array in DataStore - the same no-Room-migration approach as [StatusSeenStore] - kept
 * newest-saved first. This is only the INDEX; deleting the gallery file is the manager's job.
 */
@Singleton
class StatusDownloadsStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val key = stringPreferencesKey("status_downloads")

    val downloads: Flow<List<StatusDownload>> =
        context.dataStore.data.map { StatusDownload.decodeList(it[key]) }

    /** Add (or replace by id) a saved status, keeping the list newest-saved first. */
    suspend fun add(download: StatusDownload) {
        context.dataStore.edit { prefs ->
            val current = StatusDownload.decodeList(prefs[key]).filterNot { it.id == download.id }
            prefs[key] = StatusDownload.encodeList(listOf(download) + current)
        }
    }

    /** Drop a saved status by id (call after the gallery file is deleted). */
    suspend fun remove(id: String) {
        context.dataStore.edit { prefs ->
            prefs[key] = StatusDownload.encodeList(
                StatusDownload.decodeList(prefs[key]).filterNot { it.id == id }
            )
        }
    }

    /** The set of saved status ids, for the viewer FAB's "already saved" state. */
    val savedIds: Flow<Set<String>> = downloads.map { list -> list.mapTo(HashSet()) { it.id } }
}
