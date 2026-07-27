package com.jtech.zemer.offline

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.ln
import kotlin.math.max

/**
 * In-memory relevance search — the offline (on-device subset) port of `zemer-search/index/search.mjs`,
 * ported faithfully so an offline result ranks identically to the live server's. Two inverted indexes
 * (plain Latin tokens + Hebrew-aware consonant skeletons) with prefix + Damerau typo tolerance, synonym
 * expansion ([SubsetSynonyms]) and IDF-weighted, field-aware, coverage-gated ranking. Deterministic,
 * pure data ops.
 *
 * The scoring is load-bearing: the exact weight tables ([PLAIN]/[SKEL]/[PLAIN_LAST]/[SKEL_LAST]),
 * [ARTIST_AFFINITY], [REL_FLOOR], the `need = ceil(origCount/2)` coverage gate, the boost stack and the
 * sort/precision-floor are pinned byte-for-byte to search.mjs. Do NOT tune them here — change the JS
 * source and re-port.
 *
 * Generic over any [SearchDoc]; the category layer ([SubsetCategories]) supplies typed docs whose
 * `title`/`artistName`/`sortId` drive matching, ranking and the tie-break exactly as the JS `{title,
 * artistName, videoId ?? id}` shape does.
 */

/** A document the index can match/rank. Mirrors the JS doc shape used by buildIndex/search. */
interface SearchDoc {
    /** The primary matched field (a track/album title, or an artist's own name). */
    val title: String
    /** The secondary matched field; `""` for artists and community playlists (title-only ranking). */
    val artistName: String
    /** `videoId ?? id ?? ""` — the stable sort tie-break key (search.mjs sort clause 3). */
    val sortId: String
}

/** One ranked result: the matched [doc], its [score] and query-token [coverage]. */
data class SearchHit<T : SearchDoc>(val doc: T, val score: Double, val coverage: Int)

// --- weight tables (pinned to search.mjs) --------------------------------------------------------

private const val TITLE = 1
private const val ARTIST = 2

/** A COMPLETED plain word: exact + typo, NO prefix (only the word being typed prefix-matches). */
private val PLAIN = Weights(exact = 10, prefix = 0, fuzzy = 5)
// Skeletons cross-script EXACTLY; vowel typos are already absorbed by dropping vowels — so NO skeleton
// fuzzy (double-lossy). Precision-first.
private val SKEL = Weights(exact = 8, prefix = 0, fuzzy = 0)
/** The LAST plain token is the word being typed — a prefix of it IS the intent, weighted near-exact. */
private val PLAIN_LAST = Weights(exact = 10, prefix = 9, fuzzy = 5)
private val SKEL_LAST = Weights(exact = 8, prefix = 7, fuzzy = 0)

// Bonus per query word matching the ARTIST name — being BY the searched artist beats being merely
// mentioned in another track's title. Applied only to multi-word queries (see [searchIndex]).
private const val ARTIST_AFFINITY = 25

// Precision-first: drop any result scoring below this fraction of the top hit. (search.mjs reads
// process.env.REL_FLOOR; on device the default is the only value.)
private const val REL_FLOOR = 0.4

private data class Weights(val exact: Int, val prefix: Int, val fuzzy: Int)

// Boundary-padded bigrams: "abc" -> ^a, ab, bc, c$ (so abc<->axc still share ^a and c$).
private fun bigrams(s: String): List<String> {
    if (s.isEmpty()) return emptyList()
    if (s.length == 1) return listOf("^" + s, s + "$")
    val g = ArrayList<String>(s.length + 1)
    g.add("^" + s[0])
    for (i in 0 until s.length - 1) g.add(s.substring(i, i + 2))
    g.add(s[s.length - 1] + "$")
    return g
}

/** One inverted field (plain OR skeleton): postings, bigram candidate index, sorted vocab, idf. */
internal class Field {
    // token -> (doc -> field-bit mask: TITLE|ARTIST)
    val inv = HashMap<String, HashMap<Int, Int>>()
    val bg = HashMap<String, HashSet<String>>()
    var sorted: List<String> = emptyList()
    val idf = HashMap<String, Double>()

