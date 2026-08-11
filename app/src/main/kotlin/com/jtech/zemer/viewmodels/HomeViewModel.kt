package com.jtech.zemer.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.jtech.zemer.constants.ArtistProfilesCacheKey
import com.jtech.zemer.constants.ArtistProfilesCacheTimestampKey
import com.jtech.zemer.constants.HomeRecentArtistsKey
import com.jtech.zemer.constants.InnerTubeCookieKey
import com.jtech.zemer.constants.OnboardingCompleteKey
import com.jtech.zemer.constants.QuickPicks
import com.jtech.zemer.constants.QuickPicksKey
import com.jtech.zemer.constants.YtmSyncKey
import com.jtech.zemer.db.MusicDatabase
import com.jtech.zemer.db.entities.Album
import com.jtech.zemer.db.entities.Artist
import com.jtech.zemer.db.entities.LocalItem
import com.jtech.zemer.db.entities.Song
import com.jtech.zemer.extensions.toEnum
import com.jtech.zemer.models.toMediaMetadata
import com.jtech.zemer.playback.queues.Queue
import com.jtech.zemer.playback.queues.ZemerRadioQueue
import com.jtech.zemer.utils.ContentWhitelistDoc
import com.jtech.zemer.utils.IsraeliArtistRegistry
import com.jtech.zemer.utils.RankedContentGate
import com.jtech.zemer.utils.ZemerContentClient
import com.jtech.zemer.utils.mirrorFirst
import com.jtech.zemer.utils.ContentFilterState
import com.jtech.zemer.utils.SyncUtils
import com.jtech.zemer.search.ZemerResultMapper
import com.jtech.zemer.search.ZemerSearchRepository
import com.jtech.zemer.search.zemerSearchOptions
import com.jtech.zemer.utils.WhitelistCache
import com.jtech.zemer.utils.dataStore
import com.jtech.zemer.utils.getSuspend
import com.jtech.zemer.utils.reportException
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.SongItem
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject
import kotlin.random.Random

/**
 * The curated Zemer playlist whose tracks seed Quick Picks for a brand-new user (empty local library),
 * so the cold-start seed is Zemer-sourced rather than a YouTube home-feed fetch. The audience Top 50.
 */
