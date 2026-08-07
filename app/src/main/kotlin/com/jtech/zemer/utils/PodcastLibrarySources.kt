package com.jtech.zemer.utils

import com.jtech.zemer.db.MusicDatabase
import com.jtech.zemer.db.entities.PodcastEntity
import com.jtech.zemer.search.ZemerSearchOptions
import com.jtech.zemer.search.ZemerSearchRepository
import com.metrolist.innertube.models.SongItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * The ONE place the podcast-library data sources (subscription scope + podcast whitelist filter) live,
 * so LibraryPodcastsViewModel and WhitelistedPodcastsViewModel can't drift. A fix to the scope or the
 * filter is made here once.
 */
object PodcastLibrarySources {
    /** How many latest episodes to pull from the (global) server feed before scoping to subscriptions. */
    private const val NEW_EPISODES_FETCH = 100

    /** Locally-subscribed podcasts, whitelist-filtered. A local read, so it works for anon sessions. */
    fun whitelistedSubscribedPodcasts(database: MusicDatabase): Flow<List<PodcastEntity>> =
        database.subscribedPodcasts()
            .map { list -> list.filter { subscribedPodcastAllowed(it.channelId) } }

    /**
     * The "New Episodes" feed. DISCOVERY is now the whitelist-pure Zemer server (`/podcasts/new-episodes`,
     * global newest-first), NOT the personal account's InnerTube VLRDPN feed - so it no longer needs the
     * account-leak gate and works for anonymous sessions too. The server list is scoped CLIENT-SIDE to the
     * user's locally-subscribed shows (the reply's recommended "global + local filter"), preserving the
     * "new episodes from shows you follow" semantic. Empty when nothing is subscribed; failures yield [].
     * Episodes play by videoId via the existing InnerTube pipeline (unchanged).
     */
    suspend fun whitelistedNewEpisodes(
        repository: ZemerSearchRepository,
        options: ZemerSearchOptions,
        database: MusicDatabase,
    ): List<SongItem> {
        val subscribedIds = database.subscribedPodcasts().first()
            .filter { subscribedPodcastAllowed(it.channelId) }
            .map { it.id }
            .toSet()
        if (subscribedIds.isEmpty()) return emptyList()
        return runCatching { repository.podcastsNewEpisodes(NEW_EPISODES_FETCH, options) }
            .getOrNull()
            .orEmpty()
            .filter { it.podcast?.id in subscribedIds }
            .map { it.asSongItem() }
    }

    /**
     * Whether a locally-subscribed show passes the CHANNEL-level whitelist. The whitelist is keyed by the
     * host channel (`UC…`), NOT the show id (`MPSP…`) — filtering a [PodcastEntity] by its own `id` against
     * this cache never matches, so it must key off [channelId]. A show with no known channelId (a
     * grandfathered channel-less show, or an optimistic subscribe not yet synced) is KEPT: it is the
     * user's own explicit subscription and there is no channel to check; once a sync fills channelId in,
     * membership is enforced. Pure + unit-tested ([PodcastLibrarySourcesTest]).
     */
    internal fun subscribedPodcastAllowed(channelId: String?): Boolean =
        channelId == null || PodcastWhitelistCache.isChannelWhitelisted(channelId)

    /**
     * Whether a podcast HOST CHANNEL (`UC…`) passes the channel-level whitelist. Unlike a subscribed
     * SHOW ([subscribedPodcastAllowed]), a channel is identified by a real channel id, so there is no
     * null-channel escape hatch: a channel that is not whitelisted is not shown. Fail-closed (an
     * un-synced/empty whitelist hides everything) — correct for a content gate that must never leak a
     * non-approved channel's identity into the Kosher library.
     */
    internal fun podcastChannelAllowed(channelId: String): Boolean =
        PodcastWhitelistCache.isChannelWhitelisted(channelId)
}
