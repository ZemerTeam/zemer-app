package com.jtech.zemer.utils

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import com.jtech.zemer.db.entities.ArtistWhitelistEntity
import kotlinx.coroutines.tasks.await
import java.time.LocalDateTime
import timber.log.Timber

object WhitelistFetcher {
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    var lastFetchTime = -1L
        private set

    suspend fun fetchVersion(): Result<Long> =
        runCatching {
            val doc = firestore.collection("databasenumber").document("latest").get().await()
            val updatedAt = doc.getTimestamp("updatedAt")?.toDate()?.time
            val update = doc.getString("update") ?: doc.getLong("update")
            val value = (updatedAt ?: update)?.toString()?.toLongOrNull()
                ?: error("Missing or invalid update value in databasenumber/latest")
            value
        }

    suspend fun fetchWhitelist(onProgress: (current: Int, total: Int) -> Unit = { _, _ -> }): Result<List<ArtistWhitelistEntity>> =
        runCatching {
            val now = LocalDateTime.now()
            val whitelistEntities = mutableListOf<ArtistWhitelistEntity>()
            Timber.d("WhitelistFetcher: starting full fetch of artistsWhitelist...")

            val snapshot: QuerySnapshot = firestore.collection("artistsWhitelist")
                .get()
                .await()
            val total = snapshot.size()

            var processed = 0
            snapshot.documents.forEach { doc ->
                val artistId = (doc.getString("id") ?: doc.getString("artistId")) ?: return@forEach
                val artistName = (doc.getString("name") ?: doc.getString("artistName")) ?: return@forEach
                val isFemale = doc.getBoolean("isFemale") ?: false
                val isChasid = doc.getBoolean("isChasid") ?: false
                val isGenZ = doc.getBoolean("isGenZ") ?: false
                val isKids = doc.getBoolean("isKids") ?: false

                whitelistEntities.add(
                    ArtistWhitelistEntity(
                        artistId = artistId,
                        artistName = artistName,
                        addedAt = now,
                        source = "firestore",
                        lastSyncedAt = now,
                        isFemale = isFemale,
                        isChasid = isChasid,
                        isGenZ = isGenZ,
                        isKids = isKids
                    )
                )
                processed++
                onProgress(processed, total)
                if (processed % 200 == 0) {
                    Timber.d("WhitelistFetcher: fetched $processed/$total artists so far")
                }
            }

            lastFetchTime = System.currentTimeMillis()
            Timber.d("WhitelistFetcher: completed fetch with $processed artists")
            whitelistEntities
        }
}
