package com.jtech.zemer.utils

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import com.jtech.zemer.db.entities.ArtistWhitelistEntity
import kotlinx.coroutines.tasks.await
import java.time.LocalDateTime

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

    suspend fun fetchWhitelist(onProgress: (Int) -> Unit = {}): Result<List<ArtistWhitelistEntity>> =
        runCatching {
            val now = LocalDateTime.now()
            val whitelistEntities = mutableListOf<ArtistWhitelistEntity>()
            var processed = 0

            var snapshot: QuerySnapshot? = firestore.collection("artistsWhitelist")
                .limit(500)
                .get()
                .await()

            while (snapshot != null && !snapshot.isEmpty) {
                snapshot.documents.forEach { doc ->
                    val artistId = (doc.getString("id") ?: doc.getString("artistId")) ?: return@forEach
                    val artistName = (doc.getString("name") ?: doc.getString("artistName")) ?: return@forEach
                    val isFemale = doc.getBoolean("isFemale") ?: false
                    val isChasid = doc.getBoolean("isChasid") ?: false
                    val isGenZ = doc.getBoolean("isGenZ") ?: false

                    whitelistEntities.add(
                        ArtistWhitelistEntity(
                            artistId = artistId,
                            artistName = artistName,
                            addedAt = now,
                            source = "firestore",
                            lastSyncedAt = now,
                            isFemale = isFemale,
                            isChasid = isChasid,
                            isGenZ = isGenZ
                        )
                    )
                    processed++
                    onProgress(processed)
                }

                val last = snapshot.documents.lastOrNull()
                snapshot = if (last != null) {
                    firestore.collection("artistsWhitelist")
                        .startAfter(last)
                        .limit(500)
                        .get()
                        .await()
                } else null
            }

            lastFetchTime = System.currentTimeMillis()
            whitelistEntities
        }
}
