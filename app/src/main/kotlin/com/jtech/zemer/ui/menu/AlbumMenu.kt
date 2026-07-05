@file:Suppress("VariableNeverRead")

package com.jtech.zemer.ui.menu

import android.annotation.SuppressLint
import android.content.Intent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.jtech.zemer.LocalDatabase
import com.jtech.zemer.LocalDownloadUtil
import com.jtech.zemer.LocalPlayerConnection
import com.jtech.zemer.R
import com.jtech.zemer.db.entities.Album
import com.jtech.zemer.db.entities.Song
import com.jtech.zemer.extensions.toMediaItem
import com.jtech.zemer.playback.DownloadMenuLogic
import com.jtech.zemer.playback.DownloadStateResolver
import com.jtech.zemer.playback.queues.ListQueue
import com.jtech.zemer.ui.component.AlbumListItem
import com.jtech.zemer.ui.component.AlreadyInPlaylistDialog
import com.jtech.zemer.ui.component.ArtistChoice
import com.jtech.zemer.ui.component.SelectArtistDialog
import com.jtech.zemer.ui.component.Material3MenuGroup
import com.jtech.zemer.ui.component.Material3MenuItemData
import com.jtech.zemer.ui.component.NewAction
import com.jtech.zemer.ui.component.NewActionGrid
import com.jtech.zemer.ui.component.SongListItem
import com.metrolist.innertube.YouTube
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.jtech.zemer.tracking.Tracker
import com.jtech.zemer.tracking.TrackingActionKind

