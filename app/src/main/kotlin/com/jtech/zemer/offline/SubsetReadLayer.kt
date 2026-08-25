package com.jtech.zemer.offline

import com.jtech.zemer.search.ZemerAlbum
import com.jtech.zemer.search.ZemerAlbumHeader
import com.jtech.zemer.search.ZemerAlbumResponse
import com.jtech.zemer.search.ZemerArtist
import com.jtech.zemer.search.ZemerArtistResponse
import com.jtech.zemer.search.ZemerCuratedPlaylist
import com.jtech.zemer.search.ZemerCuratedPlaylistResponse
import com.jtech.zemer.search.ZemerCuratedPlaylistsResponse
import com.jtech.zemer.search.ZemerHomeRowsResponse
import com.jtech.zemer.search.ZemerNewEpisodesResponse
import com.jtech.zemer.search.ZemerPlaylist
import com.jtech.zemer.search.ZemerPodcastChannelHeader
import com.jtech.zemer.search.ZemerPodcastChannelResponse
import com.jtech.zemer.search.ZemerPodcastDetail
import com.jtech.zemer.search.ZemerPodcastEpisode
import com.jtech.zemer.search.ZemerPodcastGenrePageResponse
import com.jtech.zemer.search.ZemerPodcastGenreSummary
import com.jtech.zemer.search.ZemerPodcastGenresResponse
import com.jtech.zemer.search.ZemerPodcastResponse
import com.jtech.zemer.search.ZemerPodcastShow
import com.jtech.zemer.search.ZemerTrack
import com.jtech.zemer.search.resolveZemerUrl
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
 *    [ZemerAlbumHeader] no `type` / `trackCount` / `totalDurationSec` / `releaseDate` (it DOES carry
 *    `playlistId`, which [offlineAlbum] forwards).
 *  - Curated `auto-*` chart-movement badges (`prevRank` / `delta` / `new` / `reentry`) and `anchorDate`
 *    are LIVE-ONLY: the rank-history sidecar is not part of the on-device subset, so they are left
 *    null/absent offline. The 1-based [ZemerTrack.rank] (raw stored order) IS reproduced.
 *  - Curated covers are server-rendered SVGs; the `"/zemer-playlists/cover?id=<id>"` URL is emitted
 *    pre-resolved against the API host (the client-side resolution the offline path bypasses), never
 *    rendered here.
 */

// ── shared helpers ───────────────────────────────────────────────────────────────────────────────

/**
 * The derived video-frame thumbnail for server-parity fields — `mqdefault` because that is what the
 * LIVE server emits for these very fields (store.mjs `ytThumb`, api.mjs community covers), so the
 * online and offline paths produce the SAME URL (one Coil cache key, verified by the end-to-end
 * parity diff). This is deliberately NOT [com.jtech.zemer.search.ZemerResultMapper.thumbnailFor]
 * (`hqdefault`): that helper covers the client-side derivation for items the server sends NO
 * thumbnail for — a different contract. THE single Kotlin copy; [BuiltCategories] uses it too.
 */
internal fun ytThumb(vid: String?): String? =
    if (vid.isNullOrEmpty()) null else "https://i.ytimg.com/vi/$vid/mqdefault.jpg"

/**
 * THE per-item content gate every offline surface funnels through — the api.mjs/store.mjs `keep`
 * predicate: female-involved content hidden only when female is blocked, KidZone keeps only KidZone
 * artists, blockVideos drops videos. ONE definition (SubsetCategories' `allowed` delegates here
 * too): a hand-inlined copy per surface is how a new filter dimension gets missed on one site and
 * silently leaks — invisible online, where the server filters separately.
 */
internal fun contentGatePasses(
    femaleInvolved: Boolean,
    isKidZone: Boolean,
    isVideo: Boolean,
    allowFemale: Boolean,
    blockVideos: Boolean,
    kidZone: Boolean,
): Boolean = (allowFemale || !femaleInvolved) && (!kidZone || isKidZone) && (!blockVideos || !isVideo)

