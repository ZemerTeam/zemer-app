package com.jtech.zemer.sync

/**
 * Pure decision logic for the bidirectional YouTube-Music podcast sync (subscriptions +
 * episodes-for-later). Extracted from [com.jtech.zemer.utils.SyncUtils] so the reconcile rules are
 * JVM-unit-testable without a database or a live InnerTube call - the sync bodies themselves need
 * both, but every branch they take is decided here.
 *
 * Server-first, exactly like the music library sync: YouTube Music is the source of truth, but a
 * podcast/episode the user removed LOCALLY (present in the DB but no longer bookmarked / saved) is
 * never silently re-added, because the matching remote removal is likely still in flight.
 */
object PodcastSyncLogic {

    /** What to do with a remote podcast when reconciling it against the local `podcast` table. */
    enum class UpsertAction {
        /** Not in the DB at all - insert it as a freshly bookmarked subscription. */
        INSERT,
        /** In the DB and still bookmarked - refresh its metadata, keep the bookmark. */
        UPDATE_METADATA,
        /** In the DB but the user un-bookmarked it locally - leave it alone (respect local removal). */
        SKIP_UNBOOKMARKED,
    }

    /**
     * @param existsLocally whether a `podcast` row already exists for this id.
     * @param bookmarkedLocally whether that row is currently bookmarked (`bookmarkedAt != null`).
     */
    fun upsertAction(existsLocally: Boolean, bookmarkedLocally: Boolean): UpsertAction = when {
        !existsLocally -> UpsertAction.INSERT
        bookmarkedLocally -> UpsertAction.UPDATE_METADATA
        else -> UpsertAction.SKIP_UNBOOKMARKED
    }

    /**
     * Ids present locally (subscribed / saved) that are NO LONGER on the remote - these get their
     * bookmark/library flag cleared so the app stays a mirror of YouTube Music. Mirrors the music
     * sync's "unlike locals absent from the filtered remote" cleanup.
     *
     * [remoteIds] MUST be the whitelist-filtered remote id set, same as music. A de-whitelisted
     * podcast that vanishes from the filtered remote is correctly dropped locally (kosher).
     */
    fun <T> localOnly(local: List<T>, remoteIds: Set<String>, id: (T) -> String): List<T> =
        local.filterNot { id(it) in remoteIds }

    /**
     * Whether an episode returned as a `SongItem(isEpisode = true)` passes the SEPARATE podcast
     * whitelist (never the music artist whitelist). Filters-off passes everything, exactly like the
     * music path. An episode's artist ids are either the show id (MPSP...) or the host channel id
     * (UC...), so both predicates are tried against every artist id.
     */
    inline fun episodePassesPodcastWhitelist(
        artistIds: List<String?>,
        filtersEnabled: Boolean,
        isAllowedPodcastId: (String) -> Boolean,
        isAllowedChannelId: (String) -> Boolean,
    ): Boolean {
        if (!filtersEnabled) return true
        return artistIds.any { raw ->
            val id = raw ?: return@any false
            isAllowedPodcastId(id) || isAllowedChannelId(id)
        }
    }
}
