package com.jtech.zemer.utils

import com.jtech.zemer.db.MusicDatabase
import com.jtech.zemer.db.entities.PodcastWhitelistEntity
import java.util.concurrent.ConcurrentHashMap

object PodcastWhitelistCache {
    private val memory = ConcurrentHashMap<String, PodcastWhitelistEntity>()

    fun updateAll(entries: List<PodcastWhitelistEntity>) {
        memory.clear()
        entries.forEach { memory[it.podcastId] = it }
    }

    fun upsert(entry: PodcastWhitelistEntity) {
        memory[entry.podcastId] = entry
    }

    fun get(podcastId: String): PodcastWhitelistEntity? = memory[podcastId]

    fun snapshot(): Collection<PodcastWhitelistEntity> = memory.values

    fun isAllowed(podcastId: String): Boolean = memory.containsKey(podcastId)

    /**
     * Check if a channel ID belongs to a whitelisted podcast.
     * Episodes return channel ID (artist ID), not podcast ID.
     */
    fun isAllowedByChannelId(channelId: String): Boolean =
        memory.values.any { it.channelId == channelId }

    /**
     * Get podcast by channel ID.
     */
    fun getByChannelId(channelId: String): PodcastWhitelistEntity? =
        memory.values.find { it.channelId == channelId }

    fun isEmpty(): Boolean = memory.isEmpty()

    suspend fun loadFromDatabase(database: MusicDatabase) {
        if (memory.isEmpty()) {
            runCatching {
                updateAll(database.getPodcastWhitelistEntriesSync())
            }
        }
    }

    fun allEntries(): List<PodcastWhitelistEntity> = memory.values.toList()

    fun clear() {
        memory.clear()
    }
}
