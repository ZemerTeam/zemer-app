package com.jtech.zemer.ui.screens

import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.carousel.CarouselDefaults
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.animation.core.tween
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults.LoadingIndicator
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import kotlinx.coroutines.CoroutineScope
import androidx.navigation.compose.currentBackStackEntryAsState
import com.jtech.zemer.LocalDatabase
import com.jtech.zemer.LocalPlayerAwareWindowInsets
import com.jtech.zemer.LocalPlayerConnection
import com.jtech.zemer.R
import com.jtech.zemer.constants.BlockVideosKey
import com.jtech.zemer.constants.BlockPodcastsKey
import androidx.datastore.preferences.core.edit
import com.jtech.zemer.constants.HomeContentTabKey
import com.jtech.zemer.constants.ShowHomeGenresKey
import com.jtech.zemer.constants.ShowHomeStatusesKey
import com.jtech.zemer.constants.GridThumbnailHeight
import com.jtech.zemer.constants.ListItemHeight
import com.jtech.zemer.db.entities.Album
import com.jtech.zemer.db.entities.Artist
import com.jtech.zemer.db.entities.LocalItem
import com.jtech.zemer.db.entities.Playlist
import com.jtech.zemer.db.entities.Song
import com.jtech.zemer.models.toMediaMetadata
import com.jtech.zemer.playback.queues.ZemerRadioQueue
import com.jtech.zemer.search.zemerAlbumRoute
import com.jtech.zemer.search.zemerGenresRoute
import com.jtech.zemer.search.zemerPodcastGenresRoute
import com.jtech.zemer.search.zemerPlaylistRoute
import com.jtech.zemer.viewmodels.HomeSeeAllRow
import com.jtech.zemer.tracking.TrackImpressionsByKey
import com.jtech.zemer.tracking.TrackingSurface
import com.jtech.zemer.ui.component.AlbumGridItem
import com.jtech.zemer.ui.component.ArtistGridItem
import com.jtech.zemer.ui.component.LocalBottomSheetPageState
import com.jtech.zemer.ui.component.LocalMenuState
import com.jtech.zemer.ui.component.MoreVertMenuButton
import com.jtech.zemer.extensions.toMediaItem
import com.jtech.zemer.playback.queues.ListQueue
import com.jtech.zemer.tracking.PlaySource
import com.jtech.zemer.ui.component.NavigationTitle
import com.jtech.zemer.ui.component.ChipsRow
import com.jtech.zemer.ui.utils.whitelistedPodcastRoute
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import com.jtech.zemer.extensions.toEnum
import com.jtech.zemer.utils.dataStore
import com.jtech.zemer.ui.component.ZemerCuratedPlaylistGridItem
import com.jtech.zemer.ui.component.SongGridItem
import com.jtech.zemer.ui.component.SongListItem
import com.jtech.zemer.ui.component.YouTubeGridItem
import com.jtech.zemer.ui.component.shimmer.GridItemPlaceHolder
import com.jtech.zemer.ui.component.shimmer.ShimmerHost
import com.jtech.zemer.ui.component.shimmer.TextPlaceholder
import com.jtech.zemer.ui.menu.AlbumMenu
import com.jtech.zemer.ui.menu.ArtistMenu
import com.jtech.zemer.ui.menu.SongMenu
import com.jtech.zemer.ui.menu.YouTubeAlbumMenu
import com.jtech.zemer.ui.menu.YouTubeArtistMenu
import com.jtech.zemer.ui.menu.YouTubePlaylistMenu
import com.jtech.zemer.ui.menu.YouTubeSongMenu
import com.jtech.zemer.ui.menu.ytItemMenu
import com.jtech.zemer.ui.utils.activeRowTapTogglesPlayPause
import com.jtech.zemer.ui.utils.storyRoute
import com.jtech.zemer.ui.utils.SnapLayoutInfoProvider
import com.jtech.zemer.ui.utils.navigateToArtist
import com.jtech.zemer.ui.utils.navigateToAlbum
import com.jtech.zemer.utils.rememberPreference
import com.jtech.zemer.latestreleases.LatestReleaseCarouselItem
import com.jtech.zemer.viewmodels.HomeViewModel
import com.jtech.zemer.viewmodels.LatestReleasesViewModel
import com.jtech.zemer.playback.queues.StationQueue
import com.jtech.zemer.ui.component.ZemerStationCard
import com.jtech.zemer.viewmodels.ZemerCuratedPlaylistsViewModel
import kotlinx.coroutines.delay
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.jtech.zemer.viewmodels.STATION_ROW_REFRESH_MS
import com.jtech.zemer.viewmodels.ZemerGenresViewModel
import com.jtech.zemer.viewmodels.PodcastHomeRowsViewModel
import com.jtech.zemer.viewmodels.VideoHomeRowsViewModel
import com.jtech.zemer.viewmodels.PodcastSubscriptionsHomeViewModel
import com.jtech.zemer.viewmodels.ZemerStatusesViewModel
import com.jtech.zemer.viewmodels.ZemerStationsViewModel
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.YTItem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.min

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: HomeViewModel? = null,
) {
    // Use passed viewModel or create new one (fallback for direct navigation)
    @Suppress("NAME_SHADOWING")
    val viewModel: HomeViewModel = viewModel ?: hiltViewModel()
    val menuState = LocalMenuState.current
    LocalBottomSheetPageState.current
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current

    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val homeUiState by viewModel.uiState.collectAsState()

    val quickPicks = homeUiState.quickPicks
    val featuredPlaylists = homeUiState.featuredPlaylists
    val forgottenFavorites = homeUiState.forgottenFavorites
    val keepListening = homeUiState.keepListening
    val featuredAlbums = homeUiState.featuredAlbums
    val featuredArtists = homeUiState.featuredArtists
    val featuredVideos = homeUiState.featuredVideos
    // Featured albums are Zemer-sourced (telemetry-ranked) rather than the scrape fallback: open them via
    // the Zemer album route so the album screen loads through the server (immune to InnerTube bot-gating).
    val featuredAlbumsAreZemer = homeUiState.featuredAlbumsAreZemer
    // Same for featured playlists: Zemer community playlists open via the server /playlist route.
    val featuredPlaylistsAreZemer = homeUiState.featuredPlaylistsAreZemer
    val latestReleasesViewModel: LatestReleasesViewModel = hiltViewModel()
    val latestReleases by latestReleasesViewModel.releases.collectAsState()
    val zemerPlaylistsViewModel: ZemerCuratedPlaylistsViewModel = hiltViewModel()
    val zemerPlaylists by zemerPlaylistsViewModel.playlists.collectAsState()
    val zemerStationsViewModel: ZemerStationsViewModel = hiltViewModel()
    val zemerStations by zemerStationsViewModel.stations.collectAsState()
    val zemerGenresViewModel: ZemerGenresViewModel = hiltViewModel()
    val homeGenres by zemerGenresViewModel.genres.collectAsState()
    // The "Music Statuses" row (JewishStatus). Isolated + fail-soft: an outage leaves the list empty
    // and the section hides. Settings → Appearance owns its toggle.
    val zemerStatusesViewModel: ZemerStatusesViewModel = hiltViewModel()
    val statusCreators by zemerStatusesViewModel.creators.collectAsState()
    val statusSeenPostIds by zemerStatusesViewModel.seenPostIds.collectAsState()
    // Passed to the row so each ring counts only statuses the user can view under their content filter
    // (the ring never over-counts hidden kinds). Creators are NOT dropped - only the ring reflects it.
    val statusContentFilter by zemerStatusesViewModel.contentFilter.collectAsState()
    val podcastHomeRowsViewModel: PodcastHomeRowsViewModel = hiltViewModel()
    val featuredPodcasts by podcastHomeRowsViewModel.featured.collectAsState()
    val topPodcasts by podcastHomeRowsViewModel.topPodcasts.collectAsState()
    val trendingEpisodes by podcastHomeRowsViewModel.trendingEpisodes.collectAsState()
    // The Videos tab's ranked rows — isolated fail-soft VM (empty rows hide; the /video-home-rows
    // endpoint being absent leaves the tab on its topVideos lead row alone).
    val videoHomeRowsViewModel: VideoHomeRowsViewModel = hiltViewModel()
    val trendingVideos by videoHomeRowsViewModel.trending.collectAsState()
    val newVideos by videoHomeRowsViewModel.newVideos.collectAsState()
    val topVideoArtists by videoHomeRowsViewModel.artists.collectAsState()
    // Subscription-driven podcast rows (New Episodes + Subscribed Channels) — LOCAL sources, so identical
    // for anon + Google login. Own isolated fail-soft VM; each row hides when empty.
    val podcastSubscriptionsViewModel: PodcastSubscriptionsHomeViewModel = hiltViewModel()
    val homeNewEpisodes by podcastSubscriptionsViewModel.newEpisodes.collectAsState()
    val homeSubscribedChannels by podcastSubscriptionsViewModel.subscribedChannels.collectAsState()
    // The Home "Podcast Genres" chips strip, above the Podcasts row. Isolated + fail-soft like the
    // music genres strip: an outage leaves the list empty and the strip hides.
    val podcastGenresHomeViewModel: com.jtech.zemer.viewmodels.PodcastGenresHomeViewModel = hiltViewModel()
    val homePodcastGenres by podcastGenresHomeViewModel.genres.collectAsState()
    val continueListeningViewModel: com.jtech.zemer.viewmodels.ContinueListeningViewModel = hiltViewModel()
    val continueEpisodes by continueListeningViewModel.episodes.collectAsState()
    // Settings → Appearance owns these toggles (there is deliberately no in-row hide affordance).
    val (showHomeGenres, _) = rememberPreference(ShowHomeGenresKey, defaultValue = true)
    val (showHomeStatuses, _) = rememberPreference(ShowHomeStatusesKey, defaultValue = true)
    // Home content-type tab (Music / Podcasts / Radio / Video); each renders only its own shelves.
    // Persisted to DataStore. Seeded from ONE async snapshot that reads the tab AND Block Podcasts
    // together: no main-thread disk read, no flash of Music, and no flash of a blocked Podcasts tab.
    // Null = snapshot not landed yet (first frame renders no tab content).
    var homeTab by remember { mutableStateOf<HomeContentTab?>(null) }
    LaunchedEffect(Unit) {
        val prefs = context.dataStore.data.first()
        homeTab = effectiveHomeTab(
            persisted = prefs[HomeContentTabKey].toEnum(HomeContentTab.MUSIC),
            blockPodcasts = prefs[BlockPodcastsKey] == true,
        )
    }
    val tabWriteScope = rememberCoroutineScope()
    val setHomeTab: (HomeContentTab) -> Unit = { tab ->
        homeTab = tab
        tabWriteScope.launch { context.dataStore.edit { it[HomeContentTabKey] = tab.name } }
    }
    // The curated endpoint's freshness contract is a plain re-fetch on screen open (single-digit-ms
    // server reads) — this also picks up a card removed by curation while a detail open 404'd.
    LaunchedEffect(Unit) {
        zemerPlaylistsViewModel.refresh()
        zemerGenresViewModel.refresh()
        podcastGenresHomeViewModel.refresh()
        podcastHomeRowsViewModel.refresh()
        videoHomeRowsViewModel.refresh()
        podcastSubscriptionsViewModel.fetchNewEpisodes()
        zemerStatusesViewModel.refresh()
    }
    val stationsLifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(Unit) {
        // Keep the cards' now-playing line live while Home is actually VISIBLE: repeatOnLifecycle
        // suspends the ticker the moment the app leaves RESUMED (composition alone survives
        // backgrounding, so a bare while-loop would keep polling from the recents stack).
        stationsLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            zemerStationsViewModel.refresh()
            while (true) {
                delay(STATION_ROW_REFRESH_MS)
                zemerStationsViewModel.refresh()
            }
        }
    }
    val (blockVideos, _) = rememberPreference(BlockVideosKey, false)
    val (blockPodcasts, _) = rememberPreference(BlockPodcastsKey, false)
    // Blocking podcasts hides the whole content type (unlike videos, which relabel to audio) — drop the
    // tab and, if it was the persisted selection, fall back to Music so a blocked user never lands on it.
    LaunchedEffect(blockPodcasts, homeTab) {
        val current = homeTab ?: return@LaunchedEffect
        val effective = effectiveHomeTab(current, blockPodcasts)
        if (effective != current) setHomeTab(effective)
    }
    // Chip order/visibility is the pure, unit-tested visibleHomeTabs; only labels are resolved here.
    // The Video tab is ALWAYS shown; blocked-video users get it relabeled "Video songs" — its rows play
    // audio-first (the Featured Videos shelf follows the same relabel), so it's never hidden.
    val homeContentChips = visibleHomeTabs(blockPodcasts).map { tab ->
        tab to stringResource(
            when (tab) {
                HomeContentTab.MUSIC -> R.string.music
                HomeContentTab.RADIO -> R.string.radio
                HomeContentTab.PODCASTS -> R.string.podcasts
                HomeContentTab.VIDEO -> if (blockVideos) R.string.video_songs else R.string.videos
            }
        )
    }
    homeUiState.isNewUser


    // Memoized distinct lists to avoid creating new lists on every recomposition
    val uniqueQuickPicks = remember(quickPicks) { quickPicks.distinctBy { it.id } }
    val uniqueFeaturedPlaylists = remember(featuredPlaylists) { featuredPlaylists.distinctBy { it.id } }
    val uniqueForgottenFavorites = remember(forgottenFavorites) { forgottenFavorites.distinctBy { it.id } }
    // Cap the Home row; the full list lives behind "See all" (latest_releases screen).
    val latestReleasesCapped = remember(latestReleases) { latestReleases.take(12) }
    val uniqueFeaturedArtists = remember(featuredArtists) { featuredArtists.distinctBy { it.id } }
    val uniqueFeaturedAlbums = remember(featuredAlbums) { featuredAlbums.distinctBy { it.id } }
    val uniqueFeaturedVideos = remember(featuredVideos) { featuredVideos.distinctBy { it.id } }
    val uniqueTrendingVideos = remember(trendingVideos) { trendingVideos.distinctBy { it.id } }
    val uniqueNewVideos = remember(newVideos) { newVideos.distinctBy { it.id } }
    val uniqueTopVideoArtists = remember(topVideoArtists) { topVideoArtists.distinctBy { it.id } }

    val isLoading: Boolean = homeUiState.isLoading
    val isRefreshing = homeUiState.isRefreshing
    val pullRefreshState = rememberPullToRefreshState()

    val quickPicksLazyGridState = rememberLazyGridState()
    val forgottenFavoritesLazyGridState = rememberLazyGridState()

    val scope = rememberCoroutineScope()
    val lazylistState = rememberLazyListState()

    // Keep the viewport on the TRUE top when the genres strip streams in: Quick Picks (local DB)
    // renders first, the network-fetched genres insert ABOVE it, and LazyColumn anchors scroll to
    // the first visible ITEM — so on a fresh launch the new first section landed off-screen and the
    // user had to swipe up to find it. When the strip appears while the list is still effectively
    // at rest at the top (small index, zero offset, no active scroll), snap back to index 0. A user
    // who has genuinely scrolled away is never yanked.
    val genresSectionVisible = showHomeGenres && homeGenres.isNotEmpty()
    LaunchedEffect(genresSectionVisible) {
        if (genresSectionVisible &&
            !lazylistState.isScrollInProgress &&
            lazylistState.firstVisibleItemIndex <= 2 &&
            lazylistState.firstVisibleItemScrollOffset == 0
        ) {
            lazylistState.scrollToItem(0)
        }
    }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val scrollToTop =
        backStackEntry?.savedStateHandle?.getStateFlow("scrollToTop", false)?.collectAsState()
    val shuffleNow =
        backStackEntry?.savedStateHandle?.getStateFlow("shuffleNow", false)?.collectAsState()

    LaunchedEffect(scrollToTop?.value) {
        if (scrollToTop?.value == true) {
            lazylistState.animateScrollToItem(0)
            backStackEntry?.savedStateHandle?.set("scrollToTop", false)
        }
    }

    // Scroll to top on initial load and when quickPicks first loads
    var hasScrolledToTop by remember { mutableStateOf(false) }
    LaunchedEffect(quickPicks.isNotEmpty()) {
        if (quickPicks.isNotEmpty() && !hasScrolledToTop) {
            lazylistState.scrollToItem(0)
            hasScrolledToTop = true
        }
    }

    fun performShuffle() {
        // "Radio mode": a corpus-native, whitelist-pure station over the whole catalog
        // (Zemer /radio?kind=shuffle), replacing the old InnerTube lucky-item radio.
        playerConnection.playQueue(viewModel.shuffleRadioQueue())
    }

    LaunchedEffect(shuffleNow?.value) {
        if (shuffleNow?.value == true) {
            performShuffle()
            backStackEntry?.savedStateHandle?.set("shuffleNow", false)
        }
    }

    val localGridItem: @Composable (LocalItem) -> Unit = {
        when (it) {
            is Song -> SongGridItem(
                song = it,
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = {
                            if (activeRowTapTogglesPlayPause(it.id == mediaMetadata?.id, playerConnection.isStationBroadcast.value)) {
                                playerConnection.playPause()
                            } else {
                                playerConnection.playQueue(
                                    ZemerRadioQueue.song(it.toMediaMetadata(), playerConnection.service),
                                )
                            }
                        },
                        onLongClick = {
                            haptic.performHapticFeedback(
                                HapticFeedbackType.LongPress,
                            )
                            menuState.show {
                                SongMenu(
                                    originalSong = it,
                                    navController = navController,
                                    onDismiss = menuState::dismiss,
                                )
                            }
                        },
                    ),
                isActive = it.id == mediaMetadata?.id,
                isPlaying = isPlaying,
            )

            is Album -> AlbumGridItem(
                album = it,
                isActive = it.id == mediaMetadata?.album?.id,
                isPlaying = isPlaying,
                coroutineScope = scope,
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = {
                            navController.navigateToAlbum(it.id)
                        },
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            menuState.show {
                                AlbumMenu(
                                    originalAlbum = it,
                                    navController = navController,
                                    onDismiss = menuState::dismiss
                                )
                            }
                        }
                    )
            )

            is Artist -> ArtistGridItem(
                artist = it,
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = {
                            navController.navigateToArtist(it.id)
                        },
                        onLongClick = {
                            haptic.performHapticFeedback(
                                HapticFeedbackType.LongPress,
                            )
                            menuState.show {
                                ArtistMenu(
                                    originalArtist = it,
                                    coroutineScope = scope,
                                    onDismiss = menuState::dismiss,
                                )
                            }
                        },
                    ),
            )

            is Playlist -> {}
        }
    }

    val ytGridItem: @Composable (YTItem) -> Unit = { item ->
        YouTubeGridItem(
            item = item,
            isActive = item.id in listOf(mediaMetadata?.album?.id, mediaMetadata?.id),
            isPlaying = isPlaying,
            coroutineScope = scope,
            thumbnailRatio = 1f,
            modifier = Modifier
                .combinedClickable(
                    onClick = {
                        when (item) {
                            is com.metrolist.innertube.models.PodcastItem -> {}
                            is com.metrolist.innertube.models.EpisodeItem -> {}
                            is SongItem -> playerConnection.playQueue(
                                ZemerRadioQueue.song(item.toMediaMetadata(), playerConnection.service)
                            )

                            // Only the featured-albums row passes an AlbumItem through ytGridItem, so this
                            // branch is the featured album; route Zemer-sourced ones via the server album path.
                            is AlbumItem ->
                                if (featuredAlbumsAreZemer) {
                                    navController.navigate(zemerAlbumRoute(item))
                                } else {
                                    navController.navigateToAlbum(item.id)
                                }
                            is ArtistItem -> navController.navigateToArtist(item.id)
                            // Featured playlists: Zemer community playlists open via the server /playlist path
                            // and tag plays `community:<id>` (they're the discovery-sourced community row).
                            is PlaylistItem ->
                                if (featuredPlaylistsAreZemer) {
                                    navController.navigate(zemerPlaylistRoute(item.id, community = true))
                                } else {
                                    navController.navigate("online_playlist/${item.id}")
                                }
                        }
                    },
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        menuState.show(
                            ytItemMenu(
                                item = item,
                                navController = navController,
                                coroutineScope = scope,
                                onDismiss = menuState::dismiss,
                            )
                        )
                    }
                )
        )
    }

    LaunchedEffect(quickPicks) {
        quickPicksLazyGridState.scrollToItem(0)
    }

    LaunchedEffect(forgottenFavorites) {
        forgottenFavoritesLazyGridState.scrollToItem(0)
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .pullToRefresh(
                state = pullRefreshState,
                isRefreshing = isRefreshing,
                onRefresh = {
                    viewModel.refresh()
                    // The curated "Zemer Playlists" feed is separate from HomeViewModel; re-check it
                    // on every pull so newly curated playlists appear without an app restart.
                    zemerPlaylistsViewModel.refresh()
                    zemerStationsViewModel.refresh()
                    zemerGenresViewModel.refresh()
                    podcastGenresHomeViewModel.refresh()
                    podcastHomeRowsViewModel.refresh()
                    videoHomeRowsViewModel.refresh()
                    podcastSubscriptionsViewModel.fetchNewEpisodes()
                    zemerStatusesViewModel.refresh(force = true) // pull-to-refresh always re-fetches
                }
            ),
        contentAlignment = Alignment.TopStart
    ) {
        val horizontalLazyGridItemWidthFactor = if (maxWidth * 0.475f >= 320.dp) 0.475f else 0.9f
        val horizontalLazyGridItemWidth = maxWidth * horizontalLazyGridItemWidthFactor
        val quickPicksSnapLayoutInfoProvider = remember(quickPicksLazyGridState) {
            SnapLayoutInfoProvider(
                lazyGridState = quickPicksLazyGridState,
                positionInLayout = { layoutSize, itemSize ->
                    (layoutSize * horizontalLazyGridItemWidthFactor / 2f - itemSize / 2f)
                }
            )
        }
        val forgottenFavoritesSnapLayoutInfoProvider = remember(forgottenFavoritesLazyGridState) {
            SnapLayoutInfoProvider(
                lazyGridState = forgottenFavoritesLazyGridState,
                positionInLayout = { layoutSize, itemSize ->
                    (layoutSize * horizontalLazyGridItemWidthFactor / 2f - itemSize / 2f)
                }
            )
        }

        val hasLocalHomeContent =
            quickPicks.isNotEmpty() ||
                featuredPlaylists.isNotEmpty() ||
                forgottenFavorites.isNotEmpty() ||
                keepListening.isNotEmpty()
        val hasRemoteHomeContent =
            featuredArtists.isNotEmpty() ||
                featuredAlbums.isNotEmpty() ||
                featuredVideos.isNotEmpty() ||
                latestReleases.isNotEmpty() ||
                zemerPlaylists.isNotEmpty()
        // MUST stay scoped to HomeContentTab.MUSIC: this skeleton matches the MUSIC home layout (title +
        // card row). isLoading + the has*HomeContent flags are all music-VM state, so on Radio/Podcasts/
        // Videos it would paint a music-shaped skeleton that never resolves into anything on that tab.
        // Ratcheted by R22-home-shimmer (scripts/ui-audit.sh).
        val shouldShowShimmer = homeTab == HomeContentTab.MUSIC && (isLoading || (!hasLocalHomeContent && !hasRemoteHomeContent))

        LazyColumn(
            state = lazylistState,
            contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues()
        ) {
                // Content-type selector (Music / Podcasts / Radio / Video) — reuses the Library
                // ChipsRow. Each tab renders only its own shelves below; Video is dropped when videos
                // are blocked. See HomeContentTab.
                stickyHeader(key = "home_content_tabs", contentType = "header") {
                    // Opaque background so shelves scrolling under the pinned selector stay hidden.
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(top = 12.dp, bottom = 4.dp)
                    ) {
                        ChipsRow(
                            chips = homeContentChips,
                            currentValue = homeTab,
                            onValueUpdate = { it?.let(setHomeTab) },
                        )
                    }
                }

                if (homeTab == HomeContentTab.MUSIC) {
                // Genre chips carousel — the first thing on Home, above Quick Picks (owner
                // placement). Hidden by the Appearance toggle or when the catalog is
                // empty/unreachable (fail-soft, like every optional home row).
                if (showHomeGenres) {
                    homeGenres.takeIf { it.isNotEmpty() }?.let { genres ->
                        item(key = "genre_chips_title", contentType = "header") {
                            NavigationTitle(
                                title = stringResource(R.string.genres),
                                onClick = { navController.navigate(zemerGenresRoute()) },
                                modifier = Modifier.animateItem(),
                            )
                        }
                        item(key = "genre_chips_list", contentType = "grid") {
                            HomeGenresRow(
                                genres = genres,
                                navController = navController,
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                }

                quickPicks.takeIf { it.isNotEmpty() }?.let { quickPicks ->
                    item(key = "quick_picks_title", contentType = "header") {
                        NavigationTitle(
                            title = stringResource(R.string.quick_picks),
                            onClick = { navController.navigate("home_see_all/${HomeSeeAllRow.QUICK_PICKS.slug}") },
                            modifier = Modifier.animateItem()
                        )
                    }

                    item(key = "quick_picks_list", contentType = "grid") {
                        TrackImpressionsByKey(
                            surface = TrackingSurface.home("quick-picks"),
                            state = quickPicksLazyGridState,
                            parent = lazylistState,
                            parentKey = "quick_picks_list",
                            idOfKey = rememberRowImpressionIds(uniqueQuickPicks) { it.id },
                        )
                        LazyHorizontalGrid(
                            state = quickPicksLazyGridState,
                            rows = GridCells.Fixed(4),
                            flingBehavior = rememberSnapFlingBehavior(quickPicksSnapLayoutInfoProvider),
                            contentPadding = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal)
                                .asPaddingValues(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(ListItemHeight * 4)
                                .animateItem()
                        ) {
                            items(
                                items = uniqueQuickPicks,
                                key = { it.id },
                                contentType = { "song" }
                            ) { originalSong ->
                                // fetch song from database to keep updated
                                val song by database.song(originalSong.id)
                                    .collectAsState(initial = originalSong)

                                SongListItem(
                                    song = song!!,
                                    showInLibraryIcon = true,
                                    isActive = song!!.id == mediaMetadata?.id,
                                    isPlaying = isPlaying,
                                    isSwipeable = false,
                                    trailingContent = {
                                        MoreVertMenuButton(
                                            onClick = {
                                                menuState.show {
                                                    SongMenu(
                                                        originalSong = song!!,
                                                        navController = navController,
                                                        onDismiss = menuState::dismiss
                                                    )
                                                }
                                            }
                                        )
                                    },
                                    modifier = Modifier
                                        .width(horizontalLazyGridItemWidth)
                                        .combinedClickable(
                                            onClick = {
                                                if (activeRowTapTogglesPlayPause(song!!.id == mediaMetadata?.id, playerConnection.isStationBroadcast.value)) {
                                                    playerConnection.playPause()
                                                } else {
                                                    playerConnection.playQueue(
                                                        ZemerRadioQueue.song(
                                                            song!!.toMediaMetadata(), playerConnection.service
                                                        )
                                                    )
                                                }
                                            },
                                            onLongClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                menuState.show {
                                                    SongMenu(
                                                        originalSong = song!!,
                                                        navController = navController,
                                                        onDismiss = menuState::dismiss
                                                    )
                                                }
                                            }
                                        )
                                )
                            }
                        }
                    }
                }

                // "Music Statuses" (JewishStatus) — directly under Quick Picks. Hidden by the Appearance
                // toggle, when the third-party feed is empty/unreachable (fail-soft), OR when videos are
                // blocked by content filters: statuses are mostly video/media, so the same gate as the
                // Featured Videos row applies. The tap carries the creator's stable id (storyRoute).
                if (showHomeStatuses && !blockVideos) {
                    statusCreators.takeIf { it.isNotEmpty() }?.let { creators ->
                        item(key = "statuses_title", contentType = "header") {
                            NavigationTitle(
                                title = stringResource(R.string.statuses),
                                onClick = { navController.navigate("statuses") },
                                modifier = Modifier.animateItem(),
                            )
                        }
                        item(key = "statuses_list", contentType = "grid") {
                            HomeStatusesRow(
                                creators = creators,
                                seenPostIds = statusSeenPostIds,
                                contentFilter = statusContentFilter,
                                onCreatorClick = { creatorId -> navController.navigate(storyRoute(creatorId)) },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                }

                latestReleasesCapped.takeIf { it.isNotEmpty() }?.let { releases ->
                    item(key = "latest_releases_title", contentType = "header") {
                        NavigationTitle(
                            title = stringResource(R.string.latest_releases),
                            onClick = { navController.navigate("latest_releases") },
                            modifier = Modifier.animateItem()
                        )
                    }

                    item(key = "latest_releases_list", contentType = "grid") {
                        // Material 3 Expressive: Latest Releases is a multi-browse carousel - a large hero
                        // cover with shape-morphing, peeking neighbours. Each hero is the shared
                        // LatestReleaseCarouselItem (D-pad focus + library badges + play affordances),
                        // so it can't drift from the "See all" LatestReleaseCard.
                        val latestCarouselState = rememberCarouselState { releases.size }
                        HorizontalMultiBrowseCarousel(
                            state = latestCarouselState,
                            preferredItemWidth = 180.dp,
                            itemSpacing = 8.dp,
                            // Advance ONE item per swipe at a FIXED animation speed, so the carousel moves
                            // the same regardless of how hard it is flung (no velocity-scaled multi-item
                            // fling).
                            flingBehavior = CarouselDefaults.singleAdvanceFlingBehavior(
                                latestCarouselState,
                                tween(durationMillis = 400),
                            ),
                            contentPadding = WindowInsets.systemBars
                                .only(WindowInsetsSides.Horizontal)
                                .asPaddingValues(),
                            modifier = Modifier
                                .padding(vertical = 12.dp)
                                .height(180.dp)
                                .animateItem(),
                        ) { index ->
                            LatestReleaseCarouselItem(
                                release = releases[index],
                                navController = navController,
                                playerConnection = playerConnection,
                                mediaMetadata = mediaMetadata,
                                isPlaying = isPlaying,
                                coroutineScope = scope,
                            )
                        }
                    }
                }

                // Hand-curated "Zemer Playlists" (server-rendered for the user's content-filter
                // flags). Editorial order, never re-sorted; empty = nothing curated yet -> no section.
                zemerPlaylists.takeIf { it.isNotEmpty() }?.let { playlists ->
                    item(key = "zemer_playlists_title", contentType = "header") {
                        NavigationTitle(
                            title = stringResource(R.string.zemer_playlists),
                            onClick = { navController.navigate("zemer_playlists") },
                            modifier = Modifier.animateItem()
                        )
                    }

                    item(key = "zemer_playlists_list", contentType = "grid") {
                        LazyRow(
                            contentPadding = WindowInsets.systemBars
                                .only(WindowInsetsSides.Horizontal)
                                .asPaddingValues(),
                            modifier = Modifier.animateItem()
                        ) {
                            items(
                                items = playlists,
                                key = { it.id },
                                contentType = { "zemer_playlist" }
                            ) { playlist ->
                                ZemerCuratedPlaylistGridItem(
                                    playlist = playlist,
                                    // Home row: just the cover card, no sub-label at all (the count
                                    // + runtime stay on the wider See all grid).
                                    showSubtitle = false,
                                    modifier = Modifier.clickable {
                                        // The slug is server-controlled: encode so an unexpected
                                        // '/'/'?' can never break route matching (a crash on tap).
                                        navController.navigate("zemer_playlist/${Uri.encode(playlist.id)}")
                                    }
                                )
                            }
                        }
                    }
                }

                } // end MUSIC (part 1)

                if (homeTab == HomeContentTab.RADIO) {
                // "Zemer Radio" - the synchronized broadcast stations (one shared wall-clock
                // schedule per station; tap = tune in at the live position). Live cards only;
                // empty/unreachable hides the row (the /home-rows fail-soft convention). The Home
                // tab is never reachable from inside KidZone, satisfying the contract's
                // hide-in-kidZone rule the same way the curated shelf does.
                // Radio is a dedicated tab, not a discovery shelf: a shared-title header (no See-all arrow)
                // over a full 3-column grid. Chunked into Rows so the grid scrolls inside the Home
                // LazyColumn without a nested vertical scroll.
                zemerStations.takeIf { it.isNotEmpty() }?.let { stations ->
                    item(key = "zemer_stations_title", contentType = "header") {
                        NavigationTitle(
                            title = stringResource(R.string.zemer_radio_stations),
                            modifier = Modifier.animateItem()
                        )
                    }
                    items(
                        items = stations.chunked(3),
                        key = { row -> "zemer_stations_row_${row.first().id}" },
                        contentType = { "zemer_stations_grid_row" },
                    ) { rowStations ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                .animateItem(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            rowStations.forEach { station ->
                                ZemerStationCard(
                                    station = station,
                                    fillMaxWidth = true,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            // Tune in at the live broadcast position.
                                            playerConnection.playQueue(
                                                StationQueue(station.id, playerConnection.service)
                                            )
                                        }
                                )
                            }
                            // Pad a short final row so 1-2 cards keep the grid's cell width.
                            repeat(3 - rowStations.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }

                } // end RADIO

                if (homeTab == HomeContentTab.MUSIC) {
                featuredPlaylists.takeIf { it.isNotEmpty() }?.let { playlists ->
                    item(key = "featured_playlists_title", contentType = "header") {
                        NavigationTitle(
                            title = stringResource(R.string.featured_playlists),
                            onClick = { navController.navigate("home_see_all/${HomeSeeAllRow.FEATURED_PLAYLISTS.slug}") },
                            modifier = Modifier.animateItem()
                        )
                    }

                    item(key = "featured_playlists_list", contentType = "grid") {
                        LazyRow(
                            contentPadding = WindowInsets.systemBars
                                .only(WindowInsetsSides.Horizontal)
                                .asPaddingValues(),
                            modifier = Modifier.animateItem()
                        ) {
                            items(
                                items = uniqueFeaturedPlaylists,
                                key = { it.id },
                                contentType = { "playlist" }
                            ) { playlist ->
                                ytGridItem(playlist)
                            }
                        }
                    }
                }

                keepListening.takeIf { it.isNotEmpty() }?.let { keepListening ->
                    item(key = "keep_listening_title", contentType = "header") {
                        NavigationTitle(
                            title = stringResource(R.string.keep_listening),
                            onClick = { navController.navigate("home_see_all/${HomeSeeAllRow.KEEP_LISTENING.slug}") },
                            modifier = Modifier.animateItem()
                        )
                    }

                    item(key = "keep_listening_list", contentType = "grid") {
                        val rows = if (keepListening.size > 6) 2 else 1
                        val keepListeningGridState = rememberLazyGridState()
                        // Mixed row: albums and playlists sit alongside songs and simply have no
                        // videoId to report.
                        TrackImpressionsByKey(
                            surface = TrackingSurface.home("keep-listening"),
                            state = keepListeningGridState,
                            parent = lazylistState,
                            parentKey = "keep_listening_list",
                            idOfKey = rememberRowImpressionIds(keepListening) { (it as? Song)?.id },
                        )
                        LazyHorizontalGrid(
                            state = keepListeningGridState,
                            rows = GridCells.Fixed(rows),
                            contentPadding = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal)
                                .asPaddingValues(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height((GridThumbnailHeight + with(LocalDensity.current) {
                                    MaterialTheme.typography.bodyLarge.lineHeight.toDp() * 2 +
                                            MaterialTheme.typography.bodyMedium.lineHeight.toDp() * 2
                                }) * rows)
                                .animateItem()
                        ) {
                            items(
                                items = keepListening,
                                key = { it.id },
                                contentType = { "local_item" }
                            ) {
                                localGridItem(it)
                            }
                        }
                    }
                }

                forgottenFavorites.takeIf { it.isNotEmpty() }?.let { forgottenFavorites ->
                    item(key = "forgotten_favorites_title", contentType = "header") {
                        NavigationTitle(
                            title = stringResource(R.string.forgotten_favorites),
                            onClick = { navController.navigate("home_see_all/${HomeSeeAllRow.FORGOTTEN_FAVORITES.slug}") },
                            modifier = Modifier.animateItem()
                        )
                    }

                    item(key = "forgotten_favorites_list", contentType = "grid") {
                        TrackImpressionsByKey(
                            surface = TrackingSurface.home("forgotten-favorites"),
                            state = forgottenFavoritesLazyGridState,
                            parent = lazylistState,
                            parentKey = "forgotten_favorites_list",
                            idOfKey = rememberRowImpressionIds(uniqueForgottenFavorites) { it.id },
                        )
                        // take min in case list size is less than 4
                        val rows = min(4, forgottenFavorites.size)
                        LazyHorizontalGrid(
                            state = forgottenFavoritesLazyGridState,
                            rows = GridCells.Fixed(rows),
                            contentPadding = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal)
                                .asPaddingValues(),
                            flingBehavior = rememberSnapFlingBehavior(
                                forgottenFavoritesSnapLayoutInfoProvider
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(ListItemHeight * rows)
                                .animateItem()
                        ) {
                            items(
                                items = uniqueForgottenFavorites,
                                key = { it.id },
                                contentType = { "song" }
                            ) { originalSong ->
                                val song by database.song(originalSong.id)
                                    .collectAsState(initial = originalSong)

                                SongListItem(
                                    song = song!!,
                                    showInLibraryIcon = true,
                                    isActive = song!!.id == mediaMetadata?.id,
                                    isPlaying = isPlaying,
                                    isSwipeable = false,
                                    trailingContent = {
                                        MoreVertMenuButton(
                                            onClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                menuState.show {
                                                    SongMenu(
                                                        originalSong = song!!,
                                                        navController = navController,
                                                        onDismiss = menuState::dismiss
                                                    )
                                                }
                                            }
                                        )
                                    },
                                    modifier = Modifier
                                        .width(horizontalLazyGridItemWidth)
                                        .combinedClickable(
                                            onClick = {
                                                if (activeRowTapTogglesPlayPause(song!!.id == mediaMetadata?.id, playerConnection.isStationBroadcast.value)) {
                                                    playerConnection.playPause()
                                                } else {
                                                    playerConnection.playQueue(
                                                        ZemerRadioQueue.song(
                                                            song!!.toMediaMetadata(), playerConnection.service
                                                        )
                                                    )
                                                }
                                            },
                                            onLongClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                menuState.show {
                                                    SongMenu(
                                                        originalSong = song!!,
                                                        navController = navController,
                                                        onDismiss = menuState::dismiss
                                                    )
                                                }
                                            }
                                        )
                                )
                            }
                        }
                }
            }

            // Show featured artists
            if (featuredArtists.isNotEmpty()) {
                item(key = "featured_artists_title", contentType = "header") {
                    NavigationTitle(
                        title = stringResource(R.string.featured_artists),
                        onClick = { navController.navigate("home_see_all/${HomeSeeAllRow.FEATURED_ARTISTS.slug}") },
                        modifier = Modifier.animateItem()
                    )
                }

                item(key = "featured_artists_list", contentType = "grid") {
                    LazyRow(
                        contentPadding = WindowInsets.systemBars
                            .only(WindowInsetsSides.Horizontal)
                            .asPaddingValues(),
                        modifier = Modifier.animateItem()
                    ) {
                        items(
                            items = uniqueFeaturedArtists,
                            key = { "featured_artist_${it.id}" },
                            contentType = { "artist" }
                        ) { artist ->
                            ytGridItem(artist)
                        }
                    }
                }
            }

            // Show featured albums
            if (featuredAlbums.isNotEmpty()) {
                item(key = "featured_albums_title", contentType = "header") {
                    NavigationTitle(
                        title = stringResource(R.string.featured_albums),
                        onClick = { navController.navigate("home_see_all/${HomeSeeAllRow.FEATURED_ALBUMS.slug}") },
                        modifier = Modifier.animateItem()
                    )
                }

                item(key = "featured_albums_list", contentType = "grid") {
                    LazyRow(
                        contentPadding = WindowInsets.systemBars
                            .only(WindowInsetsSides.Horizontal)
                            .asPaddingValues(),
                        modifier = Modifier.animateItem()
                    ) {
                        items(
                            items = uniqueFeaturedAlbums,
                            key = { "featured_album_${it.id}" },
                            contentType = { "album" }
                        ) { album ->
                            ytGridItem(album)
                        }
                    }
                }
            }

            } // end MUSIC (part 2)

            if (homeTab == HomeContentTab.VIDEO) {
            // Shown to blocked-video users too — the rows play audio-first, so for them each shelf is
            // simply their "video songs" (relabelled, watch/download-video affordances gated off).

            // Featured Videos leads the tab as a full 16:9 hero carousel (moved to the top). It keeps the
            // same surface/playSource (resolver-default attribution); impressions are now per settled hero.
            videoHeroCarousel(
                row = HomeSeeAllRow.FEATURED_VIDEOS,
                keyPrefix = "featured_videos",
                surface = TrackingSurface.home("featured-videos"),
                playSource = null, // resolver default — the pre-rows attribution, unchanged
                videos = uniqueFeaturedVideos,
                blockVideos = blockVideos,
                parentListState = lazylistState,
                navController = navController,
                playerConnection = playerConnection,
                menuState = menuState,
                haptic = haptic,
                scope = scope,
                mediaMetadata = mediaMetadata,
                isPlaying = isPlaying,
            )

            videoSongsRow(
                row = HomeSeeAllRow.TRENDING_VIDEOS,
                keyPrefix = "trending_videos",
                surface = TrackingSurface.home("video-trending"),
                playSource = PlaySource.HOME_VIDEO_TRENDING,
                videos = uniqueTrendingVideos,
                blockVideos = blockVideos,
                parentListState = lazylistState,
                navController = navController,
                playerConnection = playerConnection,
                menuState = menuState,
                haptic = haptic,
                scope = scope,
                mediaMetadata = mediaMetadata,
                isPlaying = isPlaying,
            )

            videoSongsRow(
                row = HomeSeeAllRow.NEW_VIDEOS,
                keyPrefix = "new_videos",
                surface = TrackingSurface.home("video-new"),
                playSource = PlaySource.HOME_VIDEO_NEW,
                videos = uniqueNewVideos,
                blockVideos = blockVideos,
                parentListState = lazylistState,
                navController = navController,
                playerConnection = playerConnection,
                menuState = menuState,
                haptic = haptic,
                scope = scope,
                mediaMetadata = mediaMetadata,
                isPlaying = isPlaying,
            )

            // Top Video Artists: cards open the artist page, so plays attribute artist:UC… — no
            // per-row source or impressions needed (contract: the tracking handoff).
            if (uniqueTopVideoArtists.isNotEmpty()) {
                item(key = "top_video_artists_title", contentType = "header") {
                    NavigationTitle(
                        title = stringResource(R.string.top_video_artists),
                        onClick = { navController.navigate("home_see_all/${HomeSeeAllRow.TOP_VIDEO_ARTISTS.slug}") },
                        modifier = Modifier.animateItem()
                    )
                }
                item(key = "top_video_artists_list", contentType = "grid") {
                    LazyRow(
                        contentPadding = WindowInsets.systemBars
                            .only(WindowInsetsSides.Horizontal)
                            .asPaddingValues(),
                        modifier = Modifier.animateItem()
                    ) {
                        items(
                            items = uniqueTopVideoArtists,
                            key = { "top_video_artist_${it.id}" },
                            contentType = { "artist" }
                        ) { artist ->
                            ytGridItem(artist)
                        }
                    }
                }
            }

            } // end VIDEO

            if (homeTab == HomeContentTab.PODCASTS && !blockPodcasts) {
                // Podcast Genres strip: the podcast twin of the music genres chips — LEADS the Podcasts
                // tab. Own isolated fail-soft VM (empty -> hidden); arrow -> catalog. Gated on the SAME
                // "Show genres on home" preference as the music strip: the setting promises genres on
                // Home, and a toggle that silently left the Podcasts tab's strip up read as broken.
                homePodcastGenres.takeIf { showHomeGenres && it.isNotEmpty() }?.let { genres ->
                    item(key = "podcast_genre_chips_title", contentType = "header") {
                        NavigationTitle(
                            title = stringResource(R.string.genres),
                            onClick = { navController.navigate(zemerPodcastGenresRoute()) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                    item(key = "podcast_genre_chips_list", contentType = "grid") {
                        HomePodcastGenresRow(
                            genres = genres,
                            navController = navController,
                            modifier = Modifier.animateItem(),
                        )
                    }
                }

                // Continue Listening: in-progress episodes, most-recently-played first — the resume
                // affordance, sitting directly UNDER the Genres strip. Own isolated fail-soft VM (empty ->
                // hidden). Tapping resumes the episode (saved position restored on load by MusicService).
                continueEpisodes.takeIf { it.isNotEmpty() }?.let { eps ->
                    item(key = "continue_title", contentType = "header") {
                        NavigationTitle(
                            title = stringResource(R.string.continue_listening),
                            modifier = Modifier.animateItem(),
                        )
                    }
                    item(key = "continue_row", contentType = "grid") {
                        HomeContinueListeningRow(
                            episodes = eps,
                            onPlay = { song ->
                                playerConnection.playQueue(
                                    ListQueue(
                                        title = song.song.title,
                                        items = listOf(song.toMediaItem()),
                                        playSource = PlaySource.podcast(song.song.albumId),
                                    )
                                )
                            },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }

                // New Episodes: the newest episodes from the shows you're subscribed to (LOCAL-scoped, so it
                // works for anon + Google). The row already renders the FULL feed, so it has no See-all (like
                // Continue Listening). Tap plays the episode; long-press opens the episode menu (a SongItem
                // with isEpisode -> YouTubeSongMenu's episode branch: save-for-later / view podcast).
                podcastHomeRow(
                    keyPrefix = "home_new_episodes",
                    titleRes = R.string.new_episodes,
                    items = homeNewEpisodes,
                    isPlaying = isPlaying,
                    scope = scope,
                    activePlayingId = mediaMetadata?.id,
                    onClick = { episode ->
                        playerConnection.playQueue(
                            ListQueue(
                                title = episode.title,
                                items = listOf(episode.toMediaItem()),
                                playSource = PlaySource.podcast(episode.album?.id),
                            )
                        )
                    },
                    onLongClick = { episode ->
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        menuState.show {
                            YouTubeSongMenu(
                                song = episode,
                                navController = navController,
                                onDismiss = menuState::dismiss,
                            )
                        }
                    },
                )

                // Subscribed Channels: the user's own podcast-channel subscriptions (local bookmarks +
                // channels of subscribed shows, whitelist + female gated in the VM) — anon + Google alike.
                // SQUARE PodcastItem cards (an ArtistItem would render a circle, off next to the others).
                // See-all -> the home_see_all grid (like the other home rows, NOT the Library tab).
                podcastHomeRow(
                    keyPrefix = "home_sub_channels",
                    titleRes = R.string.subscribed_channels,
                    items = homeSubscribedChannels.map { channel ->
                        com.metrolist.innertube.models.PodcastItem(
                            id = channel.id,
                            title = channel.name,
                            author = null,
                            episodeCountText = null,
                            thumbnail = channel.thumbnailUrl,
                            playEndpoint = null,
                            shuffleEndpoint = null,
                            channelId = channel.id,
                        )
                    },
                    isPlaying = isPlaying,
                    scope = scope,
                    activePlayingId = mediaMetadata?.id,
                    onSeeAll = { navController.navigate("home_see_all/${HomeSeeAllRow.SUBSCRIBED_CHANNELS.slug}") },
                    onClick = { channel -> navController.navigateToArtist(channel.id, isPodcastChannel = true) },
                )

                // Featured: the hand-curated editorial shows (server `featured`), the LEAD show shelf and
                // strongest cold-start signal (zero telemetry). Channel-first routing; See-all -> ranked list.
                podcastHomeRow(
                    keyPrefix = "featured_podcasts",
                    titleRes = R.string.featured_podcasts,
                    items = featuredPodcasts,
                    isPlaying = isPlaying,
                    scope = scope,
                    activePlayingId = mediaMetadata?.id,
                    onSeeAll = { navController.navigate("home_see_all/${HomeSeeAllRow.FEATURED_PODCASTS.slug}") },
                    onClick = { podcast ->
                        whitelistedPodcastRoute(podcast.id, podcast.channelId)?.let { navController.navigate(it) }
                    },
                )

                // Top Podcasts: the telemetry-ranked shows row (Music-tab parity with Featured Artists). See
                // all -> the full ranked list of THESE shows (home_see_all, NOT the channel whitelist browse).
                // The server fills its own fallback while podcast telemetry is thin, so it's rarely empty.
                podcastHomeRow(
                    keyPrefix = "top_podcasts",
                    titleRes = R.string.top_podcasts,
                    items = topPodcasts,
                    isPlaying = isPlaying,
                    scope = scope,
                    activePlayingId = mediaMetadata?.id,
                    onSeeAll = { navController.navigate("home_see_all/${HomeSeeAllRow.TOP_PODCASTS.slug}") },
                    onClick = { podcast ->
                        // Channel-first routing, like every other show row (search/genre): open the host
                        // channel when known, else the show.
                        whitelistedPodcastRoute(podcast.id, podcast.channelId)?.let { navController.navigate(it) }
                    },
                )

                // Trending Episodes: the ranked episodes row (Music-tab parity with Featured Videos). Tap
                // plays by videoId; long-press opens the episode menu (EpisodeItem -> asSongItem() -> the
                // episode branch of YouTubeSongMenu, wired to its episode type).
                podcastHomeRow(
                    keyPrefix = "trending_episodes",
                    titleRes = R.string.trending_episodes,
                    items = trendingEpisodes,
                    isPlaying = isPlaying,
                    scope = scope,
                    activePlayingId = mediaMetadata?.id,
                    onSeeAll = { navController.navigate("home_see_all/${HomeSeeAllRow.TRENDING_EPISODES.slug}") },
                    onClick = { episode ->
                        playerConnection.playQueue(
                            ListQueue(
                                title = episode.title,
                                items = listOf(episode.toMediaItem()),
                                playSource = PlaySource.podcast(episode.podcast?.id),
                            )
                        )
                    },
                    onLongClick = { episode ->
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        menuState.show {
                            YouTubeSongMenu(
                                song = episode.asSongItem(),
                                navController = navController,
                                onDismiss = menuState::dismiss,
                            )
                        }
                    },
                )


            } // end PODCASTS

            if (shouldShowShimmer) {
                item(key = "loading_shimmer") {
                    ShimmerHost(
                        modifier = Modifier.animateItem()
                    ) {
                        TextPlaceholder(
                            height = 36.dp,
                            modifier = Modifier
                                .padding(12.dp)
                                .width(250.dp),
                        )
                        LazyRow(
                            contentPadding = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal).asPaddingValues(),
                        ) {
                            items(4) {
                                GridItemPlaceHolder()
                            }
                        }
                    }
                }
            }
        }

        LoadingIndicator(
            isRefreshing = isRefreshing,
            state = pullRefreshState,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(LocalPlayerAwareWindowInsets.current.asPaddingValues()),
        )
    }
}

/**
 * The impression key→videoId mapping for a home row. Every instrumented row keys its items by the
 * item's own id, so membership in this set IS the mapping.
 *
 * Deriving it from the same list the row renders is deliberate, and safe in a way an index-based
 * mapping is not: if the two ever drift, a key missing from the set is simply not reported and a set
 * entry that is never rendered never appears as a visible key. The failure mode is under-reporting,
 * which the exposure dampener treats as conservative — never reporting the wrong videoId under the
 * right surface, which would silently penalise a song that was never on screen.
 */
@Composable
private fun <T> rememberRowImpressionIds(items: List<T>, idOf: (T) -> String?): (Any?) -> String? {
    val ids = remember(items) { items.mapNotNull(idOf).toSet() }
    return remember(ids) { { key -> (key as? String)?.takeIf(ids::contains) } }
}

/**
 * One Podcasts-tab home row (a section header + a horizontal card row), the podcast twin of the music
 * tab's `ytGridItem` shelves. Extracted so New Episodes / Subscribed Channels / Featured / Top Podcasts /
 * Trending Episodes can't drift — before this, five hand-rolled copies of the same LazyRow diverged (one
 * lacked the dedup guard, one the See-all arrow, the episode rows the long-press menu).
 *
 * - Items are DEDUPED by id here, unconditionally: the keyed [LazyRow] crashes on a duplicate key, and a
 *   telemetry-ranked server row can repeat an id when its fallback fill overlaps its ranked set. The music
 *   rows guard this with `distinctBy`; this is the single chokepoint for the podcast rows.
 * - [onSeeAll] null hides the arrow (NavigationTitle gates it on onClick); pass it to expose the see-all.
 * - [onLongClick] null means tap-only; the episode rows pass a menu opener typed to their OWN item so the
 *   menu dispatches correctly (a SongItem episode vs. an EpisodeItem converted with asSongItem()).
 * - [activePlayingId] is the currently-playing videoId; an episode card highlights when it matches, a
 *   show/channel card never does (its id is an MPSP/UC id, not a videoId).
 */
@OptIn(ExperimentalFoundationApi::class)
private fun <T : YTItem> LazyListScope.podcastHomeRow(
    keyPrefix: String,
    @StringRes titleRes: Int,
    items: List<T>,
    isPlaying: Boolean,
    scope: CoroutineScope,
    activePlayingId: String?,
    onSeeAll: (() -> Unit)? = null,
    onClick: (T) -> Unit,
    onLongClick: ((T) -> Unit)? = null,
) {
    val unique = items.distinctBy { it.id }
    if (unique.isEmpty()) return
    item(key = "${keyPrefix}_title", contentType = "header") {
        NavigationTitle(title = stringResource(titleRes), onClick = onSeeAll, modifier = Modifier.animateItem())
    }
    item(key = "${keyPrefix}_list", contentType = "grid") {
        LazyRow(
            contentPadding = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal).asPaddingValues(),
            modifier = Modifier.animateItem(),
        ) {
            items(items = unique, key = { "${keyPrefix}_${it.id}" }, contentType = { keyPrefix }) { item ->
                YouTubeGridItem(
                    item = item,
                    isActive = item.id == activePlayingId,
                    isPlaying = isPlaying,
                    coroutineScope = scope,
                    thumbnailRatio = 1f,
                    modifier = Modifier
                        .combinedClickable(
                            onClick = { onClick(item) },
                            onLongClick = onLongClick?.let { open -> { open(item) } },
                        )
                        .animateItem(),
                )
            }
        }
    }
}

/**
 * One video-song home row — Featured / Trending / New all render through this single definition:
 * relabel-aware title from [HomeSeeAllRow.displayTitleRes] + the see-all arrow, strict per-row
 * impressions on [surface], square badge-less video cards (center crop hides the baked-in 16:9 title
 * text, issue #84), audio-first taps declaring [playSource] (null = the resolver default), and
 * long-press menus audio-gated when videos are blocked. Extracted so an impression or gating fix can
 * never land in one copy and miss another.
 */
@OptIn(ExperimentalFoundationApi::class)
private fun LazyListScope.videoSongsRow(
    row: HomeSeeAllRow,
    keyPrefix: String,
    surface: String,
    playSource: String?,
    videos: List<SongItem>,
    blockVideos: Boolean,
    parentListState: androidx.compose.foundation.lazy.LazyListState,
    navController: NavController,
    playerConnection: com.jtech.zemer.playback.PlayerConnection,
    menuState: com.jtech.zemer.ui.component.MenuState,
    haptic: androidx.compose.ui.hapticfeedback.HapticFeedback,
    scope: CoroutineScope,
    mediaMetadata: com.jtech.zemer.models.MediaMetadata?,
    isPlaying: Boolean,
) {
    if (videos.isEmpty()) return
    item(key = "${keyPrefix}_title", contentType = "header") {
        NavigationTitle(
            title = stringResource(row.displayTitleRes(blockVideos)),
            onClick = { navController.navigate("home_see_all/${row.slug}") },
            modifier = Modifier.animateItem()
        )
    }
    item(key = "${keyPrefix}_list", contentType = "grid") {
        val rowState = rememberLazyListState()
        TrackImpressionsByKey(
            surface = surface,
            state = rowState,
            parent = parentListState,
            parentKey = "${keyPrefix}_list",
            // Keyed by the plain videoId (unique — lists are distinctBy id), so the row's key IS its
            // impression id and the two cannot drift.
            idOfKey = rememberRowImpressionIds(videos) { it.id },
        )
        LazyRow(
            state = rowState,
            contentPadding = WindowInsets.systemBars
                .only(WindowInsetsSides.Horizontal)
                .asPaddingValues(),
            modifier = Modifier.animateItem()
        ) {
            items(items = videos, key = { it.id }, contentType = { "video" }) { video ->
                YouTubeGridItem(
                    item = video,
                    isActive = mediaMetadata?.id == video.id,
                    isPlaying = isPlaying,
                    coroutineScope = scope,
                    thumbnailRatio = 1f,
                    showVideoBadge = false,
                    modifier = Modifier
                        .combinedClickable(
                            onClick = {
                                // Audio-first always (I2); video is a per-play in-player toggle (D3).
                                playerConnection.playQueue(
                                    if (playSource != null) {
                                        ZemerRadioQueue.song(video.toMediaMetadata(), playerConnection.service, playSource)
                                    } else {
                                        ZemerRadioQueue.song(video.toMediaMetadata(), playerConnection.service)
                                    }
                                )
                            },
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                menuState.show {
                                    YouTubeSongMenu(
                                        song = video,
                                        navController = navController,
                                        onDismiss = menuState::dismiss,
                                        isVideo = video.isVideo && !blockVideos,
                                    )
                                }
                            }
                        )
                        .animateItem()
                )
            }
        }
    }
}
