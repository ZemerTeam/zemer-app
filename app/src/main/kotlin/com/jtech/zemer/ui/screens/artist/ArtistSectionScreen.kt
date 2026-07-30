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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.jtech.zemer.LocalPlayerAwareWindowInsets
import com.jtech.zemer.LocalPlayerConnection
import com.jtech.zemer.R
import com.jtech.zemer.models.toMediaMetadata
import com.jtech.zemer.playback.queues.ZemerRadioQueue
import com.jtech.zemer.tracking.PlaySource
import com.jtech.zemer.ui.component.EmptyPlaceholder
import com.jtech.zemer.ui.component.LocalMenuState
import com.jtech.zemer.ui.component.YouTubeListItem
import com.jtech.zemer.ui.menu.YouTubeSongMenu
import com.jtech.zemer.ui.screens.YtItemGrid
import com.jtech.zemer.ui.utils.activeRowTapTogglesPlayPause
import com.jtech.zemer.ui.utils.backToMain
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

    Box(Modifier.fillMaxSize()) {
        when {
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

    TopAppBar(
        title = { Text(sectionTitle) },
        navigationIcon = {
            // The app's IconButton (long-press = back to Home), matching the other See-all screens.
            com.jtech.zemer.ui.component.IconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain,
            ) {
                Icon(painterResource(R.drawable.arrow_back), contentDescription = null)
            }
        },
        scrollBehavior = scrollBehavior,
    )
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
                    IconButton(onClick = {
                        menuState.show { YouTubeSongMenu(song = song, navController = navController, onDismiss = menuState::dismiss) }
                    }) {
                        Icon(painterResource(R.drawable.more_vert), contentDescription = null)
                    }
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
