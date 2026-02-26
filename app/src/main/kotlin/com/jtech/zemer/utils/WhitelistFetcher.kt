package com.jtech.zemer.utils

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import com.jtech.zemer.db.entities.ArtistWhitelistEntity
import com.jtech.zemer.db.entities.PodcastWhitelistEntity
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
                val isKidZone = doc.getBoolean("isKidZone") ?: false

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
                        isKids = isKids,
                        isKidZone = isKidZone
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

    var lastPodcastFetchTime = -1L
        private set

    suspend fun fetchPodcastVersion(): Result<Long> =
        runCatching {
            val doc = firestore.collection("podcastDatabaseNumber").document("latest").get().await()
            val updatedAt = doc.getTimestamp("updatedAt")?.toDate()?.time
            val update = doc.getString("update") ?: doc.getLong("update")
            val value = (updatedAt ?: update)?.toString()?.toLongOrNull()
                ?: error("Missing or invalid update value in podcastDatabaseNumber/latest")
            value
        }

    suspend fun fetchPodcastWhitelist(onProgress: (current: Int, total: Int) -> Unit = { _, _ -> }): Result<List<PodcastWhitelistEntity>> =
        runCatching {
            val now = LocalDateTime.now()
            val whitelistEntities = mutableListOf<PodcastWhitelistEntity>()
            Timber.d("WhitelistFetcher: starting full fetch of podcastsWhitelist...")

            val snapshot: QuerySnapshot = firestore.collection("podcastsWhitelist")
                .get()
                .await()
            val total = snapshot.size()

            var processed = 0
            snapshot.documents.forEach { doc ->
                val podcastId = (doc.getString("id") ?: doc.getString("podcastId")) ?: return@forEach
                val podcastName = (doc.getString("name") ?: doc.getString("podcastName")) ?: return@forEach
                val thumbnailUrl = doc.getString("thumbnailUrl")
                val channelId = doc.getString("channelId")

                whitelistEntities.add(
                    PodcastWhitelistEntity(
                        podcastId = podcastId,
                        podcastName = podcastName,
                        thumbnailUrl = thumbnailUrl,
                        channelId = channelId,
                        addedAt = now,
                        source = "firestore",
                        lastSyncedAt = now
                    )
                )
                processed++
                onProgress(processed, total)
                if (processed % 50 == 0) {
                    Timber.d("WhitelistFetcher: fetched $processed/$total podcasts so far")
                }
            }

            lastPodcastFetchTime = System.currentTimeMillis()
            Timber.d("WhitelistFetcher: completed fetch with $processed podcasts")
            whitelistEntities
        }
}
