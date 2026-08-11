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
     * For SUBSCRIPTIONS [remoteIds] is the whitelist-filtered remote set (a de-whitelisted podcast
     * drops locally, kosher). For EPISODES it is the RAW set from [episodeSyncPlan]: whitelist
     * resolution gates import, never deletion.
     */
    fun <T> localOnly(local: List<T>, remoteIds: Set<String>, id: (T) -> String): List<T> =
        local.filterNot { id(it) in remoteIds }

    /** What syncEpisodesForLater imports vs. what its cleanup may treat as "still saved remotely". */
    data class EpisodeSyncPlan<T>(val imported: List<T>, val cleanupReference: Set<String>)

    /**
     * The whitelist lookup gates IMPORT only ([imported] = episodes whose show positively resolved
     * as whitelisted). [cleanupReference] is ALL raw remote ids: an episode whose show id is
     * unresolvable (no MPSP id) or 404s under the current flags is still in VLSE remotely, and
     * deleting its local save would wipe a genuine user save (the flag-hidden-album precedent).
     */
    fun <T> episodeSyncPlan(
        rawRemote: List<T>,
        id: (T) -> String,
        showIdOf: (T) -> String?,
        showAllowed: Map<String, Boolean>,
    ): EpisodeSyncPlan<T> = EpisodeSyncPlan(
        imported = rawRemote.filter { showIdOf(it)?.let(showAllowed::get) == true },
        cleanupReference = rawRemote.mapTo(mutableSetOf(), id),
    )

    /** Un-save server action when no setVideoId is stored locally and a live lookup was attempted. */
    enum class UnsaveAction { REMOVE, NOTHING_TO_REMOVE, FAIL }

    /**
     * A FAILED lookup must surface as failure (revert + toast) - treating it as success left the
     * remote entry alive and the next sync silently re-saved the episode locally.
     */
    fun unsaveAction(lookupSucceeded: Boolean, setVideoId: String?): UnsaveAction = when {
        setVideoId != null -> UnsaveAction.REMOVE
        lookupSucceeded -> UnsaveAction.NOTHING_TO_REMOVE
        else -> UnsaveAction.FAIL
    }

    /**
     * Whether a podcast/episode passes the SEPARATE podcast whitelist (never the music artist
     * whitelist). Filters-off passes everything, exactly like the music path. The whitelist is
     * CHANNEL-keyed (`UC…`), so [channelIds] must already be the item's EFFECTIVE host-channel ids: a
     * raw channel id is itself, and a SHOW id (`MPSP…`) must be resolved to its host channel by the
     * caller (via the local `podcast` row) BEFORE it lands here — an unresolved show id will never
     * match and is correctly dropped with filters on (kosher-safe).
     */
    inline fun episodePassesPodcastWhitelist(
        channelIds: List<String?>,
        filtersEnabled: Boolean,
        isWhitelistedChannel: (String) -> Boolean,
    ): Boolean {
        if (!filtersEnabled) return true
        return channelIds.any { id -> id != null && isWhitelistedChannel(id) }
    }
}
