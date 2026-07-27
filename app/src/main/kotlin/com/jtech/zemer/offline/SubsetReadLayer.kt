package com.jtech.zemer.offline

import com.jtech.zemer.search.ZemerAlbum
import com.jtech.zemer.search.ZemerAlbumHeader
import com.jtech.zemer.search.ZemerAlbumResponse
import com.jtech.zemer.search.ZemerArtist
import com.jtech.zemer.search.ZemerCuratedPlaylist
import com.jtech.zemer.search.ZemerCuratedPlaylistResponse
import com.jtech.zemer.search.ZemerCuratedPlaylistsResponse
import com.jtech.zemer.search.ZemerHomeRowsResponse
import com.jtech.zemer.search.ZemerPlaylist
import com.jtech.zemer.search.ZemerTrack
import java.util.WeakHashMap

/**
 * Offline read endpoints — the on-device port of the `/album`, `/home-rows` and
 * `/zemer-playlists` handlers in `zemer-search/server/api.mjs` and the read functions they call in
 * `zemer-search/corpus/store.mjs` (`albumDetail`, `homeRows`, `zemerPlaylistList`,
 * `zemerPlaylistDetail` / `zemerPlaylistTracks`). Each runs over a [SubsetCorpus] in memory exactly as
 * the server runs it over SQLite and returns the SAME wire models the app decodes from the live server
 * ([ZemerAlbumResponse] / [ZemerHomeRowsResponse] /
 * [ZemerCuratedPlaylistsResponse] / [ZemerCuratedPlaylistResponse]), so an offline response is consumed
 * by the Phase-4 router identically to a server one.
 *
 * All ordering, gating and filtering is pinned to the JS source; the SQL is reproduced with stable
 * Kotlin sorts (SQLite's `GROUP BY id … ORDER BY <col>` resolves ties by the grouped id, so a `.thenBy
 * { id }` reproduces it). Fields the current wire models do not carry are necessarily absent offline —
 * they are absent on the server path too, on this branch, since they travel through the same models:
 *  - [ZemerTrack] has no `thumbnail` / `album` / `playCount` / `releaseDate` (the artist/album/curated
 *    handlers emit those; the app's [com.jtech.zemer.search.ZemerTrack] does not model them yet — see
 *    the extended shape in the never-merged commit 4dc527f5), so per-song album art, play counts and
 *    dates do not survive the model.
 *  - [ZemerAlbum] carries no `type` / `trackCount` / `totalDurationSec` / `releaseDate`, and
 *    [ZemerAlbumHeader] no `playlistId` / `type` / `trackCount` / `totalDurationSec` / `releaseDate`.
 *  - Curated `auto-*` chart-movement badges (`prevRank` / `delta` / `new` / `reentry`) and `anchorDate`
 *    are LIVE-ONLY: the rank-history sidecar is not part of the on-device subset, so they are left
 *    null/absent offline. The 1-based [ZemerTrack.rank] (raw stored order) IS reproduced.
 *  - Curated covers are server-rendered SVGs; the relative `"/zemer-playlists/cover?id=<id>"` URL is
 *    emitted verbatim (resolved against the API host by the app), never rendered here.
 */

// ── shared helpers ───────────────────────────────────────────────────────────────────────────────

private fun ytThumb(vid: String?): String? =
    if (vid.isNullOrEmpty()) null else "https://i.ytimg.com/vi/$vid/mqdefault.jpg"

// The generated-cover URL the server links from a curated card (api.mjs `zemerCoverUrl`). Curated ids
// are slugs (alnum + hyphen), so `encodeURIComponent` is a no-op — interpolated verbatim.
private fun zemerCoverUrl(id: String): String = "/zemer-playlists/cover?id=$id"

/**
 * `_female` for the read filters — the female-involved videoId set (primary OR credited female, over the
 * whole corpus) UNION the curated `female` blocked ids, exactly as `api.mjs setFemaleSet` builds it
 * (`collectFemaleVideoIds` ∪ `blocked.female`). Cached per corpus so the curated-list read (which asks
 * every playlist for its tracks) rebuilds it once, not per playlist. `WeakHashMap` so a discarded corpus
 * is collectable, mirroring [SubsetCategories]'s index cache.
 */
private val femaleVideoIdsCache = WeakHashMap<SubsetCorpus, Set<String>>()

