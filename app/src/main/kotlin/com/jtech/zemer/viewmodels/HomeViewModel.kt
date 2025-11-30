package com.jtech.zemer.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.YTItem
import com.metrolist.innertube.models.filterExplicit
import com.metrolist.innertube.pages.ExplorePage
import com.metrolist.innertube.pages.HomePage
import com.metrolist.innertube.utils.completed
import com.jtech.zemer.constants.HideExplicitKey
import com.jtech.zemer.constants.InnerTubeCookieKey
import com.jtech.zemer.constants.OnboardingCompleteKey
import com.jtech.zemer.constants.QuickPicks
import com.jtech.zemer.constants.QuickPicksKey
import com.jtech.zemer.constants.YtmSyncKey
import com.jtech.zemer.db.MusicDatabase
import com.jtech.zemer.db.entities.Album
import com.jtech.zemer.db.entities.LocalItem
import com.jtech.zemer.db.entities.Song
import com.jtech.zemer.extensions.toEnum
import com.jtech.zemer.models.toMediaMetadata
import com.jtech.zemer.utils.SyncUtils
import com.jtech.zemer.utils.dataStore
import com.jtech.zemer.utils.filterWhitelisted
import com.jtech.zemer.utils.get
import com.jtech.zemer.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlin.jvm.Volatile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext val context: Context,
    val database: MusicDatabase,
    val syncUtils: SyncUtils,
) : ViewModel() {
    data class HomeUiState(
        val isLoading: Boolean = false,
        val isRefreshing: Boolean = false,
        val isNewUser: Boolean = true,
        val quickPicks: List<Song> = emptyList(),
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

    @Volatile
    private var hasLoadedOnce = false
    private val isLoadingMore = MutableStateFlow(false)

    private val quickPicksEnum = context.dataStore.data.map {
        it[QuickPicksKey].toEnum(QuickPicks.QUICK_PICKS)
    }.distinctUntilChanged()

    private suspend fun hasWhitelist(): Boolean =
        database.getAllWhitelistedArtistIdsSync().isNotEmpty()

    private suspend fun artistBasedQuickPicks(): List<Song> {
        val events = database.events().first()
        val listenedArtistIds = events
            .flatMap { it.song.artists }
            .mapNotNull { it.id }
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
            combined = historySongs.shuffled().take(20)
            Timber.d("HomeViewModel: Keep listening fallback from history - ${combined.size} items")
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
                    val filteredItems = section.items
                        .filterExplicit(hideExplicit)
                        .filterWhitelisted(database)
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
            val afterExplicit = rawAlbums.filterExplicit(hideExplicit)
            val afterWhitelist = afterExplicit.filterWhitelisted(database)
            val finalAlbums = afterWhitelist.filterIsInstance<AlbumItem>()
            page.copy(newReleaseAlbums = finalAlbums)
        }.getOrElse {
            reportException(it)
            null
        }
    }

    private suspend fun loadRecentReleases(hideExplicit: Boolean): Pair<List<AlbumItem>, List<SongItem>> {
        return runCatching {
            val browse = YouTube.browse(browseId = "FEmusic_new_releases", params = null).getOrNull()
            val items = browse
                ?.filterExplicit(hideExplicit)
                ?.items
                ?.flatMap { it.items }
                ?.filterWhitelisted(database)
                .orEmpty()
            val albums = items.filterIsInstance<AlbumItem>()
            val songs = items.filterIsInstance<SongItem>()
            albums to songs
        }.getOrElse {
            Timber.w(it, "HomeViewModel: Failed to load recent releases")
            emptyList<AlbumItem>() to emptyList()
        }
    }

    private suspend fun loadFeaturedContent(hideExplicit: Boolean): Triple<List<AlbumItem>, List<ArtistItem>, List<SongItem>> {
        val randomArtistIds = database.getRandomWhitelistedArtistIds(15)
        if (randomArtistIds.isEmpty()) return Triple(emptyList(), emptyList(), emptyList())

        val albums = mutableListOf<AlbumItem>()
        val artists = mutableListOf<ArtistItem>()
        val videos = mutableListOf<SongItem>()

        coroutineScope {
            val deferredArtistPages = randomArtistIds.take(15).map { artistId ->
                async {
                    YouTube.artist(artistId).getOrNull()
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

    private suspend fun seedQuickPicksFromHomePage(page: HomePage, hideExplicit: Boolean, existing: List<Song>): List<Song> {
        if (existing.isNotEmpty()) return existing
        val candidateSongs = page.sections
            .flatMap { it.items }
            .filterExplicit(hideExplicit)
            .filterWhitelisted(database)
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

    private suspend fun load(force: Boolean = false) {
        if (uiState.value.isLoading) return
        if (!force && hasLoadedOnce) return

        uiState.update { it.copy(isLoading = true) }
        try {
            val hideExplicit = context.dataStore.get(HideExplicitKey, false)
            val quick = loadQuickPicks()
            val forgotten = database.forgottenFavorites().first().shuffled().take(20)
            val keepListening = loadKeepListening()
            val home = loadHomePage(hideExplicit)
            val explore = loadExplorePage(hideExplicit)
            val (recentAlbums, recentSongs) = loadRecentReleases(hideExplicit)

            val quickSeeded = if (home != null) seedQuickPicksFromHomePage(home, hideExplicit, quick) else quick

            val featuredTriple = loadFeaturedContent(hideExplicit)
            val isNewUser = quickSeeded.isEmpty() && keepListening.isEmpty()

            uiState.update {
                it.copy(
                    isLoading = false,
                    isRefreshing = false,
                    isNewUser = isNewUser,
                    quickPicks = quickSeeded,
                    keepListening = keepListening,
                    forgottenFavorites = forgotten,
                    recentReleaseAlbums = recentAlbums,
                    recentReleaseSongs = recentSongs,
                    featuredAlbums = featuredTriple.first,
                    featuredArtists = featuredTriple.second,
                    featuredVideos = featuredTriple.third,
                    homePage = home,
                    explorePage = explore,
                )
            }
            hasLoadedOnce = true
        } finally {
            uiState.update { it.copy(isLoading = false) }
        }
    }

    fun loadMoreYouTubeItems(continuation: String?) {
        if (continuation == null || isLoadingMore.value) return
        val hideExplicit = context.dataStore.get(HideExplicitKey, false)

        viewModelScope.launch(Dispatchers.IO) {
            isLoadingMore.value = true
            val nextSections = YouTube.home(continuation).getOrNull()
            if (nextSections != null) {
                uiState.update { state ->
                    val existingSections = state.homePage?.sections.orEmpty()
                    val mergedSections = (existingSections + nextSections.sections).mapNotNull { section ->
                        val filteredItems = section.items
                            .filterExplicit(hideExplicit)
                            .filterWhitelisted(database)
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
            load(force = true)
            uiState.update { it.copy(isRefreshing = false) }
        }
    }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            context.dataStore.data
                .map { it[InnerTubeCookieKey] }
                .distinctUntilChanged()
                .first()

            val onboardingComplete = context.dataStore.get(OnboardingCompleteKey, false)
            if (!onboardingComplete) {
                context.dataStore.data
                    .map { it[OnboardingCompleteKey] == true }
                    .distinctUntilChanged()
                    .first { it }
            }

            val isSyncEnabled = context.dataStore.get(YtmSyncKey, true)

            load(force = true)

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
            quickPicksEnum.drop(1).collect {
                load(force = true)
            }
        }
    }
}
