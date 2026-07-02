package com.jtech.zemer.ui.screens.search

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.jtech.zemer.LocalDatabase
import com.jtech.zemer.LocalPlayerAwareWindowInsets
import com.jtech.zemer.LocalPlayerConnection
import com.jtech.zemer.R
import com.jtech.zemer.extensions.togglePlayPause
import com.jtech.zemer.models.toMediaMetadata
import com.jtech.zemer.playback.queues.YouTubeQueue
import com.jtech.zemer.constants.BlockVideosKey
import com.jtech.zemer.constants.SearchProviderKey
import com.jtech.zemer.search.SearchProvider
import com.jtech.zemer.search.onlineAlbumRoute
import com.jtech.zemer.search.onlinePlaylistRoute
import com.jtech.zemer.utils.rememberEnumPreference
import com.jtech.zemer.utils.rememberPreference
import com.jtech.zemer.ui.component.AppStateView
import com.jtech.zemer.ui.component.ChipsRow
import com.jtech.zemer.ui.component.LocalMenuState
import com.jtech.zemer.ui.component.NavigationTitle
import com.jtech.zemer.ui.component.YouTubeListItem
import com.jtech.zemer.ui.component.shimmer.ListItemPlaceHolder
import com.jtech.zemer.ui.component.shimmer.ShimmerHost
import com.jtech.zemer.ui.menu.YouTubeAlbumMenu
import com.jtech.zemer.ui.menu.YouTubeArtistMenu
import com.jtech.zemer.ui.menu.YouTubePlaylistMenu
import com.jtech.zemer.ui.menu.YouTubeSongMenu
import com.jtech.zemer.viewmodels.OnlineSearchViewModel
import com.metrolist.innertube.YouTube.SearchFilter.Companion.FILTER_ALBUM
import com.metrolist.innertube.YouTube.SearchFilter.Companion.FILTER_ARTIST
import com.metrolist.innertube.YouTube.SearchFilter.Companion.FILTER_COMMUNITY_PLAYLIST
import com.metrolist.innertube.YouTube.SearchFilter.Companion.FILTER_FEATURED_PLAYLIST
import com.metrolist.innertube.YouTube.SearchFilter.Companion.FILTER_SONG
import com.metrolist.innertube.YouTube.SearchFilter.Companion.FILTER_VIDEO
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.WatchEndpoint
import com.metrolist.innertube.models.YTItem
import com.jtech.zemer.ui.screens.videoRoute
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun OnlineSearchResult(
    navController: NavController,
    viewModel: OnlineSearchViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val haptic = LocalHapticFeedback.current
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    val coroutineScope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()
    val chipsFocusRequester = remember { FocusRequester() }
    val firstResultFocusRequester = remember { FocusRequester() }

    // Initialize chips focus after a short delay to prioritize content on TV remotes
    LaunchedEffect(Unit) {
        delay(900)
        chipsFocusRequester.requestFocus()
    }

    val searchFilter by viewModel.filter.collectAsState()
    val (blockVideos, _) = rememberPreference(BlockVideosKey, false)
    // On the default (Zemer) engine, the error / no-results states offer a one-tap switch to YouTube
    // search — the documented recovery — since the engine toggle (only in the expanded search bar) is
    // not reachable from this results screen. Flipping the preference reloads via the ViewModel.
    val (searchProvider, onSearchProviderChange) = rememberEnumPreference(SearchProviderKey, SearchProvider.ZEMER)
    val youtubeFallbackLabel = stringResource(R.string.search_try_youtube)
    val onYoutubeFallback: (() -> Unit)? =
        if (searchProvider == SearchProvider.ZEMER) {
            { onSearchProviderChange(SearchProvider.YOUTUBE) }
        } else {
            null
        }
    val searchSummary = viewModel.summaryPage
    val isSummaryLoading by viewModel.isSummaryLoading.collectAsState()
    val summaryError by viewModel.summaryError.collectAsState()
    val itemsPage by remember(searchFilter) {
        derivedStateOf {
            searchFilter?.value?.let {
                viewModel.viewStateMap[it]
            }
        }
    }
    val filterLoading = searchFilter?.value?.let { viewModel.filterLoading[it] } ?: false
    val filterError = searchFilter?.value?.let { viewModel.filterError[it] }

    LaunchedEffect(lazyListState) {
        snapshotFlow {
            lazyListState.layoutInfo.visibleItemsInfo.any { it.key == "loading" }
        }.collect { shouldLoadMore ->
            if (shouldLoadMore && !filterLoading) {
                viewModel.loadMore()
            }
        }
    }

    val ytItemContent: @Composable LazyItemScope.(YTItem) -> Unit = { item: YTItem ->
        val longClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            menuState.show {
                when (item) {
                    is SongItem ->
                        YouTubeSongMenu(
                            song = item,
                            navController = navController,
                            onDismiss = menuState::dismiss,
                            isVideo = !blockVideos && searchFilter?.value == FILTER_VIDEO.value,
                        )

                    is AlbumItem ->
                        YouTubeAlbumMenu(
                            albumItem = item,
                            navController = navController,
                            onDismiss = menuState::dismiss,
                        )

                    is ArtistItem ->
                        YouTubeArtistMenu(
                            artist = item,
                            onDismiss = menuState::dismiss,
                        )

                    is PlaylistItem ->
                        YouTubePlaylistMenu(
                            playlist = item,
                            coroutineScope = coroutineScope,
                            onDismiss = menuState::dismiss,
                        )
                }
            }
        }
        YouTubeListItem(
            item = item,
            isActive =
            when (item) {
                is SongItem -> mediaMetadata?.id == item.id
                is AlbumItem -> mediaMetadata?.album?.id == item.id
                else -> false
            },
            isPlaying = isPlaying,
            trailingContent = {
                IconButton(
                    onClick = longClick,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.more_vert),
                        contentDescription = null,
                    )
                }
            },
            modifier =
            Modifier
                .focusProperties {
                    up = chipsFocusRequester
                    down = FocusRequester.Default
                }
                .onKeyEvent { event ->
                    when {
                        event.key == Key.Enter || event.key == Key.DirectionCenter -> {
                            when (item) {
                                is SongItem -> {
                                    val isVideoFilter = !blockVideos && searchFilter?.value == FILTER_VIDEO.value
                                    if (isVideoFilter) {
                                        val artistDisplay = item.artists.joinToString(" • ") { it.name }
                                        navController.navigate(videoRoute(item.id, item.title, artistDisplay))
                                    } else if (item.id == mediaMetadata?.id) {
                                        playerConnection.player.togglePlayPause()
                                    } else {
                                        playerConnection.playQueue(
                                            YouTubeQueue(
                                                WatchEndpoint(videoId = item.id),
                                                item.toMediaMetadata(),
                                                database
                                            )
                                        )
                                    }
                                }
                                is AlbumItem -> navController.navigate(searchProvider.onlineAlbumRoute(item))
                                is ArtistItem -> navController.navigate("artist/${item.id}")
                                is PlaylistItem -> navController.navigate(searchProvider.onlinePlaylistRoute(item.id))
                            }
                            true
                        }
                        else -> false
                    }
                }
                .combinedClickable(
                    onClick = {
                        when (item) {
                            is SongItem -> {
                                val isVideoFilter = !blockVideos && searchFilter?.value == FILTER_VIDEO.value
                                if (isVideoFilter) {
                                    val artistDisplay = item.artists.joinToString(" • ") { it.name }
                                    navController.navigate(videoRoute(item.id, item.title, artistDisplay))
                                } else if (item.id == mediaMetadata?.id) {
                                    playerConnection.player.togglePlayPause()
                                } else {
                                    playerConnection.playQueue(
                                        YouTubeQueue(
                                            WatchEndpoint(videoId = item.id),
                                            item.toMediaMetadata(),
                                            database
                                        )
                                    )
                                }
                            }

                            is AlbumItem -> navController.navigate(searchProvider.onlineAlbumRoute(item))
                            is ArtistItem -> navController.navigate("artist/${item.id}")
                            is PlaylistItem -> navController.navigate(searchProvider.onlinePlaylistRoute(item.id))
                        }
                    },
                    onLongClick = longClick,
                )
                .animateItem(),
        )
    }

    LazyColumn(
        state = lazyListState,
        contentPadding =
        LocalPlayerAwareWindowInsets.current
            .asPaddingValues(),
    ) {
        stickyHeader {
            ChipsRow(
                chips =
                buildList {
                    add(null to stringResource(R.string.filter_all))
                    add(FILTER_SONG to stringResource(R.string.filter_songs))
                    if (!blockVideos) {
                        add(FILTER_VIDEO to stringResource(R.string.filter_videos))
                    }
                    add(FILTER_ALBUM to stringResource(R.string.filter_albums))
                    add(FILTER_ARTIST to stringResource(R.string.filter_artists))
                    add(FILTER_COMMUNITY_PLAYLIST to stringResource(R.string.filter_community_playlists))
                    add(FILTER_FEATURED_PLAYLIST to stringResource(R.string.filter_featured_playlists))
                },
                currentValue = searchFilter,
                onValueUpdate = {
                    if (viewModel.filter.value != it) {
                        viewModel.filter.value = it
                    }
                    coroutineScope.launch {
                        lazyListState.animateScrollToItem(0)
                    }
                },
                firstChipFocusRequester = chipsFocusRequester,
                downFocusRequester = firstResultFocusRequester,
                modifier =
                Modifier
                    .background(MaterialTheme.colorScheme.surface)
                    .windowInsetsPadding(
                        WindowInsets.systemBars
                            .only(WindowInsetsSides.Horizontal)
                    )
                    .fillMaxWidth()
            )
        }
        if (searchFilter == null) {
            when {
                summaryError != null -> {
                    item {
                        AppStateView(
                            title = stringResource(R.string.search_error_title),
                            subtitle = summaryError ?: "",
                            icon = R.drawable.search,
                            actionLabel = stringResource(R.string.search_retry),
                            onAction = viewModel::refresh,
                            secondaryActionLabel = onYoutubeFallback?.let { youtubeFallbackLabel },
                            onSecondaryAction = onYoutubeFallback,
                            modifier = Modifier
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                                .animateItem(),
                        )
                    }
                }

                searchSummary == null && isSummaryLoading -> {
                    item {
                        ShimmerHost {
                            repeat(8) {
                                ListItemPlaceHolder()
                            }
                        }
                    }
                }

                searchSummary?.summaries?.isEmpty() == true -> {
                    item {
                        AppStateView(
                            title = stringResource(R.string.search_empty_title),
                            subtitle = stringResource(R.string.no_results_found),
                            icon = R.drawable.search,
                            actionLabel = stringResource(R.string.search_retry),
                            onAction = viewModel::refresh,
                            secondaryActionLabel = onYoutubeFallback?.let { youtubeFallbackLabel },
                            onSecondaryAction = onYoutubeFallback,
                            modifier = Modifier
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                                .animateItem(),
                        )
                    }
                }

                else -> {
                    searchSummary?.summaries?.forEach { summary ->
                        if (summary.items.isNotEmpty()) {
                            item {
                                val summaryFilter =
                                    summary.items.firstOrNull()?.let(::mapItemToFilter)
                                        ?: when (summary.title.lowercase()) {
                                            "albums" -> FILTER_ALBUM
                                            "songs" -> FILTER_SONG
                                            "artists" -> FILTER_ARTIST
                                            "videos" -> if (!blockVideos) FILTER_VIDEO else null
                                            "community playlists" -> FILTER_COMMUNITY_PLAYLIST
                                            "featured playlists" -> FILTER_FEATURED_PLAYLIST
                                            else -> null
                                        }
                                NavigationTitle(
                                    title = summary.title,
                                    onClick = {
                                        summaryFilter?.let {
                                            viewModel.filter.value = summaryFilter
                                            coroutineScope.launch {
                                                lazyListState.animateScrollToItem(0)
                                            }
                                        }
                                    }
                                )
                            }

                            items(
                                items = summary.items,
                                key = { "${summary.title}/${it.id}/${summary.items.indexOf(it)}" },
                                itemContent = ytItemContent,
                            )
                        }
                    }
                }
            }
        } else {
            when {
                filterError != null -> {
                    item {
                        AppStateView(
                            title = stringResource(R.string.search_error_title),
                            subtitle = filterError,
                            icon = R.drawable.search,
                            actionLabel = stringResource(R.string.search_retry),
                            onAction = viewModel::refresh,
                            secondaryActionLabel = onYoutubeFallback?.let { youtubeFallbackLabel },
                            onSecondaryAction = onYoutubeFallback,
                            modifier = Modifier
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                                .animateItem(),
                        )
                    }
                }

                itemsPage == null && filterLoading -> {
                    item {
                        ShimmerHost {
                            repeat(8) {
                                ListItemPlaceHolder()
                            }
                        }
                    }
                }

                itemsPage?.items?.isEmpty() == true -> {
                    item {
                        AppStateView(
                            title = stringResource(R.string.search_empty_title),
                            subtitle = stringResource(R.string.no_results_found),
                            icon = R.drawable.search,
                            actionLabel = stringResource(R.string.search_retry),
                            onAction = viewModel::refresh,
                            secondaryActionLabel = onYoutubeFallback?.let { youtubeFallbackLabel },
                            onSecondaryAction = onYoutubeFallback,
                            modifier = Modifier
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                                .animateItem(),
                        )
                    }
                }

                else -> {
                    items(
                        items = itemsPage?.items.orEmpty().distinctBy { it.id },
                        key = { "filtered_${it.id}" },
                        itemContent = ytItemContent,
                    )

                    if (itemsPage?.continuation != null) {
                        item(key = "loading") {
                            ShimmerHost {
                                repeat(3) {
                                    ListItemPlaceHolder()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun mapItemToFilter(item: YTItem): com.metrolist.innertube.YouTube.SearchFilter? =
    when (item) {
        is SongItem -> FILTER_SONG
        is AlbumItem -> FILTER_ALBUM
        is ArtistItem -> FILTER_ARTIST
        is PlaylistItem -> FILTER_COMMUNITY_PLAYLIST
    }
