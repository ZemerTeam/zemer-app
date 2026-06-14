package com.jtech.zemer.recognition

import com.metrolist.innertube.models.SongItem

/**
 * Selects the whitelist-filtered [SongItem] that best matches a recognized `(title, artist)` pair.
 *
 * Invariant (the whole point of the recognition feature): the returned value is always an element of
 * [whitelistedCandidates] or `null`. Recognized Shazam metadata is used only to *rank* the
 * already-filtered candidates — it is never itself returned — so a recognized-but-not-whitelisted
 * song can never surface. This is verified by `RecognitionMatchSelectorTest`.
 */
object RecognitionMatchSelector {

    fun select(
        recognizedTitle: String,
        recognizedArtist: String,
        whitelistedCandidates: List<SongItem>,
    ): SongItem? {
        if (whitelistedCandidates.isEmpty()) return null
        val index = RecognitionMatcher.bestMatchIndex(
            recognizedTitle = recognizedTitle,
            recognizedArtist = recognizedArtist,
            candidates = whitelistedCandidates.map { song ->
                RecognitionMatcher.Candidate(
                    title = song.title,
                    artistNames = song.artists.map { it.name },
                )
            },
        ) ?: return null
        return whitelistedCandidates[index]
    }

    /**
     * Hard, config-independent whitelist gate for a chosen [song]: true iff at least one of its
     * artists is confirmed whitelisted by [isWhitelisted] (a direct `artist_whitelist` table check
     * in production). Deliberately **fails closed** — a song with no artists, or whose artists all
     * have null ids, returns false. This is the final guarantee that recognition cannot surface a
     * song outside the whitelist, even if every upstream filter were disabled or bypassed.
     */
    suspend fun isWhitelistedResult(
        song: SongItem,
        isWhitelisted: suspend (artistId: String) -> Boolean,
    ): Boolean {
        for (artist in song.artists) {
            val id = artist.id ?: continue
            if (isWhitelisted(id)) return true
        }
        return false
    }
}