// The generated-cover URL the server links from a curated card (api.mjs `zemerCoverUrl`). Curated ids
// are slugs (alnum + hyphen), so `encodeURIComponent` is a no-op. Emitted ABSOLUTE (resolveZemerUrl):
// the server path absolutizes relative covers inside ZemerSearchClient, which the offline path
// bypasses — a relative URL reaches Coil unloadable, and the absolute form also matches the cache
// keys of covers Coil already has on disk from online sessions.
private fun zemerCoverUrl(id: String): String = resolveZemerUrl("/zemer-playlists/cover?id=$id")!!

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
            HashSet(collectFemaleVideoIds(corpus, female)).apply { addAll(corpus.blocked.female) }
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
    if (!contentGatePasses(albumArtist?.isFemale == true, albumArtist?.isKidZone == true, isVideo = false, allowFemale, blockVideos, kidZone)) return null

    val femaleIds = femaleVideoIdsFor(corpus, female)
    val tracks = corpus.albumTracksByAlbum[id].orEmpty().mapNotNull { at ->
        val t = corpus.tracksById[at.videoId] ?: return@mapNotNull null
        val trackArtist = corpus.artistsById[t.artistId]
        val femInv = femaleIds.contains(t.videoId)
        val pass = contentGatePasses(femInv, trackArtist?.isKidZone == true, t.isVideo, allowFemale, blockVideos, kidZone)
        if (!pass || corpus.idDropped(t.videoId, allowFemale)) return@mapNotNull null
        ZemerTrack(
            videoId = t.videoId,
            title = t.title,
            artist = trackArtist?.name ?: "",
            durationSec = t.durationSec,
            trackNumber = at.pos + 1,
        )
    }
    return ZemerAlbumResponse(
        album = ZemerAlbumHeader(
            id = al.id,
            // The album's own OP playlist id: without it toAlbumPage's fallback chain lands on the
            // MPRE browseId and AlbumViewModel persists that as AlbumEntity.playlistId (dead-press
            // album radio, wrong share links) on every offline open from a bare album route.
            playlistId = al.playlistId,
            title = al.title,
            artist = albumArtist?.name ?: "",
            year = al.year,
            thumbnail = al.thumbnail,
        ),
        tracks = tracks,
    )
}

// ── /artist ──────────────────────────────────────────────────────────────────────────────────────

/**
 * `GET /artist?id=` — store.mjs `artistDetail` + the api.mjs gate/per-list id-override filter. Null
 * (404) when the artist is unknown, its id is blocked, or it fails the female/KidZone gate. Songs
 * are the real "Top songs" order (play count desc, NULL play counts last; the stable sort keeps
 * corpus order for ties — the stored order mirrors the server's harvestedAt tie-break);
 * albums/singles are year desc with NULL years last, ties by id (the file's SQLite convention);
 * videos empty under blockVideos. A track FEATURING a female (the `_female` set) is dropped when
 * female is blocked — the same featuring rule as `/search`. Aggregates the wire models don't carry
 * (playCount, releaseDate, trackCount, totalDurationSec) are necessarily absent, as on every other
 * offline builder.
 */
