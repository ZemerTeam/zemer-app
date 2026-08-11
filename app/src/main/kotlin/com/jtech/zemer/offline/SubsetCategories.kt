package com.jtech.zemer.offline

import com.jtech.zemer.search.ZemerAlbum
import com.jtech.zemer.search.ZemerArtist
import com.jtech.zemer.search.ZemerCategories
import com.jtech.zemer.search.ZemerPlaylist
import com.jtech.zemer.search.ZemerPodcastEpisode
import com.jtech.zemer.search.ZemerPodcastShow
import com.jtech.zemer.search.ZemerSearchResponse
import com.jtech.zemer.search.ZemerTrack
import java.util.WeakHashMap

/**
 * Grouped (categorized) offline search — the on-device port of `zemer-search/index/categories.mjs`
 * plus the `/search` handler in `zemer-search/server/api.mjs`. Builds seven per-category indexes over a
 * [SubsetCorpus] (artists / songs / albums / singles / videos / playlists / community, each doc carrying
 * the content-filter flags) and returns top-k per category as [ZemerSearchResponse] — the SAME wire model
 * the app decodes from the live server, so an offline result is consumed identically to a server one.
 *
 * The category split, the content filter, the community survival rule and the post-filter count/cover
 * recompute are pinned to the JS source; residual, deliberately-reduced differences are called out inline.
 */

// --- category docs: each implements [SearchDoc] (title/artistName/sortId drive ranking) and carries the
// content-filter fields (femaleInvolved / isKidZone / isVideo) + the id set blockedDoc() matches. --------

internal class CatArtistDoc(
    val id: String, val name: String, val thumbnail: String?,
    val femaleInvolved: Boolean, val isKidZone: Boolean,
) : SearchDoc {
    override val title get() = name
    override val artistName get() = ""
    override val sortId get() = id
}

internal class CatTrackDoc(
    val videoId: String, override val title: String, override val artistName: String,
    val explicit: Boolean, val durationSec: Int?, val isVideo: Boolean,
    val isKidZone: Boolean, val femaleInvolved: Boolean,
) : SearchDoc {
    override val sortId get() = videoId
}

internal class CatAlbumDoc(
    val id: String, val playlistId: String?, override val title: String, override val artistName: String,
    val type: String, val year: Int?, val thumbnail: String?,
    val femaleInvolved: Boolean, val isKidZone: Boolean,
) : SearchDoc {
    override val sortId get() = id
}

internal class CatPlaylistDoc(
    val id: String, override val title: String, override val artistName: String, val thumbnail: String?,
    val femaleInvolved: Boolean, val isKidZone: Boolean,
) : SearchDoc {
    override val sortId get() = id
}

// Community playlists rank by TITLE only — artistName is "" on purpose (the "artist" is a random curator,
// so matching/boosting on it would rank curator-name hits above title-begins-with hits). clsMask/fb mirror
// store.mjs COMMUNITY_CONTENT_SQL; femaleOwned mirrors female-owned.mjs (author-name only offline).
internal class CatCommunityDoc(
    val id: String, override val title: String, val author: String, val thumbnail: String?,
    val whitelisted: Int, val femaleOwned: Boolean, val clsMask: Int, val fb: Boolean,
) : SearchDoc {
    override val artistName get() = ""
    override val sortId get() = id
}

// A podcast SHOW folded into /search: title = show name, artistName = author (the host), so a query by
// host name affines. Female/KidZone are inherited from the host channel (per-show exceptions ride the
// `blocked` shard, matched by blockedDoc on the id/channelId).
internal class CatPodcastDoc(
    val id: String, override val title: String, val author: String?, val channelId: String?,
    val thumbnail: String?, val episodeCountText: String?,
    val femaleInvolved: Boolean, val isKidZone: Boolean,
) : SearchDoc {
    override val artistName get() = author ?: ""
    override val sortId get() = id
}

// A podcast EPISODE folded into /search: title = episode title, artistName = show name. Episodes are audio
// (isVideo irrelevant); female/KidZone inherited from the host channel + blocked.female on the videoId.
internal class CatEpisodeDoc(
    val videoId: String, override val title: String, val showId: String, val showName: String?,
    val channelId: String?, val thumbnail: String?, val durationSec: Int?, val publishedAt: String?,
    val femaleInvolved: Boolean, val isKidZone: Boolean,
) : SearchDoc {
    override val artistName get() = showName ?: ""
    override val sortId get() = videoId
}

