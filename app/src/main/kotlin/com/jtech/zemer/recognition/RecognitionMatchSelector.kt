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
}
