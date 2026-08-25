package com.jtech.zemer.ui.screens.playlist

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastSumBy
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.jtech.zemer.LocalPlayerAwareWindowInsets
import com.jtech.zemer.LocalPlayerConnection
import com.jtech.zemer.R
import com.jtech.zemer.constants.SongSortDescendingKey
import com.jtech.zemer.constants.SongSortType
import com.jtech.zemer.constants.SongSortTypeKey
import com.jtech.zemer.constants.VideoDownloadsInMusicKey
import com.jtech.zemer.ui.component.SwitchPreference
import com.jtech.zemer.db.entities.Song
import com.jtech.zemer.extensions.toMediaItem
import com.jtech.zemer.playback.queues.ListQueue
import com.jtech.zemer.ui.component.DraggableScrollbar
import com.jtech.zemer.ui.component.EmptyPlaceholder
import com.jtech.zemer.ui.component.LocalMenuState
import com.jtech.zemer.ui.component.MoreVertMenuButton
import com.jtech.zemer.ui.component.SearchableSelectableTopAppBar
import com.jtech.zemer.ui.component.SelectionActions
import com.jtech.zemer.ui.component.SongListItem
import com.jtech.zemer.ui.component.SortHeader
import com.jtech.zemer.ui.component.songSortTypeLabel
import com.jtech.zemer.ui.menu.SelectionSongMenu
import com.jtech.zemer.ui.menu.SongMenu
import com.jtech.zemer.ui.utils.ItemWrapper
import com.jtech.zemer.utils.rememberEnumPreference
import com.jtech.zemer.utils.rememberPreference
import com.jtech.zemer.viewmodels.DownloadedVideosViewModel

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DownloadedVideosScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: DownloadedVideosViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    val videos by viewModel.downloadedVideos.collectAsState(null)
    val downloadedVideosTitle = stringResource(R.string.downloaded_videos)
    val mutableVideos = remember { mutableStateListOf<Song>() }

    var isSearching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf(TextFieldValue()) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(isSearching) {
        if (isSearching) {
            focusRequester.requestFocus()
        }
    }

    val videoLength = remember(videos) {
        videos?.fastSumBy { it.song.duration } ?: 0
    }

    val wrappedVideos = remember(videos) {
        videos?.map { item -> ItemWrapper(item) }?.toMutableStateList() ?: mutableStateListOf()
    }

    var selection by remember { mutableStateOf(false) }

    if (isSearching) {
        BackHandler {
            isSearching = false
            query = TextFieldValue()
        }
    } else if (selection) {
        BackHandler {
            selection = false
        }
    }

    val (sortType, onSortTypeChange) = rememberEnumPreference(
        SongSortTypeKey,
        SongSortType.CREATE_DATE
    )
    val (sortDescending, onSortDescendingChange) = rememberPreference(SongSortDescendingKey, true)
    val (videosInMusic, onVideosInMusicChange) = rememberPreference(VideoDownloadsInMusicKey, true)

    LaunchedEffect(videos) {
        mutableVideos.apply {
            clear()
            videos?.let { addAll(it) }
        }
    }

    val filteredVideos = remember(wrappedVideos, query) {
        if (query.text.isEmpty()) wrappedVideos
        else wrappedVideos.filter { wrapper ->
            val video = wrapper.item
            video.song.title.contains(query.text, true) ||
                    video.artists.any { it.name.contains(query.text, true) }
        }
    }

    val state = rememberLazyListState()

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            state = state,
            contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
        ) {
            if (videos != null) {
                if (videos!!.isEmpty()) {
                    item(key = "empty_placeholder") {
                        EmptyPlaceholder(
                            icon = R.drawable.slow_motion_video,
                            text = stringResource(R.string.no_downloaded_videos),
                        )
                    }
                } else {
                    if (!isSearching) {
                        item(key = "playlist_header") {
                            PlaylistDetailHeader(
                                coverUrl = videos!![0].song.thumbnailUrl,
                                title = stringResource(R.string.downloaded_videos),
                                itemCount = videos!!.size,
                                pluralRes = R.plurals.n_video,
                                totalDurationMs = videoLength * 1000L,
                                aggregateDownload = null,
                                onAddToQueue = {
                                    playerConnection.addToQueue(
                                        items = videos!!.map { it.toMediaItem() },
                                    )
                                },
                                onPlay = {
                                    // Audio-first always (I2); video is a per-play in-player toggle (D3).
                                    playerConnection.playQueue(
                                        ListQueue(
                                            title = downloadedVideosTitle,
                                            items = videos!!.map { it.toMediaItem() },
                                        )
                                    )
                                },
                                onShuffle = {
                                    playerConnection.playQueue(
                                        ListQueue(
                                            title = downloadedVideosTitle,
                                            items = videos!!.shuffled().map { it.toMediaItem() },
                                        )
                                    )
                                },
                            )
                        }
                    }

                    item(key = "videos_in_music_toggle") {
                        // Downloaded video-songs double as ordinary audio-first song rows in the
                        // downloaded MUSIC surfaces (the one muxed file serves both renditions;
                        // in-player Song/Video toggle picks the rendition per play). Opt out here.
                        SwitchPreference(
                            title = { Text(stringResource(R.string.video_downloads_in_music)) },
                            description = stringResource(R.string.video_downloads_in_music_description),
                            checked = videosInMusic,
                            onCheckedChange = onVideosInMusicChange,
                        )
                    }

                    item(key = "videos_header") {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 16.dp),
                        ) {
                            SortHeader(
                                sortType = sortType,
                                sortDescending = sortDescending,
                                onSortTypeChange = onSortTypeChange,
                                onSortDescendingChange = onSortDescendingChange,
                                sortTypeText = ::songSortTypeLabel,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                itemsIndexed(
                    items = filteredVideos,
                    key = { _, video -> video.item.id },
                ) { index, videoWrapper ->
                    SongListItem(
                        song = videoWrapper.item,
                        isActive = videoWrapper.item.song.id == mediaMetadata?.id,
                        isPlaying = isPlaying,
                        showInLibraryIcon = true,
                        trailingContent = {
                            MoreVertMenuButton(
                                onClick = {
                                    menuState.show {
                                        SongMenu(
                                            originalSong = videoWrapper.item,
                                            navController = navController,
                                            onDismiss = menuState::dismiss,
                                        )
                                    }
                                },
                            )
                        },
                        isSelected = videoWrapper.isSelected && selection,
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = {
                                    if (!selection) {
                                        // Audio-first always (I2); video is a per-play in-player toggle (D3).
                                        playerConnection.playQueue(
                                            ListQueue(
                                                title = downloadedVideosTitle,
                                                items = filteredVideos.map { it.item.toMediaItem() },
                                                startIndex = index,
                                            )
                                        )
                                    } else {
                                        videoWrapper.isSelected = !videoWrapper.isSelected
                                    }
                                },
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    if (!selection) {
                                        selection = true
                                        wrappedVideos.forEach { it.isSelected = false }
                                        videoWrapper.isSelected = true
                                    }
                                },
                            )
                            .animateItem()
                    )
                }
            }
        }

        DraggableScrollbar(
            modifier = Modifier
                .padding(
                    LocalPlayerAwareWindowInsets.current.union(WindowInsets.ime)
                        .asPaddingValues()
                )
                .align(Alignment.CenterEnd),
            scrollState = state,
            headerItems = 2
        )

        SearchableSelectableTopAppBar(
            navController = navController,
            idleTitle = stringResource(R.string.downloaded_videos),
            isSearching = isSearching,
            onIsSearchingChange = { isSearching = it },
            query = query,
            onQueryChange = { query = it },
            focusRequester = focusRequester,
            selectionCount = if (selection) wrappedVideos.count { it.isSelected } else null,
            selectionCountPlural = R.plurals.n_video,
            onExitSelection = { selection = false },
            actions = {
                SelectionActions(
                    wrapped = wrappedVideos,
                    onMore = {
                        menuState.show {
                            SelectionSongMenu(
                                songSelection = wrappedVideos.filter { it.isSelected }
                                    .map { it.item },
                                onDismiss = menuState::dismiss,
                                clearAction = { selection = false },
                            )
                        }
                    },
                )
            },
        )
    }
}
