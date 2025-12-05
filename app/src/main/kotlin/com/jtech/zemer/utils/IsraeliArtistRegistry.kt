package com.jtech.zemer.utils

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import timber.log.Timber

/**
 * Central registry for artists that should be treated as Israeli and excluded from surfaced content.
 *
 * The data is stored in Firestore under the `israeliArtists` collection with documents containing
 * either an `id` or `artistId` field. The registry is cached in memory after the first load to
 * avoid repeated network requests and can be reused across view models to ensure consistent
 * filtering.
 */
object IsraeliArtistRegistry {
    @Volatile
    private var cachedIds: Set<String> = emptySet()

    private val mutex = Mutex()

    fun isIsraeli(artistId: String?): Boolean {
        if (artistId == null) return false
        return cachedIds.contains(artistId)
    }

    suspend fun ensureLoaded() {
        if (cachedIds.isNotEmpty()) return

        mutex.withLock {
            if (cachedIds.isNotEmpty()) return

            runCatching {
                val snapshot = FirebaseFirestore.getInstance()
                    .collection("israeliArtists")
                    .get()
                    .await()

                val ids = snapshot.documents.mapNotNull { doc ->
                    doc.getString("id") ?: doc.getString("artistId")
                }.toSet()

                cachedIds = ids
                Timber.d("IsraeliArtistRegistry: Loaded ${ids.size} artist ids")
            }.onFailure {
                Timber.w(it, "IsraeliArtistRegistry: Failed to load artist ids")
            }
        }
    }
}
