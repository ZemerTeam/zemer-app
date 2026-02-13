package com.jtech.zemer.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.jtech.zemer.constants.ArtistProfilesCacheKey
import com.jtech.zemer.constants.ArtistProfilesCacheTimestampKey
import com.jtech.zemer.constants.HideExplicitKey
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
import com.jtech.zemer.utils.IsraeliArtistRegistry
import com.jtech.zemer.utils.ContentFilterConfig
import com.jtech.zemer.utils.ContentFilterState
import com.jtech.zemer.utils.SyncUtils
import com.jtech.zemer.utils.WhitelistCache
import com.jtech.zemer.utils.dataStore
import com.jtech.zemer.utils.getSuspend
import com.jtech.zemer.utils.reportException
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.filterExplicit
import com.metrolist.innertube.pages.ExplorePage
import com.metrolist.innertube.pages.HomePage
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext val context: Context,
    val database: MusicDatabase,
    val syncUtils: SyncUtils,
) : ViewModel() {
    private data class HomeArtistProfile(
        val id: String,
        val name: String,
        val isAmerican: Boolean?,
        val isIsraeli: Boolean?,
        val isFemale: Boolean?,
        val isFamous: Boolean?,
        val isKids: Boolean?,
        val isDJ: Boolean?,
        val isGroup: Boolean?,
    )

    data class HomeUiState(
        val isLoading: Boolean = false,
        val isRefreshing: Boolean = false,
        val isNewUser: Boolean = true,
        val quickPicks: List<Song> = emptyList(),
        val featuredPlaylists: List<PlaylistItem> = emptyList(),
        val trendingSongs: List<SongItem> = emptyList(),
        val keepListening: List<LocalItem> = emptyList(),
        val forgottenFavorites: List<Song> = emptyList(),
        val recentReleaseAlbums: List<AlbumItem> = emptyList(),
        val recentReleaseSongs: List<SongItem> = emptyList(),
        val featuredAlbums: List<AlbumItem> = emptyList(),
        val featuredArtists: List<ArtistItem> = emptyList(),
        val featuredVideos: List<SongItem> = emptyList(),
        val homePage: HomePage? = null,
        val explorePage: ExplorePage? = null,
    )

    val uiState = MutableStateFlow(HomeUiState())
    val accountName = MutableStateFlow("Guest")
    val accountImageUrl = MutableStateFlow<String?>(null)

    @Volatile
    private var hasLoadedOnce = false
    @Volatile
    private var isProcessingAccountData = false
    private val isLoadingMore = MutableStateFlow(false)
    @Volatile
    private var homeArtistProfilesCache: List<HomeArtistProfile> = emptyList()

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
                database.getSongsByIds(keepListeningIds).map { it as LocalItem }
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
                    database.events().first().map { it.song }.distinctBy { it.id }.take(50)
                }
                withFallback
            }

            QuickPicks.LAST_LISTEN -> {
                val events = database.events().first()
                val song = events.firstOrNull()?.song
                val raw = when {
                    song != null && database.hasRelatedSongs(song.id) -> database.getRelatedSongs(song.id).first()
                    else -> emptyList()
                }
                val withFallback = raw.ifEmpty {
                    events.map { it.song }.distinctBy { it.id }.take(50)
                }
                withFallback
            }
        }

        val distinct = picks
            .shuffled()
            .distinctBy { it.artists.firstOrNull()?.id ?: it.id }
            .take(20)
            .ifEmpty {
                val historyFallback = database.events().first().map { it.song }.distinctBy { it.id }.take(20)
                Timber.d("HomeViewModel: Quick picks fallback from history - ${historyFallback.size} songs")
                historyFallback
            }
        Timber.d("HomeViewModel: Quick picks loaded - ${distinct.size} songs (mode=$mode, whitelistPresent=$whitelistPresent)")
        return distinct
    }

    private suspend fun loadFeaturedPlaylists(
        hideExplicit: Boolean,
        artistProfiles: List<HomeArtistProfile>,
        allowFemale: Boolean,
        random: Random,
        usedArtists: MutableSet<String>,
        recentExclusions: Set<String>,
    ): List<PlaylistItem> {
        val start = System.currentTimeMillis()
        val selectedArtists = selectWeightedArtists(
            artistProfiles,
            allowFemale,
            12,
            Random(random.nextLong()),
            usedArtists,
            recentExclusions,
        )
        if (selectedArtists.isEmpty()) return emptyList()

        val playlistItems = coroutineScope {
            selectedArtists.map { profile ->
                async {
                    YouTube.artist(profile.id).getOrNull()?.sections
                        ?.flatMap { it.items }
                        ?.filterExplicit(hideExplicit)
                        ?.filterIsInstance<PlaylistItem>()
                        ?.filter { item ->
                            item.title.contains("playlist", ignoreCase = true) ||
                                item.title.contains("by", ignoreCase = true) ||
                                item.title.contains("mix", ignoreCase = true) ||
                                item.author?.name?.isNotBlank() == true
                        }
                }
            }.awaitAll().filterNotNull().flatten()
        }
        Timber.d("NET: loadFeaturedPlaylists (${selectedArtists.size} artists) took ${System.currentTimeMillis() - start}ms")

        return playlistItems
            .shuffled()
            .distinctBy { it.id }
            .take(8)
    }

    private suspend fun loadTrendingSongs(filters: ContentFilterConfig, hideExplicit: Boolean): List<SongItem> {
        val start = System.currentTimeMillis()
        val allowedIds = WhitelistCache.allowedEntries(database, filters).map { it.artistId }.toSet()
        val charts = YouTube.getChartsPage().getOrNull()
        Timber.d("NET: YouTube.getChartsPage() took ${System.currentTimeMillis() - start}ms")
        if (charts == null) return emptyList()
        val allSongs = charts.sections
            .flatMap { it.items }
            .filterIsInstance<SongItem>()
            .filterExplicit(hideExplicit)
        val whitelisted =
            if (allowedIds.isEmpty()) allSongs
            else allSongs.filter { item -> item.artists?.any { it.id in allowedIds } == true }

        val chosen = when {
            whitelisted.isNotEmpty() -> whitelisted
            else -> allSongs
        }
        return chosen.distinctBy { it.id }.take(30)
    }

    private suspend fun loadKeepListening(): List<LocalItem> {
        val toTimeStamp = System.currentTimeMillis()
        val fromTimeStamp = toTimeStamp - 86400000 * 7 * 2
        val historySongs = database.events().first().map { it.song }.distinctBy { it.id }.take(40)

        val keepListeningSongs = runCatching {
            database.mostPlayedSongs(
                fromTimeStamp = fromTimeStamp,
                toTimeStamp = toTimeStamp,
                limit = 25,
                offset = 0,
            ).first()
        }.getOrDefault(emptyList()).ifEmpty { historySongs }

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

    private suspend fun loadHomePage(hideExplicit: Boolean): HomePage? {
        val start = System.currentTimeMillis()
        val homeResult = YouTube.home()
        Timber.d("NET: YouTube.home() took ${System.currentTimeMillis() - start}ms")
        if (homeResult.isSuccess) {
            val page = homeResult.getOrNull()!!
            return page.copy(
                sections = page.sections.mapNotNull { section ->
                    val filteredItems = section.items.filterExplicit(hideExplicit)
                    if (filteredItems.isEmpty()) null else section.copy(items = filteredItems)
                }
            )
        } else {
            homeResult.exceptionOrNull()?.let { reportException(it) }
        }
        return null
    }

    private suspend fun loadExplorePage(hideExplicit: Boolean): ExplorePage? {
        val start = System.currentTimeMillis()
        return YouTube.explore().also {
            Timber.d("NET: YouTube.explore() took ${System.currentTimeMillis() - start}ms")
        }.mapCatching { page ->
            val rawAlbums = page.newReleaseAlbums
            val finalAlbums = rawAlbums.filterExplicit(hideExplicit).filterIsInstance<AlbumItem>()
            page.copy(newReleaseAlbums = finalAlbums)
        }.getOrElse {
            reportException(it)
            null
        }
    }

    private suspend fun loadRecentReleases(hideExplicit: Boolean): Pair<List<AlbumItem>, List<SongItem>> {
        return runCatching {
            val filters = ContentFilterState.state.value
            val allowedIds = WhitelistCache.allowedEntries(database, filters).map { it.artistId }.toSet()
            val whitelistActive = filters.filtersEnabled && allowedIds.isNotEmpty()

            fun isAllowed(artists: List<com.metrolist.innertube.models.Artist>?): Boolean {
                if (!whitelistActive) return true
                return artists?.any { it.id in allowedIds } == true
            }

            val browseStart = System.currentTimeMillis()
            val browse = YouTube.browse(browseId = "FEmusic_new_releases", params = null).getOrNull()
            Timber.d("NET: YouTube.browse(new_releases) took ${System.currentTimeMillis() - browseStart}ms")
            val items = browse
                ?.filterExplicit(hideExplicit)
                ?.items
                ?.flatMap { it.items }
                .orEmpty()
            val albums = items
                .filterIsInstance<AlbumItem>()
                .filter { isAllowed(it.artists) }
            val songs = items
                .filterIsInstance<SongItem>()
                .filter { isAllowed(it.artists) }

            albums to songs
        }.getOrElse {
            Timber.w(it, "HomeViewModel: Failed to load recent releases")
            emptyList<AlbumItem>() to emptyList()
        }
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
            val snapshot = FirebaseFirestore.getInstance()
                .collection("artistsWhitelist")
                .get()
                .await()
            val fetchTime = System.currentTimeMillis() - firestoreStart

            val profiles = snapshot.documents.mapNotNull { doc ->
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
                    isDJ = doc.getBoolean("isDJ"),
                    isGroup = doc.getBoolean("isGroup"),
                )
            }

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
            val snapshot = FirebaseFirestore.getInstance()
                .collection("artistsWhitelist")
                .get()
                .await()
            Timber.d("NET: Firebase artistsWhitelist fetch took ${System.currentTimeMillis() - firestoreStart}ms (${snapshot.documents.size} docs)")

            val profiles = snapshot.documents.mapNotNull { doc ->
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
                    isDJ = doc.getBoolean("isDJ"),
                    isGroup = doc.getBoolean("isGroup"),
                )
            }
            homeArtistProfilesCache = profiles
            saveArtistProfilesToCache(profiles)
            profiles
        }.getOrElse {
            Timber.w(it, "HomeViewModel: Failed to load artist profiles")
            fallback
        }
    }

    private fun parseArtistProfilesCache(json: String): List<HomeArtistProfile> {
        return try {
            json.split("||").mapNotNull { entry ->
                val parts = entry.split("|")
                if (parts.size < 8) return@mapNotNull null
                HomeArtistProfile(
                    id = parts[0],
                    name = parts[1],
                    isAmerican = parts[2].toBooleanStrictOrNull(),
                    isIsraeli = parts[3].toBooleanStrictOrNull(),
                    isFemale = parts[4].toBooleanStrictOrNull(),
                    isFamous = parts[5].toBooleanStrictOrNull(),
                    isKids = parts[6].toBooleanStrictOrNull(),
                    isDJ = parts[7].toBooleanStrictOrNull(),
                    isGroup = parts.getOrNull(8)?.toBooleanStrictOrNull(),
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
                "${p.id}|${p.name}|${p.isAmerican}|${p.isIsraeli}|${p.isFemale}|${p.isFamous}|${p.isKids}|${p.isDJ}|${p.isGroup}"
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

    private fun SongItem.isBlocked(
        profileById: Map<String, HomeArtistProfile>,
        allowFemale: Boolean
    ): Boolean {
        val ids = this.artists?.mapNotNull { it.id }.orEmpty()
        val profiles = ids.mapNotNull { profileById[it] }
        if (!allowFemale && profiles.any { it.isFemale == true }) return true
        if (profiles.any { it.isAmerican != true }) return true
        if (profiles.any { it.isIsraeli == true }) return true
        if (profiles.any { it.isFamous != true }) return true
        return false
    }

    private fun AlbumItem.isBlocked(
        profileById: Map<String, HomeArtistProfile>,
        allowFemale: Boolean
    ): Boolean {
        val ids = this.artists?.mapNotNull { it.id }.orEmpty()
        val profiles = ids.mapNotNull { profileById[it] }
        if (!allowFemale && profiles.any { it.isFemale == true }) return true
        if (profiles.any { it.isAmerican != true }) return true
        if (profiles.any { it.isIsraeli == true }) return true
        if (profiles.any { it.isFamous != true }) return true
        return false
    }

    private fun ArtistItem.isBlocked(
        profileById: Map<String, HomeArtistProfile>,
        allowFemale: Boolean
    ): Boolean {
        val profile = profileById[id]
        if (!allowFemale && profile?.isFemale == true) return true
        if (profile?.isAmerican != true) return true
        if (profile?.isIsraeli == true) return true
        if (profile?.isFamous != true) return true
        return false
    }

    private fun PlaylistItem.isBlocked(
        profileById: Map<String, HomeArtistProfile>,
        allowFemale: Boolean
    ): Boolean {
        val authorId = author?.id
        if (authorId != null) {
            val profile = profileById[authorId]
            if (!allowFemale && profile?.isFemale == true) return true
            if (profile?.isAmerican != true) return true
            if (profile?.isIsraeli == true) return true
            if (profile?.isFamous != true) return true
        }
        return false
    }

    private fun selectWeightedArtists(
        profiles: List<HomeArtistProfile>,
        allowFemale: Boolean,
        targetCount: Int,
        random: Random,
        used: MutableSet<String>? = null,
        recentExclusions: Set<String> = emptySet(),
    ): List<HomeArtistProfile> {
        if (targetCount <= 0) return emptyList()
        val base = profiles
            .filter { it.isAmerican == true }
            .filter { it.isKids != true }
            .filter { it.isIsraeli != true }
            .filter { it.isFamous != false }
            .filter { allowFemale || it.isFemale != true }
            .filter { used?.contains(it.id) != true }
        if (base.isEmpty()) return emptyList()

        fun pickFrom(pool: List<HomeArtistProfile>, remainingTarget: Int): List<HomeArtistProfile> {
            if (pool.isEmpty() || remainingTarget <= 0) return emptyList()
            val bucketRng = Random(random.nextLong())
            val buckets = listOf(
                0.50f to pool.filter { it.isFamous == true }.shuffled(Random(bucketRng.nextLong())),
                0.25f to pool.filter { it.isDJ == true }.shuffled(Random(bucketRng.nextLong())),
                0.15f to pool.filter { it.isGroup == true }.shuffled(Random(bucketRng.nextLong())),
                0.10f to pool.shuffled(Random(bucketRng.nextLong())),
            )

            val chosen = mutableListOf<HomeArtistProfile>()
            val seen = mutableSetOf<String>()

            buckets.forEach { (ratio, bucket) ->
                if (bucket.isEmpty()) return@forEach
                val goal = (remainingTarget * ratio).toInt().coerceAtLeast(1)
                bucket.forEach { candidate ->
                    if (chosen.size >= remainingTarget) return@forEach
                    if (seen.add(candidate.id)) {
                        chosen += candidate
                        used?.add(candidate.id)
                        if (chosen.size >= goal) return@forEach
                    }
                }
            }

            if (chosen.size < remainingTarget) {
                pool.shuffled(Random(bucketRng.nextLong())).forEach { candidate ->
                    if (chosen.size >= remainingTarget) return@forEach
                    if (seen.add(candidate.id)) {
                        chosen += candidate
                        used?.add(candidate.id)
                    }
                }
            }

            return chosen.take(remainingTarget)
        }

        val primaryPool = base.filterNot { it.id in recentExclusions }
        val primaryPick = pickFrom(primaryPool, targetCount)
        if (primaryPick.size >= targetCount || recentExclusions.isEmpty()) return primaryPick.take(targetCount)

        val remaining = targetCount - primaryPick.size
        val fallbackPool = base.filter { candidate -> candidate.id !in primaryPick.map { it.id } }
        return (primaryPick + pickFrom(fallbackPool, remaining)).take(targetCount)
    }

    private suspend fun loadFeaturedContent(
        hideExplicit: Boolean,
        artistProfiles: List<HomeArtistProfile>,
        allowFemale: Boolean,
        random: Random,
        usedArtists: MutableSet<String>,
        recentExclusions: Set<String>,
    ): Triple<List<AlbumItem>, List<ArtistItem>, List<SongItem>> {
        val start = System.currentTimeMillis()
        val weightedArtists = selectWeightedArtists(
            artistProfiles,
            allowFemale,
            15,
            Random(random.nextLong()),
            usedArtists,
            recentExclusions,
        )
        if (weightedArtists.isEmpty()) return Triple(emptyList(), emptyList(), emptyList())

        val albums = mutableListOf<AlbumItem>()
        val artists = mutableListOf<ArtistItem>()
        val videos = mutableListOf<SongItem>()

        coroutineScope {
            val deferredArtistPages = weightedArtists.take(15).map { profile ->
                async {
                    YouTube.artist(profile.id).getOrNull()
                }
            }
            val artistPages = deferredArtistPages.awaitAll().filterNotNull()
            Timber.d("NET: loadFeaturedContent fetched ${artistPages.size}/${weightedArtists.size} artists in ${System.currentTimeMillis() - start}ms")
            artistPages.forEach { artistPage ->
                artists.add(artistPage.artist)
                val artistAlbums = artistPage.sections
                    .flatMap { it.items }
                    .filterExplicit(hideExplicit)
                    .filterIsInstance<AlbumItem>()
                    .take(2)
                albums.addAll(artistAlbums)

                val artistVideos = artistPage.sections
                    .filter { section ->
                        section.title.contains("video", ignoreCase = true) ||
                            section.title.contains("short", ignoreCase = true)
                    }
                    .flatMap { section ->
                        section.items.filterIsInstance<SongItem>()
                    }
                videos.addAll(artistVideos)
            }
        }

        return Triple(
            albums.shuffled().take(20),
            artists.shuffled().take(20),
            videos.distinctBy { it.id }.shuffled().take(20)
        )
    }

    private suspend fun loadWhitelistHome(
        hideExplicit: Boolean,
        artistProfiles: List<HomeArtistProfile>,
        allowFemale: Boolean,
        random: Random,
        recentExclusions: Set<String>,
    ): HomePage? {
        val start = System.currentTimeMillis()
        val filters = ContentFilterState.state.value
        val allowed = WhitelistCache.allowedEntries(database, filters)
        if (allowed.isEmpty()) return null
        val profileById = artistProfiles.associateBy { it.id }
        val candidates = allowed.mapNotNull { profileById[it.artistId] }
        val selected = selectWeightedArtists(candidates, allowFemale, 10, Random(random.nextLong()), recentExclusions = recentExclusions)
        val sections = mutableListOf<HomePage.Section>()
        val allSongs = mutableListOf<SongItem>()
        val allAlbums = mutableListOf<AlbumItem>()
        val allVideos = mutableListOf<SongItem>()

        coroutineScope {
            val pages = selected.map { entry ->
                async { YouTube.artist(entry.id).getOrNull() }
            }.awaitAll().filterNotNull()
            Timber.d("NET: loadWhitelistHome fetched ${pages.size}/${selected.size} artists in ${System.currentTimeMillis() - start}ms")

            pages.forEach { artistPage ->
                val songs = artistPage.sections.flatMap { it.items }
                    .filterExplicit(hideExplicit)
                    .filterIsInstance<SongItem>()
                    .distinctBy { it.id }
                    .take(10)
                allSongs.addAll(songs)

                val albums = artistPage.sections.flatMap { it.items }
                    .filterExplicit(hideExplicit)
                    .filterIsInstance<AlbumItem>()
                    .distinctBy { it.id }
                    .take(5)
                allAlbums.addAll(albums)

                val videos = artistPage.sections
                    .filter { section ->
                        section.title.contains("video", ignoreCase = true) ||
                            section.title.contains("short", ignoreCase = true)
                    }
                    .flatMap { section ->
                        section.items.filterIsInstance<SongItem>()
                    }
                allVideos.addAll(videos)
            }
        }

        allSongs.distinctBy { it.id }.take(30).also { songs ->
            if (songs.isNotEmpty()) {
                sections.add(
                    HomePage.Section(
                        title = "Songs",
                        label = null,
                        thumbnail = songs.firstOrNull()?.thumbnail,
                        items = songs,
                        endpoint = null,
                    )
                )
            }
        }

        allAlbums.distinctBy { it.id }.take(20).also { albums ->
            if (albums.isNotEmpty()) {
                sections.add(
                    HomePage.Section(
                        title = "Albums",
                        label = null,
                        thumbnail = albums.firstOrNull()?.thumbnail,
                        items = albums,
                        endpoint = null,
                    )
                )
            }
        }

        allVideos.distinctBy { it.id }.take(20).also { videos ->
            if (videos.isNotEmpty()) {
                sections.add(
                    HomePage.Section(
                        title = "Videos",
                        label = null,
                        thumbnail = videos.firstOrNull()?.thumbnail,
                        items = videos,
                        endpoint = null,
                    )
                )
            }
        }

        return HomePage(
            chips = null,
            sections = sections,
            continuation = null
        )
    }

    private suspend fun loadWhitelistExplore(
        hideExplicit: Boolean,
        artistProfiles: List<HomeArtistProfile>,
        allowFemale: Boolean,
        random: Random,
        recentExclusions: Set<String>,
    ): ExplorePage? {
        val start = System.currentTimeMillis()
        val filters = ContentFilterState.state.value
        val allowed = WhitelistCache.allowedEntries(database, filters)
        if (allowed.isEmpty()) return null

        val profileById = artistProfiles.associateBy { it.id }
        val candidates = allowed.mapNotNull { profileById[it.artistId] }
        val selected = selectWeightedArtists(
            candidates,
            allowFemale,
            12,
            Random(random.nextLong()),
            recentExclusions = recentExclusions,
        )
        val albums = mutableListOf<AlbumItem>()

        coroutineScope {
            val pages = selected.map { entry ->
                async { YouTube.artist(entry.id).getOrNull() }
            }.awaitAll().filterNotNull()
            Timber.d("NET: loadWhitelistExplore fetched ${pages.size}/${selected.size} artists in ${System.currentTimeMillis() - start}ms")

            pages.forEach { artistPage ->
                albums += artistPage.sections.flatMap { it.items }
                    .filterExplicit(hideExplicit)
                    .filterIsInstance<AlbumItem>()
            }
        }

        return ExplorePage(
            newReleaseAlbums = albums.distinctBy { it.id },
            moodAndGenres = emptyList()
        )
    }

    private suspend fun seedQuickPicksFromHomePage(page: HomePage, hideExplicit: Boolean, existing: List<Song>): List<Song> {
        if (existing.isNotEmpty()) return existing
        val candidateSongs = page.sections
            .flatMap { it.items }
            .filterExplicit(hideExplicit)
            .filterIsInstance<SongItem>()
            .distinctBy { it.id }
            .take(40)

        if (candidateSongs.isEmpty()) return existing

        candidateSongs.forEach { song ->
            database.insert(song.toMediaMetadata())
        }

        val seeded = database.getSongsByIds(candidateSongs.map { it.id })
        return if (seeded.isNotEmpty()) seeded.take(20) else existing
    }

    private fun selectWeightedSongs(
        songs: List<Song>,
        profileById: Map<String, HomeArtistProfile>,
        allowFemale: Boolean,
        targetCount: Int,
        random: Random
    ): List<Song> {
        if (songs.isEmpty() || targetCount <= 0) return emptyList()

        val base = songs.filter { song ->
            val artistIds = song.artists.mapNotNull { it.id }
            val profiles = artistIds.mapNotNull { profileById[it] }
            if (profiles.isEmpty()) return@filter false
            if (!allowFemale && profiles.any { it.isFemale == true }) return@filter false
            if (profiles.any { it.isAmerican != true }) return@filter false
            if (profiles.any { it.isIsraeli == true }) return@filter false
            if (profiles.any { it.isFamous != true }) return@filter false
            true
        }
        if (base.isEmpty()) return emptyList()

        val rng = Random(random.nextLong())
        val byProfile = base.groupBy { song ->
            song.artists.mapNotNull { it.id }.firstNotNullOfOrNull { profileById[it] }
        }

        val famous = byProfile.filterKeys { it?.isFamous == true }.values.flatten().shuffled(Random(rng.nextLong()))
        val dj = byProfile.filterKeys { it?.isDJ == true }.values.flatten().shuffled(Random(rng.nextLong()))
        val group = byProfile.filterKeys { it?.isGroup == true }.values.flatten().shuffled(Random(rng.nextLong()))
        val buckets = listOf(
            0.50f to famous,
            0.25f to dj,
            0.15f to group,
            0.10f to base.shuffled(Random(rng.nextLong())),
        )

        val chosen = mutableListOf<Song>()
        val seenSong = mutableSetOf<String>()

        buckets.forEach { (ratio, bucket) ->
            if (bucket.isEmpty()) return@forEach
            val goal = (targetCount * ratio).toInt().coerceAtLeast(1)
            bucket.forEach { song ->
                if (chosen.size >= targetCount) return@forEach
                if (seenSong.add(song.id)) {
                    chosen += song
                    if (chosen.size >= goal) return@forEach
                }
            }
        }

        if (chosen.size < targetCount) {
            base.shuffled(Random(rng.nextLong())).forEach { song ->
                if (chosen.size >= targetCount) return@forEach
                if (seenSong.add(song.id)) {
                    chosen += song
                }
            }
        }

        return chosen.take(targetCount)
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
            val hideExplicit = context.dataStore.getSuspend(HideExplicitKey, false)

            // Start LOCAL data loading immediately (parallel with prep work)
            val parallelStartTime = System.currentTimeMillis()
            Timber.d("HomeViewModel: Starting parallel fetch at +${parallelStartTime - loadStartTime}ms")
            val quickDeferred = viewModelScope.async(Dispatchers.IO) { loadQuickPicks() }
            val forgottenDeferred = viewModelScope.async(Dispatchers.IO) {
                database.forgottenFavorites().first().shuffled().take(20)
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
                uiState.update {
                    it.copy(
                        quickPicks = quick.shuffled(Random(System.nanoTime())),
                        forgottenFavorites = forgotten,
                        keepListening = keepListening,
                        isNewUser = quick.isEmpty() && keepListening.isEmpty()
                    )
                }
                Timber.d("HomeViewModel: Showing local data first - quick=${quick.size}, forgotten=${forgotten.size}, keep=${keepListening.size}")
            }

            // Await prep work (IsraeliArtistRegistry, WhitelistCache, artistProfiles)
            Timber.d("HomeViewModel: Awaiting prep work at +${System.currentTimeMillis() - loadStartTime}ms")
            val artistProfiles = prepDeferred.await()
            Timber.d("HomeViewModel: Prep work done at +${System.currentTimeMillis() - loadStartTime}ms")

            val allowedEntries = WhitelistCache.allowedEntries(database, filters)
            val allowedIds = allowedEntries.map { it.artistId }.toSet()
            val useWhitelist = filters.filtersEnabled && allowedEntries.isNotEmpty()
            val effectiveFilters = if (useWhitelist) filters else filters.copy(filtersEnabled = false)
            val eligibleProfiles = artistProfiles
                .filter { it.isAmerican == true }
                .filter { it.isIsraeli != true }
                .filter { it.isFamous == true }
            val profileById = artistProfiles.associateBy { it.id }
            val baseProfiles = if (useWhitelist) {
                eligibleProfiles.filter { it.id in allowedIds }
            } else {
                eligibleProfiles
            }
            val sharedUsedArtists = recentArtistIds.toMutableSet()
            val selectionRandom = Random(System.nanoTime())

            // Now start NETWORK calls (after prep work is done)
            Timber.d("HomeViewModel: Starting NETWORK fetch at +${System.currentTimeMillis() - loadStartTime}ms")
            val trendingDeferred = viewModelScope.async(Dispatchers.IO) { loadTrendingSongs(effectiveFilters, hideExplicit) }
            val homeDeferred = viewModelScope.async(Dispatchers.IO) {
                if (useWhitelist) {
                    loadWhitelistHome(
                        hideExplicit,
                        baseProfiles,
                        allowFemale,
                        selectionRandom,
                        recentArtistIds.toSet(),
                    )
                } else loadHomePage(hideExplicit)
            }
            val exploreDeferred = viewModelScope.async(Dispatchers.IO) {
                if (useWhitelist) {
                    loadWhitelistExplore(
                        hideExplicit,
                        baseProfiles,
                        allowFemale,
                        selectionRandom,
                        recentArtistIds.toSet(),
                    )
                } else loadExplorePage(hideExplicit)
            }
            val recentReleasesDeferred = viewModelScope.async(Dispatchers.IO) { loadRecentReleases(hideExplicit) }

            val trendingSongs = trendingDeferred.await()
            val home = homeDeferred.await()
            val explore = exploreDeferred.await()
            val (recentAlbums, recentSongs) = recentReleasesDeferred.await()
            Timber.d("HomeViewModel: NETWORK data ready at +${System.currentTimeMillis() - loadStartTime}ms")

            // Featured playlists loaded after (uses sharedUsedArtists which is mutable)
            val featuredPlaylists = loadFeaturedPlaylists(
                hideExplicit,
                baseProfiles,
                allowFemale,
                selectionRandom,
                sharedUsedArtists,
                recentArtistIds.toSet(),
            )

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

            fun HomePage?.filtered(): HomePage? {
                if (this == null) return null
                val filteredSections = sections.mapNotNull { section ->
                    val filteredItems = section.items.mapNotNull { item ->
                        when (item) {
                            is SongItem -> item.takeUnless { it.isBlocked(profileById, allowFemale) }
                            is AlbumItem -> item.takeUnless { it.isBlocked(profileById, allowFemale) }
                            is ArtistItem -> item.takeUnless { it.isBlocked(profileById, allowFemale) }
                            is PlaylistItem -> item.takeUnless { it.isBlocked(profileById, allowFemale) }
                        }
                    }
                    if (filteredItems.isEmpty()) return@mapNotNull null
                    val rotated = rotateByArtist(filteredItems, maxPerArtist = 1, target = filteredItems.size)
                    if (rotated.isEmpty()) null else section.copy(items = rotated)
                }
                if (filteredSections.isEmpty()) return null
                return copy(sections = filteredSections)
            }

            fun ExplorePage?.filtered(): ExplorePage? {
                if (this == null) return null
                val albums = newReleaseAlbums.filterIsInstance<AlbumItem>()
                    .filter { !it.isBlocked(profileById, allowFemale) }
                return copy(newReleaseAlbums = albums)
            }

            val filteredHome = home.filtered()
            val filteredExplore = explore.filtered()
            val filteredRecentAlbums = recentAlbums.filter { it.isAllowed() }
            val filteredRecentSongs = recentSongs.filter { it.isAllowed() }

            val quickSeeded = if (filteredHome != null) {
                seedQuickPicksFromHomePage(filteredHome, hideExplicit, quick)
            } else quick

            val filteredQuick = quickSeeded.filter { song -> song.isAllowed() }
            val fallbackQuick = runCatching {
                database.allSongs().first().filter { it.isAllowed() }.take(30)
            }.getOrDefault(emptyList())
            val freshQuick = filteredQuick.filter { song -> song.artistIds().none { it in recentArtistIds } }
            val quickPool = freshQuick.ifEmpty { filteredQuick }
            val recentAwareQuick = rotateByArtist(quickPool.ifEmpty { fallbackQuick }, 1, 20)
            Timber.d("HomeViewModel: quickPicks flow - quick=${quick.size}, filtered=${filteredQuick.size}, rotated=${recentAwareQuick.size}")
            sharedUsedArtists.addAll(recentAwareQuick.flatMap { it.artistIds() })

            val featuredTriple = loadFeaturedContent(
                hideExplicit,
                baseProfiles,
                allowFemale,
                selectionRandom,
                sharedUsedArtists,
                recentArtistIds.toSet(),
            )

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
            val finalTrending = rotateByArtist(
                trendingSongs.filter { song -> song.isAllowed() },
                maxPerArtist = 1,
                target = 30,
            )
            val finalFeaturedPlaylists = rotateByArtist(
                featuredPlaylists.filter { it.isAllowed() },
                maxPerArtist = 1,
                target = 8,
            )
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
            val finalRecentAlbums = filteredRecentAlbums
                .let { rotateByArtist(it, maxPerArtist = 1, target = 16) }
            val finalRecentSongs = filteredRecentSongs
                .let { rotateByArtist(it, maxPerArtist = 1, target = 30) }
            val finalFeaturedAlbums = featuredTriple.first.filter { it.isAllowed() }
                .let { rotateByArtist(it, maxPerArtist = 1, target = 20) }
            val finalFeaturedArtists = featuredTriple.second.filter { it.isAllowed() }
                .let { rotateByArtist(it, maxPerArtist = 1, target = 20) }
            val finalFeaturedVideos = featuredTriple.third.filter { it.isAllowed() }
                .let { rotateByArtist(it, maxPerArtist = 1, target = 20) }
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

            fun collectHomeSections(sections: List<HomePage.Section>) {
                sections.forEach { section ->
                    section.items.forEach { item ->
                        when (item) {
                            is SongItem -> collectSongItems(listOf(item))
                            is AlbumItem -> collectAlbumItems(listOf(item))
                            is ArtistItem -> collectArtistItems(listOf(item))
                            is PlaylistItem -> collectPlaylistItems(listOf(item))
                        }
                    }
                }
            }

            collectSongArtists(finalQuick)
            collectSongItems(finalTrending)
            collectPlaylistItems(finalFeaturedPlaylists)
            collectAlbumItems(finalFeaturedAlbums)
            collectArtistItems(finalFeaturedArtists)
            collectSongItems(finalFeaturedVideos)
            collectAlbumItems(finalRecentAlbums)
            collectSongItems(finalRecentSongs)
            collectSongArtists(finalForgotten)
            collectSongArtists(finalKeepListening.filterIsInstance<Song>())
            collectLocalAlbums(finalKeepListening.filterIsInstance<Album>())
            collectLocalArtists(finalKeepListening.filterIsInstance<Artist>())
            collectHomeSections(filteredHome?.sections.orEmpty())
            collectAlbumItems(filteredExplore?.newReleaseAlbums.orEmpty())

            context.dataStore.edit { prefs ->
                val buffer = LinkedHashSet<String>()
                buffer.addAll(recentArtistIds.takeLast(60))
                buffer.addAll(usedArtistIds)
                val trimmed = buffer.toList().takeLast(60)
                prefs[HomeRecentArtistsKey] = trimmed.joinToString(",")
            }

            Timber.d(
                "HomeViewModel: load -> featuredArtists=%d playlists=%d albums=%d videos=%d quick=%d",
                featuredTriple.second.size,
                featuredPlaylists.size,
                featuredTriple.first.size,
                featuredTriple.third.size,
                finalQuick.size
            )

            Timber.d("HomeViewModel: Updating final UI state at +${System.currentTimeMillis() - loadStartTime}ms")
            uiState.update {
                it.copy(
                    isLoading = false,
                    isRefreshing = false,
                    isNewUser = isNewUser,
                    quickPicks = finalQuick.shuffled(Random(System.nanoTime())),
                    trendingSongs = finalTrending,
                    featuredPlaylists = finalFeaturedPlaylists,
                    keepListening = finalKeepListening,
                    forgottenFavorites = finalForgotten,
                    recentReleaseAlbums = finalRecentAlbums,
                    recentReleaseSongs = finalRecentSongs,
                    featuredAlbums = finalFeaturedAlbums,
                    featuredArtists = finalFeaturedArtists,
                    featuredVideos = finalFeaturedVideos,
                    homePage = filteredHome,
                    explorePage = filteredExplore,
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

    fun loadMoreYouTubeItems(continuation: String?) {
        if (continuation == null || isLoadingMore.value) return
        if (ContentFilterState.state.value.filtersEnabled) return

        viewModelScope.launch(Dispatchers.IO) {
            val hideExplicit = context.dataStore.getSuspend(HideExplicitKey, false)
            val allowFemale = ContentFilterState.state.value.allowFemaleSingers
            val profileById = homeArtistProfilesCache.associateBy { it.id }
            isLoadingMore.value = true
            IsraeliArtistRegistry.ensureLoaded()
            val nextSections = YouTube.home(continuation).getOrNull()
            if (nextSections != null) {
                uiState.update { state ->
                    val existingSections = state.homePage?.sections.orEmpty()
                    val mergedSections = (existingSections + nextSections.sections).mapNotNull { section ->
                        val filteredItems = section.items
                            .filterExplicit(hideExplicit)
                            .filterNot { item ->
                                when (item) {
                                    is SongItem -> {
                                        val profiles = item.artists?.mapNotNull { profileById[it.id] }.orEmpty()
                                        val blockedByProfile = profiles.any { profile ->
                                            (!allowFemale && profile.isFemale == true) ||
                                                profile.isAmerican != true ||
                                                profile.isIsraeli == true ||
                                                profile.isFamous != true
                                        }
                                        blockedByProfile || item.artists?.any { IsraeliArtistRegistry.isIsraeli(it.id) } == true
                                    }

                                    is AlbumItem -> {
                                        val profiles = item.artists?.mapNotNull { profileById[it.id] }.orEmpty()
                                        val blockedByProfile = profiles.any { profile ->
                                            (!allowFemale && profile.isFemale == true) ||
                                                profile.isAmerican != true ||
                                                profile.isIsraeli == true ||
                                                profile.isFamous != true
                                        }
                                        blockedByProfile || item.artists?.any { IsraeliArtistRegistry.isIsraeli(it.id) } == true
                                    }

                                    is ArtistItem -> {
                                        val profile = profileById[item.id]
                                        val blockedByProfile = profile?.let {
                                            (!allowFemale && it.isFemale == true) ||
                                                it.isAmerican != true ||
                                                it.isIsraeli == true ||
                                                it.isFamous != true
                                        } == true
                                        blockedByProfile || IsraeliArtistRegistry.isIsraeli(item.id)
                                    }

                                    is PlaylistItem -> {
                                        val profile = profileById[item.author?.id]
                                        val blockedByProfile = profile?.let {
                                            (!allowFemale && it.isFemale == true) ||
                                                it.isAmerican != true ||
                                                it.isIsraeli == true ||
                                                it.isFamous != true
                                        } == true
                                        blockedByProfile || IsraeliArtistRegistry.isIsraeli(item.author?.id)
                                    }
                                }
                            }
                        if (filteredItems.isEmpty()) null else section.copy(items = filteredItems)
                    }
                    val updatedHome = (state.homePage ?: HomePage(null, emptyList(), null)).copy(
                        chips = state.homePage?.chips,
                        sections = mergedSections,
                        continuation = nextSections.continuation
                    )
                    state.copy(homePage = updatedHome)
                }
            }
            isLoadingMore.value = false
        }
    }

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
