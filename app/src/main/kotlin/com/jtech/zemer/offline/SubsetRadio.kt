package com.jtech.zemer.offline

import java.util.WeakHashMap
import kotlin.math.exp
import kotlin.math.abs

/**
 * Corpus-native radio — the offline port of `zemer-search/index/radio.mjs`'s ranking blend, scoped to
 * what the `radio-<n>` shards actually ship (2026-09-11 addendum, see [SubsetDecoder]): per-track
 * popularity reach (`pop`) and up-to-20-neighbour session/library co-occurrence lists (`lib`/`sess`).
 * Faithfully ported from the JS: the SESS/LIB co-occurrence bump, the same-artist tier, the era +
 * content-class-leaning popularity backfill, the tie/shuffle jitter (`h01`, a direct fnv-1a port), the
 * MAX_RUN=2 artist-diversity pass, and the fixed-length "canonical station" so paging is a pure prefix
 * slice of the same ordering.
 *
 * NOT reproduced offline — data the subset doesn't ship, disclosed rather than faked: the related-artist
 * tier (no artist-level cooc graph shard), the skip-dock (no per-track skip-rate shard), and acapella
 * exclusion (no per-track acapella-membership shard). [radio] returns null for `kind == "genre"` (needs
 * per-track genre membership, which the `tracks-*` shard doesn't decode yet — a separate addendum) so the
 * caller's `serverOrOffline` rethrows instead of silently serving a genre-blind station under that name.
 *
 * Deterministic: the live server carries a random `rngSeed` inside its opaque continuation token so a
 * whole session's pages share one shuffle order; an offline continuation has no server session to hold
 * that in; [radio] instead uses a FIXED seed, so the SAME station is produced every time it is recomputed
 * for a given (kind, seed, flags) — offset-independent paging is then correct as a pure prefix slice.
 */

private const val PRIOR = 3.0
private const val W_SESS = 2.0
private const val W_LIB = 1.25
private const val W_ART = 0.2
private const val JIT_TIE = 0.03
private const val JIT_SHUFFLE = 0.35
private const val ARTIST_SEED_TOPK = 8
private const val PLAYLIST_SEED_CAP = 50
private const val SHUFFLE_POOL = 3000
private const val STATION = 500
private const val MAX_LEN = 5000
private const val MAX_RUN = 2
private const val ERA_WINDOW = 8.0
private const val ERA_BONUS = 0.15
private const val CLASS_BONUS = 0.05
private const val RNG_SEED = 0

/** `r/(r+PRIOR)` — the same reach-shrinkage curve as `index/radio.mjs`'s `shrinkReach`. */
internal fun shrinkReach(popReach: Double): Double = if (popReach > 0) popReach / (popReach + PRIOR) else 0.0

/**
 * fnv-1a(id) mixed with [seed] -> a deterministic value in [0,1) — a direct 32-bit-unsigned port of
 * radio.mjs's `h01` (`(2166136261 ^ (seed>>>0))>>>0`, then `Math.imul` per char, `>>>8 / 0x1000000`).
 */
internal fun h01(id: String, seed: Int): Double {
    var x = 0x811C9DC5u xor seed.toUInt()
    for (c in id) {
        x = x xor c.code.toUInt()
        x *= 16777619u
    }
    return (x shr 8).toDouble() / 0x1000000.toDouble()
}

/** One radio-eligible track's static facts, decoupled from [SubsetCorpus] so [radio] is unit-testable. */
internal data class RadioTrack(val videoId: String, val artistId: String, val isChasid: Boolean, val year: Int?)

internal data class RadioNeighbours(val lib: List<Pair<String, Double>>, val sess: List<Pair<String, Double>>)

/** Built once per corpus (cached below, like [SubsetCategories]'s `BuiltCategories`). */
internal class RadioIndex(
    val byId: Map<String, RadioTrack>,
    private val reachOf: Map<String, Double>,
    /** ALL corpus tracks, desc by (shrunk reach, playCount) — the popularity fallback + shuffle pool. */
    val popSorted: List<String>,
    /** Reach-sorted per artist (a slice of [popSorted]). */
    val artistTracks: Map<String, List<String>>,
    val albumTrackIds: Map<String, List<String>>,
    /** Only tracks the `radio-<n>` shards actually carry (a track with neither list is simply absent). */
    val neighbours: Map<String, RadioNeighbours>,
) {
    fun reach(videoId: String): Double = reachOf[videoId] ?: 0.0
}

