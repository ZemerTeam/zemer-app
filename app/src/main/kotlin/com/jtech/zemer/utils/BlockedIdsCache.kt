package com.jtech.zemer.utils

/**
 * Server-listed, fine-grained content overrides: specific item ids that are hidden EVERYWHERE in the app
 * **conditionally**, based on the user's content-filter settings. This is the surgical complement to the
 * artist whitelist — a *mixed* channel/artist stays whitelisted while specific items from it are hidden,
 * and crucially each override is gated on a **reason** so it only applies to the users it should:
 *
 *  - `female` → hidden only when the user filters out female artists (`!allowFemaleSingers`); shown
 *    otherwise. (For an item the server's female filter misses, e.g. a male-primary track featuring a
 *    woman — it leaks for female-filtering users but is perfectly fine for users who allow female.)
 *  - `global` → hidden for everyone (any filter configuration). Absent / unknown reasons default to
 *    `global` — the safe direction (over-block rather than leak).
 *
 * All overrides are inert when content filtering is off entirely (`!filtersEnabled`), matching every
 * other filter in the app.
 *
 * The id is matched against [com.metrolist.innertube.models.YTItem.id] — the videoId for songs/videos,
 * the playlistId for playlists, the browseId/channelId for albums/artists — so one table covers every
 * item type. Unlike the artist membership whitelist (never run over raw Zemer results, as it would clip
 * legitimate hits), an id+reason override is surgical and safe to apply on BOTH engines, every surface.
 *
 * Synced read-only from the Firestore `blockedContentIds` collection; the app NEVER writes/deletes it.
 * The snapshot is a single `@Volatile` map, replaced atomically — a concurrent reader always sees a
 * complete, consistent table, and a failed sync leaves the previous table intact (never unblocks).
 */
object BlockedIdsCache {

    const val REASON_FEMALE = "female"
    const val REASON_GLOBAL = "global"

    /** id -> reason (lower-cased). */
    @Volatile
    private var blocked: Map<String, String> = emptyMap()

    /** Replace the table with the latest server snapshot. Only call on a successful fetch/load. */
    fun updateAll(entries: Map<String, String>) {
        blocked = entries.entries
            .mapNotNull { (id, reason) ->
                id.trim().takeIf(String::isNotEmpty)?.let { it to reason.trim().lowercase() }
            }
            .toMap()
    }

    /** True if [id] should be hidden under the current content-filter [config]. */
    fun isBlocked(id: String?, config: ContentFilterConfig): Boolean {
        if (id.isNullOrEmpty()) return false
        if (!config.filtersEnabled) return false
        return when (blocked[id]) {
            null -> false
            REASON_FEMALE -> !config.allowFemaleSingers
            else -> true // REASON_GLOBAL (and any unknown reason) — hidden for everyone, gated above
        }
    }

    // --- DataStore (de)serialization: one "id\treason" per line. Pure so both the sync writer and the
    // startup loader share exactly one format, and it is unit-testable. ---

    fun serialize(entries: Map<String, String>): String =
        entries.entries.joinToString("\n") { "${it.key}\t${it.value}" }

    fun parse(text: String): Map<String, String> =
        text.split('\n').mapNotNull { line ->
            if (line.isBlank()) return@mapNotNull null
            val parts = line.split('\t', limit = 2)
            val id = parts[0].trim()
            if (id.isEmpty()) return@mapNotNull null
            id to (parts.getOrNull(1)?.trim()?.lowercase()?.takeIf(String::isNotEmpty) ?: REASON_GLOBAL)
        }.toMap()
}
