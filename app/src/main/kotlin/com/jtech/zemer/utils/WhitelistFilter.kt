package com.jtech.zemer.utils

import com.jtech.zemer.db.MusicDatabase
import com.jtech.zemer.db.entities.ArtistWhitelistEntity
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.YTItem
import kotlinx.coroutines.flow.firstOrNull
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap

/**
 * Extension functions to filter YouTube API content by artist whitelist.
 *
 * These functions check if content should be displayed based on whether
 * the associated artists are in the artist_whitelist table.
 */

/**
 * Check if a song should be displayed based on whitelist.
 * Returns true if ANY of the song's artists are whitelisted.
 * If whitelist is empty, returns false (show nothing).
 */
private data class ArtistFilterDecision(
    val allowed: Boolean,
    val isChasidish: Boolean,
)

private object WhitelistEntryCache {
    private val memory = ConcurrentHashMap<String, ArtistWhitelistEntity>()

    fun get(id: String): ArtistWhitelistEntity? = memory[id]
    fun put(entry: ArtistWhitelistEntity) {
        memory[entry.artistId] = entry
    }
}

private suspend fun SongItem.isWhitelisted(
    database: MusicDatabase,
    allowedIds: Set<String>?,
    artistCache: MutableMap<String, ArtistWhitelistEntity?>,
    config: ContentFilterConfig,
    requireAllArtists: Boolean,
    fallbackArtistId: String?,
): ArtistFilterDecision {
    if (this.artists.isEmpty()) {
        // Some YTM artist sections (albums/singles) omit artist data; fall back to the page artist.
        if (fallbackArtistId != null) {
            return database.artistMatchesFilters(fallbackArtistId, allowedIds, artistCache, config)
        }
        return ArtistFilterDecision(allowed = false, isChasidish = false)
    }

    var anyAllowed = false
    var allAllowed = true
    var isChasidish = false
    for (artist in artists) {
        val artistId = artist.id ?: continue
        val decision = database.artistMatchesFilters(artistId, allowedIds, artistCache, config)
        if (decision.allowed) {
            anyAllowed = true
        } else {
            allAllowed = false
        }
        if (decision.isChasidish) {
            isChasidish = true
        }
    }
    val allowed = if (requireAllArtists) allAllowed && anyAllowed else anyAllowed
    return ArtistFilterDecision(allowed, isChasidish)
}

/**
 * Check if an album should be displayed based on whitelist.
 * Returns true if ANY of the album's artists are whitelisted.
 * If whitelist is empty, returns false (show nothing).
 */
private suspend fun AlbumItem.isWhitelisted(
    database: MusicDatabase,
    allowedIds: Set<String>?,
    artistCache: MutableMap<String, ArtistWhitelistEntity?>,
    config: ContentFilterConfig,
    requireAllArtists: Boolean,
    fallbackArtistId: String?,
): ArtistFilterDecision {
    val albumArtists = this.artists
    if (albumArtists.isNullOrEmpty()) {
        // Albums/singles/EPs sometimes omit artists in the response; trust the page artist when provided.
        if (fallbackArtistId != null) {
            return database.artistMatchesFilters(fallbackArtistId, allowedIds, artistCache, config)
        }
        return ArtistFilterDecision(allowed = false, isChasidish = false)
    }

    var anyAllowed = false
    var allAllowed = true
    var isChasidish = false
    for (artist in albumArtists) {
        val artistId = artist.id ?: continue
        val decision = database.artistMatchesFilters(artistId, allowedIds, artistCache, config)
        if (decision.allowed) {
            anyAllowed = true
        } else {
            allAllowed = false
        }
        if (decision.isChasidish) {
            isChasidish = true
        }
    }
    val allowed = if (requireAllArtists) allAllowed && anyAllowed else anyAllowed
    return ArtistFilterDecision(allowed, isChasidish)
}

/**
 * Check if an artist should be displayed based on whitelist.
 * Returns true if the artist is whitelisted.
 * If whitelist is empty, returns false (show nothing).
 */
