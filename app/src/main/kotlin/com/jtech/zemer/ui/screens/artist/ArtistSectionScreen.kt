package com.jtech.zemer.ui.screens.artist

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
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
import com.jtech.zemer.ui.component.BackTopAppBar
import com.jtech.zemer.ui.component.EmptyPlaceholder
import com.jtech.zemer.ui.component.LocalMenuState
import com.jtech.zemer.ui.component.MoreVertMenuButton
import com.jtech.zemer.ui.component.YouTubeListItem
import com.jtech.zemer.ui.menu.YouTubeSongMenu
import com.jtech.zemer.ui.screens.YtItemGrid
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.rememberCoroutineScope
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
    if (pagedEpisodes) {
        LaunchedEffect(episodeListState) {
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
            pagedEpisodes && items.isNotEmpty() ->
                ChannelEpisodeList(items.filterIsInstance<EpisodeItem>(), navController, episodeListState)
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
                    CircularProgressIndicator()
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
) {
    val menuState = LocalMenuState.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    LazyColumn(state = listState, contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues()) {
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