private const val QUICK_PICKS_SEED_PLAYLIST = "auto-top-50"

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext val context: Context,
    val database: MusicDatabase,
    val syncUtils: SyncUtils,
    private val zemerSearchRepository: ZemerSearchRepository,
) : ViewModel() {
    private data class HomeArtistProfile(
        val id: String,
        val name: String,
        val isAmerican: Boolean?,
        val isIsraeli: Boolean?,
        val isFemale: Boolean?,
        val isFamous: Boolean?,
        // Read only by the ranked kids gate (isBlockedRanked): a kids-only artist stays out of the adult
        // Home. Distinct from the whitelist's isKidZone (which drives the KidZone tab), and from isDJ/isGroup
        // (removed with the weighted-selection teardown, which were their only readers).
        val isKids: Boolean?,
    )

    data class HomeUiState(
        val isLoading: Boolean = false,
        val isRefreshing: Boolean = false,
        val isNewUser: Boolean = true,
        val quickPicks: List<Song> = emptyList(),
        val featuredPlaylists: List<PlaylistItem> = emptyList(),
        val keepListening: List<LocalItem> = emptyList(),
        val forgottenFavorites: List<Song> = emptyList(),
        val featuredAlbums: List<AlbumItem> = emptyList(),
        val featuredArtists: List<ArtistItem> = emptyList(),
        val featuredVideos: List<SongItem> = emptyList(),
        // True when [featuredAlbums] is non-empty (all featured content is Zemer-sourced now) — the row
        // opens albums through the Zemer album route so the album screen loads via the server (immune to
        // on-device InnerTube bot-gating).
        val featuredAlbumsAreZemer: Boolean = false,
        // True when [featuredPlaylists] is non-empty (all featured content is Zemer-sourced now) — the row
        // opens playlists via the Zemer `/playlist` route (whitelist-scoped, filter-matched to the card).
        val featuredPlaylistsAreZemer: Boolean = false,
    )

    val uiState = MutableStateFlow(HomeUiState())
    val accountName = MutableStateFlow("Guest")
    val accountImageUrl = MutableStateFlow<String?>(null)

    @Volatile
    private var hasLoadedOnce = false
    @Volatile
    private var isProcessingAccountData = false
    @Volatile
    private var homeArtistProfilesCache: List<HomeArtistProfile> = emptyList()

    // The community-playlist ids the row showed on the last load. Community `PlaylistItem`s carry no
    // artist id, so they get none of `rotateByArtist`'s recently-used-artist avoidance — this is the
    // equivalent for them: the next refresh prefers ids NOT just shown, so an 8-of-16 row turns over
    // fully each pull-to-refresh instead of ~half-repeating. In-memory (resets on restart) is enough.
    @Volatile
    private var recentCommunityIds: Set<String> = emptySet()

    // Cache song IDs for instant load on next app start
    private suspend fun loadCachedLocalData(): Triple<List<Song>, List<Song>, List<LocalItem>> {
        val cachedIds = context.dataStore.getSuspend(com.jtech.zemer.constants.HomeCacheKey, "")
        if (cachedIds.isBlank()) return Triple(emptyList(), emptyList(), emptyList())

        return try {
            val parts = cachedIds.split("|")
            val quickPickIds = parts.getOrNull(0)?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
            val forgottenIds = parts.getOrNull(1)?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
            val keepListeningIds = parts.getOrNull(2)?.split(",")?.filter { it.isNotBlank() } ?: emptyList()

            val quickPicks = if (quickPickIds.isNotEmpty()) database.getSongsByIds(quickPickIds) else emptyList()
            val forgotten = if (forgottenIds.isNotEmpty()) database.getSongsByIds(forgottenIds) else emptyList()
            val keepListening = if (keepListeningIds.isNotEmpty()) {
                // Drop any episode ids a stale cache may hold - they belong to Continue Listening.
                database.getSongsByIds(keepListeningIds).filter { !it.song.isEpisode }.map { it as LocalItem }
            } else emptyList()

            Triple(quickPicks, forgotten, keepListening)
        } catch (e: Exception) {
            Timber.w(e, "Failed to load cached home data")
            Triple(emptyList(), emptyList(), emptyList())
        }
    }

    private suspend fun saveCachedLocalData(quickPicks: List<Song>, forgotten: List<Song>, keepListening: List<LocalItem>) {
        try {
            val quickPickIds = quickPicks.take(20).joinToString(",") { it.id }
            val forgottenIds = forgotten.take(20).joinToString(",") { it.id }
            val keepListeningIds = keepListening.filterIsInstance<Song>().take(20).joinToString(",") { it.id }
            val cacheString = "$quickPickIds|$forgottenIds|$keepListeningIds"
            context.dataStore.edit { it[com.jtech.zemer.constants.HomeCacheKey] = cacheString }
        } catch (e: Exception) {
            Timber.w(e, "Failed to save cached home data")
        }
    }

    private val quickPicksEnum = context.dataStore.data.map {
        it[QuickPicksKey].toEnum(QuickPicks.QUICK_PICKS)
    }.distinctUntilChanged()

    private suspend fun hasWhitelist(): Boolean =
        database.getAllWhitelistedArtistIdsSync().isNotEmpty()

    private suspend fun artistBasedQuickPicks(): List<Song> {
        val events = database.events().first()
        val listenedArtistIds = events
            .flatMap { it.song.artists }
            .map { it.id }
            .toSet()

        val whitelistedSongs = runCatching { database.allSongs().first() }.getOrDefault(emptyList())
        val artistMatches = whitelistedSongs
            .filter { song -> song.artists.any { it.id in listenedArtistIds } }
            .distinctBy { it.id }

        return when {
            artistMatches.isNotEmpty() -> artistMatches.shuffled().take(20)
            whitelistedSongs.isNotEmpty() -> whitelistedSongs.shuffled().take(20)
            else -> emptyList()
        }
    }

    private suspend fun loadQuickPicks(): List<Song> {
        val mode = quickPicksEnum.first()
        val whitelistPresent = hasWhitelist()
        val picks = when (mode) {
            QuickPicks.QUICK_PICKS -> {
                val raw = runCatching { database.quickPicks().first() }.getOrDefault(emptyList())
                val seeded = raw.ifEmpty { artistBasedQuickPicks() }
                val withFallback = seeded.ifEmpty {
                    database.events().first().map { it.song }.filter { !it.song.isEpisode }.distinctBy { it.id }.take(50)
                }
                withFallback
            }

            QuickPicks.LAST_LISTEN -> {
                val events = database.events().first()
                // Seed related-songs from the last MUSIC listen, not a podcast episode.
                val song = events.firstOrNull { !it.song.song.isEpisode }?.song
                val raw = when {
                    song != null && database.hasRelatedSongs(song.id) -> database.getRelatedSongs(song.id).first()
                    else -> emptyList()
                }
                val withFallback = raw.ifEmpty {
                    events.map { it.song }.filter { !it.song.isEpisode }.distinctBy { it.id }.take(50)
                }
                withFallback
            }
        }

        val distinct = picks
            .shuffled()
            .distinctBy { it.artists.firstOrNull()?.id ?: it.id }
            .take(20)
            .ifEmpty {
                val historyFallback = database.events().first().map { it.song }.filter { !it.song.isEpisode }.distinctBy { it.id }.take(20)
                Timber.d("HomeViewModel: Quick picks fallback from history - ${historyFallback.size} songs")
                historyFallback
            }
        Timber.d("HomeViewModel: Quick picks loaded - ${distinct.size} songs (mode=$mode, whitelistPresent=$whitelistPresent)")
        return distinct
    }

    private suspend fun loadKeepListening(): List<LocalItem> {
        val toTimeStamp = System.currentTimeMillis()
        val fromTimeStamp = toTimeStamp - 86400000 * 7 * 2
        // Podcast episodes are songs too, but they belong to the podcast "Continue Listening" row, NOT
        // the music Keep-Listening row - exclude them here.
        val historySongs = database.events().first().map { it.song }.distinctBy { it.id }
            .filter { !it.song.isEpisode }.take(40)

        val keepListeningSongs = runCatching {
            database.mostPlayedSongs(
                fromTimeStamp = fromTimeStamp,
                toTimeStamp = toTimeStamp,
                limit = 25,
                offset = 0,
            ).first()
        }.getOrDefault(emptyList()).filter { !it.song.isEpisode }.ifEmpty { historySongs }

        val keepListeningAlbums = runCatching {
            database.mostPlayedAlbums(
                fromTimeStamp = fromTimeStamp,
                toTimeStamp = toTimeStamp,
                limit = 10,
                offset = 0,
            )
                .first()
                .filter { it.album.thumbnailUrl != null }
        }.getOrDefault(emptyList())

        val keepListeningArtists = runCatching {
            database.mostPlayedArtists(fromTimeStamp, limit = 10, offset = 0).first()
                .filter { it.artist.thumbnailUrl != null }
        }.getOrDefault(emptyList())

        var combined = (keepListeningSongs.shuffled().take(12) +
            keepListeningAlbums.shuffled().take(6) +
            keepListeningArtists.shuffled().take(6)).shuffled()

        if (combined.isEmpty()) {
            // Fallback to whitelisted songs if no history
            combined = runCatching { database.allSongs().first() }
                .getOrDefault(emptyList())
                .shuffled()
                .take(20)
            Timber.d("HomeViewModel: Keep listening fallback from whitelisted - ${combined.size} items")
        }
        Timber.d("HomeViewModel: Keep listening loaded - ${keepListeningSongs.size} songs, ${keepListeningAlbums.size} albums, ${keepListeningArtists.size} artists (total: ${combined.size})")
        return combined.distinctBy { it.id }
    }

    private suspend fun loadHomeArtistProfiles(force: Boolean = false): List<HomeArtistProfile> {
        if (homeArtistProfilesCache.isNotEmpty() && !force) {
            Timber.d("NET: loadHomeArtistProfiles using memory cache (${homeArtistProfilesCache.size} profiles)")
            return homeArtistProfilesCache
        }

        val registryStart = System.currentTimeMillis()
        IsraeliArtistRegistry.ensureLoaded()
        Timber.d("NET: IsraeliArtistRegistry.ensureLoaded() took ${System.currentTimeMillis() - registryStart}ms")

        // Load from DataStore cache first for instant UI
        val cachedJson = context.dataStore.getSuspend(ArtistProfilesCacheKey, "")
        var cachedProfiles: List<HomeArtistProfile> = emptyList()

        if (cachedJson.isNotBlank()) {
            val cacheStart = System.currentTimeMillis()
            cachedProfiles = parseArtistProfilesCache(cachedJson)
            if (cachedProfiles.isNotEmpty()) {
                Timber.d("NET: Loaded ${cachedProfiles.size} artist profiles from cache in ${System.currentTimeMillis() - cacheStart}ms")
                homeArtistProfilesCache = cachedProfiles
            }
        }

        // If we have cache, return it immediately and check Firebase in background
        if (cachedProfiles.isNotEmpty() && !force) {
            // Background check for updates
            viewModelScope.launch(Dispatchers.IO) {
                checkAndUpdateArtistProfiles(cachedProfiles)
            }
            return cachedProfiles
        }

        // No cache - must fetch from Firebase (first launch)
        return fetchArtistProfilesFromFirebase(cachedProfiles)
    }

    private suspend fun checkAndUpdateArtistProfiles(cachedProfiles: List<HomeArtistProfile>) {
        runCatching {
            val firestoreStart = System.currentTimeMillis()
            val profiles = fetchArtistProfilesMirrorFirst()
            val fetchTime = System.currentTimeMillis() - firestoreStart

            // Check if data changed
            val changed = profiles.size != cachedProfiles.size ||
                profiles.map { it.id }.toSet() != cachedProfiles.map { it.id }.toSet()

            if (changed) {
                Timber.d("NET: Firebase artistsWhitelist CHANGED in ${fetchTime}ms (${cachedProfiles.size} -> ${profiles.size} docs) - cache updated for next load")
                homeArtistProfilesCache = profiles
                saveArtistProfilesToCache(profiles)
            } else {
                Timber.d("NET: Firebase artistsWhitelist unchanged in ${fetchTime}ms (${profiles.size} docs)")
            }
        }.onFailure {
            Timber.w(it, "HomeViewModel: Background Firebase check failed")
        }
    }

    private suspend fun fetchArtistProfilesFromFirebase(fallback: List<HomeArtistProfile>): List<HomeArtistProfile> {
        return runCatching {
            val firestoreStart = System.currentTimeMillis()
            val profiles = fetchArtistProfilesMirrorFirst()
            Timber.d("NET: artistsWhitelist fetch took ${System.currentTimeMillis() - firestoreStart}ms (${profiles.size} docs)")
            homeArtistProfilesCache = profiles
            saveArtistProfilesToCache(profiles)
            profiles
        }.getOrElse {
            Timber.w(it, "HomeViewModel: Failed to load artist profiles")
            fallback
        }
    }

    /**
     * Artist profiles for Home — mirror-first (content.zemer.io) with the Firestore SDK as the exact
     * fallback that ran before. Booleans stay nullable (null = unknown, distinct from false).
     */
    private suspend fun fetchArtistProfilesMirrorFirst(): List<HomeArtistProfile> =
        mirrorFirst<List<HomeArtistProfile>>(
            "homeArtistProfiles",
            mirror = { ZemerContentClient.whitelist().mapNotNull { it.toHomeArtistProfile() } },
            firebase = {
                val snapshot = FirebaseFirestore.getInstance()
                    .collection("artistsWhitelist")
                    .get()
                    .await()
                snapshot.documents.mapNotNull { doc ->
                    val id = doc.getString("id") ?: doc.getString("artistId") ?: return@mapNotNull null
                    val name = doc.getString("name") ?: doc.getString("artistName") ?: id
                    HomeArtistProfile(
                        id = id,
                        name = name,
                        isAmerican = doc.getBoolean("isAmerican"),
                        isIsraeli = IsraeliArtistRegistry.isIsraeli(id),
                        isFemale = doc.getBoolean("isFemale"),
                        isFamous = doc.getBoolean("isFamous"),
                        isKids = doc.getBoolean("isKids"),
                    )
                }
            },
        )

    private fun ContentWhitelistDoc.toHomeArtistProfile(): HomeArtistProfile? {
        val resolvedId = id.takeIf { it.isNotBlank() } ?: artistId?.takeIf { it.isNotBlank() } ?: return null
        return HomeArtistProfile(
            id = resolvedId,
            name = name ?: artistName ?: resolvedId,
            isAmerican = isAmerican,
            isIsraeli = IsraeliArtistRegistry.isIsraeli(resolvedId),
            isFemale = isFemale,
            isFamous = isFamous,
            isKids = isKids,
        )
    }

    private fun parseArtistProfilesCache(json: String): List<HomeArtistProfile> {
        return try {
            json.split("||").mapNotNull { entry ->
                val parts = entry.split("|")
                // 7 fields (id|name|isAmerican|isIsraeli|isFemale|isFamous|isKids). An original 9-field
                // cache still parses — parts[6] is isKids in that layout too, and the trailing isDJ/isGroup
                // are ignored — so upgrading from the released format needs no forced refetch.
                if (parts.size < 7) return@mapNotNull null
                HomeArtistProfile(
                    id = parts[0],
                    name = parts[1],
                    isAmerican = parts[2].toBooleanStrictOrNull(),
                    isIsraeli = parts[3].toBooleanStrictOrNull(),
                    isFemale = parts[4].toBooleanStrictOrNull(),
                    isFamous = parts[5].toBooleanStrictOrNull(),
                    isKids = parts[6].toBooleanStrictOrNull(),
                )
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to parse artist profiles cache")
            emptyList()
        }
    }

    private suspend fun saveArtistProfilesToCache(profiles: List<HomeArtistProfile>) {
        try {
            val json = profiles.joinToString("||") { p ->
                "${p.id}|${p.name}|${p.isAmerican}|${p.isIsraeli}|${p.isFemale}|${p.isFamous}|${p.isKids}"
            }
            context.dataStore.edit { prefs ->
                prefs[ArtistProfilesCacheKey] = json
                prefs[ArtistProfilesCacheTimestampKey] = System.currentTimeMillis()
            }
            Timber.d("NET: Saved ${profiles.size} artist profiles to DataStore cache")
        } catch (e: Exception) {
            Timber.w(e, "Failed to save artist profiles cache")
        }
    }

    /**
     * The telemetry-ranked home rows (Top Albums / Videos / Artists / Community) from the Zemer
     * `/home-rows` endpoint — real distinct-device listening / view counts, already whitelist-scoped +
     * content-filtered server-side. Returns null on any failure; every featured row then reads as empty
     * and simply hides (no InnerTube fallback). Telemetry must never break Home.
     */
    private suspend fun loadHomeRows(): ZemerResultMapper.HomeRows? {
        val start = System.currentTimeMillis()
        return try {
            zemerSearchRepository.homeRows(zemerSearchOptions(context)).also {
                Timber.d(
                    "NET: /home-rows -> albums=%d videos=%d artists=%d community=%d in %dms",
                    it.albums.size, it.videos.size, it.artists.size, it.community.size, System.currentTimeMillis() - start,
                )
            }
        } catch (e: java.util.concurrent.CancellationException) {
            // Cooperative cancellation (VM cleared while the fetch is in flight) must propagate — not be
            // caught and logged as a Crashlytics non-fatal. Matches the load()/refresh() catch boundaries.
            throw e
        } catch (e: Exception) {
            Timber.w(e, "HomeViewModel: /home-rows fetch failed — featured rows hide this load")
            reportException(e)
            null
        }
    }

    /**
     * Seeds an empty Quick Picks for a brand-new user (no local listening yet) from a Zemer source —
     * the audience Top 50 curated playlist — instead of YouTube's home feed. Whitelist-pure and
     * content-filtered server-side. No-ops (returns [existing]) when Quick Picks already has content or
     * the fetch fails, so Home never breaks and, in normal use, home makes no InnerTube call.
     */
    private suspend fun seedQuickPicksFromZemer(existing: List<Song>): List<Song> {
        if (existing.isNotEmpty()) return existing
        val candidateSongs = (
            try {
                zemerSearchRepository.curatedPlaylist(QUICK_PICKS_SEED_PLAYLIST, zemerSearchOptions(context))?.songs
            } catch (e: java.util.concurrent.CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "HomeViewModel: Quick Picks seed fetch failed")
                null
            }
            ).orEmpty().distinctBy { it.id }.take(40)

        if (candidateSongs.isEmpty()) return existing

        candidateSongs.forEach { song ->
            database.insert(song.toMediaMetadata())
        }

        val seeded = database.getSongsByIds(candidateSongs.map { it.id })
        return if (seeded.isNotEmpty()) seeded.take(20) else existing
    }

    private suspend fun load(force: Boolean = false) {
        if (uiState.value.isLoading) return
        val loadStartTime = System.currentTimeMillis()
        Timber.d("HomeViewModel: load() START force=$force")

        uiState.update { it.copy(isLoading = true) }
        try {
            // Run prep work in parallel with local data fetch
            val prepDeferred = viewModelScope.async(Dispatchers.IO) {
                // These can run while local data loads
                IsraeliArtistRegistry.ensureLoaded()
                Timber.d("HomeViewModel: IsraeliArtistRegistry loaded at +${System.currentTimeMillis() - loadStartTime}ms")

                if (WhitelistCache.snapshot().isEmpty()) {
                    runCatching { WhitelistCache.updateAll(database.getWhitelistEntriesSync()) }
                }
                // Don't force artist profiles - use cache + background check for updates
                // Only clear memory cache on force refresh, DataStore cache remains for instant load
                if (force) {
                    homeArtistProfilesCache = emptyList()
                }
                loadHomeArtistProfiles(force = false)
            }

            val filters = ContentFilterState.state.value
            val allowFemale = filters.allowFemaleSingers
            val recentArtistIds = context.dataStore
                .getSuspend(HomeRecentArtistsKey, "")
                .orEmpty()
                .split(",")
                .filter { it.isNotBlank() }

            // Start LOCAL data loading immediately (parallel with prep work)
            val parallelStartTime = System.currentTimeMillis()
            Timber.d("HomeViewModel: Starting parallel fetch at +${parallelStartTime - loadStartTime}ms")
            val quickDeferred = viewModelScope.async(Dispatchers.IO) { loadQuickPicks() }
            val forgottenDeferred = viewModelScope.async(Dispatchers.IO) {
                // Episodes are played songs but never belong in a music discovery row.
                database.forgottenFavorites().first().filter { !it.song.isEpisode }.shuffled().take(20)
            }
            val keepListeningDeferred = viewModelScope.async(Dispatchers.IO) { loadKeepListening() }

            // Await LOCAL data first and show immediately (instant UI)
            Timber.d("HomeViewModel: Awaiting local data at +${System.currentTimeMillis() - loadStartTime}ms")
            val quick = quickDeferred.await()
            val forgottenList = forgottenDeferred.await()
            val keepListening = keepListeningDeferred.await()
            Timber.d("HomeViewModel: LOCAL data ready at +${System.currentTimeMillis() - loadStartTime}ms (quick=${quick.size}, forgotten=${forgottenList.size}, keep=${keepListening.size})")

            val forgotten = forgottenList.ifEmpty {
                // Fallback: show liked songs if no forgotten favorites
                runCatching { database.allSongs().first().filter { it.song.liked } }
                    .getOrDefault(emptyList())
                    .shuffled()
                    .take(20)
            }

            // Show local data immediately while network loads
            if (quick.isNotEmpty() || forgotten.isNotEmpty() || keepListening.isNotEmpty()) {
                val shownQuick = quick.shuffled(Random(System.nanoTime()))
                uiState.update {
                    it.copy(
                        quickPicks = shownQuick,
                        forgottenFavorites = forgotten,
                        keepListening = keepListening,
                        isNewUser = quick.isEmpty() && keepListening.isEmpty()
                    )
                }
                // Publish the local rows to the See-all store NOW — not only after the /home-rows await — so
                // tapping a local row's "See all" during the network window shows these songs, not a blank
                // page. Only the local fields are updated (copy of the current snapshot), so a pull-to-refresh
                // keeps the previous load's still-visible featured rows until the final publish replaces them.
                HomeSeeAllStore.publish(
                    HomeSeeAllStore.data.value.copy(
                        quickPicks = shownQuick,
                        forgottenFavorites = forgotten,
                        keepListening = keepListening,
                    ),
                )
                Timber.d("HomeViewModel: Showing local data first - quick=${quick.size}, forgotten=${forgotten.size}, keep=${keepListening.size}")
            }

            // Await prep work (IsraeliArtistRegistry, WhitelistCache, artistProfiles)
            Timber.d("HomeViewModel: Awaiting prep work at +${System.currentTimeMillis() - loadStartTime}ms")
            val artistProfiles = prepDeferred.await()
            Timber.d("HomeViewModel: Prep work done at +${System.currentTimeMillis() - loadStartTime}ms")

            val profileById = artistProfiles.associateBy { it.id }

            // Now start NETWORK calls (after prep work is done). The home tab is InnerTube-free for content:
            // every row is served from the Zemer `/home-rows` endpoint, local Room, or the releases feed.
            // YouTube.home()/explore() (which rendered nothing) and the InnerTube featured scrape are gone;
            // a new user's empty Quick Picks is seeded from Zemer (below), not the YouTube home feed.
            Timber.d("HomeViewModel: Starting NETWORK fetch at +${System.currentTimeMillis() - loadStartTime}ms")
            val homeRowsDeferred = viewModelScope.async(Dispatchers.IO) { loadHomeRows() }
            // Cold start (empty local library): seed Quick Picks from the Zemer audience Top 50, not YouTube.
            // It is independent of /home-rows, so run it CONCURRENTLY — cold-start first paint then waits on
            // max(RTT), not the sum. A returning user's Quick Picks is non-empty, so the seed short-circuits
            // to a no-op with no network call.
            val quickSeededDeferred = viewModelScope.async(Dispatchers.IO) { seedQuickPicksFromZemer(quick) }
            val homeRows = homeRowsDeferred.await()
            Timber.d("HomeViewModel: NETWORK data ready at +${System.currentTimeMillis() - loadStartTime}ms")

            fun isBlockedArtist(ids: List<String>): Boolean {
                if (ids.any { IsraeliArtistRegistry.isIsraeli(it) }) return true
                val profiles = ids.mapNotNull { profileById[it] }
                if (!allowFemale && profiles.any { it.isFemale == true }) return true
                if (profiles.any { it.isAmerican != true }) return true
                if (profiles.any { it.isFamous != true }) return true
                return false
            }

            fun SongItem.isAllowed(): Boolean = !isBlockedArtist(this.artists?.mapNotNull { it.id } ?: emptyList())
            fun AlbumItem.isAllowed(): Boolean = !isBlockedArtist(this.artists?.mapNotNull { it.id } ?: emptyList())
            fun ArtistItem.isAllowed(): Boolean = !isBlockedArtist(listOfNotNull(this.id))
            fun PlaylistItem.isAllowed(): Boolean = !isBlockedArtist(listOfNotNull(this.author?.id))

            // Content gate for the telemetry-ranked rows: female (when blocked) + Israeli + kids-only, NOT the
            // famous/american quality proxy that `isBlockedArtist` applies. Real listening reach is a
            // better signal than the proxy, and applying it here cut the ranked rows to near-empty
            // (handoff REPLY 3). Blocked-ids already dropped in the mapper; this is defence-in-depth over
            // the server's own female/blocked filtering, so it needs each card's real artist channel id.
            // The rule itself lives in the shared RankedContentGate (VideoHomeRowsViewModel applies the
            // same one to the /video-home-rows rows); only the profile lookup is this ViewModel's.
            fun isBlockedRanked(ids: List<String>): Boolean = RankedContentGate.isBlockedRanked(
                ids = ids,
                allowFemale = allowFemale,
                flagsOf = { id ->
                    profileById[id]?.let {
                        RankedContentGate.Flags(isFemale = it.isFemale == true, isKids = it.isKids == true)
                    }
                },
            )

            fun SongItem.isAllowedRanked(): Boolean = !isBlockedRanked(this.artists?.mapNotNull { it.id } ?: emptyList())
            fun AlbumItem.isAllowedRanked(): Boolean = !isBlockedRanked(this.artists?.mapNotNull { it.id } ?: emptyList())
            fun ArtistItem.isAllowedRanked(): Boolean = !isBlockedRanked(listOfNotNull(this.id))
            // Community playlists carry no curator channel id, so this is effectively a pass-through: the
            // server already applies the female-owner hide + member survival + blocked-ids, and the mapper
            // re-drops blocked ids. Defined for symmetry with the other ranked rows.
            fun PlaylistItem.isAllowedRanked(): Boolean = !isBlockedRanked(listOfNotNull(this.author?.id))

            fun Song.isAllowed(): Boolean = !isBlockedArtist(this.artists.map { it.id })
            fun LocalItem.isAllowed(): Boolean = when (this) {
                is Song -> this.isAllowed()
                is Album -> !isBlockedArtist(this.artists.map { it.id })
                is Artist -> !isBlockedArtist(listOfNotNull(this.id))
                else -> true
            }

            fun Song.artistIds(): List<String> = artists.mapNotNull { it.id }
            fun SongItem.artistIds(): List<String> = artists?.mapNotNull { it.id }.orEmpty()
            fun AlbumItem.artistIds(): List<String> = artists?.mapNotNull { it.id }.orEmpty()
            fun ArtistItem.artistIds(): List<String> = listOfNotNull(id)
            fun PlaylistItem.artistIds(): List<String> = listOfNotNull(author?.id)

            fun <T> rotateByArtist(
                items: List<T>,
                maxPerArtist: Int,
                target: Int,
            ): List<T> {
                if (items.isEmpty()) return emptyList()
                val shuffled = items.shuffled(Random(System.nanoTime()))
                val counts = mutableMapOf<String, Int>()
                val freshBucket = mutableListOf<T>()
                val fallbackBucket = mutableListOf<T>()

                fun extractIds(item: T): List<String> = when (item) {
                    is Song -> item.artistIds()
                    is SongItem -> item.artistIds()
                    is AlbumItem -> item.artistIds()
                    is ArtistItem -> item.artistIds()
                    is PlaylistItem -> item.artistIds()
                    is Album -> item.artists.mapNotNull { it.id }
                    is Artist -> listOfNotNull(item.id)
                    else -> emptyList()
                }

                shuffled.forEach { item ->
                    val ids = extractIds(item)
                    if (ids.isEmpty() || ids.any { it in recentArtistIds }) fallbackBucket += item
                    else freshBucket += item
                }

                fun append(from: List<T>, into: MutableList<T>) {
                    from.forEach { item ->
                        val ids = extractIds(item)
                        if (ids.all { counts.getOrDefault(it, 0) < maxPerArtist }) {
                            ids.forEach { id -> counts[id] = counts.getOrDefault(id, 0) + 1 }
                            into += item
                        }
                    }
                }

                val result = mutableListOf<T>()
                append(freshBucket, result)
                if (result.size < target) append(fallbackBucket, result)
                return result.take(target)
            }

            // Cold start (empty local library): the Zemer audience Top 50 seed, launched concurrently above.
            val quickSeeded = quickSeededDeferred.await()

            val filteredQuick = quickSeeded.filter { song -> song.isAllowed() }
            val fallbackQuick = runCatching {
                database.allSongs().first().filter { it.isAllowed() }.take(30)
            }.getOrDefault(emptyList())
            val freshQuick = filteredQuick.filter { song -> song.artistIds().none { it in recentArtistIds } }
            val quickPool = freshQuick.ifEmpty { filteredQuick }
            val recentAwareQuick = rotateByArtist(quickPool.ifEmpty { fallbackQuick }, 1, 20)
            Timber.d("HomeViewModel: quickPicks flow - quick=${quick.size}, filtered=${filteredQuick.size}, rotated=${recentAwareQuick.size}")

            // CRITICAL: Never show fewer items than already displayed to user
            val finalQuick = when {
                recentAwareQuick.size >= quick.size -> recentAwareQuick
                recentAwareQuick.size + filteredQuick.size >= quick.size -> {
                    val additional = filteredQuick.filter { it.id !in recentAwareQuick.map { q -> q.id } }
                    (recentAwareQuick + additional).take(quick.size.coerceAtLeast(5))
                }
                else -> {
                    // Fallback: keep original quick picks to avoid showing less
                    Timber.d("HomeViewModel: Keeping original quick picks (${quick.size}) instead of filtered (${recentAwareQuick.size})")
                    quick
                }
            }
            Timber.d("HomeViewModel: finalQuick=${finalQuick.size} (original=${quick.size}, rotated=${recentAwareQuick.size})")
            // The Quick Picks row is shown in a per-load shuffle; its "See all" must lead with that SAME
            // order (the See-all contract), so shuffle ONCE here and use it for both the row and the snapshot.
            val displayedQuick = finalQuick.shuffled(Random(System.nanoTime()))
            // Featured rows come ONLY from the Zemer /home-rows endpoint — the ranked-row content gate
            // (female/israeli/blocked-ids, not the famous/american proxy) then the one-per-artist rotation.
            // No InnerTube: an empty pool (only possible if search.zemer.io is unreachable) just hides the
            // row rather than falling back to a scrape. All featured content is therefore Zemer-sourced.
            val albumsPool = homeRows?.albums.orEmpty().filter { it.isAllowedRanked() }
            val artistsPool = homeRows?.artists.orEmpty().filter { it.isAllowedRanked() }
            val videosPool = homeRows?.videos.orEmpty().filter { it.isAllowedRanked() }
            val communityPool = homeRows?.community.orEmpty().filter { it.isAllowedRanked() }
            val finalFeaturedAlbums = rotateByArtist(albumsPool, maxPerArtist = 1, target = 20)
            val finalFeaturedArtists = rotateByArtist(artistsPool, maxPerArtist = 1, target = 20)
            // Videos are content-limited (only ~19 whitelisted music videos clear the 30-day reach floor,
            // ~14 distinct artists) — a 20-slot row would just show all of them every time. Cap the shown
            // count so the row stays curated AND has headroom to turn over on refresh (server RESPONSE 18).
            val finalFeaturedVideos = rotateByArtist(videosPool, maxPerArtist = 1, target = 8)
            // Community has no artist id (so no rotateByArtist recent-avoidance): shuffle, then prefer the
            // playlists NOT shown on the previous load so a pull-to-refresh turns the 8-of-16 row over fully.
            val communityShuffled = communityPool.shuffled(Random(System.nanoTime()))
            val finalFeaturedPlaylists = (
                communityShuffled.filterNot { it.id in recentCommunityIds } +
                    communityShuffled.filter { it.id in recentCommunityIds }
                ).take(8)
            recentCommunityIds = finalFeaturedPlaylists.map { it.id }.toSet()
            val featuredAlbumsAreZemer = finalFeaturedAlbums.isNotEmpty()
            val featuredPlaylistsAreZemer = finalFeaturedPlaylists.isNotEmpty()
            val finalKeepListening = rotateByArtist(
                keepListening.filter { it.isAllowed() },
                maxPerArtist = 1,
                target = 24,
            )
            val finalForgotten = rotateByArtist(
                forgotten.filter { song -> song.isAllowed() },
                maxPerArtist = 1,
                target = 20,
            )

            // Publish the FULL filtered pool for each "See all" screen, but LED BY the items the Home row is
            // currently showing, in the row's order — so tapping the arrow continues from exactly what you
            // were looking at, then the rest of the pool. (De-duped by id; the row is a subset of the pool.)
            fun <T> displayedFirst(displayed: List<T>, pool: List<T>, idOf: (T) -> String): List<T> {
                val shown = displayed.mapTo(HashSet()) { idOf(it) }
                return displayed + pool.filterNot { idOf(it) in shown }
            }
            val keepListeningAllowed = keepListening.filter { it.isAllowed() }
            val forgottenAllowed = forgotten.filter { it.isAllowed() }
            HomeSeeAllStore.publish(
                HomeSeeAllData(
                    featuredAlbums = displayedFirst(finalFeaturedAlbums, albumsPool) { it.id },
                    featuredArtists = displayedFirst(finalFeaturedArtists, artistsPool) { it.id },
                    featuredVideos = displayedFirst(finalFeaturedVideos, videosPool) { it.id },
                    featuredPlaylists = displayedFirst(finalFeaturedPlaylists, communityPool) { it.id },
                    keepListening = displayedFirst(finalKeepListening, keepListeningAllowed) { it.id },
                    forgottenFavorites = displayedFirst(finalForgotten, forgottenAllowed) { it.id },
                    quickPicks = displayedFirst(displayedQuick, filteredQuick) { it.id },
                    featuredAlbumsAreZemer = featuredAlbumsAreZemer,
                    featuredPlaylistsAreZemer = featuredPlaylistsAreZemer,
                ),
            )
            val isNewUser = finalQuick.isEmpty() && keepListening.isEmpty()

            val usedArtistIds = mutableSetOf<String>()
            fun collectSongArtists(items: List<Song>) {
                items.forEach { song ->
                    song.artists.forEach { artist -> artist.id?.let(usedArtistIds::add) }
                }
            }

            fun collectSongItems(items: List<SongItem>) {
                items.forEach { item ->
                    item.artists?.forEach { artist -> artist.id?.let(usedArtistIds::add) }
                }
            }

            fun collectAlbumItems(items: List<AlbumItem>) {
                items.forEach { album ->
                    album.artists?.forEach { artist -> artist.id?.let(usedArtistIds::add) }
                }
            }

            fun collectLocalAlbums(items: List<Album>) {
                items.forEach { album ->
                    album.artists.forEach { artist -> usedArtistIds.add(artist.id) }
                }
            }

            fun collectArtistItems(items: List<ArtistItem>) {
                items.forEach { artist -> artist.id?.let(usedArtistIds::add) }
            }

            fun collectLocalArtists(items: List<Artist>) {
                items.forEach { artist -> usedArtistIds.add(artist.id) }
            }

            fun collectPlaylistItems(items: List<PlaylistItem>) {
                items.mapNotNull { it.author?.id }.forEach(usedArtistIds::add)
            }

            collectSongArtists(finalQuick)
            collectPlaylistItems(finalFeaturedPlaylists)
            collectAlbumItems(finalFeaturedAlbums)
            collectArtistItems(finalFeaturedArtists)
            collectSongItems(finalFeaturedVideos)
            collectSongArtists(finalForgotten)
            collectSongArtists(finalKeepListening.filterIsInstance<Song>())
            collectLocalAlbums(finalKeepListening.filterIsInstance<Album>())
            collectLocalArtists(finalKeepListening.filterIsInstance<Artist>())

            context.dataStore.edit { prefs ->
                val buffer = LinkedHashSet<String>()
                buffer.addAll(recentArtistIds.takeLast(60))
                buffer.addAll(usedArtistIds)
                val trimmed = buffer.toList().takeLast(60)
                prefs[HomeRecentArtistsKey] = trimmed.joinToString(",")
            }

            Timber.d(
                "HomeViewModel: load -> featuredArtists=%d playlists=%d albums=%d videos=%d quick=%d zemerAlbums=%b",
                finalFeaturedArtists.size,
                finalFeaturedPlaylists.size,
                finalFeaturedAlbums.size,
                finalFeaturedVideos.size,
                finalQuick.size,
                featuredAlbumsAreZemer,
            )

            Timber.d("HomeViewModel: Updating final UI state at +${System.currentTimeMillis() - loadStartTime}ms")
            uiState.update {
                it.copy(
                    isLoading = false,
                    isRefreshing = false,
                    isNewUser = isNewUser,
                    quickPicks = displayedQuick,
                    featuredPlaylists = finalFeaturedPlaylists,
                    keepListening = finalKeepListening,
                    forgottenFavorites = finalForgotten,
                    featuredAlbums = finalFeaturedAlbums,
                    featuredArtists = finalFeaturedArtists,
                    featuredVideos = finalFeaturedVideos,
                    featuredAlbumsAreZemer = featuredAlbumsAreZemer,
                    featuredPlaylistsAreZemer = featuredPlaylistsAreZemer,
                )
            }
            hasLoadedOnce = true
            Timber.d("HomeViewModel: load() COMPLETE in ${System.currentTimeMillis() - loadStartTime}ms")

            // Save local data to cache for instant load next time
            saveCachedLocalData(finalQuick, finalForgotten, finalKeepListening)
        } catch (e: java.util.concurrent.CancellationException) {
            throw e
        } catch (e: Exception) {
            reportException(e)
        } finally {
            uiState.update { it.copy(isLoading = false) }
        }
    }

    /**
     * "Radio mode": a corpus-native, whitelist-pure shuffle station over the whole catalog (Zemer
     * `/radio?kind=shuffle`), replacing the old InnerTube lucky-item radio. Endless via the queue's token.
     */
    fun shuffleRadioQueue(): Queue =
        ZemerRadioQueue(
            kind = "shuffle",
            seed = null,
            context = context,
        )

    fun refresh() {
        if (uiState.value.isRefreshing) return
        viewModelScope.launch(Dispatchers.IO) {
            uiState.update { it.copy(isRefreshing = true) }
            try {
                load(force = true)
            } catch (e: java.util.concurrent.CancellationException) {
                throw e
            } catch (e: Exception) {
                reportException(e)
            } finally {
                uiState.update { it.copy(isRefreshing = false) }
            }
        }
    }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val initStart = System.currentTimeMillis()
            Timber.d("HomeViewModel: init START")

            // Load cached local data instantly for fast startup
            val cacheStart = System.currentTimeMillis()
            val (cachedQuick, cachedForgotten, cachedKeepListening) = loadCachedLocalData()
            Timber.d("HomeViewModel: loadCachedLocalData took ${System.currentTimeMillis() - cacheStart}ms")
            if (cachedQuick.isNotEmpty() || cachedForgotten.isNotEmpty() || cachedKeepListening.isNotEmpty()) {
                uiState.update {
                    it.copy(
                        quickPicks = cachedQuick,
                        forgottenFavorites = cachedForgotten,
                        keepListening = cachedKeepListening,
                        isNewUser = cachedQuick.isEmpty() && cachedKeepListening.isEmpty()
                    )
                }
                Timber.d("HomeViewModel: Loaded cached data instantly - quick=${cachedQuick.size}, forgotten=${cachedForgotten.size}, keep=${cachedKeepListening.size}")
            }

            context.dataStore.data
                .map { it[InnerTubeCookieKey] }
                .distinctUntilChanged()
                .first()

            val onboardingComplete = context.dataStore.getSuspend(OnboardingCompleteKey, false)
            if (!onboardingComplete) {
                context.dataStore.data
                    .map { it[OnboardingCompleteKey] == true }
                    .distinctUntilChanged()
                    .first { it }
            }

            val isSyncEnabled = context.dataStore.getSuspend(YtmSyncKey, true)

            Timber.d("HomeViewModel: init ready to load, elapsed ${System.currentTimeMillis() - initStart}ms")
            val loadStart = System.currentTimeMillis()
            runCatching { load(force = true) }.onFailure { reportException(it) }
            Timber.d("HomeViewModel: load() completed in ${System.currentTimeMillis() - loadStart}ms, total init ${System.currentTimeMillis() - initStart}ms")

            if (isSyncEnabled) {
                viewModelScope.launch(Dispatchers.IO) {
                    syncUtils.syncLikedSongs()
                    syncUtils.syncLibrarySongs()
                    syncUtils.syncUploadedSongs()
                    syncUtils.syncLikedAlbums()
                    syncUtils.syncUploadedAlbums()
                    syncUtils.syncArtistsSubscriptions()
                    syncUtils.syncSavedPlaylists()
                }
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            context.dataStore.data
                .map { it[InnerTubeCookieKey] }
                .collect { cookie ->
                    if (isProcessingAccountData) return@collect
                    isProcessingAccountData = true
                    try {
                        if (!cookie.isNullOrEmpty()) {
                            YouTube.cookie = cookie
                            YouTube.accountInfo().onSuccess { info ->
                                accountName.value = info.name
                                accountImageUrl.value = info.thumbnailUrl
                            }.onFailure {
                                reportException(it)
                            }
                        } else {
                            accountName.value = "Guest"
                            accountImageUrl.value = null
                        }
                    } finally {
                        isProcessingAccountData = false
                    }
                }
        }

        viewModelScope.launch(Dispatchers.IO) {
            quickPicksEnum.drop(1).collect {
                load(force = true)
            }
        }
    }
}