internal fun buildRadioIndex(corpus: SubsetCorpus): RadioIndex {
    val reachOf = HashMap<String, Double>(corpus.radioRows.size)
    val neighbours = HashMap<String, RadioNeighbours>(corpus.radioRows.size)
    for (r in corpus.radioRows) {
        if (r.pop != null) reachOf[r.videoId] = r.pop
        neighbours[r.videoId] = RadioNeighbours(r.lib, r.sess)
    }
    val byId = HashMap<String, RadioTrack>(corpus.tracks.size)
    val playCountOf = HashMap<String, Long>(corpus.tracks.size)
    for (t in corpus.tracks) {
        val artist = corpus.artistsById[t.artistId]
        val year = t.uploadDate?.take(4)?.toIntOrNull()?.takeIf { it in 1900..2100 }
        byId[t.videoId] = RadioTrack(t.videoId, t.artistId, artist?.isChasid ?: false, year)
        playCountOf[t.videoId] = t.playCount ?: 0L
    }
    // popularity order: shrunk reach first, YouTube playCount as tiebreak/tail — mirrors radio.mjs's
    // popSorted sort (reach strictly dominates; playCount only orders the never-reached long tail).
    val popSorted = corpus.tracks.map { it.videoId }
        .sortedWith(
            compareByDescending<String> { shrinkReach(reachOf[it] ?: 0.0) }
                .thenByDescending { playCountOf[it] ?: 0L },
        )
    val artistTracks = HashMap<String, MutableList<String>>()
    for (v in popSorted) artistTracks.getOrPut(byId.getValue(v).artistId) { ArrayList() }.add(v)
    val albumTrackIds = corpus.albumTracksByAlbum.mapValues { (_, rows) -> rows.map { it.videoId } }
    return RadioIndex(byId, reachOf, popSorted, artistTracks, albumTrackIds, neighbours)
}

private val radioIndexCache = WeakHashMap<SubsetCorpus, RadioIndex>()

/** Per-corpus cache so repeated radio calls don't rebuild (WeakHashMap so a discarded corpus is collectable). */
internal fun radioIndexFor(corpus: SubsetCorpus): RadioIndex =
    synchronized(radioIndexCache) { radioIndexCache.getOrPut(corpus) { buildRadioIndex(corpus) } }

internal data class RadioResult(val ids: List<String>, val nextOffset: Int?)

/**
 * One page of a deterministic radio station over [idx]. [pass] is the caller's content gate
 * (allowFemale/blockVideos/kidZone + blocked-ids), applied exactly like `index/radio.mjs`'s `pass()`.
 * [seedTracks] backs `kind == "playlist"` (the caller resolves membership from the corpus). Returns null
 * only for `kind == "genre"` or an unrecognised kind — every other case degrades gracefully to a plain
 * popularity station when its seed is unresolvable, matching the live server's own behaviour for an
 * obscure/removed seed (never a hard failure that forces a pointless live retry while offline).
 */
