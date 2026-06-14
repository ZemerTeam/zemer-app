package com.jtech.zemer.recognition

import java.text.Normalizer

/**
 * Pure (no Android, no network) matcher that decides which whitelist-filtered candidate, if any,
 * corresponds to a recognized `(title, artist)` pair.
 *
 * This is the *accuracy* gate only. The *safety* gate — that a candidate is whitelisted at all — has
 * already happened upstream (the candidate list is the output of `filterWhitelisted`), so even a
 * wrong pick here is guaranteed to be a kosher song. The matcher's job is just to avoid surfacing an
 * unrelated whitelisted song that merely shares a word with the recognized track: it requires BOTH a
 * meaningful title overlap AND a meaningful artist overlap before it will return a match.
 */
object RecognitionMatcher {

    /** A search candidate reduced to the only fields the matcher needs. */
    data class Candidate(val title: String, val artistNames: List<String>)

    // A recognized title must share at least half its words with a candidate's title…
    private const val DEFAULT_TITLE_THRESHOLD = 0.5

    // …and at least half the recognized artist's words must appear in a candidate artist.
    private const val DEFAULT_ARTIST_THRESHOLD = 0.5

    private val FEATURE_STOPWORDS = setOf("feat", "ft", "featuring")

    /**
     * Returns the index of the best-matching candidate, or `null` if none clears both thresholds.
     *
     * Two-stage: a candidate must first clear the thresholds on token *recall* (what fraction of the
     * recognized title/artist words appear in the candidate — tolerant of a candidate carrying extra
     * words like "2.0" or "(Remix)"). Among those that clear, ranking uses Jaccard *similarity*
     * (title weighted 0.6, artist 0.4) so a tight/exact match beats a looser superset. Ties resolve
     * to the earliest candidate (search-relevance order).
     */
    fun bestMatchIndex(
        recognizedTitle: String,
        recognizedArtist: String,
        candidates: List<Candidate>,
        titleThreshold: Double = DEFAULT_TITLE_THRESHOLD,
        artistThreshold: Double = DEFAULT_ARTIST_THRESHOLD,
    ): Int? {
        val recTitleTokens = tokenize(recognizedTitle)
        val recArtistTokens = tokenize(recognizedArtist)
        if (recTitleTokens.isEmpty() || recArtistTokens.isEmpty()) return null

        var bestIndex: Int? = null
        var bestScore = -1.0

        candidates.forEachIndexed { index, candidate ->
            val candTitleTokens = tokenize(candidate.title)
            val candArtistTokenSets = candidate.artistNames.map { tokenize(it) }

            val titleRecall = recall(recTitleTokens, candTitleTokens)
            val artistRecall = candArtistTokenSets.maxOfOrNull { recall(recArtistTokens, it) } ?: 0.0
            if (titleRecall < titleThreshold || artistRecall < artistThreshold) return@forEachIndexed

            val titleSim = jaccard(recTitleTokens, candTitleTokens)
            val artistSim = candArtistTokenSets.maxOfOrNull { jaccard(recArtistTokens, it) } ?: 0.0
            val combined = 0.6 * titleSim + 0.4 * artistSim
            if (combined > bestScore) {
                bestScore = combined
                bestIndex = index
            }
        }

        return bestIndex
    }

    /**
     * Fraction of [needle] tokens that also appear in [haystack] (set membership), in `[0, 1]`.
     * Returns 0 when [needle] is empty. Used for the accept/reject threshold.
     */
    private fun recall(needle: Set<String>, haystack: Set<String>): Double {
        if (needle.isEmpty()) return 0.0
        val shared = needle.count { it in haystack }
        return shared.toDouble() / needle.size
    }

    /** Jaccard similarity of two token sets, in `[0, 1]`. Used to rank accepted candidates. */
    private fun jaccard(a: Set<String>, b: Set<String>): Double {
        if (a.isEmpty() && b.isEmpty()) return 0.0
        val shared = a.count { it in b }
        val union = a.size + b.size - shared
        return if (union == 0) 0.0 else shared.toDouble() / union
    }

    /**
     * Normalizes and tokenizes a string: strips diacritics and bracketed segments, lowercases,
     * removes punctuation, drops "feat"/"ft"/"featuring" noise, and splits on whitespace.
     */
    internal fun tokenize(input: String): Set<String> {
        val withoutBrackets = input.replace(BRACKETED, " ")
        val deAccented = Normalizer.normalize(withoutBrackets, Normalizer.Form.NFD)
            .replace(COMBINING_MARKS, "")
        val cleaned = deAccented.lowercase().replace(NON_ALPHANUMERIC, " ")
        return cleaned.split(' ')
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && it !in FEATURE_STOPWORDS }
            .toSet()
    }

    private val BRACKETED = Regex("[\\(\\[\\{].*?[\\)\\]\\}]")
    private val COMBINING_MARKS = Regex("\\p{Mn}+")
    private val NON_ALPHANUMERIC = Regex("[^a-z0-9]+")
}