private fun femaleVideoIdsFor(corpus: SubsetCorpus, female: FemaleMatcher): Set<String> =
    synchronized(femaleVideoIdsCache) {
        femaleVideoIdsCache.getOrPut(corpus) {
            val out = HashSet<String>()
            for (t in corpus.tracks) {
                val artist = corpus.artistsById[t.artistId]
                if (isFemaleInvolved(t.title, artist?.name ?: "", artist?.isFemale ?: false, female)) out.add(t.videoId)
            }
            out.addAll(corpus.blocked.female)
            out
        }
    }

// videoId → the resolved release date `COALESCE(track.uploadDate, MAX(album.uploadDate))` (store.mjs
// `allTracks` / the year-rule read). Only the DYNAMIC year playlists need it, so it is computed lazily
// and cached per corpus.
private val releaseDatesCache = WeakHashMap<SubsetCorpus, Map<String, String?>>()

private fun releaseDatesFor(corpus: SubsetCorpus): Map<String, String?> =
    synchronized(releaseDatesCache) {
        releaseDatesCache.getOrPut(corpus) {
            val maxAlbumDate = HashMap<String, String>()
            for (at in corpus.albumTracks) {
                val d = corpus.albumsById[at.albumId]?.uploadDate ?: continue
                val cur = maxAlbumDate[at.videoId]
                if (cur == null || d > cur) maxAlbumDate[at.videoId] = d // MAX(al.uploadDate)
            }
            corpus.tracks.associate { it.videoId to (it.uploadDate ?: maxAlbumDate[it.videoId]) }
        }
    }

// videoIds that belong to at least one album — the year-rule `fromAlbum` (onAlbum) flag.
private val tracksOnAlbumCache = WeakHashMap<SubsetCorpus, Set<String>>()

private fun tracksOnAlbumFor(corpus: SubsetCorpus): Set<String> =
    synchronized(tracksOnAlbumCache) {
        tracksOnAlbumCache.getOrPut(corpus) { corpus.albumTracks.mapTo(HashSet()) { it.videoId } }
    }

// Server-curated id override (api.mjs `idDropped`): `global` ids dropped always, `female` ids only when
// female is blocked. Matches a result's videoId / id / playlistId / channelId / browseId.
private fun SubsetCorpus.idDropped(id: String?, allowFemale: Boolean): Boolean =
    id != null && id.isNotBlank() &&
        (blocked.global.contains(id) || (!allowFemale && blocked.female.contains(id)))

// ── /album ───────────────────────────────────────────────────────────────────────────────────────

/**
 * `GET /album?id=` — [albumDetail] + the api.mjs gate/per-track id-override filter. Null (404) when the
 * album is unknown, its id is blocked, or its artist fails the female/KidZone gate. Tracks come from the
 * album members in stored order (`ORDER BY pos`), each per-track female/KidZone/video-filtered, with
 * `trackNumber = pos + 1`.
 */
fun offlineAlbum(
    corpus: SubsetCorpus,
    female: FemaleMatcher,
    id: String,
    allowFemale: Boolean,
    blockVideos: Boolean,
    kidZone: Boolean,
): ZemerAlbumResponse? {
    if (corpus.idDropped(id, allowFemale)) return null
    val al = corpus.albumsById[id] ?: return null
    val albumArtist = corpus.artistsById[al.artistId]
    // Gate the whole album by its artist (same predicate as artistDetail).
    if ((!allowFemale && (albumArtist?.isFemale == true)) || (kidZone && !(albumArtist?.isKidZone == true))) return null

    val femaleIds = femaleVideoIdsFor(corpus, female)
    val tracks = corpus.albumTracksByAlbum[id].orEmpty().mapNotNull { at ->
        val t = corpus.tracksById[at.videoId] ?: return@mapNotNull null
        val trackArtist = corpus.artistsById[t.artistId]
        val femInv = femaleIds.contains(t.videoId)
        val pass = (allowFemale || !femInv) && (!kidZone || (trackArtist?.isKidZone == true)) && (!blockVideos || !t.isVideo)
        if (!pass || corpus.idDropped(t.videoId, allowFemale)) return@mapNotNull null
        ZemerTrack(
            videoId = t.videoId,
            title = t.title,
            artist = trackArtist?.name ?: "",
            explicit = t.explicit,
            durationSec = t.durationSec,
            trackNumber = at.pos + 1,
        )
    }
    return ZemerAlbumResponse(
        album = ZemerAlbumHeader(
            id = al.id,
            title = al.title,
            artist = albumArtist?.name ?: "",
            year = al.year,
            thumbnail = al.thumbnail,
        ),
        tracks = tracks,
    )
}

