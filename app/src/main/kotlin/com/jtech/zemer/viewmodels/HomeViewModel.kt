package com.jtech.zemer.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.WatchEndpoint
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
import com.jtech.zemer.utils.dataStore
import com.jtech.zemer.utils.filterWhitelisted
import com.jtech.zemer.utils.get
import com.jtech.zemer.utils.reportException
import com.jtech.zemer.utils.SyncUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import timber.log.Timber
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.jvm.Volatile
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext val context: Context,
    val database: MusicDatabase,
    val syncUtils: SyncUtils,
) : ViewModel() {
    @Volatile
    private var hasLoadedOnce = false
    val isRefreshing = MutableStateFlow(false)
    val isLoading = MutableStateFlow(false)

    private val quickPicksEnum = context.dataStore.data.map {
        it[QuickPicksKey].toEnum(QuickPicks.QUICK_PICKS)
    }.distinctUntilChanged()

    val quickPicks = MutableStateFlow<List<Song>?>(null)
    val forgottenFavorites = MutableStateFlow<List<Song>?>(null)
    val keepListening = MutableStateFlow<List<LocalItem>?>(null)
    val homePage = MutableStateFlow<HomePage?>(null)
    val explorePage = MutableStateFlow<ExplorePage?>(null)
    val trendingSongs = MutableStateFlow<List<YTItem>>(emptyList())
    val featuredAlbums = MutableStateFlow<List<com.metrolist.innertube.models.AlbumItem>>(emptyList())
    val featuredArtists = MutableStateFlow<List<com.metrolist.innertube.models.ArtistItem>>(emptyList())
    val isNewUser = MutableStateFlow(true)

    val allLocalItems = MutableStateFlow<List<LocalItem>>(emptyList())
    val allYtItems = MutableStateFlow<List<YTItem>>(emptyList())

    private suspend fun hasWhitelist(): Boolean =
        database.getAllWhitelistedArtistIdsSync().isNotEmpty()

    private fun updateDerivedState() {
        val hasQuickPicks = quickPicks.value?.isNotEmpty() == true
        val hasKeepListening = keepListening.value?.isNotEmpty() == true
        isNewUser.value = !hasQuickPicks && !hasKeepListening

        allLocalItems.value = (quickPicks.value.orEmpty() + forgottenFavorites.value.orEmpty() + keepListening.value.orEmpty())
            .filter { it is Song || it is Album }
    }

    private suspend fun getQuickPicks() {
        val whitelistPresent = hasWhitelist()
        when (quickPicksEnum.first()) {
            QuickPicks.QUICK_PICKS -> {
                val raw = runCatching { database.quickPicks().first() }.getOrDefault(emptyList())
                val withFallback = raw.ifEmpty {
                    // Fallback: recent unique songs from history when DB query yields nothing
                    database.events().first().map { it.song }.distinctBy { it.id }.take(50)
                }
                val picks = withFallback
                    .shuffled()
                    .distinctBy { it.artists.firstOrNull()?.id ?: it.id }
                    .take(20)
                quickPicks.value = picks
                Timber.d("HomeViewModel: Quick picks loaded - ${picks.size} songs (whitelistPresent=$whitelistPresent)")
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
                val picks = withFallback
                    .shuffled()
                    .distinctBy { it.artists.firstOrNull()?.id ?: it.id }
                    .take(20)
                quickPicks.value = picks
                Timber.d("HomeViewModel: Last listen quick picks loaded - ${picks.size} songs (whitelistPresent=$whitelistPresent)")
            }
        }
        if (quickPicks.value.isNullOrEmpty()) {
            val historyFallback = database.events().first().map { it.song }.distinctBy { it.id }.take(20)
            quickPicks.value = historyFallback
            Timber.d("HomeViewModel: Quick picks fallback from history - ${historyFallback.size} songs")
        }
    }

    private suspend fun load(force: Boolean = false) {
        if (isLoading.value) return
        if (!force && hasLoadedOnce) return

        isLoading.value = true
        try {
            val hideExplicit = context.dataStore.get(HideExplicitKey, false)

            // Debug: Check whitelist is populated before loading home content
            val whitelistCount = database.getAllWhitelistedArtistIds().first().size
            Timber.d("HomeViewModel: Whitelist has $whitelistCount artists at load time")

            getQuickPicks()

            val forgotten = database.forgottenFavorites().first().shuffled().take(20)
            forgottenFavorites.value = forgotten
            Timber.d("HomeViewModel: Forgotten favorites loaded - ${forgotten.size} songs")

            val toTimeStamp = System.currentTimeMillis()
            val fromTimeStamp = toTimeStamp - 86400000 * 7 * 2
            try {
                val historySongs = database.events().first().map { it.song }.distinctBy { it.id }.take(40)

                val keepListeningSongs = runCatching {
                    database.mostPlayedSongs(
                        fromTimeStamp = fromTimeStamp,
                        toTimeStamp = toTimeStamp,
                        limit = 25,
                        offset = 0,
                    ).first()
                }.getOrDefault(emptyList()).ifEmpty { historySongs }

                val keepListeningAlbums = try {
                    database.mostPlayedAlbums(
                        fromTimeStamp = fromTimeStamp,
                        toTimeStamp = toTimeStamp,
                        limit = 10,
                        offset = 0,
                    )
                        .first()
                        .filter { it.album.thumbnailUrl != null }
                } catch (e: Exception) {
                    Timber.e(e, "HomeViewModel: Error loading keep listening albums")
                    emptyList()
                }

                val keepListeningArtists = try {
                    database.mostPlayedArtists(fromTimeStamp, limit = 10, offset = 0).first()
                        .filter { it.artist.thumbnailUrl != null }
                } catch (e: Exception) {
                    Timber.e(e, "HomeViewModel: Error loading keep listening artists")
                    emptyList()
                }

                var combined = (keepListeningSongs.shuffled().take(12) +
                        keepListeningAlbums.shuffled().take(6) +
                        keepListeningArtists.shuffled().take(6)).shuffled()
                if (combined.isEmpty()) {
                    // Fallback to recent history if nothing else
                    combined = historySongs.shuffled().take(20)
                    Timber.d("HomeViewModel: Keep listening fallback from history - ${combined.size} items")
                }
                keepListening.value = combined
                Timber.d("HomeViewModel: Keep listening loaded - ${keepListeningSongs.size} songs, ${keepListeningAlbums.size} albums, ${keepListeningArtists.size} artists (total: ${combined.size})")
            } catch (e: Exception) {
                Timber.e(e, "HomeViewModel: Exception in keep listening section")
                keepListening.value = emptyList()
            }

            YouTube.home().onSuccess { page ->
                homePage.value = page.copy(
                    sections = page.sections.mapNotNull { section ->
                        val filteredItems = section.items
                            .filterExplicit(hideExplicit)
                            .filterWhitelisted(database)
                        if (filteredItems.isEmpty()) null else section.copy(items = filteredItems)
                    }
                )
            }.onFailure {
                reportException(it)
            }

            YouTube.explore().onSuccess { page ->
                val rawAlbums = page.newReleaseAlbums
                val afterExplicit = rawAlbums.filterExplicit(hideExplicit)
                val afterWhitelist = afterExplicit.filterWhitelisted(database)
                val finalAlbums = afterWhitelist.filterIsInstance<com.metrolist.innertube.models.AlbumItem>()

                Timber.d("HomeViewModel: New Release Albums - raw=${rawAlbums.size}, afterExplicit=${afterExplicit.size}, afterWhitelist=${afterWhitelist.size}, final=${finalAlbums.size}")

                explorePage.value = page.copy(
                    newReleaseAlbums = finalAlbums
                )
            }.onFailure {
                Timber.e(it, "HomeViewModel: Failed to load explore page")
                reportException(it)
            }

            // Detect if user is new (no history-based content)
            updateDerivedState()
            Timber.d("HomeViewModel: New user detection - hasQuickPicks=${quickPicks.value?.isNotEmpty() == true}, hasKeepListening=${keepListening.value?.isNotEmpty() == true}, isNewUser=${isNewUser.value}")

            // Load featured content from whitelisted artists (always, for all users)
            Timber.d("HomeViewModel: Loading featured content from whitelisted artists")

            // Get 15 random whitelisted artist IDs
            val randomArtistIds = database.getRandomWhitelistedArtistIds(15)
            Timber.d("HomeViewModel: Fetched ${randomArtistIds.size} random whitelisted artist IDs")

            if (randomArtistIds.isNotEmpty()) {
                val albums = mutableListOf<com.metrolist.innertube.models.AlbumItem>()
                val artists = mutableListOf<com.metrolist.innertube.models.ArtistItem>()

                // Fetch artist pages in parallel for better performance
                coroutineScope {
                    val deferredArtistPages = randomArtistIds.take(15).map { artistId ->
                        async {
                            YouTube.artist(artistId).fold(
                                onSuccess = { artistPage -> artistPage },
                                onFailure = { error ->
                                    Timber.w(error, "HomeViewModel: Failed to fetch artist $artistId")
                                    null
                                }
                            )
                        }
                    }

                    // Wait for all parallel requests to complete
                    val artistPages = deferredArtistPages.awaitAll().filterNotNull()

                    // Extract content from fetched artist pages
                    artistPages.forEach { artistPage ->
                        // Add the artist themselves
                        artists.add(artistPage.artist)

                        // Extract albums from the artist page
                        val artistAlbums = artistPage.sections
                            .flatMap { it.items }
                            .filterIsInstance<com.metrolist.innertube.models.AlbumItem>()
                            .take(2) // Take top 2 albums per artist

                        albums.addAll(artistAlbums)
                        Timber.d("HomeViewModel: Artist ${artistPage.artist.title} - added ${artistAlbums.size} albums")
                    }
                }

                featuredAlbums.value = albums.shuffled().take(20)
                featuredArtists.value = artists.shuffled().take(20)

                Timber.d("HomeViewModel: Featured content - ${featuredAlbums.value.size} albums, ${featuredArtists.value.size} artists")
            }

            allLocalItems.value = (quickPicks.value.orEmpty() + forgottenFavorites.value.orEmpty() + keepListening.value.orEmpty())
                .filter { it is Song || it is Album }
            allYtItems.value = homePage.value?.sections?.flatMap { it.items }.orEmpty()
        } finally {
            isLoading.value = false
            hasLoadedOnce = true
        }
    }

    private val _isLoadingMore = MutableStateFlow(false)
    fun loadMoreYouTubeItems(continuation: String?) {
        if (continuation == null || _isLoadingMore.value) return
        val hideExplicit = context.dataStore.get(HideExplicitKey, false)

        viewModelScope.launch(Dispatchers.IO) {
            _isLoadingMore.value = true
            val nextSections = YouTube.home(continuation).getOrNull() ?: run {
                _isLoadingMore.value = false
                return@launch
            }

            homePage.value = nextSections.copy(
                chips = homePage.value?.chips,
                sections = (homePage.value?.sections.orEmpty() + nextSections.sections).mapNotNull { section ->
                    val filteredItems = section.items
                        .filterExplicit(hideExplicit)
                        .filterWhitelisted(database)
                    if (filteredItems.isEmpty()) null else section.copy(items = filteredItems)
                }
            )
            _isLoadingMore.value = false
        }
    }

    fun refresh() {
        if (isRefreshing.value) return
        viewModelScope.launch(Dispatchers.IO) {
            isRefreshing.value = true
            load(force = true)
            isRefreshing.value = false
        }
    }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            context.dataStore.data
                .map { it[InnerTubeCookieKey] }
                .distinctUntilChanged()
                .first()

            // Avoid kicking off sync until onboarding is completed
            val onboardingComplete = context.dataStore.get(OnboardingCompleteKey, false)
            if (!onboardingComplete) {
                context.dataStore.data
                    .map { it[OnboardingCompleteKey] == true }
                    .distinctUntilChanged()
                    .first { it }
            }

            val isSyncEnabled = context.dataStore.get(YtmSyncKey, true)

            load(force = true)

            // Run other syncs in background after load completes
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

        // Update quick picks only when the user changes the mode, not on every playback event
        viewModelScope.launch(Dispatchers.IO) {
            quickPicksEnum.drop(1).collect {
                getQuickPicks()
                updateDerivedState()
            }
        }
    }
}