internal fun radio(
    idx: RadioIndex,
    kind: String,
    seed: String?,
    seedTracks: List<String>?,
    pass: (String) -> Boolean,
    offset: Int,
    limit: Int,
): RadioResult? {
    if (kind == "genre") return null
    if (kind !in KNOWN_KINDS) return null

    var seedArtist: String? = null
    var opening: List<String> = emptyList()
    var seedSet: List<String> = emptyList()
    val exclude = HashSet<String>()

    fun hasCooc(v: String) = idx.neighbours[v]?.let { it.lib.isNotEmpty() || it.sess.isNotEmpty() } == true
    fun lead(candidates: List<String>): String = candidates.reduce { a, b ->
        val sa = shrinkReach(idx.reach(a)) + JIT_SHUFFLE * h01(a, RNG_SEED)
        val sb = shrinkReach(idx.reach(b)) + JIT_SHUFFLE * h01(b, RNG_SEED)
        if (sb > sa) b else a
    }

    when (kind) {
        "song" -> if (seed != null) idx.byId[seed]?.let { t ->
            seedArtist = t.artistId
            if (pass(seed)) { opening = listOf(seed); exclude += seed }
            seedSet = if (hasCooc(seed)) listOf(seed) else listOf(seed) + idx.artistTracks[seedArtist].orEmpty().take(ARTIST_SEED_TOPK)
        }
        "artist" -> if (seed != null) {
            seedArtist = seed
            val own = idx.artistTracks[seed].orEmpty().filter(pass)
            if (own.isNotEmpty()) { val l = lead(own); opening = listOf(l); exclude += l }
            seedSet = own.take(ARTIST_SEED_TOPK)
        }
        "album" -> if (seed != null) {
            opening = idx.albumTrackIds[seed].orEmpty().filter(pass)
            exclude += opening
            seedArtist = opening.firstOrNull()?.let { idx.byId[it]?.artistId }
            seedSet = opening.take(ARTIST_SEED_TOPK)
        }
        "playlist" -> seedSet = seedTracks.orEmpty().filter { idx.byId.containsKey(it) }.take(PLAYLIST_SEED_CAP)
        "shuffle" -> Unit // no seed
    }

    val score = HashMap<String, Double>()
    fun bump(v: String, s: Double) {
        if (v == seed || exclude.contains(v)) return
        score[v] = (score[v] ?: 0.0) + s
    }
    for (sv in seedSet) idx.neighbours[sv]?.let { n ->
        for ((b, sc) in n.sess) bump(b, W_SESS * sc)
        for ((b, sc) in n.lib) bump(b, W_LIB * sc)
    }
    seedArtist?.let { sa -> for (v in idx.artistTracks[sa].orEmpty()) bump(v, W_ART * shrinkReach(idx.reach(v))) }

    val head = score.keys.asSequence()
        .filter(pass)
        .map { it to (score.getValue(it) + JIT_TIE * h01(it, RNG_SEED)) }
        .sortedByDescending { it.second }
        .map { it.first }
        .toList()

    val placed = HashSet<String>()
    seed?.let { placed += it }
    placed += exclude
    placed += head

    val canonNeed = (minOf(MAX_LEN, STATION) - opening.size).coerceAtLeast(0)
    val rest = ArrayList(head)
    if (rest.size < canonNeed) {
        if (kind == "shuffle") {
            val pool = idx.popSorted.asSequence().take(SHUFFLE_POOL)
                .filter { !placed.contains(it) && pass(it) }
                .map { it to (shrinkReach(idx.reach(it)) + JIT_SHUFFLE * h01(it, RNG_SEED)) }
                .sortedByDescending { it.second }
                .map { it.first }
                .toList()
            for (v in pool) { if (rest.size >= canonNeed) break; rest += v; placed += v }
            if (rest.size < canonNeed) for (v in idx.popSorted) {
                if (rest.size >= canonNeed) break
                if (!placed.contains(v) && pass(v)) { rest += v; placed += v }
            }
        } else {
            val st = seed?.let { idx.byId[it] }
            val sy = st?.year
            val sc = st?.isChasid
            fun key(v: String): Double {
                var k = shrinkReach(idx.reach(v))
                val t = idx.byId[v]
                if (sy != null && t?.year != null) k += ERA_BONUS * exp(-abs(t.year - sy).toDouble() / ERA_WINDOW)
                if (sc != null && t?.isChasid == sc) k += CLASS_BONUS
                return k
            }
            val pool = idx.popSorted.asSequence().filter { !placed.contains(it) && pass(it) }
                .map { it to key(it) }.sortedByDescending { it.second }.map { it.first }.toList()
            for (v in pool) { if (rest.size >= canonNeed) break; rest += v; placed += v }
        }
    }

    val full = ArrayList<String>(opening.size + rest.size)
    full += opening
    full += diversify(rest, idx)
    val peek = minOf(MAX_LEN, offset + limit + 1)
    if (full.size < peek) for (v in idx.popSorted) {
        if (full.size >= peek) break
        if (!placed.contains(v) && pass(v)) { full += v; placed += v }
    }

    val page = full.drop(offset).take(limit)
    val nextOffset = if (full.size > offset + limit && offset + limit < MAX_LEN) offset + limit else null
    return RadioResult(page, nextOffset)
}

private val KNOWN_KINDS = setOf("song", "artist", "album", "playlist", "shuffle")

/** Greedy diversity: never more than [MAX_RUN] of the same artist consecutively — a direct port of
 * radio.mjs's `diversify` (skip ahead to the next differing artist when the run would be exceeded). */
private fun diversify(ids: List<String>, idx: RadioIndex): List<String> {
    val out = ArrayList<String>(ids.size)
    val pend = ids.toMutableList()
    while (pend.isNotEmpty()) {
        var i = 0
        if (out.size >= MAX_RUN) {
            val a = idx.byId[out.last()]?.artistId
            var run = true
            for (k in 1..MAX_RUN) if (idx.byId[out[out.size - k]]?.artistId != a) { run = false; break }
            if (run) {
                val j = pend.indexOfFirst { idx.byId[it]?.artistId != a }
                if (j > 0) i = j
            }
        }
        out += pend.removeAt(i)
    }
    return out
}