// ── /home-rows ───────────────────────────────────────────────────────────────────────────────────

/**
 * `GET /home-rows` — store.mjs `homeRows`. topAlbums / topVideos / topArtists hydrate the `home_rank`
 * shard order (female/KidZone on the card + id-override on the ref AND the artist; famous/american does
 * NOT apply). topCommunity is computed LIVE (not from `home_rank`): the view-ranked eligible pool, then
 * female-owned hide + per-member survival + surviving-cover, capped at [HOME_COMMUNITY_N].
 */
fun offlineHomeRows(
    corpus: SubsetCorpus,
    female: FemaleMatcher,
    allowFemale: Boolean,
    blockVideos: Boolean,
    kidZone: Boolean,
): ZemerHomeRowsResponse {
    val femaleIds = femaleVideoIdsFor(corpus, female)
    fun ranked(row: String) = corpus.homeRankByRow[row].orEmpty()

    // top-albums → ZemerAlbum(+artistId). Gate by the album's primary artist; require it still exists;
    // drop id-blocked ref/artist. (explicit is emitted by the server but not modeled by ZemerAlbum.)
    val topAlbums = ranked("top-albums").mapNotNull { corpus.albumsById[it.refId] }
        .filter { al ->
            val artist = corpus.artistsById[al.artistId]
            (allowFemale || !(artist?.isFemale == true)) && (!kidZone || (artist?.isKidZone == true)) &&
                !corpus.idDropped(al.id, allowFemale) && !corpus.idDropped(al.artistId, allowFemale)
        }
        .map { ZemerAlbum(id = it.id, playlistId = it.playlistId, title = it.title, artist = corpus.artistsById[it.artistId]?.name ?: "", artistId = it.artistId, year = it.year, thumbnail = it.thumbnail) }

    // top-videos → ZemerTrack(+artistId). These ARE videos, so blockVideos empties the row. Female =
    // primary OR credited (the _female set).
    val topVideos = if (blockVideos) emptyList() else ranked("top-videos")
        .mapNotNull { corpus.tracksById[it.refId] }
        .filter { it.isVideo } // vidRow WHERE t.isVideo=1
        .filter { t ->
            val artist = corpus.artistsById[t.artistId]
            val femInv = (artist?.isFemale == true) || femaleIds.contains(t.videoId)
            (allowFemale || !femInv) && (!kidZone || (artist?.isKidZone == true)) &&
                !corpus.idDropped(t.videoId, allowFemale) && !corpus.idDropped(t.artistId, allowFemale)
        }
        .map { ZemerTrack(videoId = it.videoId, title = it.title, artist = corpus.artistsById[it.artistId]?.name ?: "", artistId = it.artistId, explicit = it.explicit, durationSec = it.durationSec) }

    // top-artists → ZemerArtist. Gate by the artist's own flags + id-override; no _female cross-credit.
    val topArtists = ranked("top-artists").mapNotNull { corpus.artistsById[it.refId] }
        .filter { (allowFemale || !it.isFemale) && (!kidZone || it.isKidZone) && !corpus.idDropped(it.id, allowFemale) }
        .map { ZemerArtist(id = it.id, name = it.name, thumbnail = it.thumbnail) }

    return ZemerHomeRowsResponse(
        topAlbums = topAlbums,
        topVideos = topVideos,
        topArtists = topArtists,
        topCommunity = topCommunity(corpus, female, allowFemale, blockVideos, kidZone),
    )
}

private const val HOME_COMMUNITY_POOL = 80
private const val HOME_COMMUNITY_N = 32
private const val HOME_COMMUNITY_MIN_SEC = 40 * 60 // 2400
private val ENGAGED_LISTS = listOf("auto-top-50", "auto-trending", "auto-favorites")

