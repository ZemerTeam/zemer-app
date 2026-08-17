package com.jtech.zemer.ui.screens.artist

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.jtech.zemer.LocalPlayerAwareWindowInsets
import com.jtech.zemer.LocalPlayerConnection
import com.jtech.zemer.R
import com.jtech.zemer.models.toMediaMetadata
import com.jtech.zemer.playback.queues.ZemerRadioQueue
import com.jtech.zemer.tracking.PlaySource
import com.jtech.zemer.ui.component.AppBarTitle
import com.jtech.zemer.ui.component.ZemerLoadingIndicator
import com.jtech.zemer.ui.component.BackTopAppBar
import com.jtech.zemer.ui.component.EmptyPlaceholder
import com.jtech.zemer.ui.component.LocalMenuState
import com.jtech.zemer.ui.component.MoreVertMenuButton
import com.jtech.zemer.ui.component.YouTubeListItem
import com.jtech.zemer.ui.menu.YouTubeSongMenu
import com.jtech.zemer.ui.screens.YtItemGrid
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import com.jtech.zemer.ui.component.ArtistSearchField
import com.jtech.zemer.playback.queues.ListQueue
import com.jtech.zemer.ui.menu.ytItemMenu
import com.metrolist.innertube.models.EpisodeItem
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import com.jtech.zemer.search.ZemerResultMapper
import com.jtech.zemer.ui.screens.shouldPrefetchNearEnd
import com.jtech.zemer.ui.utils.activeRowTapTogglesPlayPause
import com.jtech.zemer.viewmodels.ArtistViewModel
import com.metrolist.innertube.models.SongItem