fun offlineArtist(
    corpus: SubsetCorpus,
    female: FemaleMatcher,
    id: String,
    allowFemale: Boolean,
    blockVideos: Boolean,
    kidZone: Boolean,
): ZemerArtistResponse? {
    if (corpus.idDropped(id, allowFemale)) return null
    val a = corpus.artistsById[id] ?: return null
    // Content gate (defense-in-depth, same predicate /search uses): treat a gated artist as not-found.
    if (!contentGatePasses(a.isFemale, a.isKidZone, isVideo = false, allowFemale, blockVideos, kidZone)) return null

    val femaleIds = femaleVideoIdsFor(corpus, female)
    val tracks = corpus.tracks.asSequence()
        .filter { it.artistId == id }
        .filter { allowFemale || !femaleIds.contains(it.videoId) }
        .filter { !corpus.idDropped(it.videoId, allowFemale) }
        .sortedWith(compareBy<SubTrack> { it.playCount == null }.thenByDescending { it.playCount ?: 0L })
        .toList()
    fun song(t: SubTrack) = ZemerTrack(
        videoId = t.videoId,
        title = t.title,
        artist = a.name,
        durationSec = t.durationSec,
    )

    val albums = corpus.albums
        .filter { it.artistId == id && !corpus.idDropped(it.id, allowFemale) }
        .sortedWith(compareBy<SubAlbum> { it.year == null }.thenByDescending { it.year ?: 0 }.thenBy { it.id })
    fun album(x: SubAlbum) = ZemerAlbum(
        id = x.id,
        playlistId = x.playlistId,
        title = x.title,
        artist = a.name,
        year = x.year,
        thumbnail = x.thumbnail,
    )

    return ZemerArtistResponse(
        artist = ZemerArtist(id = a.id, name = a.name, thumbnail = a.thumbnail),
        songs = tracks.filter { !it.isVideo }.map(::song),
        videos = if (blockVideos) emptyList() else tracks.filter { it.isVideo }.map(::song),
        albums = albums.filter { it.type != "single" }.map(::album),
        singles = albums.filter { it.type == "single" }.map(::album),
        playlists = corpus.artistPlaylists
            .filter { it.artistId == id && !corpus.idDropped(it.id, allowFemale) }
            .map { ZemerPlaylist(id = it.id, title = it.title, artist = a.name, thumbnail = it.thumbnail, songCount = null) },
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
    // drop id-blocked ref/artist.
    val topAlbums = ranked("top-albums").mapNotNull { corpus.albumsById[it.refId] }
        .filter { al ->
            val artist = corpus.artistsById[al.artistId]
            contentGatePasses(artist?.isFemale == true, artist?.isKidZone == true, isVideo = false, allowFemale, blockVideos, kidZone) &&
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
            // isVideo = false: the whole row is already emptied under blockVideos above.
            contentGatePasses(femInv, artist?.isKidZone == true, isVideo = false, allowFemale, blockVideos, kidZone) &&
                !corpus.idDropped(t.videoId, allowFemale) && !corpus.idDropped(t.artistId, allowFemale)
        }
        // realVideo left at its default false: the snapshot has no CLIP classifier, so no video is flagged
        // real -> the ViewModel's empty-pool fallback shows the whole set in the hero (no special-case here).
        .map { ZemerTrack(videoId = it.videoId, title = it.title, artist = corpus.artistsById[it.artistId]?.name ?: "", artistId = it.artistId, durationSec = it.durationSec) }

    // top-artists → ZemerArtist. Gate by the artist's own flags + id-override; no _female cross-credit.
    val topArtists = ranked("top-artists").mapNotNull { corpus.artistsById[it.refId] }
        .filter { contentGatePasses(it.isFemale, it.isKidZone, isVideo = false, allowFemale, blockVideos, kidZone) && !corpus.idDropped(it.id, allowFemale) }
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
            contentGatePasses(female, isKidZone, isVideo, allowFemale, blockVideos, kidZone)
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
        if (!contentGatePasses(femInv, artist?.isKidZone == true, t.isVideo, allowFemale, blockVideos, kidZone)) continue
        out.add(
            ZemerTrack(
                videoId = t.videoId,
                title = t.title,
                artist = artist?.name ?: "",
                durationSec = t.durationSec,
                fromAlbum = fromAlbum,
            ),
        )
    }
    return out
}

// ── podcasts ───────────────────────────────────────────────────────────────────────────────────────

/**
 * The per-item podcast gate — the on-device mirror of the server's serve-time channel-membership +
 * per-item female/KidZone rule (handoff `zemer-podcasts-whitelist-by-channel-request.md`, server reply
 * 4). The snapshot is already pre-gated to approved channels, so membership is implicit; here we apply
 * the flag gate a SHOW inherits from its host channel (a wholly-female/kids channel carries the flag)
 * PLUS the per-show/episode `blocked`-shard exceptions ([idDropped], which drops `female` ids only when
 * female is blocked and `global` ids always). Episodes are audio (played by videoId via InnerTube), so
 * `blockVideos` never hides them — `isVideo = false` throughout. A channel-less grandfathered show has
 * no flags to inherit (female/KidZone = false).
 */
