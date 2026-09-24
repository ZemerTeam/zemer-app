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
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastSumBy
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.jtech.zemer.LocalDownloadUtil
import com.jtech.zemer.LocalPlayerAwareWindowInsets
import com.jtech.zemer.LocalPlayerConnection
import com.jtech.zemer.R
import com.jtech.zemer.constants.SongSortDescendingKey
import com.jtech.zemer.constants.SongSortType
import com.jtech.zemer.constants.SongSortTypeKey
import com.jtech.zemer.constants.YtmSyncKey
import com.jtech.zemer.db.entities.Song
import com.jtech.zemer.extensions.toMediaItem
import com.jtech.zemer.playback.queues.ListQueue
import com.jtech.zemer.ui.component.AggregateDownloadButton
import com.jtech.zemer.ui.component.RemoveDownloadConfirmDialog
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
import com.jtech.zemer.ui.utils.activeRowTapTogglesPlayPause
import com.jtech.zemer.ui.utils.ItemWrapper
import com.jtech.zemer.utils.rememberEnumPreference
import com.jtech.zemer.utils.rememberPreference
import com.jtech.zemer.viewmodels.AutoPlaylistViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AutoPlaylistScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: AutoPlaylistViewModel = hiltViewModel(),
) {

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val autoPlaylistTitle = stringResource(R.string.queue_auto_playlist)
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val playlist = when (viewModel.playlist) {
        "liked" -> stringResource(R.string.liked)
        "uploaded" -> stringResource(R.string.uploaded_playlist)
        "downloaded_episodes" -> stringResource(R.string.downloaded_episodes)
        else -> stringResource(R.string.offline)
    }

    val songs by viewModel.likedSongs.collectAsState(null)
    val mutableSongs =
        remember {
            mutableStateListOf<Song>()
        }

    var isSearching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue())
    }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(isSearching) {
        if (isSearching) {
            focusRequester.requestFocus()
        }
    }

    val (ytmSync) = rememberPreference(YtmSyncKey, true)

    val likeLength =
        remember(songs) {
            songs?.fastSumBy { it.song.duration } ?: 0
        }

    val playlistId = viewModel.playlist
    val playlistType = when (playlistId) {
        "liked" -> PlaylistType.LIKE
        "downloaded", "downloaded_episodes" -> PlaylistType.DOWNLOAD
        "uploaded" -> PlaylistType.UPLOADED
        else -> PlaylistType.OTHER
    }

    val wrappedSongs = remember(songs) {
        songs?.map { item -> ItemWrapper(item) }?.toMutableStateList() ?: mutableStateListOf()
    }

    var selection by remember {
        mutableStateOf(false)
    }

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

    val downloadUtil = LocalDownloadUtil.current

    LaunchedEffect(Unit) {
        if (ytmSync) {
            withContext(Dispatchers.IO) {
                if (playlistType == PlaylistType.LIKE) viewModel.syncLikedSongs()
            }
        }
    }

    LaunchedEffect(songs) {
        mutableSongs.apply {
            clear()
            songs?.let { addAll(it) }
        }
    }

    var showRemoveDownloadDialog by remember {
        mutableStateOf(false)
    }

    if (showRemoveDownloadDialog) {
        RemoveDownloadConfirmDialog(
            playlistName = playlist,
            onDismiss = { showRemoveDownloadDialog = false },
            onConfirm = {
                showRemoveDownloadDialog = false
                songs!!.forEach { song ->
                    coroutineScope.launch {
                        downloadUtil.removeDownload(song.song.id)
                    }
                }
            },
        )
    }

    val filteredSongs = remember(wrappedSongs, query) {
        if (query.text.isEmpty()) wrappedSongs
        else wrappedSongs.filter { wrapper ->
            val song = wrapper.item
            song.song.title.contains(query.text, true) ||
                    song.artists.any { it.name.contains(query.text, true) }
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
            if (songs != null) {
                if (songs!!.isEmpty()) {
                    item(key = "empty_placeholder") {
                        EmptyPlaceholder(
                            icon = R.drawable.music_note,
                            text = stringResource(R.string.playlist_is_empty),
                        )
                    }
                } else {
                    if (!isSearching) {
                        item(key = "playlist_header") {
                            PlaylistDetailHeader(
                                coverUrl = songs!![0].song.thumbnailUrl,
                                title = playlist,
                                itemCount = songs!!.size,
                                pluralRes = R.plurals.n_song,
                                totalDurationMs = likeLength * 1000L,
                                aggregateDownload = {
                                    AggregateDownloadButton(
                                        songs = songs.orEmpty(),
                                        onDownloadAll = {
                                            songs?.forEach { downloadUtil.downloadToMediaStore(it) }
                                        },
                                        onRemoveAll = { showRemoveDownloadDialog = true },
                                        onCancelAll = {
                                            songs?.forEach { song ->
                                                coroutineScope.launch {
                                                    downloadUtil.removeDownload(song.song.id)
                                                }
                                            }
                                        },
                                    )
                                },
                                onAddToQueue = {
                                    playerConnection.addToQueue(
                                        items = songs!!.map { it.toMediaItem() },
                                    )
                                },
                                onPlay = {
                                    playerConnection.playQueue(
                                        ListQueue(
                                            title = autoPlaylistTitle,
                                            items = songs!!.map { it.toMediaItem() },
                                        ),
                                    )
                                },
                                onShuffle = {
                                    playerConnection.playQueue(
                                        ListQueue(
                                            title = playlist,
                                            items = songs!!.shuffled()
                                                .map { it.toMediaItem() },
                                        ),
                                    )
                                },
                            )
                        }
                    }

                    item(key = "songs_header") {
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
                    items = filteredSongs,
                    key = { _, song -> song.item.id },
                ) { index, songWrapper ->
                    SongListItem(
                        song = songWrapper.item,
                        isActive = songWrapper.item.song.id == mediaMetadata?.id,
                        isPlaying = isPlaying,
                        showInLibraryIcon = true,
                        trailingContent = {
                            MoreVertMenuButton(
                                onClick = {
                                    menuState.show {
                                        SongMenu(
                                            originalSong = songWrapper.item,
                                            navController = navController,
                                            onDismiss = menuState::dismiss,
                                        )
                                    }
                                },
                            )
                        },
                        isSelected = songWrapper.isSelected && selection,
                        modifier =
                        Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = {
                                    if (!selection) {
                                        if (activeRowTapTogglesPlayPause(songWrapper.item.song.id == mediaMetadata?.id, playerConnection.isStationBroadcast.value)) {
                                            playerConnection.playPause()
                                        } else {
                                            playerConnection.playQueue(
                                                ListQueue(
                                                    title = playlist,
                                                    items = songs!!.map { it.toMediaItem() },
                                                    startIndex = songs!!.indexOfFirst { it.id == songWrapper.item.id }
                                                ),
                                            )
                                        }
                                    } else {
                                        songWrapper.isSelected = !songWrapper.isSelected
                                    }
                                },
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    if (!selection) {
                                        selection = true
                                        wrappedSongs.forEach { it.isSelected = false }
                                        songWrapper.isSelected = true
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
            idleTitle = playlist,
            isSearching = isSearching,
            onIsSearchingChange = { isSearching = it },
            query = query,
            onQueryChange = { query = it },
            focusRequester = focusRequester,
            selectionCount = { if (selection) wrappedSongs.count { it.isSelected } else null },
            selectionCountPlural = R.plurals.n_song,
            onExitSelection = { selection = false },
            actions = {
                SelectionActions(
                    wrapped = wrappedSongs,
                    onMore = {
                        menuState.show {
                            SelectionSongMenu(
                                songSelection = wrappedSongs.filter { it.isSelected }
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

enum class PlaylistType {
    LIKE, DOWNLOAD, UPLOADED, OTHER
}