/**
 * "See all" for one artist-page section. `/artist` returns each section's whole catalog, so this reads
 * the same [ArtistViewModel] (keyed by the route's `artistId`), finds the section by its title, and shows
 * the full list — a vertical song list for the top-songs shelf, the shared [YtItemGrid] for videos /
 * albums / singles / playlists (Zemer-routed, so opens go through the server, not InnerTube).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistSectionScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    sectionTitle: String,
    viewModel: ArtistViewModel = hiltViewModel(),
) {
    val artistPage = viewModel.artistPage
    val isLoading = viewModel.isLoading
    val section = artistPage?.sections?.firstOrNull { it.title == sectionTitle }
    val items = section?.items?.distinctBy { it.id }.orEmpty()
    val isVideoSection = sectionTitle.contains("video", ignoreCase = true) ||
        sectionTitle.contains("short", ignoreCase = true)
    val isSongList = items.firstOrNull() is SongItem && !isVideoSection

    // A podcast channel's Episodes section is PAGED (`/podcast-channel?offset=`, channel-wide list):
    // near-edge prefetch off the list state appends the next page through the ViewModel (single-flight,
    // cursor-driven — a pre-paging server / the offline snapshot just never sets a cursor). Off-composition
    // snapshotFlow, the GenreScreen tracklist pattern. Episodes render as a vertical LIST (the shared
    // YouTubeListItem row, like the search episode rows) — dated long-form rows read as a feed, not a grid.
    val pagedEpisodes = viewModel.isPodcastChannel && sectionTitle == ZemerResultMapper.TITLE_EPISODES
    val episodeListState = rememberLazyListState()
    // Channel-wide episode search (the shared ArtistSearchField, like the Artists/Podcasts browse
    // screens): a CLIENT-side title filter over the loaded pages, with an explicit QUERY-DRIVEN
    // drain ([ArtistViewModel.drainEpisodeHistoryForSearch]) pulling the remaining history in so
    // the search covers the whole catalog. The drain is bounded and failure-terminated, and
    // [searchingHistory] mirrors exactly its lifetime - so the empty state always RESOLVES
    // (results or "No results found"), never a spinner that circles forever. It deliberately does
    // NOT ride the near-edge prefetch: a failed page leaves the cursor unchanged, which never
    // re-arms that trigger, and an empty filtered list pinned "near its end" would otherwise show
    // an unresolvable spinner (the shipped first cut's bug).
    var episodeQuery by rememberSaveable { mutableStateOf("") }
    var searchingHistory by remember { mutableStateOf(false) }
    // False when the last drain stopped early (failed page / cap) with the history NOT fully
    // loaded: an empty filter is then "couldn't search everything" + Retry, never an
    // authoritative "no results". [drainAttempt] re-keys the effect so Retry can re-run the SAME
    // query (the query key alone only fires on text edits - the false negative was sticky).
    var drainComplete by remember { mutableStateOf(true) }
    var drainAttempt by remember { mutableStateOf(0) }
    if (pagedEpisodes) {
        LaunchedEffect(episodeQuery, drainAttempt) {
            if (episodeQuery.isBlank()) return@LaunchedEffect
            // Searching flips ON before the debounce: the client-side filter applies on the FIRST
            // keystroke, so waiting until after the delay flashed "No results found" for 300ms per
            // keystroke on a query whose matches live in not-yet-loaded pages.
            searchingHistory = true
            try {
                // Debounce: one drain per settled query, not one per keystroke (a changed query
                // cancels this effect and the in-flight drain with it).
                kotlinx.coroutines.delay(300)
                drainComplete = viewModel.drainEpisodeHistoryForSearch()
            } finally {
                searchingHistory = false
            }
        }
    }
    if (pagedEpisodes) {
        // Keyed on the CURSOR too (the GenreScreen trigger pattern): a landed page re-arms the
        // effect, so a short/overlapping page that leaves the user still past the threshold (nearEnd
        // continuously true — distinctUntilChanged would suppress it) chains the next fetch instead
        // of stalling until the user scrolls away and back.
        //
        // GATED OFF while a query is active: the drain owns paging then. Left armed, this trigger
        // measured the FILTERED list - an unmatched query pins it 2 items long and permanently
        // "near its end", chaining uncapped background pages of the whole channel history and
        // racing the drain for the single-flight flag (the review's fetch-storm + premature
        // "no results" pair).
        val queryActive = episodeQuery.isNotBlank()
        LaunchedEffect(episodeListState, viewModel.episodesNextOffset, queryActive) {
            if (queryActive) return@LaunchedEffect
            snapshotFlow {
                shouldPrefetchNearEnd(
                    episodeListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index,
                    episodeListState.layoutInfo.totalItemsCount,
                )
            }
                .distinctUntilChanged()
                .collect { nearEnd -> if (nearEnd) viewModel.loadMoreEpisodes() }
        }
    }

    Box(Modifier.fillMaxSize()) {
        when {
            pagedEpisodes && items.isNotEmpty() -> {
                // Remembered: the full drained list can run to thousands of rows, and an
                // unremembered filter re-scanned + re-allocated it (new list identity, full
                // LazyColumn re-key) on EVERY recomposition, not just query/page changes.
                val filteredEpisodes = remember(items, episodeQuery) {
                    filterChannelEpisodes(items.filterIsInstance<EpisodeItem>(), episodeQuery)
                }
                ChannelEpisodeList(
                    episodes = filteredEpisodes,
                    navController = navController,
                    listState = episodeListState,
                    query = episodeQuery,
                    onQueryChange = { episodeQuery = it },
                    searching = searchingHistory,
                    drainComplete = drainComplete,
                    onRetryDrain = { drainAttempt++ },
                )
            }
            isSongList ->
                ArtistSongList(items.filterIsInstance<SongItem>(), navController, viewModel.artistId)
            items.isNotEmpty() ->
                YtItemGrid(
                    items = items,
                    navController = navController,
                    zemerAlbums = true,
                    zemerPlaylists = true,
                    communityPlaylists = false,
                )
            isLoading ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    ZemerLoadingIndicator()
                }
            else ->
                EmptyPlaceholder(
                    icon = R.drawable.music_note,
                    text = stringResource(R.string.home_see_all_empty),
                    modifier = Modifier.windowInsetsPadding(LocalPlayerAwareWindowInsets.current),
                )
        }
    }

    BackTopAppBar(
        title = { AppBarTitle(sectionTitle) },
        navController = navController,
        scrollBehavior = scrollBehavior,
    )
}

/**
 * The paged channel-wide episode list: the shared [YouTubeListItem] row (same as the search episode
 * rows), tap plays the single episode via [ListQueue.episode] under the show's podcast play-source,
 * long-press / 3-dot opens the shared [ytItemMenu]. [listState] is owned by the caller so the
 * near-edge paging trigger watches the same state.
 */