@SuppressLint("MutableCollectionMutableState")
@Composable
fun AlbumMenu(
    originalAlbum: Album,
    navController: NavController,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val downloadUtil = LocalDownloadUtil.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val scope = rememberCoroutineScope()
    val libraryAlbum by database.album(originalAlbum.id).collectAsState(initial = originalAlbum)
    val album = libraryAlbum ?: originalAlbum
    var songs by remember {
        mutableStateOf(emptyList<Song>())
    }
    var showReportDialog by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        database.albumSongs(album.id).collect {
            songs = it
        }
    }

    val mediaStoreDownloads by downloadUtil.getAllMediaStoreDownloads().collectAsState()

    var refetchIconDegree by remember { mutableFloatStateOf(0f) }

    val rotationAnimation by animateFloatAsState(
        targetValue = refetchIconDegree,
        animationSpec = tween(durationMillis = 800),
        label = "",
    )

    var showChoosePlaylistDialog by rememberSaveable {
        mutableStateOf(false)
    }

    var showSelectArtistDialog by rememberSaveable {
        mutableStateOf(false)
    }

    var showErrorPlaylistAddDialog by rememberSaveable {
        mutableStateOf(false)
    }

    val notAddedList by remember {
        mutableStateOf(mutableListOf<Song>())
    }

    AddToPlaylistDialog(
        isVisible = showChoosePlaylistDialog,
        onGetSong = { playlist ->
            coroutineScope.launch(Dispatchers.IO) {
                playlist.playlist.browseId?.let { playlistId ->
                    album.album.playlistId?.let { addPlaylistId ->
                        YouTube.addPlaylistToPlaylist(playlistId, addPlaylistId)
                    }
                }
            }
            songs.map { it.id }
        },
        onDismiss = {
            showChoosePlaylistDialog = false
        },
    )

    if (showErrorPlaylistAddDialog) {
        AlreadyInPlaylistDialog(onDismiss = { showErrorPlaylistAddDialog = false }) {
            items(notAddedList) { song ->
                SongListItem(song = song)
            }
        }
    }

    if (showSelectArtistDialog) {
        SelectArtistDialog(
            artists = album.artists.distinctBy { it.id }.map { ArtistChoice(it.id, it.name, it.thumbnailUrl) },
            onDismiss = { showSelectArtistDialog = false },
            onArtistClick = { artistId ->
                navController.navigate("artist/$artistId")
                onDismiss()
            },
        )
    }

    AlbumListItem(
        album = album,
        showLikedIcon = false,
        badges = {},
        trailingContent = {
            IconButton(
                onClick = {
                    database.query {
                        update(album.album.toggleLike())
                    }
                },
            ) {
                Icon(
                    painter = painterResource(if (album.album.bookmarkedAt != null) R.drawable.favorite else R.drawable.favorite_border),
                    tint = if (album.album.bookmarkedAt != null) MaterialTheme.colorScheme.error else LocalContentColor.current,
                    contentDescription = null,
                )
            }
        },
    )

    HorizontalDivider()

    Spacer(modifier = Modifier.height(12.dp))

    if (showReportDialog) {
        ReportContentDialog(
            subject = mapOf(
                "artistId" to (album.artists.firstOrNull()?.id ?: ""),
                "artistName" to (album.artists.firstOrNull()?.name ?: ""),
                "albumId" to album.id,
                "albumTitle" to album.title,
            ),
            onDismiss = { showReportDialog = false },
        )
    }

    LazyColumn(
        contentPadding = PaddingValues(
            start = 0.dp,
            top = 0.dp,
            end = 0.dp,
            bottom = 8.dp + WindowInsets.systemBars.asPaddingValues().calculateBottomPadding(),
        ),
    ) {
        item {
            NewActionGrid(
                actions = listOf(
                    NewAction(
                        icon = {
                            Icon(
                                painter = painterResource(R.drawable.play),
                                contentDescription = null,
                                modifier = Modifier.size(28.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        text = stringResource(R.string.play),
                        onClick = {
                            onDismiss()
                            if (songs.isNotEmpty()) {
                                playerConnection.playQueue(
                                    ListQueue(
                                        title = album.album.title,
                                        items = songs.map(Song::toMediaItem)
                                    )
                                )
                            }
                        }
                    ),
                    NewAction(
                        icon = {
                            Icon(
                                painter = painterResource(R.drawable.shuffle),
                                contentDescription = null,
                                modifier = Modifier.size(28.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        text = stringResource(R.string.shuffle),
                        onClick = {
                            onDismiss()
                            if (songs.isNotEmpty()) {
                                album.album.playlistId?.let { playlistId ->
                                    playerConnection.service.getAutomix(playlistId)
                                }
                                playerConnection.playQueue(
                                    ListQueue(
                                        title = album.album.title,
                                        items = songs.shuffled().map(Song::toMediaItem)
                                    )
                                )
                            }
                        }
                    ),
                    NewAction(
                        icon = {
                            Icon(
                                painter = painterResource(R.drawable.share),
                                contentDescription = null,
                                modifier = Modifier.size(28.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        text = stringResource(R.string.share),
                        onClick = {
                            onDismiss()
                            Tracker.action(TrackingActionKind.SHARE, album.id)
                            val intent = Intent().apply {
                                action = Intent.ACTION_SEND
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, "https://music.zemer.io/playlist?list=${album.album.playlistId}")
                            }
                            context.startActivity(Intent.createChooser(intent, null))
                        }
                    )
                ),
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 16.dp)
            )
        }

        item {
            Material3MenuGroup(
                modifier = Modifier.padding(horizontal = 4.dp),
                items = buildList {
                    add(
                        Material3MenuItemData(
                            icon = { Icon(painterResource(R.drawable.playlist_play), null, Modifier.size(24.dp)) },
                            title = { Text(stringResource(R.string.play_next)) },
                            onClick = {
                                onDismiss()
                                playerConnection.playNext(songs.map { it.toMediaItem() })
                            },
                        )
                    )
                    add(
                        Material3MenuItemData(
                            icon = { Icon(painterResource(R.drawable.queue_music), null, Modifier.size(24.dp)) },
                            title = { Text(stringResource(R.string.add_to_queue)) },
                            onClick = {
                                onDismiss()
                                playerConnection.addToQueue(songs.map { it.toMediaItem() })
                            },
                        )
                    )
                    add(
                        Material3MenuItemData(
                            icon = { Icon(painterResource(R.drawable.warning), null, Modifier.size(24.dp)) },
                            title = { Text(stringResource(R.string.report_artist)) },
                            onClick = { showReportDialog = true },
                        )
                    )
                    add(
                        Material3MenuItemData(
                            icon = { Icon(painterResource(R.drawable.playlist_add), null, Modifier.size(24.dp)) },
                            title = { Text(stringResource(R.string.add_to_playlist)) },
                            onClick = { showChoosePlaylistDialog = true },
                        )
                    )
                    val dlStatus = DownloadStateResolver.aggregateSongs(songs, mediaStoreDownloads)
                    val dlProgress = DownloadStateResolver.aggregateProgress(songs, mediaStoreDownloads)
                    downloadMenuItem(
                        kind = DownloadMenuLogic.collectionRow(dlStatus),
                        progress = dlProgress,
                        onDownload = { songs.forEach { downloadUtil.downloadToMediaStore(it) } },
                        onCancel = { songs.forEach { downloadUtil.cancelMediaStoreDownload(it.id) } },
                        onRetry = { songs.forEach { downloadUtil.retryMediaStoreDownload(it.id) } },
                        onRemove = { coroutineScope.launch { songs.forEach { downloadUtil.removeDownload(it.id) } } },
                    )?.let { add(it) }
                    add(
                        Material3MenuItemData(
                            icon = { Icon(painterResource(R.drawable.artist), null, Modifier.size(24.dp)) },
                            title = { Text(stringResource(R.string.view_artist)) },
                            onClick = {
                                if (album.artists.size == 1) {
                                    navController.navigate("artist/${album.artists[0].id}")
                                    onDismiss()
                                } else {
                                    showSelectArtistDialog = true
                                }
                            },
                        )
                    )
                    add(
                        Material3MenuItemData(
                            icon = {
                                Icon(
                                    painterResource(R.drawable.sync),
                                    null,
                                    Modifier
                                        .size(24.dp)
                                        .graphicsLayer(rotationZ = rotationAnimation),
                                )
                            },
                            title = { Text(stringResource(R.string.refetch)) },
                            onClick = {
                                refetchIconDegree -= 360
                                scope.launch(Dispatchers.IO) {
                                    YouTube.album(album.id).onSuccess {
                                        database.transaction {
                                            update(album.album, it, album.artists)
                                        }
                                    }
                                }
                            },
                        )
                    )
                },
            )
        }

    }
}