private fun SubsetCorpus.podcastShowPasses(
    show: SubPodcastShow,
    allowFemale: Boolean,
    blockVideos: Boolean,
    kidZone: Boolean,
): Boolean {
    val ch = show.channelId?.let { podcastChannelsById[it] }
    if (idDropped(show.id, allowFemale) || idDropped(show.channelId, allowFemale)) return false
    return contentGatePasses(ch?.isFemale == true, ch?.isKidZone == true, isVideo = false, allowFemale, blockVideos, kidZone)
}

// newest-first: publishedAt desc (ISO dates sort lexicographically), NULLs last, then videoId asc.
private val EPISODE_RECENCY = compareByDescending<SubPodcastEpisode> { it.publishedAt != null }
    .thenByDescending { it.publishedAt ?: "" }
    .thenBy { it.videoId }

private fun SubPodcastEpisode.toWire(show: SubPodcastShow?): ZemerPodcastEpisode = ZemerPodcastEpisode(
    videoId = videoId,
    title = title,
    podcastId = showId,
    podcastName = show?.name,
    channelId = show?.channelId,
    thumbnail = thumbnail,
    durationSeconds = durationSec ?: 0,
    publishedAt = publishedAt,
)

private fun SubPodcastShow.toWire(): ZemerPodcastShow = ZemerPodcastShow(
    id = id, name = name, author = author, channelId = channelId,
    thumbnail = thumbnail, episodeCountText = episodeCountText,
)

/**
 * `GET /podcast?id=&offset=` — the SHOW page. Null (404) for an unknown show or one whose channel fails
 * the female/KidZone gate / is id-blocked. Offline serves the show's WHOLE gated episode list in one page
 * (no server-side paging), newest-first, so `nextOffset` is null; a paged follow-up call (offset > 0)
 * returns no further episodes.
 */
fun offlinePodcast(
    corpus: SubsetCorpus,
    id: String,
    offset: Int,
    allowFemale: Boolean,
    blockVideos: Boolean,
    kidZone: Boolean,
): ZemerPodcastResponse? {
    val show = corpus.podcastsById[id] ?: return null
    if (!corpus.podcastShowPasses(show, allowFemale, blockVideos, kidZone)) return null
    val episodes = if (offset > 0) emptyList() else corpus.podcastEpisodesByShow[id].orEmpty()
        .filter { !corpus.idDropped(it.videoId, allowFemale) }
        .sortedWith(EPISODE_RECENCY)
        .map { it.toWire(show) }
    return ZemerPodcastResponse(
        podcast = ZemerPodcastDetail(
            id = show.id,
            name = show.name,
            author = show.author,
            channelId = show.channelId,
            thumbnail = show.thumbnail,
            // description + categories are LIVE-ONLY (not in the shard); absent offline.
        ),
        episodes = episodes,
        nextOffset = null,
    )
}

private const val PODCAST_CHANNEL_EPISODES = 50

/**
 * `GET /podcast-channel?id=UC…` — the host-channel page (shows shelf + latest loose episodes). Null (404)
 * for an unknown/non-approved channel or one that fails the female/KidZone gate / is id-blocked. Shows are
 * the channel's gated shows in stored order; the loose episodes are the channel's newest gated episodes
 * (capped at [PODCAST_CHANNEL_EPISODES], a preview like the live shelf).
 */