    fun put(tok: String, doc: Int, bit: Int) {
        val m = inv.getOrPut(tok) { HashMap() }
        m[doc] = (m[doc] ?: 0) or bit
    }

    fun finalize(n: Int) {
        for ((tok, postings) in inv) {
            idf[tok] = ln(1.0 + n.toDouble() / postings.size) // rare token -> high idf
            for (g in bigrams(tok)) bg.getOrPut(g) { HashSet() }.add(tok)
        }
        sorted = inv.keys.sorted()
    }
}

/** A built index over [docs]. Immutable; reused across queries. */
class SubsetIndex<T : SearchDoc> internal constructor(
    internal val docs: List<T>,
    private val plain: Field,
    private val skel: Field,
    private val titleP: Array<String>,
    private val artistP: Array<String>,
    private val titleS: Array<String>,
    private val artistS: Array<String>,
) {
    internal fun plainField() = plain
    internal fun skelField() = skel
    internal fun titleP(i: Int) = titleP[i]
    internal fun artistP(i: Int) = artistP[i]
    internal fun titleS(i: Int) = titleS[i]
    internal fun artistS(i: Int) = artistS[i]
}

private fun <T> uniq(a: List<T>): List<T> = a.distinct()

/** buildIndex(tracks) — one plain + one skeleton inverted index plus the stored field keys. */
fun <T : SearchDoc> buildSubsetIndex(docs: List<T>): SubsetIndex<T> {
    val n = if (docs.isEmpty()) 1 else docs.size
    val plain = Field()
    val skel = Field()
    val titleP = Array(docs.size) { "" }
    val artistP = Array(docs.size) { "" }
    val titleS = Array(docs.size) { "" }
    val artistS = Array(docs.size) { "" }
    docs.forEachIndexed { i, t ->
        val tp = uniq(SubsetNormalize.plainTokens(t.title))
        val ap = uniq(SubsetNormalize.plainTokens(t.artistName))
        val ts = uniq(SubsetNormalize.skeletonTokens(t.title))
        val ask = uniq(SubsetNormalize.skeletonTokens(t.artistName))
        for (tok in tp) plain.put(tok, i, TITLE)
        for (tok in ap) plain.put(tok, i, ARTIST)
        for (tok in ts) skel.put(tok, i, TITLE)
        for (tok in ask) skel.put(tok, i, ARTIST)
        titleP[i] = tp.joinToString(" ")
        artistP[i] = ap.joinToString(" ")
        titleS[i] = SubsetNormalize.skeletonKey(t.title)
        artistS[i] = SubsetNormalize.skeletonKey(t.artistName)
    }
    plain.finalize(n)
    skel.finalize(n)
    return SubsetIndex(docs, plain, skel, titleP, artistP, titleS, artistS)
}

// prefixMatches: binary-search lower bound over sorted vocab, then all entries that startWith qt (!= qt).
private fun prefixMatches(sorted: List<String>, qt: String): List<String> {
    var lo = 0
    var hi = sorted.size
    while (lo < hi) {
        val m = (lo + hi) ushr 1
        if (sorted[m] < qt) lo = m + 1 else hi = m
    }
    val out = ArrayList<String>()
    var i = lo
    while (i < sorted.size && sorted[i].startsWith(qt)) {
        if (sorted[i] != qt) out.add(sorted[i])
        i++
    }
    return out
}

private fun bigramCandidates(field: Field, qt: String): Set<String> {
    val cand = HashSet<String>()
    for (g in bigrams(qt)) field.bg[g]?.let { cand.addAll(it) }
    cand.remove(qt)
    return cand
}

private class Match(var w: Double, var mask: Int)

