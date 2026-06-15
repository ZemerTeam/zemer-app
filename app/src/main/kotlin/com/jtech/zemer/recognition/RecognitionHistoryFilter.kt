package com.jtech.zemer.recognition

/**
 * The whitelist gate for recognition *history*, kept pure (no DB, no Android) so it can be unit
 * tested and reused.
 *
 * Mirrors [RecognitionMatchSelector.isWhitelistedResult]: an entry is allowed iff at least one of
 * its resolved artists is currently whitelisted, and it **fails closed** — an entry with no stored
 * artist IDs (older rows, or a song whose artists had null IDs) is never shown or played. The
 * whitelist changes over time, so this is re-evaluated against the *current* set every time the
 * history list is built, which guarantees a de-whitelisted song can never be replayed from history.
 */
object RecognitionHistoryFilter {

    /** The delimiter used to join artist browse IDs in [RecognitionHistoryEntity.artistIds]. */
    const val ID_SEPARATOR = ","

    /** True iff any of the comma-joined [artistIds] is in [whitelistedArtistIds]. Fails closed. */
    fun isAllowed(artistIds: String, whitelistedArtistIds: Set<String>): Boolean =
        artistIds.split(ID_SEPARATOR).any { it.isNotBlank() && it in whitelistedArtistIds }

    /** Joins non-blank artist browse IDs into the stored [RecognitionHistoryEntity.artistIds] form. */
    fun joinIds(ids: List<String?>): String =
        ids.asSequence().filterNotNull().filter { it.isNotBlank() }.joinToString(ID_SEPARATOR)
}