/** One community member's filter-relevant snapshot, taken at build time (see [BuiltCategories]). */
internal class CatCommunityMember(
    val pos: Int,
    val videoId: String,
    /** No corpus track AND no discovery artist — kept fail-open by the recompute. */
    val unknown: Boolean,
    val female: Boolean,
    val isKidZone: Boolean,
    val isVideo: Boolean,
)

/**
 * The seven built indexes for one corpus, plus the data the post-filter recompute needs. Build once.
 *
 * Deliberately holds NO [SubsetCorpus] reference: instances are cached in a `WeakHashMap` keyed by
 * the corpus, and a value strongly referencing its own key can never be collected (WeakHashMap's
 * documented trap) — each sync's corpus + indexes would be pinned forever. Everything the recompute
 * needs is snapshotted into [communityMembers]/[blocked]/[femaleVideoIds] at build time instead.
 */
class BuiltCategories internal constructor(
    private val blocked: SubBlocked,
    private val communityMembers: Map<String, List<CatCommunityMember>>,
    private val artists: SubsetIndex<CatArtistDoc>,
    private val songs: SubsetIndex<CatTrackDoc>,
    private val videos: SubsetIndex<CatTrackDoc>,
    private val albums: SubsetIndex<CatAlbumDoc>,
    private val singles: SubsetIndex<CatAlbumDoc>,
    private val playlists: SubsetIndex<CatPlaylistDoc>,
    private val community: SubsetIndex<CatCommunityDoc>,
    private val podcasts: SubsetIndex<CatPodcastDoc>,
    private val episodes: SubsetIndex<CatEpisodeDoc>,
    // female-involved videoIds (primary OR credited) UNION blocked.female — matches the server's `_female`
    // set (api.mjs setFemaleSet), used by the community post-filter kept-count recompute.
    private val femaleVideoIds: Set<String>,
) {

    fun search(q: String, k: Int, allowFemale: Boolean, blockVideos: Boolean, kidZone: Boolean): ZemerCategories {
        // pick = search n*4 -> filter allowed & !blocked -> slice n -> map (categories.mjs `pick`).
        fun <T : SearchDoc> pick(index: SubsetIndex<T>, n: Int, keep: (T) -> Boolean, blockIds: (T) -> List<String?>) =
            searchIndex(index, q, n * 4).asSequence().map { it.doc }
                .filter { keep(it) && !blockedDoc(blockIds(it), allowFemale) }
                .take(n).toList()

        val artistRows = pick(artists, k,
            { allowed(it.femaleInvolved, it.isKidZone, isVideo = false, allowFemale, blockVideos, kidZone) },
            { listOf(it.id) })
            .map { ZemerArtist(id = it.id, name = it.name, thumbnail = it.thumbnail) }

        fun trackRows(index: SubsetIndex<CatTrackDoc>) = pick(index, k,
            { allowed(it.femaleInvolved, it.isKidZone, it.isVideo, allowFemale, blockVideos, kidZone) },
            { listOf(it.videoId) })
            .map { ZemerTrack(videoId = it.videoId, title = it.title, artist = it.artistName, explicit = it.explicit, durationSec = it.durationSec) }

        fun albumRows(index: SubsetIndex<CatAlbumDoc>) = pick(index, k,
            { allowed(it.femaleInvolved, it.isKidZone, isVideo = false, allowFemale, blockVideos, kidZone) },
            { listOf(it.id, it.playlistId) })
            .map { ZemerAlbum(id = it.id, playlistId = it.playlistId, title = it.title, artist = it.artistName, year = it.year, thumbnail = it.thumbnail) }

        val playlistRows = pick(playlists, k,
            { allowed(it.femaleInvolved, it.isKidZone, isVideo = false, allowFemale, blockVideos, kidZone) },
            { listOf(it.id) })
            // Artist-owned playlists carry no `whitelisted` field on the wire => songCount null.
            .map { ZemerPlaylist(id = it.id, title = it.title, artist = it.artistName, thumbnail = it.thumbnail, songCount = null) }

        // Community: title-only ranking; communitySurvives() gate; then the api.mjs post-filter recompute of
        // whitelisted count + cover (no-op when no filter is active).
        val filterActive = !allowFemale || kidZone || blockVideos
        val communityRows = searchIndex(community, q, k * 4).asSequence().map { it.doc }
            .filter { communitySurvives(it, allowFemale, blockVideos, kidZone) && !blockedDoc(listOf(it.id), allowFemale) }
            .take(k).toList()
            .map { d ->
                var songCount = d.whitelisted
                var thumb = d.thumbnail
                if (filterActive) {
                    val kept = communityKept(d.id, allowFemale, blockVideos, kidZone)
                    songCount = kept.count
                    if (kept.cover != null) thumb = kept.cover
                }
                ZemerPlaylist(id = d.id, title = d.title, artist = d.author, thumbnail = thumb, songCount = songCount)
            }

        // Podcasts: shows + episodes folded into the same matcher (server reply 4). Both gated on the
        // channel-inherited female/KidZone flags (isVideo=false: episodes are audio) + the blocked shard.
        val podcastRows = pick(podcasts, k,
            { allowed(it.femaleInvolved, it.isKidZone, isVideo = false, allowFemale, blockVideos, kidZone) },
            { listOf(it.id, it.channelId) })
            .map { ZemerPodcastShow(id = it.id, name = it.title, author = it.author, channelId = it.channelId, thumbnail = it.thumbnail, episodeCountText = it.episodeCountText) }

        val episodeRows = pick(episodes, k,
            { allowed(it.femaleInvolved, it.isKidZone, isVideo = false, allowFemale, blockVideos, kidZone) },
            // A show blocked by id (per-show exception on a mixed channel) must also drop its
            // episodes here — every other offline podcast surface checks the show id, so matching
            // only the videoId let blocked shows' episodes leak through offline search.
            { listOf(it.videoId, it.showId, it.channelId) })
            .map { ZemerPodcastEpisode(videoId = it.videoId, title = it.title, podcastId = it.showId, podcastName = it.showName, channelId = it.channelId, thumbnail = it.thumbnail, durationSeconds = it.durationSec ?: 0, publishedAt = it.publishedAt) }

        return ZemerCategories(
            artists = artistRows,
            songs = trackRows(songs),
            albums = albumRows(albums),
            singles = albumRows(singles),
            videos = trackRows(videos),
            playlists = playlistRows,
            community = communityRows,
            podcasts = podcastRows,
            episodes = episodeRows,
        )
    }

    // Content filters apply ONLY when explicitly requested (categories.mjs `allowed`) — delegates to
    // the shared [contentGatePasses] so the gate has ONE definition across the offline surfaces.
    private fun allowed(femaleInvolved: Boolean, isKidZone: Boolean, isVideo: Boolean, allowFemale: Boolean, blockVideos: Boolean, kidZone: Boolean): Boolean =
        contentGatePasses(femaleInvolved, isKidZone, isVideo, allowFemale, blockVideos, kidZone)

    // Server-curated id overrides (categories.mjs `blockedDoc`): `global` dropped always, `female` when
    // female is blocked. Matched against a result's videoId / id / playlistId.
    private fun blockedDoc(ids: List<String?>, allowFemale: Boolean): Boolean {
        for (id in ids) {
            if (id != null && (blocked.global.contains(id) || (!allowFemale && blocked.female.contains(id)))) return true
        }
        return false
    }

    // A community playlist survives iff >=1 whitelisted member survives the active filter (categories.mjs
    // `communitySurvives`), using the packed [CatCommunityDoc.clsMask]. Fail-open on no class data.
    private fun communitySurvives(p: CatCommunityDoc, allowFemale: Boolean, blockVideos: Boolean, kidZone: Boolean): Boolean {
        if (allowFemale && !kidZone && !blockVideos) return true // no filter active
        if (p.femaleOwned && !allowFemale) return false // a female artist's OWN playlist -> hide
        if (p.fb) return true // has an unknown member -> always kept
        val mask = p.clsMask
        if (mask == 0) return true // no data -> don't hide
        for (c in 0 until 8) {
            if (mask and (1 shl c) == 0) continue
            val female = (c shr 2) and 1
            val video = (c shr 1) and 1
            val kidzone = c and 1
            val excluded = (female == 1 && !allowFemale) || (video == 1 && blockVideos) || (kidzone == 0 && kidZone)
            if (!excluded) return true
        }
        return false
    }

    private class KeptCount(val count: Int, val cover: String?)

    // The api.mjs `communityKeptCounts` recompute: post-filter surviving-member count + first-surviving
    // member's cover, over the build-time member snapshots (no corpus reference — see the class KDoc).
    private fun communityKept(id: String, allowFemale: Boolean, blockVideos: Boolean, kidZone: Boolean): KeptCount {
        var count = 0
        var coverPos = Int.MAX_VALUE
        var coverVid: String? = null
        for (m in communityMembers[id].orEmpty()) {
            val keep = if (m.unknown) {
                true // unknown member -> kept (fail-open)
            } else {
                val female = m.female || femaleVideoIds.contains(m.videoId)
                contentGatePasses(female, m.isKidZone, m.isVideo, allowFemale, blockVideos, kidZone)
            }
            if (keep) {
                count++
                if (m.pos < coverPos) {
                    coverPos = m.pos
                    coverVid = m.videoId
                }
            }
        }
        return KeptCount(count, ytThumb(coverVid))
    }

    companion object {
        /**
         * buildCategories over a [corpus] (categories.mjs `buildCategories`). Produces the seven indexes;
         * [female] is the shared matcher (build once via [buildFemaleMatcher]).
         */
        fun build(corpus: SubsetCorpus, female: FemaleMatcher): BuiltCategories {
            // Enrich tracks: femaleInvolved = primary female OR a credited female — the shared
            // [collectFemaleVideoIds] scan (SubsetFemale), the same one the read layer caches. Split
            // songs / videos.
            val involvedVideoIds = collectFemaleVideoIds(corpus, female)
            val trackDocs = ArrayList<CatTrackDoc>(corpus.tracks.size)
            for (t in corpus.tracks) {
                val artist = corpus.artistsById[t.artistId]
                trackDocs.add(
                    CatTrackDoc(
                        videoId = t.videoId, title = t.title, artistName = artist?.name ?: "",
                        explicit = t.explicit, durationSec = t.durationSec, isVideo = t.isVideo,
                        isKidZone = artist?.isKidZone ?: false, femaleInvolved = t.videoId in involvedVideoIds,
                    ),
                )
            }
            // `_female` = female-involved videoIds UNION the curated `female` blocked ids (api.mjs setFemaleSet).
            val femaleVideoIds = HashSet(involvedVideoIds).apply { addAll(corpus.blocked.female) }
            val songDocs = trackDocs.filter { !it.isVideo }
            val videoDocs = trackDocs.filter { it.isVideo }

            val albumDocs = corpus.albums.map { a ->
                val artist = corpus.artistsById[a.artistId]
                val artistName = artist?.name ?: ""
                val primaryFemale = artist?.isFemale ?: false
                CatAlbumDoc(
                    id = a.id, playlistId = a.playlistId, title = a.title, artistName = artistName,
                    type = a.type, year = a.year, thumbnail = a.thumbnail,
                    femaleInvolved = primaryFemale || isFemaleInvolved(a.title, artistName, primaryFemale, female),
                    isKidZone = artist?.isKidZone ?: false,
                )
            }

            val artistDocs = corpus.artists.map { a ->
                CatArtistDoc(id = a.id, name = a.name, thumbnail = a.thumbnail, femaleInvolved = a.isFemale, isKidZone = a.isKidZone)
            }

            val playlistDocs = corpus.artistPlaylists.map { p ->
                val artist = corpus.artistsById[p.artistId]
                CatPlaylistDoc(
                    id = p.id, title = p.title, artistName = artist?.name ?: "", thumbnail = p.thumbnail,
                    femaleInvolved = artist?.isFemale ?: false, isKidZone = artist?.isKidZone ?: false,
                )
            }

            // Community docs: clsMask/fb computed from members (store.mjs COMMUNITY_CONTENT_SQL); femaleOwned
            // from the author name (SubsetFemale — the female-owned id set is not shipped on device).
            val communityDocs = corpus.community.map { c ->
                val members = corpus.communityTracksByPlaylist[c.id].orEmpty()
                var clsMask = 0
                var fb = false
                var coverVid: String? = null
                for (m in members) {
                    if (coverVid == null) coverVid = m.videoId // members are pos-sorted -> first = cover
                    val t = corpus.tracksById[m.videoId]
                    val known = t != null || m.artistId != null
                    if (!known) {
                        fb = true
                    } else {
                        val a = t?.let { corpus.artistsById[it.artistId] }
                        val am = m.artistId?.let { corpus.artistsById[it] }
                        val female2 = (a?.isFemale ?: false) || (am?.isFemale ?: false) || femaleVideoIds.contains(m.videoId)
                        val video = t?.isVideo ?: false
                        val kidzone = (a?.isKidZone ?: false) || (am?.isKidZone ?: false)
                        val cls = (if (female2) 4 else 0) + (if (video) 2 else 0) + (if (kidzone) 1 else 0)
                        clsMask = clsMask or (1 shl cls)
                    }
                }
                CatCommunityDoc(
                    id = c.id, title = c.title, author = c.author ?: "", thumbnail = ytThumb(coverVid),
                    whitelisted = c.whitelisted, femaleOwned = isCommunityFemaleOwned(c.author, female),
                    clsMask = clsMask, fb = fb,
                )
            }

            // Snapshot the per-community member facts the kept-count recompute needs, so the built
            // value carries NO corpus reference (see the class KDoc — the WeakHashMap self-pin trap).
            val communityMembers = corpus.community.associate { c ->
                c.id to corpus.communityTracksByPlaylist[c.id].orEmpty().map { m ->
                    val t = corpus.tracksById[m.videoId]
                    val a = t?.let { corpus.artistsById[it.artistId] }
                    val am = m.artistId?.let { corpus.artistsById[it] }
                    CatCommunityMember(
                        pos = m.pos,
                        videoId = m.videoId,
                        unknown = t == null && m.artistId == null,
                        female = a?.isFemale ?: am?.isFemale ?: false,
                        isKidZone = a?.isKidZone ?: am?.isKidZone ?: false,
                        isVideo = t?.isVideo ?: false,
                    )
                }
            }

            // Podcast docs: female/KidZone inherited from the host channel. Orphan episodes (no in-corpus
            // show) are dropped — they can't render a show name or be routed.
            val podcastDocs = corpus.podcasts.map { s ->
                val ch = s.channelId?.let { corpus.podcastChannelsById[it] }
                CatPodcastDoc(
                    id = s.id, title = s.name, author = s.author, channelId = s.channelId,
                    thumbnail = s.thumbnail, episodeCountText = s.episodeCountText,
                    femaleInvolved = ch?.isFemale ?: false, isKidZone = ch?.isKidZone ?: false,
                )
            }
            val episodeDocs = corpus.podcastEpisodes.mapNotNull { e ->
                val s = corpus.podcastsById[e.showId] ?: return@mapNotNull null
                val ch = s.channelId?.let { corpus.podcastChannelsById[it] }
                CatEpisodeDoc(
                    videoId = e.videoId, title = e.title, showId = e.showId, showName = s.name,
                    channelId = s.channelId, thumbnail = e.thumbnail, durationSec = e.durationSec, publishedAt = e.publishedAt,
                    femaleInvolved = ch?.isFemale ?: false, isKidZone = ch?.isKidZone ?: false,
                )
            }

            return BuiltCategories(
                blocked = corpus.blocked,
                communityMembers = communityMembers,
                artists = buildSubsetIndex(artistDocs),
                songs = buildSubsetIndex(songDocs),
                videos = buildSubsetIndex(videoDocs),
                albums = buildSubsetIndex(albumDocs.filter { it.type != "single" }),
                singles = buildSubsetIndex(albumDocs.filter { it.type == "single" }),
                playlists = buildSubsetIndex(playlistDocs),
                community = buildSubsetIndex(communityDocs),
                podcasts = buildSubsetIndex(podcastDocs),
                episodes = buildSubsetIndex(episodeDocs),
                femaleVideoIds = femaleVideoIds,
            )
        }
    }
}