// store.mjs `homeRows` topCommunity: view-ranked pool (viewCount!=null AND runtime>=MIN_SEC AND — unless
// no engagement data — a member in an ENGAGED_LISTS track), ordered viewCount desc, whitelisted desc, id
// asc, LIMIT POOL; then female-owned hide + per-member survival + surviving-cover, take <= N.
private fun topCommunity(
    corpus: SubsetCorpus,
    female: FemaleMatcher,
    allowFemale: Boolean,
    blockVideos: Boolean,
    kidZone: Boolean,
): List<ZemerPlaylist> {
    // Engagement signal: the raw track refIds across the ENGAGED_LISTS (kind='track' only).
    val engaged = HashSet<String>()
    for (pl in ENGAGED_LISTS) {
        for (it in corpus.zemerItemsByPlaylist[pl].orEmpty()) if (it.kind == "track") engaged.add(it.refId)
    }
    val engagedActive = engaged.isNotEmpty() // fail-safe: no data → runtime-only gate, pure view-count rank

    fun runtimeSec(id: String): Int =
        corpus.communityTracksByPlaylist[id].orEmpty().sumOf { corpus.tracksById[it.videoId]?.durationSec ?: 0 }
    fun hasEngaged(id: String): Boolean =
        corpus.communityTracksByPlaylist[id].orEmpty().any { engaged.contains(it.videoId) }

    val pool = corpus.community.asSequence()
        .filter { it.viewCount != null && runtimeSec(it.id) >= HOME_COMMUNITY_MIN_SEC && (!engagedActive || hasEngaged(it.id)) }
        .sortedWith(compareByDescending<SubCommunity> { it.viewCount ?: 0L }.thenByDescending { it.whitelisted }.thenBy { it.id })
        .take(HOME_COMMUNITY_POOL)
        .toList()

    val filterActive = !allowFemale || kidZone || blockVideos
    val out = ArrayList<ZemerPlaylist>()
    for (c in pool) {
        if (out.size >= HOME_COMMUNITY_N) break
        if (corpus.idDropped(c.id, allowFemale)) continue
        if (!allowFemale && isCommunityFemaleOwned(c.author, female)) continue // gotcha #7 rule 2
        var songCount = c.whitelisted
        var cover = ytThumb(corpus.communityTracksByPlaylist[c.id].orEmpty().firstOrNull()?.videoId)
        if (filterActive) {
            val kept = communityKept(corpus, femaleVideoIdsFor(corpus, female), c.id, allowFemale, blockVideos, kidZone)
            if (kept.count <= 0) continue
            songCount = kept.count
            cover = kept.cover
        }
        out.add(ZemerPlaylist(id = c.id, title = c.title, artist = c.author ?: "", thumbnail = cover, songCount = songCount))
    }
    return out
}

private class KeptCount(val count: Int, val cover: String?)

// api.mjs `communityKeptCounts`: post-filter surviving-member count + first-surviving member's cover,
// over the corpus membership (the same `keep` predicate /community + /search use).
private fun communityKept(
    corpus: SubsetCorpus,
    femaleIds: Set<String>,
    id: String,
    allowFemale: Boolean,
    blockVideos: Boolean,
    kidZone: Boolean,
): KeptCount {
    var count = 0
    var coverPos = Int.MAX_VALUE
    var coverVid: String? = null
    for (m in corpus.communityTracksByPlaylist[id].orEmpty()) {
        val t = corpus.tracksById[m.videoId]
        val a = t?.let { corpus.artistsById[it.artistId] }
        val am = m.artistId?.let { corpus.artistsById[it] }
        val keep = if (t == null && m.artistId == null) {
            true // unknown member → kept (fail-open)
        } else {
            val female = (a?.isFemale ?: am?.isFemale ?: false) || femaleIds.contains(m.videoId)
            val isKidZone = a?.isKidZone ?: am?.isKidZone ?: false
            val isVideo = t?.isVideo ?: false
            (allowFemale || !female) && (!kidZone || isKidZone) && (!blockVideos || !isVideo)
        }
        if (keep) {
            count++
            if (m.pos < coverPos) { coverPos = m.pos; coverVid = m.videoId }
        }
    }
    return KeptCount(count, ytThumb(coverVid))
}

// ── /zemer-playlists ─────────────────────────────────────────────────────────────────────────────