@Composable
private fun ChannelEpisodeList(
    episodes: List<EpisodeItem>,
    navController: NavController,
    listState: LazyListState,
    query: String,
    onQueryChange: (String) -> Unit,
    // Whether the query-driven history drain is still running - decides between the progress row
    // and the "no results" row for an empty filter. Mirrors the drain's exact lifetime, so this
    // always flips false (the empty state must resolve, never spin forever).
    searching: Boolean,
    // Whether the last drain covered the FULL history. False (failed page / cap) turns an empty
    // filter into "couldn't search everything" + Retry instead of a false "No results found".
    drainComplete: Boolean,
    onRetryDrain: () -> Unit,
) {
    val menuState = LocalMenuState.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val searchFocus = remember { FocusRequester() }

    LazyColumn(state = listState, contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues()) {
        item(key = "episode_search", contentType = "search") {
            ArtistSearchField(
                query = query,
                onQueryChange = onQueryChange,
                searchFocus = searchFocus,
                placeholderRes = R.string.search_episodes,
                // Breathing room between the pill and the first episode row (the component's own
                // 8dp bottom padding reads cramped over the dense dated rows).
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        if (episodes.isEmpty() && query.isNotBlank()) {
            item(key = "episode_search_state", contentType = "state") {
                when {
                    searching ->
                        // The drain is still walking older history in - progress, not a premature
                        // "no results". Bounded + failure-terminated, so it always resolves.
                        Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                            ZemerLoadingIndicator()
                        }
                    !drainComplete ->
                        // The drain stopped before covering the history (failed page / cap):
                        // an authoritative "no results" would be false - say so and offer Retry.
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.search_episodes_incomplete),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            TextButton(onClick = onRetryDrain) {
                                Text(stringResource(R.string.retry))
                            }
                        }
                    else ->
                        EmptyPlaceholder(icon = R.drawable.search, text = stringResource(R.string.no_results_found))
                }
            }
        }
        items(items = episodes, key = { it.id }) { episode ->
            YouTubeListItem(
                item = episode,
                isActive = mediaMetadata?.id == episode.id,
                isPlaying = isPlaying,
                trailingContent = {
                    MoreVertMenuButton(onClick = {
                        menuState.show(ytItemMenu(episode, navController, coroutineScope, menuState::dismiss))
                    })
                },
                modifier = Modifier.combinedClickable(
                    onClick = {
                        if (activeRowTapTogglesPlayPause(episode.id == mediaMetadata?.id, playerConnection.isStationBroadcast.value)) {
                            playerConnection.playPause()
                        } else {
                            // The one way an episode tap plays (never song radio around its videoId).
                            playerConnection.playQueue(
                                ListQueue.episode(episode, PlaySource.podcast(episode.podcast?.id)),
                            )
                        }
                    },
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        menuState.show(ytItemMenu(episode, navController, coroutineScope, menuState::dismiss))
                    },
                ),
            )
        }
    }
}

/** The full top-songs list: tap plays the song under the artist play-source, matching the artist page. */
@Composable
private fun ArtistSongList(
    songs: List<SongItem>,
    navController: NavController,
    artistId: String,
) {
    val menuState = LocalMenuState.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val haptic = LocalHapticFeedback.current
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    LazyColumn(contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues()) {
        items(items = songs, key = { it.id }) { song ->
            YouTubeListItem(
                item = song,
                isActive = mediaMetadata?.id == song.id,
                isPlaying = isPlaying,
                trailingContent = {
                    MoreVertMenuButton(onClick = {
                        menuState.show { YouTubeSongMenu(song = song, navController = navController, onDismiss = menuState::dismiss) }
                    })
                },
                modifier = Modifier.combinedClickable(
                    onClick = {
                        if (activeRowTapTogglesPlayPause(song.id == mediaMetadata?.id, playerConnection.isStationBroadcast.value)) {
                            playerConnection.playPause()
                        } else {
                            playerConnection.playQueue(
                                ZemerRadioQueue.song(
                                    song.toMediaMetadata(),
                                    playerConnection.service,
                                    PlaySource.artist(artistId),
                                ),
                            )
                        }
                    },
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        menuState.show { YouTubeSongMenu(song = song, navController = navController, onDismiss = menuState::dismiss) }
                    },
                ),
            )
        }
    }
}

/**
 * The channel-episode search filter: a case-insensitive title match over the pages loaded so far.
 * Blank query = everything (the plain paged list). Pure so the rule is unit-tested.
 */
internal fun filterChannelEpisodes(episodes: List<EpisodeItem>, query: String): List<EpisodeItem> {
    val q = query.trim()
    if (q.isEmpty()) return episodes
    return episodes.filter { it.title.contains(q, ignoreCase = true) }
}
