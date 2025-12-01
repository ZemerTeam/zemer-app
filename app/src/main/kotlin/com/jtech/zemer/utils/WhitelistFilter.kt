package com.jtech.zemer.utils

import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.YTItem
import com.jtech.zemer.db.MusicDatabase
import com.jtech.zemer.db.entities.ArtistWhitelistEntity
import com.jtech.zemer.utils.ContentFilterConfig
import com.jtech.zemer.utils.ContentFilterState
import java.util.concurrent.ConcurrentHashMap
import timber.log.Timber

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
): ArtistFilterDecision {
    if (this.artists.isEmpty()) return ArtistFilterDecision(false, false)

    var anyAllowed = false
    var isChasidish = false
    for (artist in artists) {
        val artistId = artist.id ?: continue
        val decision = database.artistMatchesFilters(artistId, allowedIds, artistCache, config)
        if (decision.allowed) {
            anyAllowed = true
        }
        if (decision.isChasidish) {
            isChasidish = true
        }
    }
    return ArtistFilterDecision(anyAllowed, isChasidish)
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
): ArtistFilterDecision {
    val albumArtists = this.artists ?: return ArtistFilterDecision(false, false)
    if (albumArtists.isEmpty()) return ArtistFilterDecision(false, false)

    var anyAllowed = false
    var isChasidish = false
    for (artist in albumArtists) {
        val artistId = artist.id ?: continue
        val decision = database.artistMatchesFilters(artistId, allowedIds, artistCache, config)
        if (decision.allowed) {
            anyAllowed = true
        }
        if (decision.isChasidish) {
            isChasidish = true
        }
    }
    return ArtistFilterDecision(anyAllowed, isChasidish)
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
    val authorId = this.author?.id ?: return ArtistFilterDecision(false, false)
    return database.artistMatchesFilters(authorId, allowedIds, artistCache, config)
}

/**
 * Filter a list of YTItems by whitelist.
 * Only items whose artists are whitelisted will be returned.
 */
suspend fun List<YTItem>.filterWhitelisted(
    database: MusicDatabase,
    config: ContentFilterConfig = ContentFilterState.current,
): List<YTItem> {
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
        val decision = when (item) {
            is SongItem -> item.isWhitelisted(database, allowedIds, artistCache, config).also {
                Timber.d("WhitelistFilter: SongItem '${item.title}' by ${item.artists.joinToString { it.name }} - allowed=${it.allowed}")
            }
            is AlbumItem -> item.isWhitelisted(database, allowedIds, artistCache, config).also {
                Timber.d("WhitelistFilter: AlbumItem '${item.title}' by ${item.artists?.joinToString { it.name }} - allowed=${it.allowed}")
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

    val result = if (config.promoteChasidish) {
        filtered.sortedByDescending { it.second }.map { it.first }
    } else {
        filtered.map { it.first }
    }

    Timber.d("WhitelistFilter: Result: ${result.size} items passed filter (${this.size - result.size} filtered out)")
    return result
}

private suspend fun MusicDatabase.artistMatchesFilters(
    artistId: String,
    allowedIds: Set<String>?,
    artistCache: MutableMap<String, ArtistWhitelistEntity?>,
    config: ContentFilterConfig,
): ArtistFilterDecision {
    allowedIds?.let { ids ->
        if (ids.isNotEmpty()) {
            val allowed = artistId in ids
            return ArtistFilterDecision(allowed, false)
        }
    }

    var entry = artistCache[artistId]
        ?: WhitelistEntryCache.get(artistId)
        ?: getWhitelistEntry(artistId)?.also { WhitelistEntryCache.put(it) }
    if (entry != null) {
        artistCache[artistId] = entry
    }

    val needsRemoteCheck =
        entry == null ||
            (config.filtersEnabled && !config.allowFemaleSingers && entry?.isFemale != true) ||
            (config.filtersEnabled && config.hideOldStuff && entry?.isGenZ != true) ||
            (config.promoteChasidish && entry?.isChasid != true)

    if (needsRemoteCheck) {
        // Fall back to DB once before giving up
        entry = getWhitelistEntry(artistId)
        if (entry != null) {
            artistCache[artistId] = entry
            WhitelistEntryCache.put(entry)
        }
    }

    entry ?: return ArtistFilterDecision(false, false)

    if (config.filtersEnabled) {
        if (!config.allowFemaleSingers && entry.isFemale) {
            return ArtistFilterDecision(false, entry.isChasid)
        }
        if (config.hideOldStuff && !entry.isGenZ) {
            return ArtistFilterDecision(false, entry.isChasid)
        }
    }

    return ArtistFilterDecision(true, entry.isChasid)
}