/**
 * `GET /zemer-playlists` (no id) — store.mjs `zemerPlaylistList` + api.mjs. Editorial order (`ORDER BY
 * pos, id`); a playlist with no member surviving the flags is hidden; the id-override drops a blocked
 * playlist; the thumbnail is the relative generated-cover URL (never a member's art).
 */
fun offlineCuratedPlaylists(
    corpus: SubsetCorpus,
    female: FemaleMatcher,
    allowFemale: Boolean,
    blockVideos: Boolean,
    kidZone: Boolean,
): ZemerCuratedPlaylistsResponse {
    val femaleIds = femaleVideoIdsFor(corpus, female)
    val playlists = corpus.zemerPlaylists.sortedWith(compareBy<SubZemerPlaylist> { it.pos }.thenBy { it.id })
        .filter { !corpus.idDropped(it.id, allowFemale) }
        .mapNotNull { p ->
            val tracks = zemerPlaylistTracks(corpus, femaleIds, p, allowFemale, blockVideos, kidZone)
            if (tracks.isEmpty()) null else zemerCard(p.id, p.title, tracks)
        }
    return ZemerCuratedPlaylistsResponse(playlists = playlists)
}

/**
 * `GET /zemer-playlists?id=` — store.mjs `zemerPlaylistDetail` + api.mjs. Null (404) for an unknown/
 * blocked id or when every member is filtered out. `{playlist, albums, tracks}`; for `auto-*` ids each
 * track's [ZemerTrack.rank] is its 1-based position in the RAW stored track order (`applyRanks`). The
 * chart-movement badges and `anchorDate` are LIVE-ONLY (rank-history sidecar absent from the subset) and
 * stay null/absent offline.
 */
fun offlineCuratedPlaylist(
    corpus: SubsetCorpus,
    female: FemaleMatcher,
    id: String,
    allowFemale: Boolean,
    blockVideos: Boolean,
    kidZone: Boolean,
): ZemerCuratedPlaylistResponse? {
    if (corpus.idDropped(id, allowFemale)) return null
    val p = corpus.zemerPlaylists.firstOrNull { it.id == id } ?: return null
    val femaleIds = femaleVideoIdsFor(corpus, female)
    var tracks = zemerPlaylistTracks(corpus, femaleIds, p, allowFemale, blockVideos, kidZone)
    if (tracks.isEmpty()) return null

    val albums = curatedAlbums(corpus, p, tracks, allowFemale)

    if (id.startsWith("auto-")) {
        // rank = 1-based position on the RAW stored chart (a filtered row index is NOT the chart
        // position). Badges/anchorDate stay absent — LIVE-ONLY.
        val raw = corpus.zemerItemsByPlaylist[id].orEmpty().filter { it.kind == "track" }.map { it.refId }
        val rankOf = raw.withIndex().associate { (i, v) -> v to i + 1 }
        tracks = tracks.map { t -> rankOf[t.videoId]?.let { t.copy(rank = it) } ?: t }
    }

    return ZemerCuratedPlaylistResponse(
        playlist = zemerCard(p.id, p.title, tracks),
        albums = albums,
        tracks = tracks,
    )
}

// store.mjs `zemerCard`: post-filter count/runtime, cover = the relative generated-cover URL (api.mjs
// overrides the track-art the store computes). totalDurationSec is null unless ≥1 track carries one.
private fun zemerCard(id: String, title: String, tracks: List<ZemerTrack>): ZemerCuratedPlaylist =
    ZemerCuratedPlaylist(
        id = id,
        title = title,
        thumbnail = zemerCoverUrl(id),
        trackCount = tracks.size,
        totalDurationSec = if (tracks.any { it.durationSec != null }) tracks.sumOf { it.durationSec ?: 0 } else null,
    )

/**
 * store.mjs `zemerPlaylistTracks`: the expanded, filtered, curated-ordered tracks of one playlist.
 *  - DYNAMIC year rule (`year != null`): every track whose resolved release date's year matches, newest
 *    first (releaseDate desc, videoId asc); `fromAlbum` = the track is on any album.
 *  - item rule: direct videoIds in file order, then each album expanded in album order; a videoId
 *    reached twice keeps its FIRST position (`fromAlbum` = the kind that owns that kept position).
 * Both apply the same female/KidZone/video + id-override filters. Returns the wire [ZemerTrack]s (rank
 * is added by the caller for `auto-*`).
 */