// Per-corpus cache of the built indexes so repeated offlineSearch calls don't rebuild (WeakHashMap so a
// discarded corpus is collectable). Keyed on corpus identity; the matcher is derived deterministically
// from the same corpus.
private val categoriesCache = WeakHashMap<SubsetCorpus, BuiltCategories>()

private fun categoriesFor(corpus: SubsetCorpus, female: FemaleMatcher): BuiltCategories =
    synchronized(categoriesCache) { categoriesCache.getOrPut(corpus) { BuiltCategories.build(corpus, female) } }

/**
 * Top-level offline search entry point — the on-device equivalent of `GET /search`. Strips LEADING
 * whitespace (a TRAILING space is kept — it signals a completed last word), clamps [k] to [1, 200] and
 * returns the same [ZemerSearchResponse] the live server would for the same query + flags over the same
 * corpus build. The category indexes are built once per corpus and cached.
 *
 * @param allowFemale false drops female-involved results (server default is true / open).
 * @param blockVideos true drops the videos category and video tracks.
 * @param kidZone true keeps only KidZone artists.
 */
fun offlineSearch(
    corpus: SubsetCorpus,
    female: FemaleMatcher,
    q: String,
    k: Int,
    allowFemale: Boolean,
    blockVideos: Boolean,
    kidZone: Boolean,
): ZemerSearchResponse {
    val query = q.trimStart() // keep a TRAILING space (it signals a completed last word)
    val kClamped = k.coerceIn(1, 200)
    if (query.isBlank()) return ZemerSearchResponse(q = query, count = 0, categories = ZemerCategories())
    val cats = categoriesFor(corpus, female)
    val categories = cats.search(query, kClamped, allowFemale, blockVideos, kidZone)
    val count = categories.artists.size + categories.songs.size + categories.albums.size +
        categories.singles.size + categories.videos.size + categories.playlists.size + categories.community.size +
        categories.podcasts.size + categories.episodes.size
    return ZemerSearchResponse(q = query, count = count, categories = categories)
}