fun offlinePodcastChannel(
    corpus: SubsetCorpus,
    id: String,
    allowFemale: Boolean,
    blockVideos: Boolean,
    kidZone: Boolean,
): ZemerPodcastChannelResponse? {
    val ch = corpus.podcastChannelsById[id] ?: return null
    if (corpus.idDropped(id, allowFemale)) return null
    if (!contentGatePasses(ch.isFemale, ch.isKidZone, isVideo = false, allowFemale, blockVideos, kidZone)) return null

    val shows = corpus.podcastsByChannel[id].orEmpty()
        .filter { corpus.podcastShowPasses(it, allowFemale, blockVideos, kidZone) }
    val showIds = shows.mapTo(HashSet()) { it.id }
    val episodes = corpus.podcastEpisodes.asSequence()
        .filter { it.showId in showIds && !corpus.idDropped(it.videoId, allowFemale) }
        .sortedWith(EPISODE_RECENCY)
        .take(PODCAST_CHANNEL_EPISODES)
        .map { it.toWire(corpus.podcastsById[it.showId]) }
        .toList()

    return ZemerPodcastChannelResponse(
        channel = ZemerPodcastChannelHeader(id = ch.id, name = ch.name, thumbnail = ch.thumbnail),
        shows = shows.map { it.toWire() },
        episodes = episodes,
    )
}

/**
 * `GET /podcasts/new-episodes?k=` — the newest gated episodes across every approved channel's shows,
 * newest-first, capped at [k].
 */
fun offlinePodcastsNewEpisodes(
    corpus: SubsetCorpus,
    k: Int,
    allowFemale: Boolean,
    blockVideos: Boolean,
    kidZone: Boolean,
): ZemerNewEpisodesResponse {
    val episodes = corpus.podcastEpisodes.asSequence()
        .filter { ep ->
            val show = corpus.podcastsById[ep.showId] ?: return@filter false
            corpus.podcastShowPasses(show, allowFemale, blockVideos, kidZone) &&
                !corpus.idDropped(ep.videoId, allowFemale)
        }
        .sortedWith(EPISODE_RECENCY)
        .take(k.coerceAtLeast(0))
        .map { it.toWire(corpus.podcastsById[it.showId]) }
        .toList()
    return ZemerNewEpisodesResponse(episodes = episodes)
}

// A genre slug's display title, derived offline the same way the server labels it: the vocabulary is
// single lowercase words, so the title is the slug with its first letter uppercased ("gemara" -> "Gemara").
private fun podcastGenreTitle(slug: String): String = slug.replaceFirstChar { it.uppercase() }

/**
 * `GET /podcast-genres` — the flat catalog. Each approved, gate-passing show contributes to every genre
 * slug in its `genres` field; the count is the post-filter show count per slug. Ordered most-populated
 * first (then slug), matching the server catalog.
 */
fun offlinePodcastGenres(
    corpus: SubsetCorpus,
    allowFemale: Boolean,
    blockVideos: Boolean,
    kidZone: Boolean,
): ZemerPodcastGenresResponse {
    val counts = HashMap<String, Int>()
    for (show in corpus.podcasts) {
        if (show.genres.isEmpty()) continue
        if (!corpus.podcastShowPasses(show, allowFemale, blockVideos, kidZone)) continue
        for (slug in show.genres) counts[slug] = (counts[slug] ?: 0) + 1
    }
    val genres = counts.entries
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .map { ZemerPodcastGenreSummary(id = it.key, title = podcastGenreTitle(it.key), showCount = it.value) }
    return ZemerPodcastGenresResponse(count = genres.size, genres = genres)
}

/**
 * `GET /podcast-genres?id=<slug>` — one genre's member shows (gate-passing), in stored order. Null (404)
 * when no show survives, mirroring the server + the live client's back-out behavior.
 */
fun offlinePodcastGenre(
    corpus: SubsetCorpus,
    slug: String,
    allowFemale: Boolean,
    blockVideos: Boolean,
    kidZone: Boolean,
): ZemerPodcastGenrePageResponse? {
    val shows = corpus.podcasts.filter {
        slug in it.genres && corpus.podcastShowPasses(it, allowFemale, blockVideos, kidZone)
    }
    if (shows.isEmpty()) return null
    return ZemerPodcastGenrePageResponse(
        genre = ZemerPodcastGenreSummary(id = slug, title = podcastGenreTitle(slug), showCount = shows.size),
        shows = shows.map { it.toWire() },
    )
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