// One query token -> Map<doc, {w, mask}>. w = base(matchType) x idf(matchedToken); mask = where it hit
// (0 for fuzzy — a fuzzy hit can't grant artist-affinity/field boosts it didn't earn).
private fun matchToken(field: Field, qt: String, cap: Int, weights: Weights, minPrefix: Int): Map<Int, Match> {
    val out = HashMap<Int, Match>()
    fun consider(v: String, base: Int, strong: Boolean) {
        val postings = field.inv[v] ?: return
        val idf = field.idf[v] ?: 1.0
        val w = base * idf
        for ((doc, mask) in postings) {
            val eff = if (strong) mask else 0
            val cur = out[doc]
            if (cur == null) {
                out[doc] = Match(w, eff)
            } else {
                cur.mask = cur.mask or eff
                if (w > cur.w) cur.w = w
            }
        }
    }
    if (field.inv.containsKey(qt)) consider(qt, weights.exact, true)
    if (weights.prefix != 0 && qt.length >= minPrefix) for (v in prefixMatches(field.sorted, qt)) consider(v, weights.prefix, true)
    // Fuzzy only when this field uses it (plain only) and both tokens >=3.
    if (weights.fuzzy != 0 && qt.length >= 3) for (v in bigramCandidates(field, qt)) {
        if (v.length >= 3 && abs(v.length - qt.length) <= cap && SubsetNormalize.damerau(v, qt, cap) <= cap) {
            consider(v, weights.fuzzy, false)
        }
    }
    return out
}

private class Acc {
    var score = 0.0
    var mP = 0
    var mS = 0
    var aP = 0
    var aS = 0
}

private fun startsWith(key: String, prefix: String): Boolean = prefix.isNotEmpty() && key.startsWith(prefix)

// The sort tie-break mirrors JS `String.localeCompare` (Node's default en/ICU root collation, verified
// against the live server): over the YouTube id alphabet [A-Za-z0-9_-] that ordering is punctuation
// (`-` then `_`), then digits, then letters COMPARED CASE-INSENSITIVELY with lowercase sorting before
// uppercase as the tie-break — NOT UTF-16 code-unit order (which would put every uppercase letter before
// every lowercase one). Reproduced explicitly here so it is identical on the JVM (tests) and on Android's
// ICU-backed Collator (which handle punctuation weighting differently), and never platform-dependent.
// Only fires on an exact (score, title-length) tie.
private fun idCharWeight(c: Char): Int = when {
    c == '-' -> 0
    c == '_' -> 1
    c in '0'..'9' -> 2 + (c - '0') // 2..11
    c in 'a'..'z' -> 20 + (c - 'a') * 2 // lowercase: even
    c in 'A'..'Z' -> 20 + (c - 'A') * 2 + 1 // uppercase: after its lowercase
    else -> 1000 + c.code // any other char sorts last, by code point (ids never contain these)
}

private fun localeCompareIds(a: String, b: String): Int {
    val n = minOf(a.length, b.length)
    for (i in 0 until n) {
        val d = idCharWeight(a[i]) - idCharWeight(b[i])
        if (d != 0) return d
    }
    return a.length - b.length
}

/**
 * search(index, query, k) — ranked docs for [query]. Faithful port of search.mjs: synonym expansion,
 * per-token plain+skeleton matching, IDF-weighted scoring, coverage gate, the boost stack and the
 * precision floor. Returns at most [k] hits.
 */
