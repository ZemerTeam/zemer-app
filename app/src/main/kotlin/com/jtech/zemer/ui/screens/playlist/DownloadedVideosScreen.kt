package com.jtech.zemer.ui.screens.playlist

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastSumBy
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.jtech.zemer.LocalPlayerAwareWindowInsets
import com.jtech.zemer.LocalPlayerConnection
import com.jtech.zemer.R
import com.jtech.zemer.constants.AlbumThumbnailSize
import com.jtech.zemer.constants.SongSortDescendingKey
import com.jtech.zemer.constants.SongSortType
import com.jtech.zemer.constants.SongSortTypeKey
import com.jtech.zemer.constants.ThumbnailCornerRadius
import com.jtech.zemer.db.entities.Song
import com.jtech.zemer.extensions.toMediaItem
import com.jtech.zemer.playback.queues.ListQueue
import com.jtech.zemer.ui.component.AutoResizeText
import com.jtech.zemer.ui.component.DraggableScrollbar
import com.jtech.zemer.ui.component.EmptyPlaceholder
import com.jtech.zemer.ui.component.FontSizeRange
import com.jtech.zemer.ui.component.IconButton
import com.jtech.zemer.ui.component.LocalMenuState
import com.jtech.zemer.ui.component.SongListItem
import com.jtech.zemer.ui.component.SortHeader
import com.jtech.zemer.ui.menu.SelectionSongMenu
import com.jtech.zemer.ui.menu.SongMenu
import com.jtech.zemer.ui.screens.videoRoute
import com.jtech.zemer.ui.utils.ItemWrapper
import com.jtech.zemer.ui.utils.backToMain
import com.jtech.zemer.utils.makeTimeString
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
    val focusManager = LocalFocusManager.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    val videos by viewModel.downloadedVideos.collectAsState(null)
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
                            Column(
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.padding(12.dp),
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .size(AlbumThumbnailSize)
                                            .clip(RoundedCornerShape(ThumbnailCornerRadius))
                                            .fillMaxWidth(),
                                    ) {
                                        AsyncImage(
                                            model = videos!![0].song.thumbnailUrl,
                                            contentDescription = null,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(ThumbnailCornerRadius)),
                                        )
                                    }

                                    Column(
                                        verticalArrangement = Arrangement.Center,
                                    ) {
                                        AutoResizeText(
                                            text = stringResource(R.string.downloaded_videos),
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            fontSizeRange = FontSizeRange(16.sp, 22.sp),
                                        )

                                        Text(
                                            text = pluralStringResource(
                                                R.plurals.n_video,
                                                videos!!.size,
                                                videos!!.size,
                                            ),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Normal,
                                        )

                                        Text(
                                            text = makeTimeString(videoLength * 1000L),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Normal,
                                        )

                                        Row {
                                            IconButton(
                                                onClick = {
                                                    playerConnection.addToQueue(
                                                        items = videos!!.map { it.toMediaItem() },
                                                    )
                                                },
                                            ) {
                                                Icon(
                                                    painter = painterResource(R.drawable.queue_music),
                                                    contentDescription = null,
                                                )
                                            }
                                        }
                                    }
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Button(
                                        onClick = {
                                            videos!!.firstOrNull()?.let { first ->
                                                val artistDisplay = first.artists.joinToString(" • ") { it.name }
                                                navController.navigate(videoRoute(first.id, first.song.title, artistDisplay))
                                            }
                                        },
                                        contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.play),
                                            contentDescription = null,
                                            modifier = Modifier.size(ButtonDefaults.IconSize),
                                        )
                                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                                        Text(stringResource(R.string.play))
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            videos!!.shuffled().firstOrNull()?.let { first ->
                                                val artistDisplay = first.artists.joinToString(" • ") { it.name }
                                                navController.navigate(videoRoute(first.id, first.song.title, artistDisplay))
                                            }
                                        },
                                        contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.shuffle),
                                            contentDescription = null,
                                            modifier = Modifier.size(ButtonDefaults.IconSize),
                                        )
                                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                                        Text(stringResource(R.string.shuffle))
                                    }
                                }
                            }
                        }
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
                                sortTypeText = { sortType ->
                                    when (sortType) {
                                        SongSortType.CREATE_DATE -> R.string.sort_by_create_date
                                        SongSortType.NAME -> R.string.sort_by_name
                                        SongSortType.ARTIST -> R.string.sort_by_artist
                                        SongSortType.PLAY_TIME -> R.string.sort_by_play_time
                                    }
                                },
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
                            IconButton(
                                onClick = {
                                    menuState.show {
                                        SongMenu(
                                            originalSong = videoWrapper.item,
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
                        isSelected = videoWrapper.isSelected && selection,
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = {
                                    if (!selection) {
                                        val artistDisplay = videoWrapper.item.artists.joinToString(" • ") { it.name }
                                        navController.navigate(videoRoute(videoWrapper.item.id, videoWrapper.item.song.title, artistDisplay))
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

        TopAppBar(
            title = {
                when {
                    selection -> {
                        val count = wrappedVideos.count { it.isSelected }
                        Text(
                            text = pluralStringResource(R.plurals.n_video, count, count),
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                    isSearching -> {
                        TextField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = {
                                Text(
                                    text = stringResource(R.string.search),
                                    style = MaterialTheme.typography.titleLarge
                                )
                            },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.titleLarge,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                disabledIndicatorColor = Color.Transparent,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester)
                        )
                    }
                    else -> {
                        Text(
                            text = stringResource(R.string.downloaded_videos),
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                }
            },
            navigationIcon = {
                IconButton(
                    onClick = {
                        when {
                            isSearching -> {
                                isSearching = false
                                query = TextFieldValue()
                                focusManager.clearFocus()
                            }
                            selection -> {
                                selection = false
                            }
                            else -> {
                                navController.navigateUp()
                            }
                        }
                    },
                    onLongClick = {
                        if (!isSearching && !selection) {
                            navController.backToMain()
                        }
                    }
                ) {
                    Icon(
                        painter = painterResource(
                            if (selection) R.drawable.close else R.drawable.arrow_back
                        ),
                        contentDescription = null
                    )
                }
            },
            actions = {
                if (selection) {
                    val count = wrappedVideos.count { it.isSelected }
                    IconButton(
                        onClick = {
                            if (count == wrappedVideos.size) {
                                wrappedVideos.forEach { it.isSelected = false }
                            } else {
                                wrappedVideos.forEach { it.isSelected = true }
                            }
                        },
                    ) {
                        Icon(
                            painter = painterResource(
                                if (count == wrappedVideos.size) R.drawable.deselect else R.drawable.select_all
                            ),
                            contentDescription = null
                        )
                    }

                    IconButton(
                        onClick = {
                            menuState.show {
                                SelectionSongMenu(
                                    songSelection = wrappedVideos.filter { it.isSelected }
                                        .map { it.item },
                                    onDismiss = menuState::dismiss,
                                    clearAction = { selection = false },
                                )
                            }
                        },
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.more_vert),
                            contentDescription = null
                        )
                    }
                } else if (!isSearching) {
                    IconButton(
                        onClick = { isSearching = true }
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.search),
                            contentDescription = null
                        )
                    }
                }
            }
        )
    }
}
