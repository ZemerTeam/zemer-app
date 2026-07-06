package com.jtech.zemer.ui.screens.playlist

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.jtech.zemer.LocalPlayerAwareWindowInsets
import com.jtech.zemer.LocalPlayerConnection
import com.jtech.zemer.R
import com.jtech.zemer.constants.AlbumThumbnailSize
import com.jtech.zemer.constants.ThumbnailCornerRadius
import com.jtech.zemer.extensions.toMediaItem
import com.jtech.zemer.extensions.togglePlayPause
import com.jtech.zemer.latestreleases.LatestReleaseFilter
import com.jtech.zemer.playback.queues.ListQueue
import com.jtech.zemer.tracking.PlaySource
import com.jtech.zemer.search.SearchProvider
import com.jtech.zemer.search.onlineAlbumRoute
import com.jtech.zemer.ui.component.AutoResizeText
import com.jtech.zemer.ui.component.ChipsRow
import com.jtech.zemer.ui.component.FontSizeRange
import com.jtech.zemer.ui.component.IconButton
import com.jtech.zemer.ui.component.LocalMenuState
import com.jtech.zemer.ui.component.YouTubeListItem
import com.jtech.zemer.ui.component.zemerCuratedPlaylistRuntimeLabel
import com.jtech.zemer.ui.menu.YouTubeAlbumMenu
import com.jtech.zemer.ui.menu.YouTubeSongMenu
import com.jtech.zemer.ui.utils.backToMain
import com.jtech.zemer.utils.joinByBullet
import com.jtech.zemer.viewmodels.ZemerCuratedPlaylistViewModel
import com.jtech.zemer.viewmodels.ZemerCuratedPlaylistViewModel.UiState
import com.metrolist.innertube.models.SongItem

/**
 * The tracks visible under an All/Albums/Songs chip, keeping curated order. Reuses the Latest
 * Releases filter enum — same chips, same semantics: ALBUMS = tracks that entered the playlist via a
 * curated-album expansion ([albumTrackIds], from the server's `fromAlbum`), SONGS = direct picks.
 */
internal fun filterCuratedTracks(
    songs: List<SongItem>,
    albumTrackIds: Set<String>,
    filter: LatestReleaseFilter,
): List<SongItem> = when (filter) {
    LatestReleaseFilter.ALL -> songs
    LatestReleaseFilter.ALBUMS -> songs.filter { it.id in albumTrackIds }
    LatestReleaseFilter.SONGS -> songs.filter { it.id !in albumTrackIds }
}

/**
 * True when the currently selected chip has nothing to render — the ALBUMS chip shows album rows,
 * every other chip shows the filtered tracks. This (not the raw ALL list) gates the empty-state
 * message: a chip whose filtered result is empty must say so, never show a silent blank body.
 */
internal fun isCuratedChipEmpty(
    filter: LatestReleaseFilter,
    albumCount: Int,
    visibleSongCount: Int,
): Boolean = if (filter == LatestReleaseFilter.ALBUMS) albumCount == 0 else visibleSongCount == 0

