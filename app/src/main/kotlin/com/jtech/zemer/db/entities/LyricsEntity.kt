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
         * [cached]. A legacy PLAIN body is always kept (stamped [PROVIDER_LEGACY]): a pre-provider manual
         * entry looks exactly like an old auto-cached plain row, and user-typed text must never be silently
         * replaced (Refetch is the explicit way out). A legacy SYNCED body is replaced when the chain answers:
         * nobody types timestamps, so it is an old ungated LrcLib match, the class of row that motivated the
         * gated chain. Not found keeps any legacy body.
         */
        fun resolved(id: String, cached: LyricsEntity?, lyrics: String, provider: String?): LyricsEntity {
            val legacyBody = cached != null && cached.provider == null && cached.lyrics != LYRICS_NOT_FOUND
            if (legacyBody && (lyrics == LYRICS_NOT_FOUND || !isSyncedBody(cached!!.lyrics))) return cached.copy(provider = PROVIDER_LEGACY)
            return LyricsEntity(id = id, lyrics = lyrics, provider = provider)
        }

        /** An LRC-style body: at least one `[mm:ss.xx]` line tag (no user types these). */
        private fun isSyncedBody(lyrics: String): Boolean = LINE_TAG.containsMatchIn(lyrics)

        private val LINE_TAG = Regex("\\[\\d\\d:\\d\\d\\.\\d{2,3}]")
    }
}
