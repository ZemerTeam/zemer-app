package com.jtech.zemer.utils

import com.jtech.zemer.db.entities.PodcastWhitelistEntity

/**
 * The podcast whitelist allow-set, keyed by CHANNEL id (`UC…`). The whitelist is channel-level: an
 * approved channel vouches for its whole catalog, so a show/episode passes iff its host channel is a
 * member here. A SHOW id (`MPSP…`) is NOT a member key — resolve it to its host channel first (the
 * caller does this via the local `podcast` row) before checking membership.
 */
object PodcastWhitelistCache {
    // A @Volatile reference to an IMMUTABLE snapshot, swapped whole on each sync. Readers (channelPasses,
    // run inside filterWhitelisted on every browse/playback pass, plus the library display gates) always
    // see a complete map — the old clear()+repopulate exposed a transient empty window that momentarily
    // dropped a genuinely-whitelisted podcast mid-sync.
    @Volatile
    private var memory: Map<String, PodcastWhitelistEntity> = emptyMap()

    fun updateAll(entries: List<PodcastWhitelistEntity>) {
        memory = entries.associateBy { it.channelId }
    }

    /** Whether [channelId] is a whitelisted host channel (`UC…`). Show ids never match — see class doc. */
    fun isChannelWhitelisted(channelId: String): Boolean = memory.containsKey(channelId)

    /** The current allow-set (immutable snapshot's keys) — the offline layer's live overlay input. */
    fun channelIds(): Set<String> = memory.keys

    /**
     * Whether [channelId] is whitelisted AND passes the female gate — a wholly-female channel
     * (`isFemale`) is hidden when [allowFemale] is false, matching the server, the offline layer, and the
     * artist browse. (`kidZone` is always off for podcast surfaces, so it's not a factor here.) Use this
     * for DISPLAY/filtering; [isChannelWhitelisted] stays pure membership for routing / whitelist loading.
     */
    fun channelPasses(channelId: String, allowFemale: Boolean): Boolean {
        val entry = memory[channelId] ?: return false
        return allowFemale || !entry.isFemale
    }

    /** Whether [channelId] is a WHOLLY-kid channel — navigateToArtist auto-scopes those to KidZone. */
    fun isKidChannel(channelId: String): Boolean = memory[channelId]?.isKidZone == true
}
