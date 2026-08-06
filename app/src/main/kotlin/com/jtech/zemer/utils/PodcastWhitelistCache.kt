package com.jtech.zemer.utils

import com.jtech.zemer.db.entities.PodcastWhitelistEntity
import java.util.concurrent.ConcurrentHashMap

/**
 * The podcast whitelist allow-set, keyed by CHANNEL id (`UC…`). The whitelist is channel-level: an
 * approved channel vouches for its whole catalog, so a show/episode passes iff its host channel is a
 * member here. [isAllowed] and [isAllowedByChannelId] are the same membership check (both kept because
 * callers pass either a channel id directly or an episode's author/channel id — the two names document
 * intent at the call site).
 */
object PodcastWhitelistCache {
    private val memory = ConcurrentHashMap<String, PodcastWhitelistEntity>()

    fun updateAll(entries: List<PodcastWhitelistEntity>) {
        memory.clear()
        entries.forEach { memory[it.channelId] = it }
    }

    /** Whether [channelId] is a whitelisted host channel. */
    fun isAllowed(channelId: String): Boolean = memory.containsKey(channelId)

    /** Synonym for [isAllowed] — the whitelist is channel-keyed, so channel membership IS the allow-set. */
    fun isAllowedByChannelId(channelId: String): Boolean = memory.containsKey(channelId)
}
