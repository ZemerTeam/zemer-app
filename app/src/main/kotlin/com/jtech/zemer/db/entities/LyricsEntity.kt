package com.jtech.zemer.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "lyrics")
data class LyricsEntity(
    @PrimaryKey val id: String,
    val lyrics: String,
    /** Which provider answered (e.g. "Zemer · jkaraoke", "SimpMusic", "manual"); null for rows cached before this column. */
    val provider: String? = null,
) {
    companion object {
        const val LYRICS_NOT_FOUND = "LYRICS_NOT_FOUND"

        /**
         * Provider stamp for a row cached before provider tracking whose song the gated chain could not
         * resolve. The body is kept (it may be a manual entry, and manual entries are typically made for
         * songs no provider covers) and shown with unknown provenance; it is not re-fetched again.
         */
        const val PROVIDER_LEGACY = "legacy"

        /**
         * Whether the provider chain should run for [cached]: nothing cached, or a legacy row (provider
         * null) holding a real body that is re-resolved once so its provenance becomes known. A not-found
         * row is a negative cache and is NOT re-fetched: re-running every provider over the network on
         * each play of a song without lyrics is exactly what the cache exists to prevent.
         */
        fun needsFetch(cached: LyricsEntity?): Boolean =
            cached == null || (cached.provider == null && cached.lyrics != LYRICS_NOT_FOUND)

        /**
         * The row to store after the chain answered [lyrics]/[provider] for a song whose cache held
         * [cached]. A found body replaces the row with known provenance. Not found keeps a legacy body
         * (stamped [PROVIDER_LEGACY]) instead of deleting or overwriting user-entered text.
         */
        fun resolved(id: String, cached: LyricsEntity?, lyrics: String, provider: String?): LyricsEntity =
            if (lyrics == LYRICS_NOT_FOUND && cached != null && cached.lyrics != LYRICS_NOT_FOUND) cached.copy(provider = PROVIDER_LEGACY)
            else LyricsEntity(id = id, lyrics = lyrics, provider = provider)
    }
}
