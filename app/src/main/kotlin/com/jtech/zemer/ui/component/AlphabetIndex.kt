package com.jtech.zemer.ui.component

import kotlin.math.roundToInt

/**
 * Pure logic for the letter fast-scroll index ([AlphabetScrollbar]): bucketing display names into
 * index letters and thinning the strip to the rows that fit on screen. Kept free of Compose so the
 * rules are unit-testable on the JVM (see AlphabetIndexTest).
 */

/** One letter of the fast-scroll index: [letter] plus the position (in the displayed, already
 * sorted list) of the first item belonging to that bucket. */
data class AlphabetIndexEntry(
    val letter: Char,
    val itemIndex: Int,
)

/** Names that start with a digit, or contain no letter/digit at all, share one catch-all bucket. */
const val ALPHABET_OTHER_BUCKET = '#'

/**
 * The index letter for a display name: its first letter or digit, with Latin letters uppercased
 * (the artist sort is COLLATE NOCASE), Hebrew and other scripts kept as-is, and digits/symbol-only
 * names grouped under [ALPHABET_OTHER_BUCKET]. Leading punctuation ("The-", quotes, parentheses)
 * is skipped so decorated names land in their real letter.
 */
fun alphabetBucketOf(name: String): Char {
    val first = name.firstOrNull { it.isLetterOrDigit() } ?: return ALPHABET_OTHER_BUCKET
    return if (first.isDigit()) ALPHABET_OTHER_BUCKET else first.uppercaseChar()
}

/**
 * Builds the letter index over [names] in their existing (sorted) order: one entry per distinct
 * bucket, pointing at the first item of that bucket. The strip deliberately shows only the letters
 * actually present — with a mixed Hebrew/English catalog a fixed alphabet would be mostly dead rows.
 */
fun alphabetIndexOf(names: List<String>): List<AlphabetIndexEntry> {
    val seen = HashSet<Char>()
    val entries = mutableListOf<AlphabetIndexEntry>()
    names.forEachIndexed { index, name ->
        val letter = alphabetBucketOf(name)
        if (seen.add(letter)) entries += AlphabetIndexEntry(letter, index)
    }
    return entries
}

/**
 * Evenly thins [entries] to at most [maxRows] letters (first and last kept) so the strip fits the
 * available height — a Hebrew+Latin catalog can carry ~50 buckets, more than a phone screen of
 * 14dp rows. Selection still maps drag position through the FULL index (see [AlphabetScrollbar]),
 * so hidden letters remain reachable between the shown ones.
 */
fun thinAlphabetIndex(entries: List<AlphabetIndexEntry>, maxRows: Int): List<AlphabetIndexEntry> {
    if (maxRows <= 0) return emptyList()
    if (entries.size <= maxRows) return entries
    if (maxRows == 1) return listOf(entries.first())
    val step = (entries.size - 1).toFloat() / (maxRows - 1)
    return (0 until maxRows)
        .map { row -> entries[(row * step).roundToInt().coerceAtMost(entries.size - 1)] }
        .distinct()
}
