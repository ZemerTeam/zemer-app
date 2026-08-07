package com.jtech.zemer.utils

import com.jtech.zemer.db.entities.PodcastWhitelistEntity
import java.util.concurrent.ConcurrentHashMap

/**
 * The podcast whitelist allow-set, keyed by CHANNEL id (`UC…`). The whitelist is channel-level: an
 * approved channel vouches for its whole catalog, so a show/episode passes iff its host channel is a
 * member here. A SHOW id (`MPSP…`) is NOT a member key — resolve it to its host channel first (the
 * caller does this via the local `podcast` row) before checking membership.
 */
object PodcastWhitelistCache {
    private val memory = ConcurrentHashMap<String, PodcastWhitelistEntity>()

    fun updateAll(entries: List<PodcastWhitelistEntity>) {
        memory.clear()
        entries.forEach { memory[it.channelId] = it }
    }

    /** Whether [channelId] is a whitelisted host channel (`UC…`). Show ids never match — see class doc. */
    fun isChannelWhitelisted(channelId: String): Boolean = memory.containsKey(channelId)
}
