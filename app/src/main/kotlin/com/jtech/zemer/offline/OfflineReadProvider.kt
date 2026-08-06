package com.jtech.zemer.offline

import android.content.Context
import com.jtech.zemer.constants.OfflineSubsetLastSyncedAtKey
import com.jtech.zemer.search.ZemerAlbumResponse
import com.jtech.zemer.search.ZemerArtistResponse
import com.jtech.zemer.search.ZemerCuratedPlaylistResponse
import com.jtech.zemer.search.ZemerCuratedPlaylistsResponse
import com.jtech.zemer.search.ZemerHomeRowsResponse
import com.jtech.zemer.search.ZemerNewEpisodesResponse
import com.jtech.zemer.search.ZemerPodcastChannelResponse
import com.jtech.zemer.search.ZemerPodcastGenrePageResponse
import com.jtech.zemer.search.ZemerPodcastGenresResponse
import com.jtech.zemer.search.ZemerPodcastResponse
import com.jtech.zemer.search.ZemerSearchResponse
import com.jtech.zemer.utils.WhitelistCache
import com.jtech.zemer.utils.dataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.lang.ref.SoftReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Serves the reproducible Zemer read endpoints from the on-device snapshot ([SubsetCorpus]) so the app
 * keeps working when `search.zemer.io` is unreachable. It returns the SAME response models the live
 * [com.jtech.zemer.search.ZemerSearchClient] returns, so [com.jtech.zemer.search.ZemerSearchRepository]
 * routes to it transparently (server-first, offline on network failure).
 *
 * The decoded corpus is cached behind a [SoftReference] — warm across repeated offline reads, but never
 * pinning heap the app needs elsewhere (the snapshot is tens of MB in memory). It is reloaded when the
 * on-disk manifest version changes (a sync landed) or the GC reclaimed it. All flags mirror the client:
 * `kidZone` is always false (the client sends `kidZone=0` for these surfaces) and `hideExplicit` is
 * applied by the shared mapper afterwards, not here.
 *
 * The endpoints that are NOT reproducible offline — `/playlist` (live YouTube) and `/radio` (needs the
 * co-occurrence graph, not shipped) — have no method here; the repository leaves those server-only.
 */
@Singleton
class OfflineReadProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val store = SubsetStore(context)

    private class Loaded(
        val version: Int,
        val whitelistFingerprint: Long,
        val corpus: SubsetCorpus,
        val female: FemaleMatcher,
    )

    private val lock = Any()
    private var cache: SoftReference<Loaded>? = null

    /**
     * The decoded snapshot, gated on freshness ([subsetSnapshotIsFresh] — an unsyncable device must
     * not serve an ever-aging copy) and overlaid with the live Firestore-synced whitelist
     * ([SubsetCorpus.withLiveWhitelist] — a de-whitelisted or since-female-flagged artist is dropped
     * the moment the app's whitelist sync lands, not on the next snapshot download). The cache is
     * keyed on both the manifest version and the whitelist fingerprint so either changing rebuilds.
     */
    private suspend fun snapshot(): Loaded? {
        val lastSyncedAt = context.dataStore.data.first()[OfflineSubsetLastSyncedAtKey] ?: 0L
        if (!subsetSnapshotIsFresh(lastSyncedAt, System.currentTimeMillis())) return null
        val live = WhitelistCache.snapshot().associate { it.artistId to it.isFemale }
        return synchronized(lock) {
            val manifest = store.localManifest() ?: run { cache = null; return null }
            val fingerprint = liveWhitelistFingerprint(live)
            cache?.get()?.let { if (it.version == manifest.v && it.whitelistFingerprint == fingerprint) return it }
            val corpus = SubsetDecoder.loadCorpus(store)?.withLiveWhitelist(live)
                ?: run { cache = null; return null }
            Loaded(manifest.v, fingerprint, corpus, buildFemaleMatcher(corpus.artists))
                .also { cache = SoftReference(it) }
        }
    }

    suspend fun search(query: String, k: Int, allowFemale: Boolean, blockVideos: Boolean): ZemerSearchResponse? =
        withContext(Dispatchers.IO) {
            snapshot()?.let { offlineSearch(it.corpus, it.female, query, k, allowFemale, blockVideos, kidZone = false) }
        }

    suspend fun album(id: String, allowFemale: Boolean, blockVideos: Boolean): ZemerAlbumResponse? =
        withContext(Dispatchers.IO) {
            snapshot()?.let { offlineAlbum(it.corpus, it.female, id, allowFemale, blockVideos, kidZone = false) }
        }

    suspend fun artist(id: String, allowFemale: Boolean, blockVideos: Boolean): ZemerArtistResponse? =
        withContext(Dispatchers.IO) {
            snapshot()?.let { offlineArtist(it.corpus, it.female, id, allowFemale, blockVideos, kidZone = false) }
        }

    suspend fun homeRows(allowFemale: Boolean, blockVideos: Boolean): ZemerHomeRowsResponse? =
        withContext(Dispatchers.IO) {
            snapshot()?.let { offlineHomeRows(it.corpus, it.female, allowFemale, blockVideos, kidZone = false) }
        }

    suspend fun curatedPlaylists(allowFemale: Boolean, blockVideos: Boolean): ZemerCuratedPlaylistsResponse? =
        withContext(Dispatchers.IO) {
            snapshot()?.let { offlineCuratedPlaylists(it.corpus, it.female, allowFemale, blockVideos, kidZone = false) }
        }

    suspend fun curatedPlaylist(id: String, allowFemale: Boolean, blockVideos: Boolean): ZemerCuratedPlaylistResponse? =
        withContext(Dispatchers.IO) {
            snapshot()?.let { offlineCuratedPlaylist(it.corpus, it.female, id, allowFemale, blockVideos, kidZone = false) }
        }

    // Podcasts (server reply 4 — pre-gated to approved channels in the snapshot). The browse-grid + channel
    // allow-set come from the Room-backed content mirror, not here; these serve the drill-in reads.
    suspend fun podcast(id: String, offset: Int, allowFemale: Boolean, blockVideos: Boolean): ZemerPodcastResponse? =
        withContext(Dispatchers.IO) {
            snapshot()?.let { offlinePodcast(it.corpus, id, offset, allowFemale, blockVideos, kidZone = false) }
        }

    suspend fun podcastChannel(id: String, allowFemale: Boolean, blockVideos: Boolean): ZemerPodcastChannelResponse? =
        withContext(Dispatchers.IO) {
            snapshot()?.let { offlinePodcastChannel(it.corpus, id, allowFemale, blockVideos, kidZone = false) }
        }

    suspend fun podcastsNewEpisodes(k: Int, allowFemale: Boolean, blockVideos: Boolean): ZemerNewEpisodesResponse? =
        withContext(Dispatchers.IO) {
            snapshot()?.let { offlinePodcastsNewEpisodes(it.corpus, k, allowFemale, blockVideos, kidZone = false) }
        }

    suspend fun podcastGenres(allowFemale: Boolean, blockVideos: Boolean): ZemerPodcastGenresResponse? =
        withContext(Dispatchers.IO) {
            snapshot()?.let { offlinePodcastGenres(it.corpus, allowFemale, blockVideos, kidZone = false) }
        }

    suspend fun podcastGenre(id: String, allowFemale: Boolean, blockVideos: Boolean): ZemerPodcastGenrePageResponse? =
        withContext(Dispatchers.IO) {
            snapshot()?.let { offlinePodcastGenre(it.corpus, id, allowFemale, blockVideos, kidZone = false) }
        }
}
