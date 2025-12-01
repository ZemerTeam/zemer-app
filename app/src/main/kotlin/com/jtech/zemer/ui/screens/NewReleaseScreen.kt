package com.jtech.zemer.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
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
import com.jtech.zemer.models.toMediaMetadata
import com.jtech.zemer.playback.queues.YouTubeQueue
import com.jtech.zemer.ui.component.AppStateView
import com.jtech.zemer.ui.component.IconButton
import com.jtech.zemer.ui.component.LocalMenuState
import com.jtech.zemer.ui.component.NavigationTitle
import com.jtech.zemer.ui.component.YouTubeGridItem
import com.jtech.zemer.ui.component.YouTubeListItem
import com.jtech.zemer.ui.component.shimmer.GridItemPlaceHolder
import com.jtech.zemer.ui.component.shimmer.ShimmerHost
import com.jtech.zemer.ui.menu.YouTubeAlbumMenu
import com.jtech.zemer.ui.menu.YouTubeSongMenu
import com.jtech.zemer.ui.utils.backToMain
import com.jtech.zemer.viewmodels.NewReleaseViewModel
import com.metrolist.innertube.models.WatchEndpoint

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun NewReleaseScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: NewReleaseViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val database = LocalDatabase.current
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    val newReleaseAlbums by viewModel.newReleaseAlbums.collectAsState()
    val newReleaseSongs by viewModel.newReleaseSongs.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()

    val coroutineScope = rememberCoroutineScope()

    LazyColumn(
        contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
    ) {
        when {
            isLoading -> {
                item {
                    ShimmerHost {
                        repeat(6) {
                            GridItemPlaceHolder(fillMaxWidth = true)
                        }
                    }
                }
            }

            error != null -> {
                item(key = "new_release_error") {
                    AppStateView(
                        title = stringResource(R.string.new_release_error_title),
                        subtitle = error ?: "",
                        icon = R.drawable.explore_outlined,
                        actionLabel = stringResource(R.string.new_release_retry),
                        onAction = viewModel::refresh,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
            }

            newReleaseAlbums.isEmpty() && newReleaseSongs.isEmpty() -> {
                item(key = "new_release_empty") {
                    AppStateView(
                        title = stringResource(R.string.new_release_empty_title),
                        subtitle = stringResource(R.string.new_release_empty_subtitle),
                        icon = R.drawable.explore_outlined,
                        actionLabel = stringResource(R.string.new_release_retry),
                        onAction = viewModel::refresh,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
            }

            else -> {
                if (newReleaseSongs.isNotEmpty()) {
                    item(key = "new_release_songs_title") {
                        NavigationTitle(
                            title = stringResource(R.string.new_release_songs_title),
                            modifier = Modifier.animateItem()
                        )
                    }
                    item(key = "new_release_songs_list") {
                        LazyRow(
                            modifier = Modifier.animateItem(),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp)
                        ) {
                            items(
                                items = newReleaseSongs.distinctBy { it.id },
                                key = { it.id }
                            ) { song ->
                                YouTubeListItem(
                                    item = song,
                                    isActive = mediaMetadata?.id == song.id,
                                    isPlaying = isPlaying,
                                    trailingContent = {
                                        IconButton(
                                            onClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                menuState.show {
                                                    YouTubeSongMenu(
                                                        song = song,
                                                        navController = navController,
                                                        onDismiss = menuState::dismiss,
                                                    )
                                                }
                                            },
                                            onLongClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                menuState.show {
                                                    YouTubeSongMenu(
                                                        song = song,
                                                        navController = navController,
                                                        onDismiss = menuState::dismiss,
                                                    )
                                                }
                                            }
                                        ) {
                                            Icon(
                                                painter = painterResource(R.drawable.more_vert),
                                                contentDescription = null,
                                            )
                                        }
                                    },
                                    modifier = Modifier
                                        .padding(horizontal = 8.dp)
                                        .combinedClickable(
                                            onClick = {
                                                playerConnection.playQueue(
                                                    YouTubeQueue(
                                                        song.endpoint ?: WatchEndpoint(videoId = song.id),
                                                        song.toMediaMetadata(),
                                                        database = database
                                                    )
                                                )
                                            },
                                            onLongClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                menuState.show {
                                                    YouTubeSongMenu(
                                                        song = song,
                                                        navController = navController,
                                                        onDismiss = menuState::dismiss,
                                                    )
                                                }
                                            }
                                        )
                                )
                            }
                        }
                    }

                    item { Spacer(modifier = Modifier.height(12.dp)) }
                }

                if (newReleaseAlbums.isNotEmpty()) {
                    item(key = "new_release_albums_title") {
                        NavigationTitle(
                            title = stringResource(R.string.new_release_albums_title),
                            modifier = Modifier.animateItem()
                        )
                    }
                    item(key = "new_release_albums_list") {
                        LazyRow(
                            modifier = Modifier.animateItem(),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp)
                        ) {
                            items(
                                items = newReleaseAlbums.distinctBy { it.id },
                                key = { it.id },
                            ) { album ->
                                YouTubeGridItem(
                                    item = album,
                                    isActive = mediaMetadata?.album?.id == album.id,
                                    isPlaying = isPlaying,
                                    fillMaxWidth = false,
                                    coroutineScope = coroutineScope,
                                    modifier =
                                    Modifier
                                        .padding(horizontal = 8.dp)
                                        .combinedClickable(
                                            onClick = {
                                                navController.navigate("album/${album.id}")
                                            },
                                            onLongClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                menuState.show {
                                                    YouTubeAlbumMenu(
                                                        albumItem = album,
                                                        navController = navController,
                                                        onDismiss = menuState::dismiss,
                                                    )
                                                }
                                            },
                                        ),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    TopAppBar(
        title = { Text(stringResource(R.string.new_release_title)) },
        navigationIcon = {
            IconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain,
            ) {
                Icon(
                    painterResource(R.drawable.arrow_back),
                    contentDescription = null,
                )
            }
        },
    )
}