private suspend fun ArtistItem.isWhitelisted(
    database: MusicDatabase,
    allowedIds: Set<String>?,
    artistCache: MutableMap<String, ArtistWhitelistEntity?>,
    config: ContentFilterConfig,
): ArtistFilterDecision {
    val decision = database.artistMatchesFilters(this.id, allowedIds, artistCache, config)
    return decision
}

/**
 * Check if a playlist should be displayed based on whitelist.
 * Returns true if the playlist author/curator is whitelisted.
 */
private suspend fun PlaylistItem.isWhitelisted(
    database: MusicDatabase,
    allowedIds: Set<String>?,
    artistCache: MutableMap<String, ArtistWhitelistEntity?>,
    config: ContentFilterConfig,
): ArtistFilterDecision {
    val authorId = this.author?.id ?: return ArtistFilterDecision(false, isChasidish = false)
    return database.artistMatchesFilters(authorId, allowedIds, artistCache, config)
}

/**
 * Filter a list of YTItems by whitelist.
 * Only items whose artists are whitelisted will be returned.
 * Set [requireAllArtists] to true to require every listed artist to be allowed.
 */
suspend fun List<YTItem>.filterWhitelisted(
    database: MusicDatabase,
    config: ContentFilterConfig = ContentFilterState.current,
    requireAllArtists: Boolean = false,
    fallbackArtistId: String? = null,
): List<YTItem> {
    // Chasidish preference is now handled separately since it's for recommendations only
    // For now, default to false - in a real implementation, you might want to get this from a separate source
    val promoteChasidish = false
    Timber.d("WhitelistFilter: Filtering ${this.size} items")
    var allowedEntries = WhitelistCache.allowedEntries(config)
    if (allowedEntries.isEmpty()) {
        runCatching { WhitelistCache.updateAll(database.getWhitelistEntriesSync()) }
        allowedEntries = WhitelistCache.allowedEntries(config)
    }
    val allowedIds: Set<String>? = allowedEntries.takeIf { it.isNotEmpty() }?.map { it.artistId }?.toSet()
    val artistCache = mutableMapOf<String, ArtistWhitelistEntity?>()
    val filtered = mutableListOf<Pair<YTItem, Boolean>>()

    this.forEach { item ->
        // Id-level override: a specific item can be hidden everywhere even when its artist is whitelisted
        // (e.g. one female-featuring song from an otherwise-allowed mixed channel). Gated on the current
        // content-filter config (a `female` override only hides for users filtering out female), surgical,
        // and applied before the artist check.
        if (BlockedIdsCache.isBlocked(item.id, config)) {
            Timber.d("WhitelistFilter: item '${item.title}' (${item.id}) hidden by id override")
            return@forEach
        }
        val decision = when (item) {
            is SongItem -> item.isWhitelisted(
                database,
                allowedIds,
                artistCache,
                config,
                requireAllArtists,
                fallbackArtistId
            ).also {
                Timber.d("WhitelistFilter: SongItem '${item.title}' by ${item.artists.joinToString { it -> it.name }} - allowed=${it.allowed}")
            }
            is AlbumItem -> item.isWhitelisted(
                database,
                allowedIds,
                artistCache,
                config,
                requireAllArtists,
                fallbackArtistId
            ).also {
                Timber.d("WhitelistFilter: AlbumItem '${item.title}' by ${item.artists?.joinToString { it -> it.name }} - allowed=${it.allowed}")
            }
            is ArtistItem -> item.isWhitelisted(database, allowedIds, artistCache, config).also {
                Timber.d("WhitelistFilter: ArtistItem '${item.title}' (${item.id}) - allowed=${it.allowed}")
            }
            is PlaylistItem -> item.isWhitelisted(database, allowedIds, artistCache, config).also {
                Timber.d("WhitelistFilter: PlaylistItem '${item.title}' - allowed=${it.allowed}")
            }
        }
        if (decision.allowed) {
            filtered.add(item to decision.isChasidish)
        }
    }

    val result = if (promoteChasidish) {
        filtered.sortedByDescending { it.second }.map { it.first }
    } else {
        filtered.map { it.first }
    }

    Timber.d("WhitelistFilter: Result: ${result.size} items passed filter (${this.size - result.size} filtered out)")
    return result
}

