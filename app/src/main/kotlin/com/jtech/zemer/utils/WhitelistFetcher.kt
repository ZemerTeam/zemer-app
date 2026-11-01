package com.jtech.zemer.utils

import com.jtech.zemer.db.entities.ArtistWhitelistEntity
import com.jtech.zemer.db.entities.PodcastWhitelistEntity
import java.time.LocalDateTime
import timber.log.Timber

/**
 * Reads the content allow-sets from the content server ([ZemerContentClient], content.zemer.io), the
 * single source of truth. Every call returns a [Result]; a failure (server unreachable, non-2xx, empty
 * or invalid body) leaves the caller's last-good local table in place (see `SyncUtils`).
 */
object WhitelistFetcher {
    var lastFetchTime = -1L
        private set

    // firstSync: the caller has no local copy yet, the only case an unsigned response may be applied (ContentSignature)

    suspend fun fetchVersion(firstSync: Boolean): Result<Long> = contentRead("version") { ZemerContentClient.version(firstSync) }

    suspend fun fetchWhitelist(
        firstSync: Boolean,
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> },
    ): Result<List<ArtistWhitelistEntity>> =
        contentRead("whitelist") {
            val now = LocalDateTime.now()
            val docs = ZemerContentClient.whitelist(firstSync = firstSync)
            val total = docs.size
            Timber.d("WhitelistFetcher: mapping %d artists from content mirror", total)
            val entities = ArrayList<ArtistWhitelistEntity>(total)
            docs.forEachIndexed { index, doc ->
                // Drives the splash-screen sync progress.
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
                        // Coerce absent booleans to false ONLY here (HomeArtistProfile keeps them null).
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
        }

    /** Artists the server names as removed from the whitelist: the only list that may delete local data. */
    suspend fun fetchRemovedArtists(): Result<Set<String>> =
        contentRead("whitelistRemoved") { ZemerContentClient.removedArtists() }

    /**
     * The read-only `blockedContentIds` overrides - id-level content blocks applied everywhere in the app
     * (see [BlockedIdsCache]), as id -> reason ("female", "global"). The app only ever READS them.
     */
    suspend fun fetchBlockedIds(firstSync: Boolean): Result<Map<String, String>> =
        contentRead("blockedIds") { ZemerContentClient.blockedIds(firstSync) }

    // The podcast whitelist is the authoritative allow-set + version gate, exactly like the artist
    // whitelist (fetchVersion/fetchWhitelist). `zemer-search /podcasts` is NOT the gate; the gate lives here.

    suspend fun fetchPodcastVersion(firstSync: Boolean): Result<Long> =
        contentRead("podcastVersion") { ZemerContentClient.podcastsWhitelistVersion(firstSync) }

    suspend fun fetchPodcastWhitelist(
        firstSync: Boolean,
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> },
    ): Result<List<PodcastWhitelistEntity>> =
        contentRead("podcastWhitelist") {
            val now = LocalDateTime.now()
            val docs = ZemerContentClient.podcastsWhitelist(firstSync)
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
        }
}
