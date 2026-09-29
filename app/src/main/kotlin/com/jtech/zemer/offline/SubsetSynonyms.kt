package com.jtech.zemer.offline

/**
 * Synonym expansion — the offline port of `zemer-search/index/synonyms.mjs` (`compileSynonyms` +
 * `expandQuery`). For equivalences the consonant skeleton can't infer (abbreviations, acronyms,
 * nicknames — e.g. "MBD" <-> "Mordechai Ben David"): each group is a list of equivalent surface forms;
 * at compile time we precompute the union of plain + skeleton tokens across the group, and at query
 * time a query that hits ANY token of a group is expanded with ALL of the group's tokens.
 *
 * The server loads the groups from `zemer-search/data/synonyms.json`. A synced snapshot now ships that
 * file verbatim as the `synonyms` shard (2026-09-11, additive); [SubsetDecoder.decodeSynonymGroups] reads
 * it and [SubsetCategories] compiles it once per corpus ([compile]), so offline matching tracks the
 * server file on the next sync instead of a hand-copied table. [DEFAULT] (built from [DEFAULT_GROUPS])
 * is the fallback for a snapshot that predates the shard, and stays the default for every direct
 * [expand] call — callers that don't have a corpus handy (or its tests) see unchanged behaviour.
 */

/** Result of [Compiled.expand] — original tokens first, synonym tokens appended (JS Set order). */
data class ExpandedQuery(val plain: List<String>, val skel: List<String>)

object SubsetSynonyms {

    /** Verbatim mirror of zemer-search/data/synonyms.json as of the port — the pre-shard fallback table. */
    val DEFAULT_GROUPS: List<List<String>> = listOf(
        listOf("mbd", "mordechai ben david"),
        listOf("lipa", "lipa schmeltzer"),
        listOf("8th day", "eighth day"),
    )

    /** A compiled group: the de-duplicated union of plain + skeleton tokens across its surface forms. */
    internal class Group(val plain: List<String>, val skel: List<String>)

    /** A compiled synonym table, ready to expand queries against — build once per group source with [compile]. */
    class Compiled internal constructor(private val groups: List<Group>) {
        /**
         * Expand the original query token sets with any synonym group the query overlaps (by plain OR
         * skeleton token). Original tokens are preserved in order; each matched group's tokens are
         * appended after — matching JS `Set` insertion order (`new Set(qPlain)` then group forEach).
         */
        fun expand(plain: List<String>, skel: List<String>): ExpandedQuery {
            val outPlain = LinkedHashSet(plain)
            val outSkel = LinkedHashSet(skel)
            for (g in groups) {
                if (g.plain.any { outPlain.contains(it) } || g.skel.any { outSkel.contains(it) }) {
                    g.plain.forEach { outPlain.add(it) }
                    g.skel.forEach { outSkel.add(it) }
                }
            }
            return ExpandedQuery(outPlain.toList(), outSkel.toList())
        }
    }

    // compileSynonyms: keep groups with >=2 forms; union each form's plain + skeleton tokens (Set order).
    fun compile(groups: List<List<String>>): Compiled = Compiled(
        groups.filter { it.size >= 2 }.map { forms ->
            val plain = LinkedHashSet<String>()
            val skel = LinkedHashSet<String>()
            for (f in forms) {
                SubsetNormalize.plainTokens(f).forEach { plain.add(it) }
                SubsetNormalize.skeletonTokens(f).forEach { skel.add(it) }
            }
            Group(plain.toList(), skel.toList())
        },
    )

    /** The built-in table, compiled once — the default for [expand] and the fallback when a snapshot's
     * `synonymGroups` is empty (no shard, or an older sync). */
    val DEFAULT: Compiled by lazy { compile(DEFAULT_GROUPS) }

    /** Expand [plain]/[skel] against [compiled] (default: the built-in table). The search path
     * ([searchIndex]) calls [Compiled.expand] on the corpus's own compiled shard groups directly. */
    fun expand(plain: List<String>, skel: List<String>, compiled: Compiled = DEFAULT): ExpandedQuery =
        compiled.expand(plain, skel)
}
