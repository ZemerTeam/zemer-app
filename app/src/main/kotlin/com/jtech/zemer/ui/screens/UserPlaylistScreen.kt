package com.jtech.zemer.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.jtech.zemer.LocalDatabase
import com.jtech.zemer.LocalPlayerAwareWindowInsets
import com.jtech.zemer.LocalPlayerConnection
import com.jtech.zemer.R
import com.jtech.zemer.constants.AlbumThumbnailSize
import com.jtech.zemer.constants.ThumbnailCornerRadius
import com.jtech.zemer.extensions.toMediaItem
import com.jtech.zemer.models.toMediaMetadata
import com.jtech.zemer.playback.queues.ListQueue
import com.jtech.zemer.tracking.PlaySource
import com.jtech.zemer.ui.component.AppStateView
import com.jtech.zemer.ui.component.AutoResizeText
import com.jtech.zemer.ui.component.FontSizeRange
import com.jtech.zemer.ui.component.IconButton
import com.jtech.zemer.ui.component.LocalMenuState
import com.jtech.zemer.ui.component.YouTubeListItem
import com.jtech.zemer.ui.component.zemerCuratedPlaylistRuntimeLabel
import com.jtech.zemer.ui.component.shimmer.ListItemPlaceHolder
import com.jtech.zemer.ui.component.shimmer.ShimmerHost
import com.jtech.zemer.ui.menu.ImportPlaylistDialog
import com.jtech.zemer.ui.menu.YouTubeSongMenu
import com.jtech.zemer.ui.screens.playlist.PlaylistPlayShuffleButtons
import com.jtech.zemer.ui.screens.playlist.filteredPlaylistCover
import com.jtech.zemer.ui.utils.backToMain
import com.jtech.zemer.utils.joinByBullet
import com.jtech.zemer.viewmodels.UserPlaylistViewModel
import com.metrolist.innertube.models.SongItem

/**
 * A shared user playlist opened from its link (issue #176), in the app's standard playlist-detail
 * dress: cover derived from the FIRST content-filtered track (the filteredPlaylistCover doctrine —
 * never sender-chosen art), [AutoResizeText] title, "Shared playlist • N songs" line, the shared
 * [PlaylistPlayShuffleButtons], Save a copy, and full song rows (active-state, per-song menu).
 * Everything plays via a [ListQueue] tagged `shared:<id>`. States: 404 → "not available";
 * all-filtered → an honest empty state, never a broken screen.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun UserPlaylistScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: UserPlaylistViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val database = LocalDatabase.current
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val page by viewModel.page.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val notFound by viewModel.notFound.collectAsState()
    val loadFailed by viewModel.loadFailed.collectAsState()
    var showImportDialog by remember { mutableStateOf(false) }

    val playSource = PlaySource.shared(viewModel.shareId)
    fun queueOf(songs: List<SongItem>, startIndex: Int = 0, shuffle: Boolean = false) = ListQueue(
        title = page?.header?.title,
        items = (if (shuffle) songs.shuffled() else songs).map { it.toMediaItem() },
        startIndex = startIndex,
        playSource = playSource,
    )

    val showSongMenu: (SongItem) -> Unit = { song ->
        menuState.show {
            YouTubeSongMenu(
                song = song,
                navController = navController,
                onDismiss = menuState::dismiss,
            )
        }
    }

    LazyColumn(
        contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
        modifier = Modifier.fillMaxSize(),
    ) {
        when {
            isLoading -> item(key = "loading") {
                ShimmerHost { repeat(8) { ListItemPlaceHolder() } }
            }

            loadFailed -> item(key = "load_failed") {
                // A fetch failure on a possibly-valid link: retryable, never the permanent
                // "not available" copy a real 404 gets.
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    AppStateView(
                        title = stringResource(R.string.user_playlist_load_failed),
                        icon = R.drawable.queue_music,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
                    )
                    OutlinedButton(onClick = viewModel::retry) {
                        Text(stringResource(R.string.retry))
                    }
                }
            }

            notFound -> item(key = "not_found") {
                AppStateView(
                    title = stringResource(R.string.user_playlist_not_found),
                    icon = R.drawable.queue_music,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
                )
            }

            page?.songs.isNullOrEmpty() -> item(key = "empty") {
                // The link is valid but nothing survives this receiver's content filters — an
                // honest state, per the handoff ("must not look broken").
                AppStateView(
                    title = page?.header?.title ?: "",
                    subtitle = stringResource(R.string.user_playlist_empty_filtered),
                    icon = R.drawable.queue_music,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
                )
            }

            else -> {
                val songs = page!!.songs
                val title = page!!.header.title
                item(key = "header") {
                    Column(Modifier.padding(12.dp)) {
                        Row {
                            AsyncImage(
                                // The filteredPlaylistCover doctrine: the cover is the FIRST
                                // content-filtered track's art — never sender-supplied imagery.
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(filteredPlaylistCover(songs) { it.thumbnail })
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
                                    text = title,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    fontSizeRange = FontSizeRange(16.sp, 22.sp),
                                )

                                Text(
                                    text = joinByBullet(
                                        // "shared by <name>" when the sender gave one, else the
                                        // generic label; then count + runtime like every playlist.
                                        page!!.header.sharedBy
                                            ?.let { stringResource(R.string.user_playlist_shared_by, it) }
                                            ?: stringResource(R.string.user_playlist_shared_label),
                                        pluralStringResource(R.plurals.n_song, songs.size, songs.size),
                                        zemerCuratedPlaylistRuntimeLabel(page!!.header.totalDurationSec),
                                    ),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Normal,
                                )
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        PlaylistPlayShuffleButtons(
                            onPlay = { playerConnection.playQueue(queueOf(songs)) },
                            onShuffle = { playerConnection.playQueue(queueOf(songs, shuffle = true)) },
                        )

                        Spacer(Modifier.height(8.dp))

                        OutlinedButton(
                            onClick = { showImportDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(painterResource(R.drawable.playlist_add), contentDescription = null)
                            Text(
                                stringResource(R.string.user_playlist_save_copy),
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                }

                itemsIndexed(items = songs, key = { _, song -> song.id }) { index, song ->
                    YouTubeListItem(
                        item = song,
                        isActive = mediaMetadata?.id == song.id,
                        isPlaying = isPlaying,
                        trailingContent = {
                            IconButton(onClick = { showSongMenu(song) }, onLongClick = {}) {
                                Icon(painterResource(R.drawable.more_vert), contentDescription = null)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = {
                                    if (song.id == mediaMetadata?.id) {
                                        playerConnection.playPause()
                                    } else {
                                        playerConnection.playQueue(queueOf(songs, startIndex = index))
                                    }
                                },
                                onLongClick = { showSongMenu(song) },
                            ),
                    )
                }
            }
        }
    }

    if (showImportDialog) {
        ImportPlaylistDialog(
            isVisible = true,
            playlistTitle = page?.header?.title.orEmpty(),
            onGetSong = {
                val songs = page?.songs.orEmpty()
                database.transaction { songs.forEach { insert(it.toMediaMetadata()) } }
                songs.map { it.id }
            },
            onDismiss = { showImportDialog = false },
        )
    }

    TopAppBar(
        title = { Text(page?.header?.title ?: "") },
        navigationIcon = {
            IconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain,
            ) {
                Icon(painterResource(R.drawable.arrow_back), contentDescription = null)
            }
        },
        scrollBehavior = scrollBehavior,
    )
}
