package com.jtech.zemer.statuses

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.jtech.zemer.utils.dataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persisted set of status post ids the user has already viewed (WhatsApp "seen" state). A creator's
 * story ring is muted once every one of its `recent_post_ids` is in here, and lights up again when a
 * new status arrives (its id is not yet seen). Seen statuses stay openable — this only drives the ring
 * colour, never blocks viewing.
 */
@Singleton
class StatusSeenStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val key = stringSetPreferencesKey("status_seen_post_ids")

    val seen: Flow<Set<String>> = context.dataStore.data.map { it[key] ?: emptySet() }

    suspend fun markSeen(ids: Collection<String>) {
        if (ids.isEmpty()) return
        context.dataStore.edit { prefs -> prefs[key] = (prefs[key] ?: emptySet()) + ids }
    }
}
