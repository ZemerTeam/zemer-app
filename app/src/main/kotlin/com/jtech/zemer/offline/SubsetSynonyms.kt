package com.jtech.zemer.offline

/**
 * Synonym expansion — the offline port of `zemer-search/index/synonyms.mjs` (`compileSynonyms` +
 * `expandQuery`). For equivalences the consonant skeleton can't infer (abbreviations, acronyms,
 * nicknames — e.g. "MBD" <-> "Mordechai Ben David"): each group is a list of equivalent surface forms;
 * at compile time we precompute the union of plain + skeleton tokens across the group, and at query
 * time a query that hits ANY token of a group is expanded with ALL of the group's tokens.
 *
 * The server loads the groups from `zemer-search/data/synonyms.json`. That file is tiny and rarely
 * changes, so the on-device subset EMBEDS its current contents as [GROUPS] rather than shipping a data
 * file — keep it byte-equivalent to `data/synonyms.json` when that file changes.
 */

/** Result of [SubsetSynonyms.expand] — original tokens first, synonym tokens appended (JS Set order). */
data class ExpandedQuery(val plain: List<String>, val skel: List<String>)

object SubsetSynonyms {

    // Verbatim mirror of zemer-search/data/synonyms.json (as of the port). Keep in sync with that file.
    private val GROUPS: List<List<String>> = listOf(
        listOf("mbd", "mordechai ben david"),
        listOf("lipa", "lipa schmeltzer"),
        listOf("8th day", "eighth day"),
    )

    /** A compiled group: the de-duplicated union of plain + skeleton tokens across its surface forms. */
    private class Group(val plain: List<String>, val skel: List<String>)

    // compileSynonyms: keep groups with >=2 forms; union each form's plain + skeleton tokens (Set order).
    private val compiled: List<Group> = GROUPS
        .filter { it.size >= 2 }
        .map { forms ->
            val plain = LinkedHashSet<String>()
            val skel = LinkedHashSet<String>()
            for (f in forms) {
                SubsetNormalize.plainTokens(f).forEach { plain.add(it) }
                SubsetNormalize.skeletonTokens(f).forEach { skel.add(it) }
            }
            Group(plain.toList(), skel.toList())
        }

    /**
     * Expand the original query token sets with any synonym group the query overlaps (by plain OR
     * skeleton token). Original tokens are preserved in order; each matched group's tokens are appended
     * after — matching JS `Set` insertion order (`new Set(qPlain)` then group forEach).
     */
    fun expand(plain: List<String>, skel: List<String>): ExpandedQuery {
        val outPlain = LinkedHashSet(plain)
        val outSkel = LinkedHashSet(skel)
        for (g in compiled) {
            if (g.plain.any { outPlain.contains(it) } || g.skel.any { outSkel.contains(it) }) {
                g.plain.forEach { outPlain.add(it) }
                g.skel.forEach { outSkel.add(it) }
            }
        }
        return ExpandedQuery(outPlain.toList(), outSkel.toList())
    }
}
