package com.jtech.zemer.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.jtech.zemer.constants.HideExplicitKey
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
    ): List<PlaylistItem> {
        val selectedArtists = selectWeightedArtists(
            artistProfiles,
            allowFemale,
            12,
            Random(random.nextLong()),
            usedArtists,
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

        return playlistItems
            .shuffled()
            .distinctBy { it.id }
            .take(8)
    }

    private suspend fun loadTrendingSongs(filters: ContentFilterConfig, hideExplicit: Boolean): List<SongItem> {
        val allowedIds = WhitelistCache.allowedEntries(database, filters).map { it.artistId }.toSet()
        val charts = YouTube.getChartsPage().getOrNull() ?: return emptyList()
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
        val homeResult = YouTube.home()
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
        return YouTube.explore().mapCatching { page ->
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

            val browse = YouTube.browse(browseId = "FEmusic_new_releases", params = null).getOrNull()
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
        if (homeArtistProfilesCache.isNotEmpty() && !force) return homeArtistProfilesCache

        return runCatching {
            IsraeliArtistRegistry.ensureLoaded()

            val snapshot = FirebaseFirestore.getInstance()
                .collection("artistsWhitelist")
                .get()
                .await()

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
            profiles
        }.getOrElse {
            Timber.w(it, "HomeViewModel: Failed to load artist profiles")
            emptyList()
        }
    }

    private fun SongItem.isBlocked(
        profileById: Map<String, HomeArtistProfile>,
        allowFemale: Boolean
    ): Boolean {
        val ids = this.artists?.mapNotNull { it.id }.orEmpty()
        if (!allowFemale && ids.any { profileById[it]?.isFemale == true }) return true
        return false
    }

    private fun AlbumItem.isBlocked(
        profileById: Map<String, HomeArtistProfile>,
        allowFemale: Boolean
    ): Boolean {
        val ids = this.artists?.mapNotNull { it.id }.orEmpty()
        if (!allowFemale && ids.any { profileById[it]?.isFemale == true }) return true
        return false
    }

    private fun ArtistItem.isBlocked(
        profileById: Map<String, HomeArtistProfile>,
        allowFemale: Boolean
    ): Boolean {
        if (!allowFemale && profileById[id]?.isFemale == true) return true
        return false
    }

    private fun PlaylistItem.isBlocked(
        profileById: Map<String, HomeArtistProfile>,
        allowFemale: Boolean
    ): Boolean {
        val authorId = author?.id
        if (authorId != null) {
            if (!allowFemale && profileById[authorId]?.isFemale == true) return true
        }
        return false
    }

    private fun selectWeightedArtists(
        profiles: List<HomeArtistProfile>,
        allowFemale: Boolean,
        targetCount: Int,
        random: Random,
        used: MutableSet<String>? = null
    ): List<HomeArtistProfile> {
        if (targetCount <= 0) return emptyList()
        val base = profiles
            .filter { it.isKids != true }
            .filter { it.isIsraeli != true }
            .filter { it.isFamous != false }
            .filter { allowFemale || it.isFemale != true }
            .filter { used?.contains(it.id) != true }
        if (base.isEmpty()) return emptyList()

        val bucketRng = Random(random.nextLong())
        val buckets = listOf(
            0.50f to base.filter { it.isFamous == true }.shuffled(Random(bucketRng.nextLong())),
            0.25f to base.filter { it.isDJ == true }.shuffled(Random(bucketRng.nextLong())),
            0.15f to base.filter { it.isGroup == true }.shuffled(Random(bucketRng.nextLong())),
            0.10f to base.filter { it.isAmerican == false }.shuffled(Random(bucketRng.nextLong())),
        )

        val chosen = mutableListOf<HomeArtistProfile>()
        val seen = mutableSetOf<String>()

        buckets.forEach { (ratio, bucket) ->
            if (bucket.isEmpty()) return@forEach
            val goal = (targetCount * ratio).toInt().coerceAtLeast(1)
            bucket.forEach { candidate ->
                if (chosen.size >= targetCount) return@forEach
                if (seen.add(candidate.id)) {
                    chosen += candidate
                    used?.add(candidate.id)
                    if (chosen.size >= goal) return@forEach
                }
            }
        }

        if (chosen.size < targetCount) {
            base.shuffled(Random(bucketRng.nextLong())).forEach { candidate ->
                if (chosen.size >= targetCount) return@forEach
                if (seen.add(candidate.id)) {
                    chosen += candidate
                    used?.add(candidate.id)
                }
            }
        }

        return chosen.take(targetCount)
    }

    private suspend fun loadFeaturedContent(
        hideExplicit: Boolean,
        artistProfiles: List<HomeArtistProfile>,
        allowFemale: Boolean,
        random: Random,
        usedArtists: MutableSet<String>,
    ): Triple<List<AlbumItem>, List<ArtistItem>, List<SongItem>> {
        val weightedArtists = selectWeightedArtists(
            artistProfiles,
            allowFemale,
            15,
            Random(random.nextLong()),
            usedArtists,
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
        random: Random
    ): HomePage? {
        val filters = ContentFilterState.state.value
        val allowed = WhitelistCache.allowedEntries(database, filters)
        if (allowed.isEmpty()) return null
        val profileById = artistProfiles.associateBy { it.id }
        val candidates = allowed.mapNotNull { profileById[it.artistId] }
        val selected = selectWeightedArtists(candidates, allowFemale, 10, Random(random.nextLong()))
        val sections = mutableListOf<HomePage.Section>()
        val allSongs = mutableListOf<SongItem>()
        val allAlbums = mutableListOf<AlbumItem>()
        val allVideos = mutableListOf<SongItem>()

        coroutineScope {
            val pages = selected.map { entry ->
                async { YouTube.artist(entry.id).getOrNull() }
            }.awaitAll().filterNotNull()

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
        random: Random
    ): ExplorePage? {
        val filters = ContentFilterState.state.value
        val allowed = WhitelistCache.allowedEntries(database, filters)
        if (allowed.isEmpty()) return null

        val profileById = artistProfiles.associateBy { it.id }
        val candidates = allowed.mapNotNull { profileById[it.artistId] }
        val selected = selectWeightedArtists(candidates, allowFemale, 12, Random(random.nextLong()))
        val albums = mutableListOf<AlbumItem>()

        coroutineScope {
            val pages = selected.map { entry ->
                async { YouTube.artist(entry.id).getOrNull() }
            }.awaitAll().filterNotNull()

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
        val nonAmerican = byProfile.filterKeys { it?.isAmerican == false }.values.flatten().shuffled(Random(rng.nextLong()))

        val buckets = listOf(
            0.50f to famous,
            0.25f to dj,
            0.15f to group,
            0.10f to nonAmerican,
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

        uiState.update { it.copy(isLoading = true) }
        try {
            IsraeliArtistRegistry.ensureLoaded()

            if (WhitelistCache.snapshot().isEmpty()) {
                runCatching { WhitelistCache.updateAll(database.getWhitelistEntriesSync()) }
            }
            if (force) {
                homeArtistProfilesCache = emptyList()
            }
            val filters = ContentFilterState.state.value
            val allowedEntries = WhitelistCache.allowedEntries(database, filters)
            val allowedIds = allowedEntries.map { it.artistId }.toSet()
            val useWhitelist = filters.filtersEnabled && allowedEntries.isNotEmpty()
            val effectiveFilters = if (useWhitelist) filters else filters.copy(filtersEnabled = false)
            val allowFemale = filters.allowFemaleSingers

            val artistProfiles = loadHomeArtistProfiles(force = force)
            val eligibleProfiles = artistProfiles
                .filter { it.isIsraeli != true }
                .filter { it.isFamous == true }
            val profileById = artistProfiles.associateBy { it.id }
            val baseProfiles = if (useWhitelist) {
                eligibleProfiles.filter { it.id in allowedIds }
            } else {
                eligibleProfiles
            }
            val sharedUsedArtists = mutableSetOf<String>()
            val selectionRandom = Random(System.nanoTime())

            val hideExplicit = context.dataStore.getSuspend(HideExplicitKey, false)
            val quick = loadQuickPicks()
            val featuredPlaylists = loadFeaturedPlaylists(
                hideExplicit,
                baseProfiles,
                allowFemale,
                selectionRandom,
                sharedUsedArtists,
            )
            val trendingSongs = loadTrendingSongs(effectiveFilters, hideExplicit)
            val forgottenList = database.forgottenFavorites().first().shuffled().take(20)
            val forgotten = forgottenList.ifEmpty {
                // Fallback: show liked songs if no forgotten favorites
                runCatching { database.allSongs().first().filter { it.song.liked } }
                    .getOrDefault(emptyList())
                    .shuffled()
                    .take(20)
            }
            val keepListening = loadKeepListening()
            val home = if (useWhitelist) loadWhitelistHome(hideExplicit, baseProfiles, allowFemale, selectionRandom) else loadHomePage(hideExplicit)
            val explore = if (useWhitelist) loadWhitelistExplore(hideExplicit, baseProfiles, allowFemale, selectionRandom) else loadExplorePage(hideExplicit)
            val (recentAlbums, recentSongs) = loadRecentReleases(hideExplicit)

            fun isBlockedArtist(ids: List<String>): Boolean {
                if (ids.any { IsraeliArtistRegistry.isIsraeli(it) }) return true
                val profiles = ids.mapNotNull { profileById[it] }
                if (!allowFemale && profiles.any { it.isFemale == true }) return true
                if (profiles.any { it.isAmerican == false }) return true
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

            fun HomePage?.filtered(): HomePage? {
                if (this == null) return null
                val filteredSections = sections.mapNotNull { section ->
                    val filteredItems = section.items.mapNotNull { item ->
                        when (item) {
                            is SongItem -> item.takeUnless { it.isBlocked(profileById, allowFemale) }
                            is AlbumItem -> item.takeUnless { it.isBlocked(profileById, allowFemale) }
                            is ArtistItem -> item.takeUnless { it.isBlocked(profileById, allowFemale) }
                            is PlaylistItem -> item.takeUnless { it.isBlocked(profileById, allowFemale) }
                            else -> item
                        }
                    }
                    if (filteredItems.isEmpty()) null else section.copy(items = filteredItems)
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

            val featuredTriple = loadFeaturedContent(
                hideExplicit,
                baseProfiles,
                allowFemale,
                selectionRandom,
                sharedUsedArtists,
            )

            val filteredQuick = quickSeeded.filter { song -> song.isAllowed() }
            val fallbackQuick = runCatching {
                database.allSongs().first().filter { it.isAllowed() }.take(20)
            }.getOrDefault(emptyList())
            val finalQuick = filteredQuick
                .shuffled(Random(System.nanoTime()))
                .take(20)
                .ifEmpty { filteredQuick.ifEmpty { fallbackQuick } }
            val isNewUser = finalQuick.isEmpty() && keepListening.isEmpty()

            Timber.d(
                "HomeViewModel: load -> featuredArtists=%d playlists=%d albums=%d videos=%d quick=%d",
                featuredTriple.second.size,
                featuredPlaylists.size,
                featuredTriple.first.size,
                featuredTriple.third.size,
                finalQuick.size
            )

            uiState.update {
                it.copy(
                    isLoading = false,
                    isRefreshing = false,
                    isNewUser = isNewUser,
                    quickPicks = finalQuick.shuffled(Random(System.nanoTime())),
                    trendingSongs = trendingSongs.filter { song -> song.isAllowed() }.shuffled(Random(System.nanoTime())),
                    featuredPlaylists = featuredPlaylists.filter { it.isAllowed() }.shuffled(Random(System.nanoTime())),
                    keepListening = keepListening.filter { it.isAllowed() }.shuffled(Random(System.nanoTime())),
                    forgottenFavorites = forgotten.filter { song -> song.isAllowed() }.shuffled(Random(System.nanoTime())),
                    recentReleaseAlbums = filteredRecentAlbums.shuffled(Random(System.nanoTime())),
                    recentReleaseSongs = filteredRecentSongs.shuffled(Random(System.nanoTime())),
                    featuredAlbums = featuredTriple.first.filter { it.isAllowed() }.shuffled(Random(System.nanoTime())),
                    featuredArtists = featuredTriple.second.filter { it.isAllowed() }.shuffled(Random(System.nanoTime())),
                    featuredVideos = featuredTriple.third.filter { it.isAllowed() }.shuffled(Random(System.nanoTime())),
                    homePage = filteredHome,
                    explorePage = filteredExplore,
                )
            }
            hasLoadedOnce = true
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
                                    is SongItem -> item.artists?.any { IsraeliArtistRegistry.isIsraeli(it.id) } == true
                                    is AlbumItem -> item.artists?.any { IsraeliArtistRegistry.isIsraeli(it.id) } == true
                                    is ArtistItem -> IsraeliArtistRegistry.isIsraeli(item.id)
                                    is PlaylistItem -> IsraeliArtistRegistry.isIsraeli(item.author?.id)
                                    else -> false
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

            runCatching { load(force = true) }.onFailure { reportException(it) }

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