/**
 * Detail screen for one hand-curated "Zemer Playlists" entry. Deliberately its OWN small screen —
 * the id is a server slug, not a YouTube playlist id, so it must never route through the
 * YouTube-playlist screen (whose save/like/menu actions all assume a YouTube id). Header + All/
 * Albums/Songs chips (the Latest Releases chip set) + track/album rows; tracks play like any Zemer
 * search result, albums open the album screen through the server path.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZemerCuratedPlaylistScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: ZemerCuratedPlaylistViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    val state by viewModel.state.collectAsState()

    // 404 = the curation changed between the Home list and this open; back out gracefully. The Home
    // section re-fetches on screen-open, so the stale card disappears on return.
    LaunchedEffect(state) {
        if (state is UiState.NotFound) navController.navigateUp()
    }

    val lazyListState = rememberLazyListState()
    val showTopBarTitle by remember {
        derivedStateOf { lazyListState.firstVisibleItemIndex > 0 }
    }

    // All / Albums / Songs chips, exactly like the Latest Releases screen (same enum, same strings).
    // Hoisted above the LazyColumn: everything below — the chip row, the visible rows, Play, Shuffle —
    // reads the same filtered list, so shuffling under a chip plays exactly what is shown.
    val loadedPage = (state as? UiState.Loaded)?.page
    val loadedSongs = loadedPage?.songs.orEmpty()
    var filter by rememberSaveable { mutableStateOf(LatestReleaseFilter.ALL) }
    val visibleSongs = remember(loadedPage, filter) {
        filterCuratedTracks(loadedSongs, loadedPage?.albumTrackIds.orEmpty(), filter)
    }

    val showSongMenu: (SongItem) -> Unit = { song ->
        menuState.show {
            YouTubeSongMenu(
                song = song,
                navController = navController,
                onDismiss = menuState::dismiss,
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = lazyListState,
            contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
        ) {
            when (val uiState = state) {
                UiState.Loading, UiState.NotFound -> {
                    item(key = "loading_shimmer") {
                        PlaylistHeaderShimmer()
                    }
                }

                is UiState.Loaded -> {
                    val playlist = uiState.page.playlist

                    item(key = "playlist_header") {
                        Column(
                            modifier = Modifier
                                .padding(12.dp)
                                .animateItem(),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AsyncImage(
                                    // The server guarantees this cover is filter-safe (a generated
                                    // title card, never member art); null -> placeholder.
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(playlist.thumbnail)
                                        .build(),
                                    contentDescription = null,
                                    placeholder = painterResource(R.drawable.queue_music),
                                    error = painterResource(R.drawable.queue_music),
                                    modifier = Modifier
                                        .size(AlbumThumbnailSize)
                                        .clip(RoundedCornerShape(ThumbnailCornerRadius)),
                                )

                                Spacer(Modifier.width(16.dp))

                                Column(verticalArrangement = Arrangement.Center) {
                                    AutoResizeText(
                                        text = playlist.title,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        fontSizeRange = FontSizeRange(16.sp, 22.sp),
                                    )

                                    Text(
                                        text = joinByBullet(
                                            pluralStringResource(R.plurals.n_song, loadedSongs.size, loadedSongs.size),
                                            // Null runtime = unknown; the label is simply hidden.
                                            zemerCuratedPlaylistRuntimeLabel(playlist.totalDurationSec),
                                        ),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Normal,
                                    )
                                }
                            }

                            Spacer(Modifier.height(12.dp))

                            if (visibleSongs.isNotEmpty()) {
                                PlaylistPlayShuffleButtons(
                                    onPlay = {
                                        playerConnection.playQueue(
                                            ListQueue(
                                                title = playlist.title,
                                                items = visibleSongs.map { it.toMediaItem() },
                                                playSource = PlaySource.zemer(playlist.id),
                                            )
                                        )
                                    },
                                    onShuffle = {
                                        playerConnection.playQueue(
                                            ListQueue(
                                                title = playlist.title,
                                                items = visibleSongs.map { it.toMediaItem() }.shuffled(),
                                                playSource = PlaySource.zemer(playlist.id),
                                            )
                                        )
                                    },
                                )
                            }
                        }
                    }

                    // All / Albums / Songs — scrolls with the list, like the Latest Releases screen.
                    item(key = "filter") {
                        ChipsRow(
                            chips = listOf(
                                LatestReleaseFilter.ALL to stringResource(R.string.filter_all),
                                LatestReleaseFilter.ALBUMS to stringResource(R.string.filter_albums),
                                LatestReleaseFilter.SONGS to stringResource(R.string.filter_songs),
                            ),
                            currentValue = filter,
                            onValueUpdate = { filter = it },
                        )
                    }

                    if (isCuratedChipEmpty(filter, uiState.page.albums.size, visibleSongs.size)) {
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
                            }
                        }
                    }

                    // Albums chip: the curated albums as browsable rows (tap opens the album screen
                    // via the server /album path). Play/Shuffle above still play the chip's tracks.
                    if (filter == LatestReleaseFilter.ALBUMS) {
                        items(
                            items = uiState.page.albums,
                            key = { it.browseId },
                        ) { album ->
                            YouTubeListItem(
                                item = album,
                                isActive = mediaMetadata?.album?.id == album.browseId,
                                isPlaying = isPlaying,
                                modifier = Modifier
                                    .combinedClickable(
                                        onClick = {
                                            navController.navigate(
                                                SearchProvider.ZEMER.onlineAlbumRoute(album)
                                            )
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
                                    )
                                    .animateItem(),
                            )
                        }
                    } else itemsIndexed(
                        items = visibleSongs,
                        key = { _, song -> song.id },
                    ) { index, song ->
                        // No albumIndex: the shared row shows the number INSTEAD of the artwork (an
                        // album-screen convention where all rows share one cover) — here every row
                        // has its own art, so the art wins, like the online-playlist screen.
                        YouTubeListItem(
                            item = song,
                            isActive = mediaMetadata?.id == song.id,
                            isPlaying = isPlaying,
                            trailingContent = {
                                IconButton(onClick = { showSongMenu(song) }) {
                                    Icon(
                                        painter = painterResource(R.drawable.more_vert),
                                        contentDescription = null,
                                    )
                                }
                            },
                            modifier = Modifier
                                .combinedClickable(
                                    onClick = {
                                        if (song.id == mediaMetadata?.id) {
                                            playerConnection.playPause()
                                        } else {
                                            playerConnection.playQueue(
                                                ListQueue(
                                                    title = playlist.title,
                                                    items = visibleSongs.map { it.toMediaItem() },
                                                    startIndex = index,
                                                    playSource = PlaySource.zemer(playlist.id),
                                                )
                                            )
                                        }
                                    },
                                    onLongClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        showSongMenu(song)
                                    },
                                )
                                .animateItem(),
                        )
                    }
                }

                UiState.Error -> {
                    item(key = "error_state") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = stringResource(R.string.error_unknown),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = viewModel::load) {
                                Text(stringResource(R.string.retry))
                            }
                        }
                    }
                }
            }
        }

        TopAppBar(
            title = {
                if (showTopBarTitle) {
                    Text((state as? UiState.Loaded)?.page?.playlist?.title.orEmpty())
                }
            },
            navigationIcon = {
                IconButton(
                    onClick = navController::navigateUp,
                    onLongClick = navController::backToMain,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.arrow_back),
                        contentDescription = null
                    )
                }
            },
            scrollBehavior = scrollBehavior,
        )
    }
}
