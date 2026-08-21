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
            mirrorFirst<Long>(
                "version",
                mirror = { ZemerContentClient.version() },
                firebase = {
                    val doc = firestore.collection("databasenumber").document("latest").get().await()
                    val updatedAt = doc.getTimestamp("updatedAt")?.toDate()?.time
                    val update = doc.getString("update") ?: doc.getLong("update")
                    (updatedAt ?: update)?.toString()?.toLongOrNull()
                        ?: error("Missing or invalid update value in databasenumber/latest")
                },
            )
        }

    suspend fun fetchWhitelist(onProgress: (current: Int, total: Int) -> Unit = { _, _ -> }): Result<List<ArtistWhitelistEntity>> =
        runCatching {
            mirrorFirst<List<ArtistWhitelistEntity>>(
                "whitelist",
                mirror = {
                    val now = LocalDateTime.now()
                    val docs = ZemerContentClient.whitelist()
                    val total = docs.size
                    Timber.d("WhitelistFetcher: mapping %d artists from content mirror", total)
                    val entities = ArrayList<ArtistWhitelistEntity>(total)
                    docs.forEachIndexed { index, doc ->
                        // Drive the sync-overlay progress the same way the Firestore path does.
                        onProgress(index + 1, total)
                        val artistId = (doc.id.takeIf { it.isNotBlank() } ?: doc.artistId)?.takeIf { it.isNotBlank() }
                            ?: return@forEachIndexed
                        val artistName = (doc.name ?: doc.artistName)?.takeIf { it.isNotBlank() }
                            ?: return@forEachIndexed
                        entities.add(
                            ArtistWhitelistEntity(
                                artistId = artistId,
                                artistName = artistName,
                                addedAt = now,
                                source = "mirror",
                                lastSyncedAt = now,
                                // Coerce absent booleans to false ONLY here (same as the Firestore `?: false`).
                                isFemale = doc.isFemale ?: false,
                                isChasid = doc.isChasid ?: false,
                                isGenZ = doc.isGenZ ?: false,
                                isKids = doc.isKids ?: false,
                                isKidZone = doc.isKidZone ?: false,
                                displayName = doc.displayName?.takeIf { it.isNotBlank() },
                                altName = doc.altName?.takeIf { it.isNotBlank() },
                            ).also { it.thumbnailUrl = doc.thumbnail?.takeIf { t -> t.isNotBlank() } }
                        )
                    }
                    lastFetchTime = System.currentTimeMillis()
                    Timber.d("WhitelistFetcher: completed content-mirror fetch with %d artists", entities.size)
                    entities
                },
                firebase = {
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
                        val thumbnailUrl = doc.getString("thumbnail")?.takeIf { it.isNotBlank() }

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
                                isKidZone = isKidZone,
                                displayName = doc.getString("displayName")?.takeIf { it.isNotBlank() },
                                altName = doc.getString("altName")?.takeIf { it.isNotBlank() },
                            ).also { it.thumbnailUrl = thumbnailUrl }
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
                },
            )
        }

    /**
     * Fetch the read-only `blockedContentIds` collection — id-level content overrides applied everywhere
     * in the app (see [BlockedIdsCache]). Each document is one override: the id is read from the `id`
     * field (falling back to the document id), and the `reason` field (e.g. "female", "video") decides
     * which content-filter setting the block is gated on. The app only ever READS this collection.
     */
    suspend fun fetchBlockedIds(): Result<Map<String, String>> =
        runCatching {
            mirrorFirst<Map<String, String>>(
                "blockedIds",
                mirror = { ZemerContentClient.blockedIds() },
                firebase = {
                    val snapshot: QuerySnapshot = firestore.collection("blockedContentIds")
                        .get()
                        .await()
                    val entries = snapshot.documents.mapNotNull { doc ->
                        // A `disabled: true` entry is a soft-delete / template — kept in Firestore (never deleted)
                        // but not applied, so the team can turn an override off without removing the document.
                        if (doc.getBoolean("disabled") == true) return@mapNotNull null
                        val id = (doc.getString("id") ?: doc.id).trim().takeIf { it.isNotEmpty() }
                            ?: return@mapNotNull null
                        val reason = (doc.getString("reason") ?: doc.getString("category") ?: "global")
                            .trim().lowercase()
                        id to reason
                    }.toMap()
                    Timber.d("WhitelistFetcher: fetched ${entries.size} blocked content overrides")
                    entries
                },
            )
        }

    // The podcast whitelist is the authoritative allow-set + version gate, read MIRROR-FIRST from
    // content.zemer.io (already live) with the Firestore collection as fallback — exactly like the artist
    // whitelist (fetchVersion/fetchWhitelist). `zemer-search /podcasts` is the rich browse catalog, NOT
    // the gate (it enriches art in SyncUtils); the gate lives here.

    suspend fun fetchPodcastVersion(): Result<Long> =
        runCatching {
            mirrorFirst<Long>(
                "podcastVersion",
                mirror = { ZemerContentClient.podcastsWhitelistVersion() },
                firebase = {
                    val doc = firestore.collection("podcastDatabaseNumber").document("latest").get().await()
                    val updatedAt = doc.getTimestamp("updatedAt")?.toDate()?.time
                    val update = doc.getString("update") ?: doc.getLong("update")
                    (updatedAt ?: update)?.toString()?.toLongOrNull()
                        ?: error("Missing or invalid update value in podcastDatabaseNumber/latest")
                },
            )
        }

    suspend fun fetchPodcastWhitelist(onProgress: (current: Int, total: Int) -> Unit = { _, _ -> }): Result<List<PodcastWhitelistEntity>> =
        runCatching {
            mirrorFirst<List<PodcastWhitelistEntity>>(
                "podcastWhitelist",
                mirror = {
                    val now = LocalDateTime.now()
                    val docs = ZemerContentClient.podcastsWhitelist()
                    val total = docs.size
                    Timber.d("WhitelistFetcher: mapping %d podcast channels from content mirror", total)
                    val entities = ArrayList<PodcastWhitelistEntity>(total)
                    docs.forEachIndexed { index, doc ->
                        onProgress(index + 1, total)
                        val channelId = doc.id.takeIf { it.isNotBlank() } ?: return@forEachIndexed
                        val name = doc.name?.takeIf { it.isNotBlank() } ?: return@forEachIndexed
                        entities.add(
                            PodcastWhitelistEntity(
                                channelId = channelId,
                                name = name,
                                thumbnailUrl = doc.thumbnailUrl,
                                isFemale = doc.isFemale,
                                isKidZone = doc.isKidZone,
                                isVerified = doc.isVerified,
                                showCount = doc.showCount,
                                lastSyncedAt = now,
                            )
                        )
                    }
                    entities
                },
                firebase = {
                    val now = LocalDateTime.now()
                    val whitelistEntities = mutableListOf<PodcastWhitelistEntity>()
                    val snapshot: QuerySnapshot = firestore.collection("podcastChannelsWhitelist").get().await()
                    val total = snapshot.size()
                    var processed = 0
                    snapshot.documents.forEach { doc ->
                        val channelId = (doc.getString("id") ?: doc.id).takeIf { it.isNotBlank() } ?: return@forEach
                        val name = doc.getString("name")?.takeIf { it.isNotBlank() } ?: return@forEach
                        whitelistEntities.add(
                            PodcastWhitelistEntity(
                                channelId = channelId,
                                name = name,
                                thumbnailUrl = doc.getString("thumbnailUrl"),
                                isFemale = doc.getBoolean("isFemale") ?: false,
                                isKidZone = doc.getBoolean("isKidZone") ?: false,
                                isVerified = doc.getBoolean("isVerified") ?: false,
                                showCount = (doc.getLong("showCount") ?: 0L).toInt(),
                                lastSyncedAt = now,
                            )
                        )
                        processed++
                        onProgress(processed, total)
                    }
                    whitelistEntities
                },
            )
        }
}
