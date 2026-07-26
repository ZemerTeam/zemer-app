package com.jtech.zemer.ui.screens

import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults.Indicator
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
import androidx.navigation.compose.currentBackStackEntryAsState
import com.jtech.zemer.LocalDatabase
import com.jtech.zemer.LocalPlayerAwareWindowInsets
import com.jtech.zemer.LocalPlayerConnection
import com.jtech.zemer.R
import com.jtech.zemer.constants.BlockVideosKey
import com.jtech.zemer.constants.GridThumbnailHeight
import com.jtech.zemer.constants.ListItemHeight
import com.jtech.zemer.db.entities.Album
import com.jtech.zemer.db.entities.Artist
import com.jtech.zemer.db.entities.LocalItem
import com.jtech.zemer.db.entities.Playlist
import com.jtech.zemer.db.entities.Song
import com.jtech.zemer.models.toMediaMetadata
import com.jtech.zemer.playback.queues.LocalAlbumRadio
import com.jtech.zemer.playback.queues.YouTubeAlbumRadio
import com.jtech.zemer.playback.queues.YouTubeQueue
import com.jtech.zemer.search.SearchProvider
import com.jtech.zemer.search.onlineAlbumRoute
import com.jtech.zemer.search.onlinePlaylistRoute
import com.jtech.zemer.viewmodels.HomeSeeAllRow
import com.jtech.zemer.tracking.TrackImpressionsByKey
import com.jtech.zemer.tracking.TrackingSurface
import com.jtech.zemer.ui.component.AlbumGridItem
import com.jtech.zemer.ui.component.ArtistGridItem
import com.jtech.zemer.ui.component.LocalBottomSheetPageState
import com.jtech.zemer.ui.component.LocalMenuState
import com.jtech.zemer.ui.component.NavigationTitle
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
import com.jtech.zemer.ui.screens.videoRoute
import com.jtech.zemer.ui.utils.SnapLayoutInfoProvider
import com.jtech.zemer.utils.rememberPreference
import com.jtech.zemer.latestreleases.LatestReleaseCard
import com.jtech.zemer.viewmodels.HomeViewModel
import com.jtech.zemer.viewmodels.LatestReleasesViewModel
import com.jtech.zemer.viewmodels.ZemerCuratedPlaylistsViewModel
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.WatchEndpoint
import com.metrolist.innertube.models.YTItem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.min
import kotlin.random.Random

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
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
    // The curated endpoint's freshness contract is a plain re-fetch on screen open (single-digit-ms
    // server reads) — this also picks up a card removed by curation while a detail open 404'd.
    LaunchedEffect(Unit) { zemerPlaylistsViewModel.refresh() }
    val (blockVideos, _) = rememberPreference(BlockVideosKey, false)
    homeUiState.isNewUser

    val allLocalItems =
        remember(quickPicks, forgottenFavorites, keepListening) {
            (quickPicks + forgottenFavorites + keepListening).filter { it is Song || it is Album }
        }
    val allYtItems =
        remember(featuredVideos, featuredAlbums, featuredArtists, blockVideos) {
            buildList {
                if (!blockVideos) {
                    addAll(featuredVideos)
                }
                // A featured (Zemer) album is lucky-playable only with a real OLAK playlist id: when
                // /home-rows sends none, toAlbumItem falls playlistId back to the MPRE browseId, which
                // YouTubeAlbumRadio's InnerTube lookup can't resolve — a lucky pick on it would dead-press.
                // Keep only albums with a distinct (resolvable) playlist id, same guard as the artists below.
                addAll(featuredAlbums.filter { it.playlistId != it.id })
                // Telemetry-ranked (Zemer) artist cards carry no InnerTube radio endpoint, so the lucky
                // shuffle can't start radio from them — keep only artists that can actually play (the
                // scrape-fallback ones) in the pool, so a lucky pick never dead-presses.
                addAll(featuredArtists.filter { it.radioEndpoint != null })
            }
        }

    // Memoized distinct lists to avoid creating new lists on every recomposition
    val uniqueQuickPicks = remember(quickPicks) { quickPicks.distinctBy { it.id } }
    val uniqueFeaturedPlaylists = remember(featuredPlaylists) { featuredPlaylists.distinctBy { it.id } }
    val uniqueForgottenFavorites = remember(forgottenFavorites) { forgottenFavorites.distinctBy { it.id } }
    // Cap the Home row; the full list lives behind "See all" (latest_releases screen).
    val latestReleasesCapped = remember(latestReleases) { latestReleases.take(12) }
    val uniqueFeaturedArtists = remember(featuredArtists) { featuredArtists.distinctBy { it.id } }
    val uniqueFeaturedAlbums = remember(featuredAlbums) { featuredAlbums.distinctBy { it.id } }
    val uniqueFeaturedVideos = remember(featuredVideos) { featuredVideos.distinctBy { it.id } }

    val isLoading: Boolean = homeUiState.isLoading
    val isRefreshing = homeUiState.isRefreshing
    val pullRefreshState = rememberPullToRefreshState()

    val quickPicksLazyGridState = rememberLazyGridState()
    val forgottenFavoritesLazyGridState = rememberLazyGridState()

    val scope = rememberCoroutineScope()
    val lazylistState = rememberLazyListState()
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
        if (allLocalItems.isEmpty() && allYtItems.isEmpty()) {
            android.widget.Toast.makeText(context, R.string.radio_empty_message, android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        val local = when {
            allLocalItems.isNotEmpty() && allYtItems.isNotEmpty() -> Random.nextFloat() < 0.5
            allLocalItems.isNotEmpty() -> true
            else -> false
        }
        if (local) {
            when (val luckyItem = allLocalItems.random()) {
                is Song -> playerConnection.playQueue(YouTubeQueue.radio(luckyItem.toMediaMetadata(), database))
                is Album -> {
                    scope.launch {
                        val albumWithSongs = database.albumWithSongs(luckyItem.id).first()
                        albumWithSongs?.let {
                            playerConnection.playQueue(LocalAlbumRadio(it, database = database))
                        }
                    }
                }

                is Artist -> {}
                is Playlist -> {}
            }
        } else {
            when (val luckyItem = allYtItems.random()) {
                is SongItem -> playerConnection.playQueue(YouTubeQueue.radio(luckyItem.toMediaMetadata(), database))
                is AlbumItem -> playerConnection.playQueue(YouTubeAlbumRadio(luckyItem.playlistId, database))
                is ArtistItem -> luckyItem.radioEndpoint?.let {
                    playerConnection.playQueue(YouTubeQueue(it, preloadItem = null, database))
                }

                is PlaylistItem -> luckyItem.playEndpoint?.let {
                    playerConnection.playQueue(YouTubeQueue(it, preloadItem = null, database))
                }
            }
        }
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
                            if (it.id == mediaMetadata?.id) {
                                playerConnection.playPause()
                            } else {
                                playerConnection.playQueue(
                                    YouTubeQueue.radio(it.toMediaMetadata(), database),
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
                            navController.navigate("album/${it.id}")
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
                            navController.navigate("artist/${it.id}")
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
                            is SongItem -> playerConnection.playQueue(
                                YouTubeQueue(
                                    item.endpoint ?: WatchEndpoint(
                                        videoId = item.id
                                    ), item.toMediaMetadata(), database
                                )
                            )

                            // Only the featured-albums row passes an AlbumItem through ytGridItem, so this
                            // branch is the featured album; route Zemer-sourced ones via the server album path.
                            is AlbumItem ->
                                if (featuredAlbumsAreZemer) {
                                    navController.navigate(SearchProvider.ZEMER.onlineAlbumRoute(item))
                                } else {
                                    navController.navigate("album/${item.id}")
                                }
                            is ArtistItem -> navController.navigate("artist/${item.id}")
                            // Featured playlists: Zemer community playlists open via the server /playlist path
                            // and tag plays `community:<id>` (they're the discovery-sourced community row).
                            is PlaylistItem ->
                                if (featuredPlaylistsAreZemer) {
                                    navController.navigate(SearchProvider.ZEMER.onlinePlaylistRoute(item.id, community = true))
                                } else {
                                    navController.navigate("online_playlist/${item.id}")
                                }
                        }
                    },
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        menuState.show {
                            when (item) {
                                is SongItem -> YouTubeSongMenu(
                                    song = item,
                                    navController = navController,
                                    onDismiss = menuState::dismiss
                                )

                                is AlbumItem -> YouTubeAlbumMenu(
                                    albumItem = item,
                                    navController = navController,
                                    onDismiss = menuState::dismiss
                                )

                                is ArtistItem -> YouTubeArtistMenu(
                                    artist = item,
                                    onDismiss = menuState::dismiss
                                )

                                is PlaylistItem -> YouTubePlaylistMenu(
                                    playlist = item,
                                    coroutineScope = scope,
                                    onDismiss = menuState::dismiss
                                )
                            }
                        }
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
                (!blockVideos && featuredVideos.isNotEmpty()) ||
                latestReleases.isNotEmpty() ||
                zemerPlaylists.isNotEmpty()
        val shouldShowShimmer = isLoading || (!hasLocalHomeContent && !hasRemoteHomeContent)

        LazyColumn(
            state = lazylistState,
            contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues()
        ) {
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
                                        IconButton(
                                            onClick = {
                                                menuState.show {
                                                    SongMenu(
                                                        originalSong = song!!,
                                                        navController = navController,
                                                        onDismiss = menuState::dismiss
                                                    )
                                                }
                                            }
                                        ) {
                                            Icon(
                                                painter = painterResource(R.drawable.more_vert),
                                                contentDescription = null
                                            )
                                        }
                                    },
                                    modifier = Modifier
                                        .width(horizontalLazyGridItemWidth)
                                        .combinedClickable(
                                            onClick = {
                                                if (song!!.id == mediaMetadata?.id) {
                                                    playerConnection.playPause()
                                                } else {
                                                    playerConnection.playQueue(
                                                        YouTubeQueue.radio(
                                                            song!!.toMediaMetadata(), database
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

                latestReleasesCapped.takeIf { it.isNotEmpty() }?.let { releases ->
                    item(key = "latest_releases_title", contentType = "header") {
                        NavigationTitle(
                            title = stringResource(R.string.latest_releases),
                            onClick = { navController.navigate("latest_releases") },
                            modifier = Modifier.animateItem()
                        )
                    }

                    item(key = "latest_releases_list", contentType = "grid") {
                        LazyRow(
                            contentPadding = WindowInsets.systemBars
                                .only(WindowInsetsSides.Horizontal)
                                .asPaddingValues(),
                            modifier = Modifier.animateItem()
                        ) {
                            items(
                                items = releases,
                                key = { it.browseId },
                                contentType = { "album" }
                            ) { release ->
                                LatestReleaseCard(
                                    release = release,
                                    navController = navController,
                                    playerConnection = playerConnection,
                                    database = database,
                                    mediaMetadata = mediaMetadata,
                                    isPlaying = isPlaying,
                                    asGrid = true,
                                    coroutineScope = scope,
                                )
                            }
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
                                    // The compact 128dp row card: runtime overflows its two subtitle
                                    // lines, so it shows the song count alone (full label on See all).
                                    showRuntime = false,
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
                                        IconButton(
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
                                        ) {
                                            Icon(
                                                painter = painterResource(R.drawable.more_vert),
                                                contentDescription = null
                                            )
                                        }
                                    },
                                    modifier = Modifier
                                        .width(horizontalLazyGridItemWidth)
                                        .combinedClickable(
                                            onClick = {
                                                if (song!!.id == mediaMetadata?.id) {
                                                    playerConnection.playPause()
                                                } else {
                                                    playerConnection.playQueue(
                                                        YouTubeQueue.radio(
                                                            song!!.toMediaMetadata(), database
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

            if (!blockVideos && featuredVideos.isNotEmpty()) {
                item(key = "featured_videos_title", contentType = "header") {
                    NavigationTitle(
                        title = stringResource(R.string.featured_videos),
                        onClick = { navController.navigate("home_see_all/${HomeSeeAllRow.FEATURED_VIDEOS.slug}") },
                        modifier = Modifier.animateItem()
                    )
                }

                item(key = "featured_videos_list", contentType = "grid") {
                    val featuredVideosRowState = rememberLazyListState()
                    TrackImpressionsByKey(
                        surface = TrackingSurface.home("featured-videos"),
                        state = featuredVideosRowState,
                        parent = lazylistState,
                        parentKey = "featured_videos_list",
                        idOfKey = rememberRowImpressionIds(uniqueFeaturedVideos) { it.id },
                    )
                    LazyRow(
                        state = featuredVideosRowState,
                        contentPadding = WindowInsets.systemBars
                            .only(WindowInsetsSides.Horizontal)
                            .asPaddingValues(),
                        modifier = Modifier.animateItem()
                    ) {
                        items(
                            items = uniqueFeaturedVideos,
                            // Keyed by the plain videoId (unique — the list is distinctBy id), so
                            // the row's key IS its impression id and the two cannot drift.
                            key = { it.id },
                            contentType = { "video" }
                        ) { video ->
                            YouTubeGridItem(
                                item = video,
                                isActive = mediaMetadata?.id == video.id,
                                isPlaying = isPlaying,
                                coroutineScope = scope,
                                // Square (1f) to match the artist screen's video sections: a center
                                // crop hides most of the title text YouTube bakes into the 16:9 video
                                // thumbnail, which is illegible behind the card. See issue #84.
                                thumbnailRatio = 1f,
                                modifier = Modifier
                                    .combinedClickable(
                                        onClick = {
                                            val artistDisplay = video.artists.joinToString(" • ") { it.name }
                                            navController.navigate(videoRoute(video.id, video.title, artistDisplay))
                                        },
                                        onLongClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            menuState.show {
                                                YouTubeSongMenu(
                                                    song = video,
                                                    navController = navController,
                                                    onDismiss = menuState::dismiss,
                                                    isVideo = true,
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

        Indicator(
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