private fun zemerPlaylistTracks(
    corpus: SubsetCorpus,
    femaleIds: Set<String>,
    p: SubZemerPlaylist,
    allowFemale: Boolean,
    blockVideos: Boolean,
    kidZone: Boolean,
): List<ZemerTrack> {
    // Ordered (videoId, fromAlbum) candidate pairs, before dedup/filter.
    val ordered: List<Pair<String, Boolean>> = if (p.year != null) {
        val dates = releaseDatesFor(corpus)
        val onAlbum = tracksOnAlbumFor(corpus)
        val y = p.year.toString()
        corpus.tracks.asSequence()
            .filter { (dates[it.videoId] ?: "").take(4) == y }
            .sortedWith(compareByDescending<SubTrack> { dates[it.videoId] ?: "" }.thenBy { it.videoId })
            .map { it.videoId to onAlbum.contains(it.videoId) }
            .toList()
    } else {
        val items = corpus.zemerItemsByPlaylist[p.id].orEmpty()
        // Reproduce the UNION ordered by (ipos, spos): direct tracks (spos = -1) sort before an album
        // expansion sharing the same item pos; album members keep album order.
        data class Cand(val ipos: Int, val spos: Int, val videoId: String)
        val cands = ArrayList<Cand>()
        for (it in items) {
            when (it.kind) {
                "track" -> cands.add(Cand(it.pos, -1, it.refId))
                "album" -> for (at in corpus.albumTracksByAlbum[it.refId].orEmpty()) cands.add(Cand(it.pos, at.pos, at.videoId))
            }
        }
        cands.sortedWith(compareBy<Cand> { it.ipos }.thenBy { it.spos })
            .map { it.videoId to (it.spos >= 0) }
    }

    val seen = HashSet<String>()
    val out = ArrayList<ZemerTrack>()
    for ((videoId, fromAlbum) in ordered) {
        if (!seen.add(videoId)) continue // first position wins
        val t = corpus.tracksById[videoId] ?: continue // JOIN track — only corpus tracks serve
        val artist = corpus.artistsById[t.artistId]
        if (corpus.idDropped(videoId, allowFemale)) continue
        val femInv = femaleIds.contains(videoId)
        if ((!allowFemale && femInv) || (kidZone && !(artist?.isKidZone == true)) || (blockVideos && t.isVideo)) continue
        out.add(
            ZemerTrack(
                videoId = t.videoId,
                title = t.title,
                artist = artist?.name ?: "",
                explicit = t.explicit,
                durationSec = t.durationSec,
                fromAlbum = fromAlbum,
            ),
        )
    }
    return out
}

// store.mjs `zemerPlaylistDetail` album rows: the curated album items (or, for a year rule, the albums
// released in the year), each describing ONLY its members that actually serve in this playlist (aggregate
// over the KEPT tracks); an album with zero surviving members is omitted. Album rows keep their real art.
private fun curatedAlbums(
    corpus: SubsetCorpus,
    p: SubZemerPlaylist,
    tracks: List<ZemerTrack>,
    allowFemale: Boolean,
): List<ZemerAlbum> {
    val kept = tracks.mapTo(HashSet()) { it.videoId }

    // (album, its member videoIds) in the correct order.
    val albumOrder: List<SubAlbum> = if (p.year != null) {
        val y = p.year.toString()
        corpus.albums.asSequence()
            .filter { (it.uploadDate?.take(4) == y) || (it.uploadDate == null && it.year == p.year) }
            .sortedWith(compareBy<SubAlbum> { it.uploadDate == null }.thenByDescending { it.uploadDate ?: "" }.thenBy { it.id })
            .toList()
    } else {
        corpus.zemerItemsByPlaylist[p.id].orEmpty().filter { it.kind == "album" }.mapNotNull { corpus.albumsById[it.refId] }
    }

    return albumOrder.mapNotNull { al ->
        if (corpus.idDropped(al.id, allowFemale)) return@mapNotNull null
        val members = corpus.albumTracksByAlbum[al.id].orEmpty().count { kept.contains(it.videoId) }
        if (members == 0) return@mapNotNull null
        ZemerAlbum(
            id = al.id,
            playlistId = al.playlistId,
            title = al.title,
            artist = corpus.artistsById[al.artistId]?.name ?: "",
            year = al.year,
            thumbnail = al.thumbnail,
        )
    }
}