/**
 * Decide whether a song in the user's OWN synced playlist should be KEPT locally, keeping the app
 * 100% whitelisted: keep the song only if at least one of its artists — resolved from the playlist
 * renderer OR the local DB row — is whitelisted. Nothing unverified is ever kept (an empty allow-set,
 * or a song with no whitelisted artist in either source, is dropped).
 *
 * The local-DB fallback is the fix for issue #130: YTM playlist renderers routinely omit artist ids
 * or report a topic/"- Topic" channel that differs from the whitelisted artist channel, so a song the
 * user added (saved locally with correct artist metadata) looked non-whitelisted and was wiped on
 * sync even though it was by a whitelisted artist. Resolving the artist from the local row keeps that
 * genuinely-whitelisted song without ever admitting a non-whitelisted one.
 */
fun shouldKeepPlaylistSong(
    remoteArtistIds: Collection<String>,
    localArtistIds: Collection<String>,
    allowedArtistIds: Set<String>,
): Boolean {
    if (allowedArtistIds.isEmpty()) return false
    return remoteArtistIds.any { it in allowedArtistIds } || localArtistIds.any { it in allowedArtistIds }
}

/**
 * Whitelist filter for the user's OWN synced playlists, with local-DB artist enrichment (issue #130).
 * Keeps the app 100% whitelisted (see [shouldKeepPlaylistSong]) while no longer dropping user-added
 * songs whose playlist renderer carries sparse/mismatched artist ids.
 *
 * [allowedArtistIds] is passed in (not recomputed) so the caller can verify the whitelist is loaded
 * and skip the sync entirely when it is empty — filtering against an empty allow-set correctly keeps
 * nothing, which a caller must never turn into a playlist wipe.
 */
suspend fun List<SongItem>.filterWhitelistedWithLocalArtists(
    database: MusicDatabase,
    allowedArtistIds: Set<String>,
): List<SongItem> = filter { song ->
    val remoteIds = song.artists.mapNotNull { it.id }
    val localIds = database.song(song.id).firstOrNull()?.artists?.map { it.id } ?: emptyList()
    shouldKeepPlaylistSong(remoteIds, localIds, allowedArtistIds)
}

private suspend fun MusicDatabase.artistMatchesFilters(
    artistId: String,
    allowedIds: Set<String>?,
    artistCache: MutableMap<String, ArtistWhitelistEntity?>,
    config: ContentFilterConfig,
): ArtistFilterDecision {
    if (!config.filtersEnabled) {
        return ArtistFilterDecision(allowed = true, isChasidish = false)
    }

    allowedIds?.let { ids ->
        if (ids.isNotEmpty()) {
            val allowed = artistId in ids
            return ArtistFilterDecision(allowed, false)
        }
    }

    IsraeliArtistRegistry.ensureLoaded()
    if (IsraeliArtistRegistry.isIsraeli(artistId)) {
        return ArtistFilterDecision(allowed = false, isChasidish = false)
    }

    var entry = artistCache[artistId]
        ?: WhitelistEntryCache.get(artistId)
        ?: getWhitelistEntry(artistId)?.also { WhitelistEntryCache.put(it) }
    if (entry != null) {
        artistCache[artistId] = entry
    }

    val needsRemoteCheck =
        entry == null || (config.filtersEnabled && !config.allowFemaleSingers && entry?.isFemale == true)

    if (needsRemoteCheck) {
        // Fall back to DB once before giving up
        entry = getWhitelistEntry(artistId)
        if (entry != null) {
            artistCache[artistId] = entry
            WhitelistEntryCache.put(entry)
        }
    }

    entry ?: return ArtistFilterDecision(allowed = false, isChasidish = false)

    if (config.filtersEnabled) {
        if (!config.allowFemaleSingers && entry.isFemale) {
            return ArtistFilterDecision(false, entry.isChasid)
        }
    }

    return ArtistFilterDecision(true, entry.isChasid)
}
