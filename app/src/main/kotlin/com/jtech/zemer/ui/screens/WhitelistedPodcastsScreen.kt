package com.jtech.zemer.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import com.jtech.zemer.ui.component.focusVisualsEnabled
import com.jtech.zemer.db.entities.PodcastEntity
import com.jtech.zemer.ui.component.ArtistCountHeader
import com.jtech.zemer.ui.component.SearchHandoffPill
import com.jtech.zemer.search.zemerSearchRoute
import com.jtech.zemer.search.SEARCH_FILTER_EPISODES
import com.jtech.zemer.ui.component.ArtistSearchField
import com.jtech.zemer.ui.component.focusBorder
import com.metrolist.innertube.models.SongItem
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.jtech.zemer.ui.utils.navigateToPodcast
import com.jtech.zemer.LocalPlayerAwareWindowInsets
import com.jtech.zemer.LocalPlayerConnection
import com.jtech.zemer.R
import com.jtech.zemer.constants.PodcastViewTypeKey
import com.jtech.zemer.constants.LibraryViewType
import com.jtech.zemer.constants.CONTENT_TYPE_HEADER
import com.jtech.zemer.constants.CONTENT_TYPE_PODCAST
import com.jtech.zemer.extensions.toMediaItem
import com.jtech.zemer.playback.queues.ListQueue
import com.jtech.zemer.ui.component.EmptyPlaceholder
import com.jtech.zemer.ui.component.LocalMenuState
import com.jtech.zemer.ui.component.WhitelistedPodcastGridItem
import com.jtech.zemer.ui.component.WhitelistedPodcastListItem
import com.jtech.zemer.utils.rememberEnumPreference
import com.jtech.zemer.viewmodels.WhitelistedPodcastsViewModel

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun WhitelistedPodcastsScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: WhitelistedPodcastsViewModel = hiltViewModel(),
) {
    val playerConnection = LocalPlayerConnection.current ?: return

    var viewType by rememberEnumPreference(PodcastViewTypeKey, LibraryViewType.GRID)
    val firstFocus = remember { FocusRequester() }
    val searchFocus = remember { FocusRequester() }
    val firstPodcastFocus = remember { FocusRequester() }

    val podcasts by viewModel.allPodcasts.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val syncProgress by viewModel.syncProgress.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val subscribedPodcasts by viewModel.subscribedPodcasts.collectAsState()
    val newEpisodes by viewModel.newEpisodes.collectAsState()
    val isLoadingNewEpisodes by viewModel.isLoadingNewEpisodes.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val menuState = LocalMenuState.current
    var showSyncOverlay by remember { mutableStateOf(false) }

    LaunchedEffect(syncProgress.total, syncProgress.isComplete, syncProgress.current, isSyncing) {
        showSyncOverlay = isSyncing || (syncProgress.total > 0 && !syncProgress.isComplete)
        if (!isSyncing && (syncProgress.isComplete || syncProgress.total == 0)) {
            showSyncOverlay = false
        }
    }

    val lazyListState = rememberLazyListState()
    val lazyGridState = rememberLazyGridState()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val scrollToTop =
        backStackEntry?.savedStateHandle?.getStateFlow("scrollToTop", false)?.collectAsState()

    val showBackToTop by remember {
        derivedStateOf {
            when (viewType) {
                LibraryViewType.LIST -> lazyListState.firstVisibleItemIndex > 2
                LibraryViewType.GRID -> lazyGridState.firstVisibleItemIndex > 5
            }
        }
    }

    val dpadSession = focusVisualsEnabled()
    LaunchedEffect(Unit) {
        // Touch sessions skip the initial grab: focused M3 components paint their own
        // focus pill even in touch mode, which is pure noise without a D-pad.
        if (dpadSession) {
            firstFocus.requestFocus()
        }
    }

    LaunchedEffect(scrollToTop?.value) {
        if (scrollToTop?.value == true) {
            when (viewType) {
                LibraryViewType.LIST -> lazyListState.animateScrollToItem(0)
                LibraryViewType.GRID -> lazyGridState.animateScrollToItem(0)
            }
            backStackEntry?.savedStateHandle?.set("scrollToTop", false)
        }
    }

    val searchContent = @Composable {
        ArtistSearchField(
            query = searchQuery,
            onQueryChange = { viewModel.searchQuery.value = it },
            searchFocus = searchFocus,
            downTarget = if (podcasts.isNotEmpty()) firstPodcastFocus else firstFocus,
            placeholderRes = R.string.search_podcasts,
        )
    }

    val headerContent = @Composable {
        ArtistCountHeader(
            titleRes = R.string.podcasts,
            count = podcasts.size,
            countPluralRes = R.plurals.n_channel,
            viewType = viewType,
            onToggleViewType = { viewType = viewType.toggle() },
            firstFocus = firstFocus,
            searchFocus = searchFocus,
            downTarget = if (podcasts.isNotEmpty()) firstPodcastFocus else FocusRequester.Default,
        )
    }

    // Shared header-section wiring, so the LIST and GRID branches build the identical Subscribed-Channels
    // + New-Episodes header without copy-pasting the section blocks (only the grid's `span` differs).
    val onSyncSubscribed: () -> Unit = { viewModel.syncSubscribedPodcasts() }
    val onChannelClick: (String) -> Unit = { navController.navigateToPodcast(it) }
    val onRefreshEpisodes: () -> Unit = { viewModel.fetchNewEpisodes() }
    val onEpisodeClick: (SongItem) -> Unit = { episode ->
        playerConnection.playQueue(
            ListQueue(title = episode.title, items = listOf(episode.toMediaItem())),
        )
    }
    val hasHeaderSections = subscribedPodcasts.isNotEmpty() || newEpisodes.isNotEmpty() || isLoadingNewEpisodes

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        when (viewType) {
            LibraryViewType.LIST ->
                LazyColumn(
                    state = lazyListState,
                    contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
                ) {
                    item(
                        key = "search",
                        contentType = CONTENT_TYPE_HEADER,
                    ) {
                        searchContent()
                    }

                    if (hasHeaderSections) {
                        item(key = "podcast_header_sections", contentType = CONTENT_TYPE_HEADER) {
                            PodcastLibraryHeaderSections(
                                subscribedPodcasts = subscribedPodcasts,
                                newEpisodes = newEpisodes,
                                isLoadingNewEpisodes = isLoadingNewEpisodes,
                                onSync = onSyncSubscribed,
                                onChannelClick = onChannelClick,
                                onRefresh = onRefreshEpisodes,
                                onEpisodeClick = onEpisodeClick,
                            )
                        }
                    }

                    item(
                        key = "header",
                        contentType = CONTENT_TYPE_HEADER,
                    ) {
                        headerContent()
                    }

                    if (podcasts.isEmpty()) {
                        item(key = "empty_placeholder") {
                            EmptyPlaceholder(
                                icon = R.drawable.podcast,
                                text = if (searchQuery.isEmpty()) {
                                    stringResource(R.string.library_podcast_empty)
                                } else {
                                    stringResource(R.string.no_results_found)
                                },
                                modifier = Modifier.animateItem()
                            )
                        }
                    }

                    itemsIndexed(
                        items = podcasts,
                        key = { _, item -> item.channelId },
                        contentType = { _, _ -> CONTENT_TYPE_PODCAST },
                    ) { index, podcast ->
                        WhitelistedPodcastListItem(
                            navController = navController,
                            menuState = menuState,
                            modifier = Modifier
                                .then(if (index == 0) Modifier.focusRequester(firstPodcastFocus) else Modifier)
                                .animateItem(),
                            podcast = podcast,
                        )
                    }

                    // This screen's search filters CHANNELS (a local title match over the
                    // allow-set); episode search is the global search screen's job. A typed query
                    // gets one hand-off row into it, landing on the Episodes chip prefilled -
                    // covering the "typed an episode name here" case (esp. zero channel matches)
                    // without turning this instant local filter into a networked results screen.
                    if (searchQuery.isNotBlank()) {
                        item(key = "search_episodes_handoff") {
                            SearchHandoffPill(
                                text = stringResource(R.string.search_episodes_for, searchQuery.trim()),
                                onClick = {
                                    navController.navigate(
                                        zemerSearchRoute(searchQuery.trim(), SEARCH_FILTER_EPISODES)
                                    )
                                },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                }

            LibraryViewType.GRID ->
                LazyVerticalGrid(
                    state = lazyGridState,
                    columns = GridCells.Fixed(3),
                    contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
                ) {
                    item(
                        key = "search",
                        span = { GridItemSpan(maxLineSpan) },
                        contentType = CONTENT_TYPE_HEADER,
                    ) {
                        searchContent()
                    }

                    if (hasHeaderSections) {
                        item(key = "podcast_header_sections", span = { GridItemSpan(maxLineSpan) }, contentType = CONTENT_TYPE_HEADER) {
                            PodcastLibraryHeaderSections(
                                subscribedPodcasts = subscribedPodcasts,
                                newEpisodes = newEpisodes,
                                isLoadingNewEpisodes = isLoadingNewEpisodes,
                                onSync = onSyncSubscribed,
                                onChannelClick = onChannelClick,
                                onRefresh = onRefreshEpisodes,
                                onEpisodeClick = onEpisodeClick,
                            )
                        }
                    }

                    item(
                        key = "header",
                        span = { GridItemSpan(maxLineSpan) },
                        contentType = CONTENT_TYPE_HEADER,
                    ) {
                        headerContent()
                    }

                    if (podcasts.isEmpty()) {
                        item(
                            key = "empty_placeholder",
                            span = { GridItemSpan(maxLineSpan) }
                        ) {
                            EmptyPlaceholder(
                                icon = R.drawable.podcast,
                                text = if (searchQuery.isEmpty()) {
                                    stringResource(R.string.library_podcast_empty)
                                } else {
                                    stringResource(R.string.no_results_found)
                                },
                                modifier = Modifier.animateItem()
                            )
                        }
                    }

                    itemsIndexed(
                        items = podcasts,
                        key = { _, item -> item.channelId },
                        contentType = { _, _ -> CONTENT_TYPE_PODCAST },
                    ) { index, podcast ->
                        WhitelistedPodcastGridItem(
                            navController = navController,
                            menuState = menuState,
                            modifier = Modifier
                                .then(if (index == 0) Modifier.focusRequester(firstPodcastFocus) else Modifier)
                                .animateItem(),
                            podcast = podcast,
                        )
                    }

                    // Same episode-search hand-off as the LIST view (see the comment there).
                    if (searchQuery.isNotBlank()) {
                        item(key = "search_episodes_handoff", span = { GridItemSpan(maxLineSpan) }) {
                            SearchHandoffPill(
                                text = stringResource(R.string.search_episodes_for, searchQuery.trim()),
                                onClick = {
                                    navController.navigate(
                                        zemerSearchRoute(searchQuery.trim(), SEARCH_FILTER_EPISODES)
                                    )
                                },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                }
        }

        AnimatedVisibility(
            visible = showBackToTop,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .windowInsetsPadding(
                    LocalPlayerAwareWindowInsets.current
                        .only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)
                )
                .padding(16.dp)
        ) {
            SmallFloatingActionButton(
                onClick = {
                    coroutineScope.launch {
                        scrollBehavior.state.heightOffset = 0f
                        when (viewType) {
                            LibraryViewType.LIST -> lazyListState.scrollToItem(0)
                            LibraryViewType.GRID -> lazyGridState.scrollToItem(0)
                        }
                    }
                },
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                Icon(
                    painter = painterResource(R.drawable.arrow_upward),
                    contentDescription = stringResource(R.string.back_to_top),
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        if (showSyncOverlay && !syncProgress.isComplete) {
            LoadingScreen(
                onFinished = { showSyncOverlay = false },
                shouldStartSync = false,
                progressFlow = viewModel.syncProgress
            )
        }
    }
}

/**
 * Shared gold section header (title + a trailing sync/refresh icon), used by both podcast rows so the
 * two can't drift.
 */
@Composable
private fun PodcastSectionHeader(title: String, onSync: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onSync) {
            Icon(
                painter = painterResource(R.drawable.sync),
                contentDescription = stringResource(R.string.action_sync),
            )
        }
    }
}

/** A subscribed-podcast avatar card (the "Subscribed Channels" row). D-pad focusable. */
@Composable
private fun SubscribedPodcastCard(
    title: String,
    thumbnailUrl: String?,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(100.dp)
            .focusBorder()
            .clickable(onClick = onClick)
            .padding(4.dp),
    ) {
        AsyncImage(
            model = thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(80.dp)
                .clip(RoundedCornerShape(8.dp)),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

/** A "New Episodes" card (larger thumbnail + title + author). D-pad focusable. */
@Composable
private fun NewEpisodeCard(
    episode: SongItem,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(140.dp)
            .focusBorder()
            .clickable(onClick = onClick)
            .padding(4.dp),
    ) {
        AsyncImage(
            model = episode.thumbnail,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(120.dp)
                .clip(RoundedCornerShape(8.dp)),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = episode.title,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Text(
            text = episode.artists.joinToString { it.name },
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.secondary,
        )
    }
}

/**
 * The two library header sections (Subscribed Channels — the account's synced + whitelist-filtered
 * subscriptions; New Episodes — the Zemer server `/podcasts/new-episodes` feed), rendered once and shared
 * by the LIST and GRID branches so they can't drift. Each inner section hides itself when empty.
 */
@Composable
private fun PodcastLibraryHeaderSections(
    subscribedPodcasts: List<PodcastEntity>,
    newEpisodes: List<SongItem>,
    isLoadingNewEpisodes: Boolean,
    onSync: () -> Unit,
    onChannelClick: (String) -> Unit,
    onRefresh: () -> Unit,
    onEpisodeClick: (SongItem) -> Unit,
) {
    if (subscribedPodcasts.isNotEmpty()) {
        SubscribedChannelsSection(
            podcasts = subscribedPodcasts,
            onSync = onSync,
            onChannelClick = onChannelClick,
        )
    }
    if (newEpisodes.isNotEmpty() || isLoadingNewEpisodes) {
        NewEpisodesSection(
            episodes = newEpisodes,
            onRefresh = onRefresh,
            onEpisodeClick = onEpisodeClick,
        )
    }
}

/**
 * The "Subscribed Channels" row: gold header + sync + a horizontal strip of avatar cards. One composable
 * shared by the LIST and GRID layouts (they differ only by the LazyGrid span on the wrapping item).
 */
@Composable
private fun SubscribedChannelsSection(
    podcasts: List<PodcastEntity>,
    onSync: () -> Unit,
    onChannelClick: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        PodcastSectionHeader(title = stringResource(R.string.subscribed_channels), onSync = onSync)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(items = podcasts, key = { it.id }) { podcast ->
                SubscribedPodcastCard(
                    title = podcast.title,
                    thumbnailUrl = podcast.thumbnailUrl,
                    onClick = { onChannelClick(podcast.id) },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

/**
 * The "New Episodes" row: gold header + refresh + a horizontal strip of episode cards. Shared by the
 * LIST and GRID layouts.
 */
@Composable
private fun NewEpisodesSection(
    episodes: List<SongItem>,
    onRefresh: () -> Unit,
    onEpisodeClick: (SongItem) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        PodcastSectionHeader(title = stringResource(R.string.new_episodes), onSync = onRefresh)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(items = episodes, key = { it.id }) { episode ->
                NewEpisodeCard(episode = episode, onClick = { onEpisodeClick(episode) })
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

