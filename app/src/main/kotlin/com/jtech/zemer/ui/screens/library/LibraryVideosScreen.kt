package com.jtech.zemer.ui.screens.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import com.jtech.zemer.constants.CONTENT_TYPE_HEADER
import com.jtech.zemer.extensions.metadata
import com.jtech.zemer.extensions.toMediaItem
import com.jtech.zemer.playback.queues.YouTubeQueue
import com.jtech.zemer.ui.component.HideOnScrollFAB
import com.jtech.zemer.ui.component.LocalMenuState
import com.jtech.zemer.ui.component.SongListItem
import com.jtech.zemer.ui.menu.SongMenu
import com.jtech.zemer.ui.screens.videoRoute
import com.jtech.zemer.viewmodels.LibraryVideosViewModel
import com.metrolist.innertube.models.WatchEndpoint

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryVideosScreen(
    navController: NavController,
    onDeselect: () -> Unit,
    viewModel: LibraryVideosViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val database = LocalDatabase.current
    val isPlaying = playerConnection.isPlaying.collectAsState().value
    val mediaMetadata = playerConnection.mediaMetadata.collectAsState().value

    val videos = viewModel.videos.collectAsState().value

    val lazyListState = rememberLazyListState()

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            state = lazyListState,
            contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
        ) {
            item(
                key = "filter",
                contentType = CONTENT_TYPE_HEADER,
            ) {
                FilterChip(
                    label = { Text(stringResource(R.string.videos)) },
                    selected = true,
                    colors = FilterChipDefaults.filterChipColors(containerColor = MaterialTheme.colorScheme.surface),
                    onClick = onDeselect,
                    shape = RoundedCornerShape(16.dp),
                    leadingIcon = {
                        Icon(
                            painter = painterResource(R.drawable.close),
                            contentDescription = stringResource(R.string.close)
                        )
                    },
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .fillMaxWidth()
                )
            }

            items(videos, key = { it.id }) { video ->
                SongListItem(
                    song = video.copy(song = video.song.copy(isVideo = true)),
                    showInLibraryIcon = true,
                    isPlaying = isPlaying && mediaMetadata?.id == video.id,
                    isActive = mediaMetadata?.id == video.id,
                    trailingContent = {
                        IconButton(
                            onClick = {
                                menuState.show {
                                    SongMenu(
                                        originalSong = video,
                                        navController = navController,
                                        onDismiss = menuState::dismiss,
                                    )
                                }
                            },
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.more_vert),
                                contentDescription = null,
                            )
                        }
                    },
                    modifier =
                    Modifier
                        .combinedClickable(
                            onClick = {
                                val artistDisplay = video.artists.joinToString(" • ") { it.name }
                                navController.navigate(videoRoute(video.id, video.song.title, artistDisplay))
                            },
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                menuState.show {
                                    SongMenu(
                                        originalSong = video,
                                        navController = navController,
                                        onDismiss = menuState::dismiss,
                                    )
                                }
                            },
                        ),
                )
            }
        }

        HideOnScrollFAB(
            visible = videos.isNotEmpty(),
            lazyListState = lazyListState,
            icon = R.drawable.playlist_play,
            onClick = {
                videos.firstOrNull()?.let { first ->
                    playerConnection.playQueue(
                        YouTubeQueue(
                            WatchEndpoint(videoId = first.id),
                            first.toMediaItem().metadata,
                            database
                        )
                    )
                }
            }
        )
    }
}
