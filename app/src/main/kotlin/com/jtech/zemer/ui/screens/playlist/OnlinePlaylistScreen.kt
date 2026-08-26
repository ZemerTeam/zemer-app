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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastAny
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.jtech.zemer.LocalDatabase
import com.jtech.zemer.LocalPlayerAwareWindowInsets
import com.jtech.zemer.LocalPlayerConnection
import com.jtech.zemer.R
import com.jtech.zemer.constants.AlbumThumbnailSize
import com.jtech.zemer.constants.ThumbnailCornerRadius
import com.jtech.zemer.db.entities.PlaylistEntity
import com.jtech.zemer.db.entities.PlaylistSongMap
import com.jtech.zemer.extensions.metadata
import com.jtech.zemer.extensions.toMediaItem
import com.jtech.zemer.models.toMediaMetadata
import com.jtech.zemer.playback.queues.ListQueue
import com.jtech.zemer.ui.component.AutoResizeText
import com.jtech.zemer.ui.component.DraggableScrollbar
import com.jtech.zemer.ui.component.FontSizeRange
import com.jtech.zemer.ui.component.IconButton
import com.jtech.zemer.ui.component.LocalMenuState
import com.jtech.zemer.ui.component.MoreVertMenuButton
import com.jtech.zemer.ui.component.ErrorRetryState
import com.jtech.zemer.ui.component.SelectionActions
import com.jtech.zemer.ui.component.SearchableSelectableTopAppBar
import com.jtech.zemer.ui.component.YouTubeListItem
import com.jtech.zemer.ui.component.shimmer.LoadingListPlaceholder
import com.jtech.zemer.ui.menu.SelectionMediaMetadataMenu
import com.jtech.zemer.ui.menu.YouTubePlaylistMenu
import com.jtech.zemer.ui.menu.YouTubeSongMenu
import com.jtech.zemer.ui.utils.activeRowTapTogglesPlayPause
import com.jtech.zemer.ui.utils.ItemWrapper
import com.jtech.zemer.ui.utils.navigateToArtist
import com.jtech.zemer.viewmodels.OnlinePlaylistViewModel
import com.metrolist.innertube.models.SongItem

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun OnlinePlaylistScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: OnlinePlaylistViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    val database = LocalDatabase.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    val playlist by viewModel.playlist.collectAsState()
    val songs by viewModel.playlistSongs.collectAsState()
    val dbPlaylist by viewModel.dbPlaylist.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val error by viewModel.error.collectAsState()

    var selection by remember {
        mutableStateOf(false)
    }

    val lazyListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var isSearching by rememberSaveable { mutableStateOf(false) }

    var query by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue())
    }

    val filteredSongs =
        remember(songs, query) {
            if (query.text.isEmpty()) {
                songs.mapIndexed { index, song -> index to song }
            } else {
                songs
                    .mapIndexed { index, song -> index to song }
                    .filter { (_, song) ->
                        song.title.contains(query.text, ignoreCase = true) ||
                                song.artists.fastAny {
                                    it.name.contains(
                                        query.text,
                                        ignoreCase = true
                                    )
                                }
                    }
            }
        }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(isSearching) {
        if (isSearching) {
            focusRequester.requestFocus()
        }
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

    val wrappedSongs = remember(filteredSongs) {
        filteredSongs.map { item -> ItemWrapper(item) }
    }.toMutableStateList()

    val showTopBarTitle by remember {
        derivedStateOf {
            lazyListState.firstVisibleItemIndex > 0
        }
    }

    LaunchedEffect(lazyListState) {
        snapshotFlow { lazyListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { lastVisibleIndex ->
                if (songs.size >= 5 && lastVisibleIndex != null && lastVisibleIndex >= songs.size - 5) {
                    viewModel.loadMoreSongs()
                }
            }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            state = lazyListState,
            contentPadding = LocalPlayerAwareWindowInsets.current.union(WindowInsets.ime)
                .asPaddingValues(),
        ) {
            playlist.let { playlist ->
                if (isLoading) {
                    item(key = "loading_shimmer") {
                        PlaylistHeaderShimmer()
                    }
                } else if (playlist != null) {
                    if (!isSearching) {
                        item(key = "playlist_header") {
                            Column(
                                modifier =
                                Modifier
                                    .padding(12.dp)
                                    .animateItem(),
                            ) {
                                Row(
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
                                        // Derive the header cover from the first content-filtered track,
                                        // never the raw curator image (which can be female even when
                                        // female is blocked). Null -> the queue_music placeholder below.
                                        model = ImageRequest.Builder(LocalContext.current)
                                            .data(filteredPlaylistCover(songs) { it.thumbnail })
                                            .build(),
                                        contentDescription = null,
                                        placeholder = painterResource(R.drawable.queue_music),
                                        error = painterResource(R.drawable.queue_music),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(ThumbnailCornerRadius)),
                                        )
                                    }

                                    Spacer(Modifier.width(16.dp))

                                    Column(
                                        verticalArrangement = Arrangement.Center,
                                    ) {
                                        AutoResizeText(
                                            text = playlist.title,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            fontSizeRange = FontSizeRange(16.sp, 22.sp),
                                        )

                                        playlist.author?.let { artist ->
                                            Text(
                                                buildAnnotatedString {
                                                    withStyle(
                                                        style =
                                                        MaterialTheme.typography.titleMedium
                                                            .copy(
                                                                fontWeight = FontWeight.Normal,
                                                                color = MaterialTheme.colorScheme.onBackground,
                                                            ).toSpanStyle(),
                                                    ) {
                                                        if (artist.id != null) {
                                                            val link =
                                                                LinkAnnotation.Clickable(artist.id!!) {
                                                                    navController.navigateToArtist(artist.id)
                                                                }
                                                            withLink(link) {
                                                                append(artist.name)
                                                            }
                                                        } else {
                                                            append(artist.name)
                                                        }
                                                    }
                                                },
                                            )
                                        }

                                        playlist.songCountText?.let { songCountText ->
                                            Text(
                                                text = songCountText,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Normal,
                                            )
                                        }

                                        Row {
                                            if (playlist.id != "LM") {
                                                IconButton(
                                                    onClick = {
                                                        if (dbPlaylist?.playlist == null) {
                                                            database.transaction {
                                                                val playlistEntity = PlaylistEntity(
                                                                    name = playlist.title,
                                                                    browseId = playlist.id,
                                                                    // Save the filtered-track cover, not the
                                                                    // raw curator image, so a saved community
                                                                    // playlist never shows a female cover.
                                                                    thumbnailUrl = filteredPlaylistCover(songs) { it.thumbnail },
                                                                    isEditable = playlist.isEditable,
                                                                    playEndpointParams = playlist.playEndpoint?.params,
                                                                    shuffleEndpointParams = playlist.shuffleEndpoint?.params,
                                                                    radioEndpointParams = playlist.radioEndpoint?.params
                                                                ).toggleLike()
                                                                insert(playlistEntity)
                                                                songs.map(SongItem::toMediaMetadata)
                                                                    .onEach(::insert)
                                                                    .mapIndexed { index, song ->
                                                                        PlaylistSongMap(
                                                                            songId = song.id,
                                                                            playlistId = playlistEntity.id,
                                                                            position = index
                                                                        )
                                                                    }
                                                                    .forEach(::insert)
                                                            }
                                                        } else {
                                                            database.transaction {
                                                                // Update playlist information including thumbnail before toggling like
                                                                val currentPlaylist = dbPlaylist!!.playlist
                                                                update(currentPlaylist, playlist)
                                                                update(currentPlaylist.toggleLike())
                                                            }
                                                        }
                                                    }
                                                ) {
                                                    Icon(
                                                        painter = painterResource(
                                                            if (dbPlaylist?.playlist?.bookmarkedAt != null) R.drawable.favorite else R.drawable.favorite_border
                                                        ),
                                                        contentDescription = null,
                                                        tint = if (dbPlaylist?.playlist?.bookmarkedAt != null) MaterialTheme.colorScheme.error else LocalContentColor.current
                                                    )
                                                }
                                            }

                                            MoreVertMenuButton(
                                                onClick = {
                                                    menuState.show {
                                                        YouTubePlaylistMenu(
                                                            playlist = playlist,
                                                            songs = songs,
                                                            coroutineScope = coroutineScope,
                                                            onDismiss = menuState::dismiss,
                                                            selectAction = { selection = true },
                                                            canSelect = true,
                                                        )
                                                    }
                                                },
                                            )
                                        }
                                    }
                                }

                                Spacer(Modifier.height(12.dp))

                                PlaylistPlayShuffleButtons(
                                    onPlay = {
                                        playerConnection.playQueue(
                                            ListQueue(
                                                title = playlist.title,
                                                items = songs.map { it.toMediaItem() },
                                                playSource = viewModel.playSource,
                                            )
                                        )
                                    }.takeIf { songs.isNotEmpty() },
                                    onShuffle = {
                                        playerConnection.playQueue(
                                            ListQueue(
                                                title = playlist.title,
                                                items = songs.map { it.toMediaItem() }.shuffled(),
                                                playSource = viewModel.playSource,
                                            )
                                        )
                                        // Shuffle just reorders the already-loaded `songs`, so a Zemer
                                        // playlist (whole filtered list up front, no `shuffleEndpoint`) can
                                        // offer it whenever there are songs — same as the curated screen.
                                        // YouTube playlists keep the endpoint gate.
                                    }.takeIf { if (viewModel.isZemer) songs.isNotEmpty() else playlist.shuffleEndpoint != null },
                                )
                            }
                        }
                    }

                    if (songs.isEmpty() && error == null) {
                        // Show empty playlist message when playlist is loaded but has no songs
                        item(key = "empty_playlist") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = stringResource(R.string.empty_playlist),
                                    style = MaterialTheme.typography.titleLarge
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = stringResource(R.string.empty_playlist_desc),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    itemsIndexed(
                        items = wrappedSongs,
                    ) { index, song ->
                        YouTubeListItem(
                            item = song.item.second,
                            isActive = mediaMetadata?.id == song.item.second.id,
                            isPlaying = isPlaying,
                            isSelected = song.isSelected && selection,
                            trailingContent = {
                                MoreVertMenuButton(
                                    onClick = {
                                        menuState.show {
                                            YouTubeSongMenu(
                                                song = song.item.second,
                                                navController = navController,
                                                onDismiss = menuState::dismiss,
                                            )
                                        }
                                    },
                                )
                            },
                            modifier =
                            Modifier
                                .combinedClickable(
                                    onClick = {
                                        if (!selection) {
                                            if (activeRowTapTogglesPlayPause(song.item.second.id == mediaMetadata?.id, playerConnection.isStationBroadcast.value)) {
                                                playerConnection.playPause()
                                            } else {
                                                playerConnection.playQueue(
                                                    ListQueue(
                                                        title = playlist.title,
                                                        items = filteredSongs.map { it.second.toMediaItem() },
                                                        startIndex = index,
                                                        playSource = viewModel.playSource
                                                    )
                                                )
                                            }
                                        } else {
                                            song.isSelected = !song.isSelected
                                        }
                                    },
                                    onLongClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        if (!selection) {
                                            selection = true
                                        }
                                        wrappedSongs.forEach { it.isSelected = false }
                                        song.isSelected = true
                                    },
                                )
                                .animateItem(),
                        )
                    }

                    if (viewModel.continuation != null && songs.isNotEmpty() && isLoadingMore) {
                        item(key = "loading_more") {
                            LoadingListPlaceholder(2)
                        }
                    }

                } else {
                    // Show error state when playlist is null and there's an error
                    item(key = "error_state") {
                        if (error != null) {
                            ErrorRetryState(
                                onRetry = { viewModel.retry() },
                                detail = error,
                            )
                        } else {
                            // Not-found is not an error: neutral copy, no retry (it won't appear).
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = stringResource(R.string.playlist_not_found),
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = stringResource(R.string.playlist_not_found_desc),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
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
            scrollState = lazyListState,
            headerItems = 1
        )

        SearchableSelectableTopAppBar(
            navController = navController,
            idleTitle = if (showTopBarTitle) playlist?.title.orEmpty() else null,
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
                            SelectionMediaMetadataMenu(
                                songSelection = wrappedSongs.filter { it.isSelected }
                                    .map { it.item.second.toMediaItem().metadata!! },
                                onDismiss = menuState::dismiss,
                                clearAction = { selection = false },
                                currentItems = emptyList()
                            )
                        }
                    },
                )
            },
        )

        SnackbarHost(
            hostState = snackbarHostState,
            modifier =
            Modifier
                .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.union(WindowInsets.ime))
                .align(Alignment.BottomCenter),
        )
    }
}
