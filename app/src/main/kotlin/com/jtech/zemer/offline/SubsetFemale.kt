package com.jtech.zemer.offline

/**
 * Female-involvement detection — the offline port of `zemer-search/index/credits.mjs` plus the
 * author-name half of `zemer-search/index/female-owned.mjs`. Deterministic, pure string ops.
 *
 * Two jobs, both reusing the same cross-script matcher built from the whitelist's female artists:
 *  - [isFemaleInvolved] — a male-primary track still counts as female-involved when the credit is in
 *    the TITLE (e.g. `"Shiru (feat. Franciska)"`) or in a secondary artist credit. Every credited name
 *    is validated against the female whitelist, so an unknown name can never over-filter (only a
 *    positively-known female triggers a drop).
 *  - [isCommunityFemaleOwned] — a community playlist is female-owned when its curator name matches a
 *    known female artist.
 *
 * Matching reuses [SubsetNormalize] (the same normalizer the rest of offline search uses): exact
 * whole-token normalized names for same-script, plus a consonant skeleton tagged with the entry's
 * SCRIPT for CROSS-SCRIPT alignment only (a Hebrew whitelist name vs a romanized credit, or vice
 * versa — same-script skeletons collide badly, so same-script must hit the exact name). Skeletons
 * gate at >=3 chars (a 2-char skeleton collides).
 */

/** Which scripts a female artist with a given skeleton writes her name in. */
internal class ScriptFlags(var heb: Boolean = false, var lat: Boolean = false)

/**
 * A compiled matcher over the whitelist's female artists.
 *  - [names]: exact normalized full names (`plainTokens(name).join(" ")`) — same-script matching, and
 *    also the key set for [isCommunityFemaleOwned] (JS `femaleNameKey` == this `norm`).
 *  - [skels]: consonant-skeleton -> the scripts a female with that skeleton uses — cross-script only.
 */
class FemaleMatcher internal constructor(
    internal val names: Set<String>,
    internal val skels: Map<String, ScriptFlags>,
) {
    internal fun isEmpty(): Boolean = names.isEmpty() && skels.isEmpty()
}

// `norm` / `skel` / `hasHeb` — the three helpers at the top of credits.mjs.
private fun norm(s: String?): String = SubsetNormalize.plainTokens(s).joinToString(" ")
private fun skel(s: String?): String = SubsetNormalize.skeletonKey(s).replace(Regex("\\s+"), "")
private val HEB_LETTER = Regex("[֐-׿]") // /[֐-׿]/
private fun hasHeb(s: String?): Boolean = HEB_LETTER.containsMatchIn(s ?: "")

/**
 * Build the matcher from the whitelist's female artists (credits.mjs `buildFemaleMatcher`). Exact
 * normalized names, plus >=3-char skeletons tagged with the script(s) each female uses.
 */
fun buildFemaleMatcher(artists: List<SubArtist>): FemaleMatcher {
    val names = LinkedHashSet<String>()
    val skels = LinkedHashMap<String, ScriptFlags>()
    for (a in artists) {
        if (!a.isFemale || a.name.isEmpty()) continue
        val n = norm(a.name)
        if (n.isNotEmpty()) names.add(n)
        val sk = skel(a.name)
        if (sk.length >= 3) {
            val e = skels.getOrPut(sk) { ScriptFlags() }
            if (hasHeb(a.name)) e.heb = true else e.lat = true
        }
    }
    return FemaleMatcher(names, skels)
}

// A candidate credit is female iff its normalized name is a known female (same-script), OR its
// >=3-char skeleton matches a known female written in the OTHER script (cross-script only).
private fun matchesFemale(name: String, m: FemaleMatcher): Boolean {
    val n = norm(name)
    if (n.isNotEmpty() && m.names.contains(n)) return true
    val sk = skel(name)
    if (sk.length < 3) return false
    val e = m.skels[sk] ?: return false
    return if (hasHeb(name)) e.lat else e.heb
}

// Credit separators, incl. Hebrew עם (credits.mjs SPLIT). Ported verbatim; case-insensitive.
private val SPLIT = Regex(
    "\\s*(?:,|&|\\+|·|/|\\bx\\b|\\bfeat\\.?\\b|\\bft\\.?\\b|\\bfeaturing\\b|\\bwith\\b|\\band\\b|\\bvs\\.?\\b|×|עם)\\s*",
    RegexOption.IGNORE_CASE,
)
private fun splitNames(s: String): List<String> =
    SPLIT.split(s).map { it.trim() }.filter { it.isNotEmpty() }

// TITLE credits — ONLY text after an explicit credit marker (never scan the whole title). Inside a
// parenthetical, "with"/"duet" also count; a non-parenthetical tail needs a STRONG marker.
private val CREDIT_PAREN = Regex("(?:feat\\.?|ft\\.?|featuring|duet(?:\\s+with)?|with)\\s+(.+)", RegexOption.IGNORE_CASE)
private val CREDIT_TAIL = Regex("(?:feat\\.?|ft\\.?|featuring)\\s+(.+)", RegexOption.IGNORE_CASE)
private val PAREN_GROUP = Regex("[(\\[{]([^)\\]}]*)[)\\]}]")
private val PAREN_STRIP = Regex("[(\\[{][^)\\]}]*[)\\]}]")

private fun titleCredits(title: String): List<String> {
    val out = ArrayList<String>()
    for (mr in PAREN_GROUP.findAll(title)) {
        val inner = mr.groupValues[1]
        val c = CREDIT_PAREN.find(inner)
        if (c != null) out += splitNames(c.groupValues[1])
    }
    val tail = CREDIT_TAIL.find(PAREN_STRIP.replace(title, " "))
    if (tail != null) out += splitNames(tail.groupValues[1])
    return out
}

// ARTIST-string credits — the primary is covered by its isFemale flag; this catches a female credited
// as a SECONDARY artist in the artist field.
private fun artistCredits(artistName: String): List<String> = splitNames(artistName)

/**
 * Is ANY credited artist female? (credits.mjs `isFemaleInvolved`.) The primary flag short-circuits;
 * otherwise every title / artist-string credit is validated against the female whitelist.
 */
fun isFemaleInvolved(title: String, artistName: String, primaryIsFemale: Boolean, matcher: FemaleMatcher): Boolean {
    if (primaryIsFemale) return true
    if (matcher.isEmpty()) return false
    for (c in titleCredits(title)) if (matchesFemale(c, matcher)) return true
    for (c in artistCredits(artistName)) if (matchesFemale(c, matcher)) return true
    return false
}

/**
 * "Female-owned" community playlist detection (female-owned.mjs `makeFemaleOwned`), reduced for the
 * on-device subset. The server also owns a set of playlist ids belonging to female artists, but that
 * id set is NOT shipped in the on-device subset — so offline it is EMPTY and the predicate reduces to
 * the author-name check: the curator name normalizes (`femaleNameKey` == this matcher's name key) to a
 * known female artist. A null/blank author is never female-owned.
 */
fun isCommunityFemaleOwned(author: String?, matcher: FemaleMatcher): Boolean {
    if (author.isNullOrEmpty()) return false
    // female-owned id set is empty offline (see KDoc): id-membership branch is always false.
    return matcher.names.contains(norm(author))
}
