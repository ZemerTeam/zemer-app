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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.jtech.zemer.LocalPlayerAwareWindowInsets
import com.jtech.zemer.LocalPlayerConnection
import com.jtech.zemer.R
import com.jtech.zemer.extensions.togglePlayPause
import com.jtech.zemer.models.toMediaMetadata
import com.jtech.zemer.extensions.toMediaItem
import com.jtech.zemer.playback.queues.ListQueue
import com.jtech.zemer.playback.queues.ZemerRadioQueue
import com.jtech.zemer.tracking.PlaySource
import com.jtech.zemer.tracking.Tracker
import com.jtech.zemer.tracking.TrackImpressionsByKey
import com.jtech.zemer.tracking.TrackingSurface
import com.jtech.zemer.constants.BlockVideosKey
import com.jtech.zemer.constants.BlockPodcastsKey
import com.jtech.zemer.search.ZEMER_FILTER_PODCAST
import com.jtech.zemer.search.ZEMER_FILTER_EPISODE
import com.jtech.zemer.search.zemerAlbumRoute
import com.jtech.zemer.search.zemerPlaylistRoute
import com.jtech.zemer.utils.rememberPreference
import com.jtech.zemer.ui.utils.activeRowTapTogglesPlayPause
import com.jtech.zemer.ui.utils.navigateToArtist
import com.jtech.zemer.ui.utils.whitelistedPodcastRoute
import com.jtech.zemer.ui.component.AppStateView
import com.jtech.zemer.ui.component.ChipsRow
import com.jtech.zemer.ui.component.LocalMenuState
import com.jtech.zemer.ui.component.MoreVertMenuButton
import com.jtech.zemer.ui.component.NavigationTitle
import com.jtech.zemer.ui.component.OfflineBackupPromoCard
import com.jtech.zemer.ui.component.YouTubeListItem
import com.jtech.zemer.ui.component.shimmer.LoadingListPlaceholder
import com.jtech.zemer.ui.menu.YouTubeAlbumMenu
import com.jtech.zemer.ui.menu.YouTubeArtistMenu
import com.jtech.zemer.ui.menu.YouTubePlaylistMenu
import com.jtech.zemer.ui.menu.YouTubeSongMenu
import com.jtech.zemer.ui.menu.ytItemMenu
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
import com.metrolist.innertube.models.EpisodeItem
import com.metrolist.innertube.models.PodcastItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.YTItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun OnlineSearchResult(
    navController: NavController,
    viewModel: OnlineSearchViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
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
    val (blockPodcasts, _) = rememberPreference(BlockPodcastsKey, false)
    // Block Podcasts must also evict an ALREADY-SELECTED podcast/episode chip: the chip row below
    // hides those chips reactively, but the retained ViewModel keeps its selected filter and the
    // fetched page — without this reset a results screen left on the Podcasts chip keeps rendering
    // blocked content after a Settings round-trip (HomeScreen's PODCASTS -> MUSIC snap is the same
    // rule for the home tabs).
    LaunchedEffect(blockPodcasts) {
        if (!searchFilterAllowed(viewModel.filter.value, blockPodcasts)) {
            viewModel.filter.value = null
        }
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

    // Impressions (what was SHOWN, for the ranking side's exposure dampener). Keyed rather than
    // indexed: chips, section titles and shimmer rows share the list's index space with results, so
    // an index says nothing about which song it is. The keys come from [summaryItemKey]/
    // [filteredItemKey], the same functions the lazy items below are keyed by — changing a key
    // shape in one place and not the other would otherwise compile fine and silently report
    // nothing, dropping `search` out of the server's coverage gate with no error anywhere. Only
    // SongItems are mapped, since albums/artists/playlists have no videoId to expose.
    val impressionIdByKey = remember(searchSummary, itemsPage) {
        buildMap {
            searchSummary?.summaries?.forEach { summary ->
                summary.items.forEachIndexed { index, item ->
                    if (item is SongItem) put(summaryItemKey(summary.title, item.id, index), item.id)
                }
            }
            itemsPage?.items.orEmpty().distinctBy { it.id }.forEach { item ->
                if (item is SongItem) put(filteredItemKey(item.id), item.id)
            }
        }
    }
    TrackImpressionsByKey(
        surface = TrackingSurface.SEARCH,
        state = lazyListState,
        idOfKey = { impressionIdByKey[it] },
    )

    // rank = the row's 0-based position within its displayed category (telemetry `click` events).
    val ytItemContent: @Composable LazyItemScope.(YTItem, Int) -> Unit = { item: YTItem, rank: Int ->
        // ONE activation path for tap and D-pad select: fires the telemetry click, then the
        // existing navigation/playback behavior (search-tapped songs play with source "search").
        val activate = {
            Tracker.click(viewModel.query, item.id, clickKind(item, searchFilter?.value), rank)
            when (item) {
                // Podcast SHOW: open the host channel when known (where Subscribe lives), else the show —
                // exactly the browse-grid routing (whitelistedPodcastRoute).
                is PodcastItem -> {
                    val route = whitelistedPodcastRoute(item.id, item.channelId)
                    if (route != null) navController.navigate(route)
                }
                // Episode: play the single episode via a plain ListQueue. A podcast episode must NOT seed
                // music song-radio around its videoId (that fills the queue with unrelated corpus songs) —
                // mirrors the artist-page episode tap.
                is EpisodeItem ->
                    playerConnection.playQueue(
                        ListQueue(
                            title = item.title,
                            items = listOf(item.toMediaItem()),
                            // Same declared source as the sibling song taps on this screen — the
                            // default OTHER would undercount search-driven listens.
                            playSource = PlaySource.SEARCH,
                        )
                    )
                is SongItem -> {
                    // Audio-first always (I2): every result plays as a normal song; video is a per-play
                    // in-player toggle, never a separate watch entry point (D3).
                    if (activeRowTapTogglesPlayPause(item.id == mediaMetadata?.id, playerConnection.isStationBroadcast.value)) {
                        playerConnection.playPause()
                    } else {
                        playerConnection.playQueue(
                            ZemerRadioQueue.song(item.toMediaMetadata(), playerConnection.service, PlaySource.SEARCH)
                        )
                    }
                }

                is AlbumItem -> navController.navigate(zemerAlbumRoute(item))
                is ArtistItem -> navController.navigateToArtist(item.id)
                // A discovery-sourced community playlist tags its plays `community:<id>` (same source as the
                // home Community row); featured/artist-owned stay `playlist:`. Community-ness covers the
                // Community chip AND the Zemer summary preview, not just the chip — see [playlistIsCommunity].
                is PlaylistItem -> navController.navigate(
                    zemerPlaylistRoute(
                        item.id,
                        community = playlistIsCommunity(searchFilter?.value),
                    )
                )
            }
        }
        val longClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            menuState.show(
                ytItemMenu(
                    item = item,
                    navController = navController,
                    coroutineScope = coroutineScope,
                    onDismiss = menuState::dismiss,
                    // Video download / video share is offered only for a video item while video
                    // imagery is not blocked; otherwise it downloads/shares as ordinary audio.
                    // Per-item flag, not the chip: the same video row must get the same menu on the
                    // All summary and the Videos chip.
                    isVideo = item is SongItem && item.isVideo && !blockVideos,
                )
            )
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
                MoreVertMenuButton(
                    onClick = longClick,
                )
            },
            modifier =
            Modifier
                .focusProperties {
                    up = chipsFocusRequester
                    down = FocusRequester.Default
                }
                .onKeyEvent { event ->
                    when {
                        // KeyDown only (the app's D-pad convention, e.g. KidZoneScreen) and never
                        // auto-repeats: one press = one activation = one telemetry click. Consuming
                        // the KeyDown also stops clickable's internal KeyUp-onClick from doubling it.
                        event.type == KeyEventType.KeyDown &&
                            event.nativeKeyEvent.repeatCount == 0 &&
                            (event.key == Key.Enter || event.key == Key.DirectionCenter) -> {
                            activate()
                            true
                        }
                        else -> false
                    }
                }
                .combinedClickable(
                    onClick = activate,
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
                    // Videos are always browsable and audio-first now. When imagery is blocked they are
                    // audio-only "Video songs"; otherwise they are labelled "Videos" (watchable via the
                    // in-player toggle). The chip is always available either way.
                    val videosAsAudio = blockVideos
                    add(
                        FILTER_VIDEO to stringResource(
                            if (videosAsAudio) R.string.filter_video_songs else R.string.filter_videos
                        )
                    )
                    add(FILTER_ALBUM to stringResource(R.string.filter_albums))
                    add(FILTER_ARTIST to stringResource(R.string.filter_artists))
                    add(FILTER_COMMUNITY_PLAYLIST to stringResource(R.string.filter_community_playlists))
                    add(FILTER_FEATURED_PLAYLIST to stringResource(R.string.filter_featured_playlists))
                    // Podcasts + episodes are corpus content with no YouTube filter. Hidden entirely
                    // when podcasts are blocked (same content type as the gated summary sections).
                    if (!blockPodcasts) {
                        add(ZEMER_FILTER_PODCAST to stringResource(R.string.filter_podcasts))
                        add(ZEMER_FILTER_EPISODE to stringResource(R.string.filter_episodes))
                    }
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
        item(key = "offline_backup_promo") {
            // One-time pre-failure discovery of the search backup (self-hides once
            // enabled/dismissed) — existing installs never see the onboarding step.
            OfflineBackupPromoCard(
                onSetUp = { navController.navigate("settings/offline_search") },
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .animateItem(),
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
                            modifier = Modifier
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                                .animateItem(),
                        )
                    }
                }

                searchSummary == null && isSummaryLoading -> {
                    item {
                        LoadingListPlaceholder(8)
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
                            modifier = Modifier
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                                .animateItem(),
                        )
                    }
                }

                else -> {
                    searchSummary?.summaries?.forEach { summary ->
                        // Blocked podcasts hide the whole content type — both the podcast SHOWS section
                        // and the EPISODES section (episodes are podcast content too).
                        val isPodcastSection = summary.items.firstOrNull().let { it is PodcastItem || it is EpisodeItem }
                        if (summary.items.isNotEmpty() && !(blockPodcasts && isPodcastSection)) {
                            item {
                                val summaryFilter =
                                    summary.items.firstOrNull()?.let(::mapItemToFilter)
                                        ?: when (summary.title.lowercase()) {
                                            "albums" -> FILTER_ALBUM
                                            "songs" -> FILTER_SONG
                                            "artists" -> FILTER_ARTIST
                                            "videos" -> FILTER_VIDEO
                                            "community playlists" -> FILTER_COMMUNITY_PLAYLIST
                                            "featured playlists" -> FILTER_FEATURED_PLAYLIST
                                            else -> null
                                        }
                                val isVideoSection = (summary.items.firstOrNull() as? SongItem)?.isVideo == true
                                NavigationTitle(
                                    title = if (isVideoSection && blockVideos)
                                        stringResource(R.string.video_songs) else summary.title,
                                    // Summary rows are capped PREVIEWS; the see-all switches to the full
                                    // filtered view, so show it whenever that view exists (a resolvable
                                    // filter) rather than gating on the preview size.
                                    onClick = summaryFilter?.let { filter ->
                                        {
                                            viewModel.filter.value = filter
                                            coroutineScope.launch {
                                                lazyListState.animateScrollToItem(0)
                                            }
                                        }
                                    }
                                )
                            }

                            itemsIndexed(
                                items = summary.items,
                                key = { index, it -> summaryItemKey(summary.title, it.id, index) },
                            ) { index, it -> ytItemContent(it, index) }
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
                            modifier = Modifier
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                                .animateItem(),
                        )
                    }
                }

                itemsPage == null && filterLoading -> {
                    item {
                        LoadingListPlaceholder(8)
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
                            modifier = Modifier
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                                .animateItem(),
                        )
                    }
                }

                else -> {
                    // Belt-and-braces for the frame(s) before the LaunchedEffect above resets a
                    // now-disallowed podcast/episode filter: never render its page.
                    if (searchFilterAllowed(searchFilter, blockPodcasts)) {
                        itemsIndexed(
                            items = itemsPage?.items.orEmpty().distinctBy { it.id },
                            key = { _, it -> filteredItemKey(it.id) },
                        ) { index, it -> ytItemContent(it, index) }
                    }
                }
            }
        }
    }
}

