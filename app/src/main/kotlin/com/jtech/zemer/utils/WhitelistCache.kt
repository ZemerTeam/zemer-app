package com.jtech.zemer.utils

import com.jtech.zemer.db.entities.ArtistWhitelistEntity

object WhitelistCache {
    // A @Volatile immutable map swapped whole, NOT a mutable map mutated in place: the old
    // clear-then-refill left a window where concurrent readers (notably the offline subset's
    // live-whitelist overlay) saw an empty or partial whitelist mid-refresh — an empty read
    // short-circuits the overlay and briefly serves de-whitelisted content.
    @Volatile
    private var memory: Map<String, ArtistWhitelistEntity> = emptyMap()

    fun updateAll(entries: List<ArtistWhitelistEntity>) {
        memory = entries.associateBy { it.artistId }
    }

    fun get(artistId: String): ArtistWhitelistEntity? = memory[artistId]

    /** An immutable, point-in-time view — safe to iterate while a refresh swaps the map. */
    fun snapshot(): Collection<ArtistWhitelistEntity> = memory.values

    suspend fun allowedEntries(database: com.jtech.zemer.db.MusicDatabase, config: ContentFilterConfig): List<ArtistWhitelistEntity> {
        var entries = allowedEntries(config)
        if (entries.isEmpty()) {
            runCatching { updateAll(database.getWhitelistEntriesSync()) }
            entries = allowedEntries(config)
        }
        return entries
    }

    fun allowedEntries(config: ContentFilterConfig): List<ArtistWhitelistEntity> =
        memory.values.filter { isAllowed(it, config) }

    fun isAllowed(entry: ArtistWhitelistEntity, config: ContentFilterConfig): Boolean {
        if (config.filtersEnabled) {
            if (!config.allowFemaleSingers && entry.isFemale) return false
        }
        return true
    }
}
