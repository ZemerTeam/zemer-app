package com.jtech.zemer.utils

import com.jtech.zemer.db.entities.ArtistWhitelistEntity
import java.util.concurrent.ConcurrentHashMap

object WhitelistCache {
    private val memory = ConcurrentHashMap<String, ArtistWhitelistEntity>()

    fun updateAll(entries: List<ArtistWhitelistEntity>) {
        memory.clear()
        entries.forEach { memory[it.artistId] = it }
    }

    fun upsert(entry: ArtistWhitelistEntity) {
        memory[entry.artistId] = entry
    }

    fun get(artistId: String): ArtistWhitelistEntity? = memory[artistId]

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
            if (config.hideOldStuff && !entry.isGenZ) return false
        }
        return true
    }
}
