package com.jtech.zemer.ui.component

/**
 * Pure logic for the Artists-tab fast scroller ([LetterFastScrollbar]): mapping a display name to
 * the index letter shown in the drag preview bubble. Kept free of Compose so the rules are
 * unit-testable on the JVM (see AlphabetIndexTest).
 */

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
