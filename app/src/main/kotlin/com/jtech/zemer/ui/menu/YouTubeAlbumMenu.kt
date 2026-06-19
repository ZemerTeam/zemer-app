package com.jtech.zemer.ui.menu

import android.annotation.SuppressLint
import android.content.Intent
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
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.jtech.zemer.LocalDatabase
import com.jtech.zemer.LocalDownloadUtil
import com.jtech.zemer.LocalPlayerConnection
import com.jtech.zemer.R
import com.jtech.zemer.db.entities.Song
import com.jtech.zemer.extensions.toMediaItem
import com.jtech.zemer.playback.DownloadMenuLogic
import com.jtech.zemer.playback.DownloadStateResolver
import com.jtech.zemer.playback.queues.YouTubeAlbumRadio
import com.jtech.zemer.ui.component.AlreadyInPlaylistDialog
import com.jtech.zemer.ui.component.ArtistChoice
import com.jtech.zemer.ui.component.Material3MenuGroup
import com.jtech.zemer.ui.component.Material3MenuItemData
import com.jtech.zemer.ui.component.NewAction
import com.jtech.zemer.ui.component.NewActionGrid
import com.jtech.zemer.ui.component.SelectArtistDialog
import com.jtech.zemer.ui.component.SongListItem
import com.jtech.zemer.ui.component.YouTubeListItem
import com.jtech.zemer.utils.reportException
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.AlbumItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("MutableCollectionMutableState")
@Composable
fun YouTubeAlbumMenu(
    albumItem: AlbumItem,
    navController: NavController,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val downloadUtil = LocalDownloadUtil.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val album by database.albumWithSongs(albumItem.id).collectAsState(initial = null)
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        database.album(albumItem.id).collect { album ->
            if (album == null) {
                YouTube
                    .album(albumItem.id)
                    .onSuccess { albumPage ->
                        database.transaction {
                            insert(albumPage)
                        }
                    }.onFailure {
                        reportException(it)
                    }
            }
        }
    }

    val mediaStoreDownloads by downloadUtil.getAllMediaStoreDownloads().collectAsState()

    var showChoosePlaylistDialog by rememberSaveable {
        mutableStateOf(false)
    }

    var showErrorPlaylistAddDialog by rememberSaveable {
        mutableStateOf(false)
    }

    var showReportDialog by remember { mutableStateOf(false) }

    val notAddedList by remember {
        mutableStateOf(mutableListOf<Song>())
    }

    AddToPlaylistDialog(
        isVisible = showChoosePlaylistDialog,
        onGetSong = { playlist ->
            coroutineScope.launch(Dispatchers.IO) {
                playlist.playlist.browseId?.let { playlistId ->
                    album?.album?.playlistId?.let { addPlaylistId ->
                        YouTube.addPlaylistToPlaylist(playlistId, addPlaylistId)
                    }
                }
            }
            album?.songs?.map { it.id }.orEmpty()
        },
        onDismiss = { showChoosePlaylistDialog = false }
    )

    if (showErrorPlaylistAddDialog) {
        AlreadyInPlaylistDialog(onDismiss = { showErrorPlaylistAddDialog = false }) {
            items(notAddedList) { song ->
                SongListItem(song = song)
            }
        }
    }

    var showSelectArtistDialog by rememberSaveable {
        mutableStateOf(false)
    }

    if (showSelectArtistDialog) {
        SelectArtistDialog(
            artists = album?.artists.orEmpty().distinctBy { it.id }
                .map { ArtistChoice(id = it.id, name = it.name, thumbnailUrl = it.thumbnailUrl) },
            onDismiss = { showSelectArtistDialog = false },
            onArtistClick = { artistId ->
                navController.navigate("artist/$artistId")
                onDismiss()
            },
        )
    }

    YouTubeListItem(
        item = albumItem,
        badges = {},
        trailingContent = {
            IconButton(
                onClick = {
                    database.query {
                        album?.album?.toggleLike()?.let(::update)
                    }
                },
            ) {
                Icon(
                    painter = painterResource(if (album?.album?.bookmarkedAt != null) R.drawable.favorite else R.drawable.favorite_border),
                    tint = if (album?.album?.bookmarkedAt != null) MaterialTheme.colorScheme.error else LocalContentColor.current,
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
                "artistId" to (albumItem.artists?.firstOrNull()?.id ?: ""),
                "artistName" to (albumItem.artists?.firstOrNull()?.name ?: ""),
                "albumId" to albumItem.id,
                "albumTitle" to albumItem.title,
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
                            album?.songs?.let { songs ->
                                if (songs.isNotEmpty()) {
                                    playerConnection.playQueue(YouTubeAlbumRadio(albumItem.playlistId, database))
                                }
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
                            album?.songs?.let { songs ->
                                if (songs.isNotEmpty()) {
                                    playerConnection.playQueue(YouTubeAlbumRadio(albumItem.playlistId, database))
                                }
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
                            val intent = Intent().apply {
                                action = Intent.ACTION_SEND
                                type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, albumItem.shareLink)
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
                                album
                                    ?.songs
                                    ?.map { it.toMediaItem() }
                                    ?.let(playerConnection::playNext)
                                onDismiss()
                            },
                        )
                    )
                    add(
                        Material3MenuItemData(
                            icon = { Icon(painterResource(R.drawable.queue_music), null, Modifier.size(24.dp)) },
                            title = { Text(stringResource(R.string.add_to_queue)) },
                            onClick = {
                                album
                                    ?.songs
                                    ?.map { it.toMediaItem() }
                                    ?.let(playerConnection::addToQueue)
                                onDismiss()
                            },
                        )
                    )
                    add(
                        Material3MenuItemData(
                            icon = { Icon(painterResource(R.drawable.playlist_add), null, Modifier.size(24.dp)) },
                            title = { Text(stringResource(R.string.add_to_playlist)) },
                            onClick = { showChoosePlaylistDialog = true },
                        )
                    )
                    add(
                        Material3MenuItemData(
                            icon = { Icon(painterResource(R.drawable.warning), null, Modifier.size(24.dp)) },
                            title = { Text(stringResource(R.string.report_artist)) },
                            onClick = { showReportDialog = true },
                        )
                    )
                    val songs = album?.songs.orEmpty()
                    val dlStatus = DownloadStateResolver.aggregateSongs(songs, mediaStoreDownloads)
                    val dlProgress = DownloadStateResolver.aggregateProgress(songs, mediaStoreDownloads)
                    downloadMenuItem(
                        kind = DownloadMenuLogic.collectionRow(dlStatus),
                        progress = dlProgress,
                        // Fetch-if-empty: the album loads async, so a first tap before it arrived used
                        // to be a no-op ("press once does nothing, twice works"). Resolve the songs at
                        // click time, fetching the album page if the DB doesn't have it yet.
                        onDownload = {
                            coroutineScope.launch(Dispatchers.IO) {
                                var toDownload = database.albumWithSongs(albumItem.id).first()?.songs.orEmpty()
                                if (toDownload.isEmpty()) {
                                    YouTube.album(albumItem.id)
                                        .onSuccess { page -> database.transaction { insert(page) } }
                                        .onFailure { reportException(it) }
                                    toDownload = database.albumWithSongs(albumItem.id).first()?.songs.orEmpty()
                                }
                                toDownload.forEach { downloadUtil.downloadToMediaStore(it) }
                            }
                        },
                        onCancel = { songs.forEach { downloadUtil.cancelMediaStoreDownload(it.id) } },
                        onRetry = { songs.forEach { downloadUtil.retryMediaStoreDownload(it.id) } },
                        onRemove = { coroutineScope.launch { songs.forEach { downloadUtil.removeDownload(it.id) } } },
                    )?.let { add(it) }
                    albumItem.artists?.let { artists ->
                        add(
                            Material3MenuItemData(
                                icon = { Icon(painterResource(R.drawable.artist), null, Modifier.size(24.dp)) },
                                title = { Text(stringResource(R.string.view_artist)) },
                                onClick = {
                                    if (artists.size == 1) {
                                        navController.navigate("artist/${artists[0].id}")
                                        onDismiss()
                                    } else {
                                        showSelectArtistDialog = true
                                    }
                                },
                            )
                        )
                    }
                },
            )
        }

    }
}