/**
 * Telemetry `click.kind` (spec §3.4): the tapped item's category as displayed. SongItems on the
 * Videos chip are videos; PlaylistItems on the Community chip are community playlists — elsewhere
 * the app can't reliably tell, so the base category is sent.
 */
/**
 * The lazy-list item keys, defined once because they are read twice: by the list itself and by the
 * impression map that translates a visible key back into a videoId. Duplicated string templates
 * would let a key change compile cleanly and silently stop all impression reporting on this screen.
 */
private fun summaryItemKey(sectionTitle: String, id: String, index: Int) = "$sectionTitle/$id/$index"

private fun filteredItemKey(id: String) = "filtered_$id"

private fun clickKind(item: YTItem, filterValue: String?): String = when (item) {
    // Per-item flag, not the chip: a video row in the All summary's Videos section is displayed
    // (and badged) as a video there too, so it reports "video" on either surface.
    is SongItem -> if (item.isVideo) "video" else "song"
    is AlbumItem -> "album"
    is ArtistItem -> "artist"
    is PodcastItem -> "podcast"
    is EpisodeItem -> "episode"
    is PlaylistItem -> if (playlistIsCommunity(filterValue)) "community" else "playlist"
}

/**
 * Whether a tapped [PlaylistItem] is a discovery-sourced community playlist (tagged `community:<id>` and
 * counted "community" for telemetry) vs. an artist-owned/featured one. The Community chip is community;
 * with no chip picked the summary's only playlist section IS the community preview (ZemerResultMapper's
 * TITLE_PLAYLISTS = categories.community). The Featured chip is plain playlists (the `else`).
 */
private fun playlistIsCommunity(filterValue: String?): Boolean = when {
    filterValue == FILTER_COMMUNITY_PLAYLIST.value -> true
    filterValue == null -> true
    else -> false
}

private fun mapItemToFilter(item: YTItem): com.metrolist.innertube.YouTube.SearchFilter? =
    when (item) {
        is SongItem -> if (item.isVideo) FILTER_VIDEO else FILTER_SONG
        is AlbumItem -> FILTER_ALBUM
        is ArtistItem -> FILTER_ARTIST
        is PlaylistItem -> FILTER_COMMUNITY_PLAYLIST
        // Podcasts/episodes have their own Zemer chips, so a summary section's "see all" switches to them.
        is PodcastItem -> ZEMER_FILTER_PODCAST
        is EpisodeItem -> ZEMER_FILTER_EPISODE
    }
