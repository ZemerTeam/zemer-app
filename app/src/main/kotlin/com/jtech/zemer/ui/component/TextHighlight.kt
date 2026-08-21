package com.jtech.zemer.ui.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString

/**
 * Every case-insensitive, non-overlapping occurrence of [query] (trimmed) in [text], as character
 * ranges. Pure so the matching rules (blank query, no match, repeats, case) are JVM-tested — the
 * composable below only paints these ranges.
 */
fun highlightMatchRanges(text: String, query: String): List<IntRange> {
    val needle = query.trim()
    if (needle.isEmpty()) return emptyList()
    val ranges = mutableListOf<IntRange>()
    var from = 0
    while (true) {
        val at = text.indexOf(needle, from, ignoreCase = true)
        if (at < 0) break
        ranges.add(at until at + needle.length)
        from = at + needle.length
    }
    return ranges
}

/**
 * [text] with the matches of [query] painted in the accent color — how the browse screens' instant
 * local filter shows WHY a row matched. A blank/null query (or no match) renders the plain text.
 */
@Composable
fun rememberHighlightedText(text: String, query: String?): AnnotatedString {
    val accent = MaterialTheme.colorScheme.primary
    return remember(text, query, accent) {
        val ranges = highlightMatchRanges(text, query.orEmpty())
        if (ranges.isEmpty()) {
            AnnotatedString(text)
        } else {
            buildAnnotatedString {
                append(text)
                ranges.forEach { range ->
                    addStyle(SpanStyle(color = accent), range.first, range.last + 1)
                }
            }
        }
    }
}