fun <T : SearchDoc> searchIndex(index: SubsetIndex<T>, query: String, k: Int = 10): List<SearchHit<T>> {
    val qp0 = uniq(SubsetNormalize.plainTokens(query))
    val qs0 = uniq(SubsetNormalize.skeletonTokens(query))
    if (qp0.isEmpty() && qs0.isEmpty()) return emptyList()
    val expanded = SubsetSynonyms.expand(qp0, qs0)
    val qp = expanded.plain
    val qs = expanded.skel
    val qpKey = qp0.joinToString(" ")
    // Word-aligned skeleton key for the exact/begins boosts, used only when >=3 chars.
    val skKeyRaw = SubsetNormalize.skeletonKey(query)
    val skKey = if (skKeyRaw.length >= 3) skKeyRaw else ""
    val origCount = max(qp0.size, qs0.size)
    val need = max(1, ceil(origCount / 2.0).toInt())

    val acc = HashMap<Int, Acc>()
    fun get(doc: Int): Acc = acc.getOrPut(doc) { Acc() }
    // A trailing space => last word finished => no prefix anywhere. No trailing space => last token is the prefix.
    val typing = !(query.isNotEmpty() && query.last().isWhitespace())
    val lastP = if (typing) qp0.size - 1 else -1
    val lastS = if (typing) qs0.size - 1 else -1

    qp.forEachIndexed { i, qt ->
        val bit = if (i < 31) 1 shl i else 0
        val last = i == lastP
        for ((doc, m) in matchToken(index.plainField(), qt, 1, if (last) PLAIN_LAST else PLAIN, if (last) 2 else 3)) {
            val a = get(doc)
            a.score += m.w
            a.mP = a.mP or bit
            if (m.mask and ARTIST != 0) a.aP = a.aP or bit
        }
    }
    // A 2-char consonant skeleton is far too ambiguous — skip skeleton matching below 3 chars.
    qs.forEachIndexed { i, qt ->
        if (qt.length < 3) return@forEachIndexed
        val bit = if (i < 31) 1 shl i else 0
        val last = i == lastS
        val cap = if (qt.length <= 4) 1 else 2
        for ((doc, m) in matchToken(index.skelField(), qt, cap, if (last) SKEL_LAST else SKEL, 2)) {
            val a = get(doc)
            a.score += m.w
            a.mS = a.mS or bit
            if (m.mask and ARTIST != 0) a.aS = a.aS or bit
        }
    }

    val out = ArrayList<SearchHit<T>>(acc.size)
    for ((doc, a) in acc) {
        val cov = max(Integer.bitCount(a.mP), Integer.bitCount(a.mS))
        if (cov < need) continue
        val artistCov = max(Integer.bitCount(a.aP), Integer.bitCount(a.aS))
        var boost = 1.0 + (if (cov >= origCount) 0.4 else 0.0) // matched the whole query
        // Rank by MATCH POSITION: exact > begins-with > contains (begins-with checked before contains).
        if (index.artistP(doc) == qpKey || (skKey.isNotEmpty() && index.artistS(doc) == skKey)) boost += 2.5 // exact artist
        else if (startsWith(index.artistP(doc), qpKey) || startsWith(index.artistS(doc), skKey)) boost += 1.6 // artist BEGINS WITH
        else if (artistCov >= origCount) boost += 0.8 // artist CONTAINS query
        if (index.titleP(doc) == qpKey || (skKey.isNotEmpty() && index.titleS(doc) == skKey)) boost += 2.0 // exact title
        else if (startsWith(index.titleP(doc), qpKey) || startsWith(index.titleS(doc), skKey)) boost += 1.4 // title BEGINS WITH
        // cov*8: matching MORE query words wins. artistCov*AFFINITY applies only to MULTI-word queries.
        val score = (a.score + cov * 8 + (if (origCount >= 2) artistCov * ARTIST_AFFINITY else 0)) * boost
        out.add(SearchHit(index.docs[doc], score, cov))
    }
    out.sortWith(Comparator { x, y ->
        val s = y.score.compareTo(x.score)
        if (s != 0) return@Comparator s
        val tl = x.doc.title.length - y.doc.title.length
        if (tl != 0) return@Comparator tl
        localeCompareIds(x.doc.sortId, y.doc.sortId)
    })
    if (out.isEmpty()) return out
    val floor = out[0].score * REL_FLOOR
    val kept = ArrayList<SearchHit<T>>()
    for (r in out) {
        if (r.score < floor || kept.size >= k) break
        kept.add(r)
    }
    return kept
}
