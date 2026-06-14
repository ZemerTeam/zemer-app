@file:Suppress("VariableNeverRead")

package com.jtech.zemer.ui.menu

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.Configuration
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.jtech.zemer.LocalDatabase
import com.jtech.zemer.LocalDownloadUtil
import com.jtech.zemer.LocalPlayerConnection
import com.jtech.zemer.R
import com.jtech.zemer.constants.ListItemHeight
import com.jtech.zemer.constants.ListThumbnailSize
import com.jtech.zemer.db.entities.Album
import com.jtech.zemer.db.entities.Song
import com.jtech.zemer.extensions.toMediaItem
import com.jtech.zemer.playback.queues.ListQueue
import com.jtech.zemer.ui.component.AlbumListItem
import com.jtech.zemer.ui.component.ListDialog
import com.jtech.zemer.ui.component.ListItem
import com.jtech.zemer.ui.component.Material3MenuGroup
import com.jtech.zemer.ui.component.Material3MenuItemData
import com.jtech.zemer.ui.component.NewAction
import com.jtech.zemer.ui.component.NewActionGrid
import com.jtech.zemer.ui.component.SongListItem
import com.metrolist.innertube.YouTube
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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

    var mediaStoreDownloadState by remember {
        mutableStateOf<AlbumMediaStoreDownloadStatus>(AlbumMediaStoreDownloadStatus.NotDownloaded)
    }

    LaunchedEffect(songs) {
        if (songs.isEmpty()) return@LaunchedEffect
        downloadUtil.getAllMediaStoreDownloads().collect { states ->
            val songStates = songs.mapNotNull { states[it.id] }
            mediaStoreDownloadState = when {
                songStates.isEmpty() -> AlbumMediaStoreDownloadStatus.NotDownloaded
                songStates.all { it.status == com.jtech.zemer.playback.MediaStoreDownloadManager.DownloadState.Status.COMPLETED } ->
                    AlbumMediaStoreDownloadStatus.Completed
                songStates.any {
                    it.status == com.jtech.zemer.playback.MediaStoreDownloadManager.DownloadState.Status.DOWNLOADING ||
                    it.status == com.jtech.zemer.playback.MediaStoreDownloadManager.DownloadState.Status.QUEUED
                } -> {
                    val totalProgress = songStates.sumOf { it.progress.toDouble() } / songs.size
                    AlbumMediaStoreDownloadStatus.Downloading(totalProgress.toFloat())
                }
                songStates.any { it.status == com.jtech.zemer.playback.MediaStoreDownloadManager.DownloadState.Status.FAILED } ->
                    AlbumMediaStoreDownloadStatus.Failed
                else -> AlbumMediaStoreDownloadStatus.NotDownloaded
            }
        }
    }

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
        ListDialog(
            onDismiss = {
                showErrorPlaylistAddDialog = false
                onDismiss()
            },
        ) {
            item {
                ListItem(
                    title = stringResource(R.string.already_in_playlist),
                    thumbnailContent = {
                        Image(
                            painter = painterResource(R.drawable.close),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onBackground),
                            modifier = Modifier.size(ListThumbnailSize),
                        )
                    },
                    modifier =
                    Modifier
                        .clickable { showErrorPlaylistAddDialog = false },
                )
            }

            items(notAddedList) { song ->
                SongListItem(song = song)
            }
        }
    }

    if (showSelectArtistDialog) {
        ListDialog(
            onDismiss = { showSelectArtistDialog = false },
        ) {
            items(
                items = album.artists.distinctBy { it.id },
                key = { it.id },
            ) { artist ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                    Modifier
                        .height(ListItemHeight)
                        .clickable {
                            navController.navigate("artist/${artist.id}")
                            showSelectArtistDialog = false
                            onDismiss()
                        }
                        .padding(horizontal = 12.dp),
                ) {
                    Box(
                        modifier = Modifier.padding(8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        AsyncImage(
                            model = artist.thumbnailUrl,
                            contentDescription = null,
                            modifier =
                            Modifier
                                .size(ListThumbnailSize)
                                .clip(CircleShape),
                        )
                    }
                    Text(
                        text = artist.name,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier =
                        Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                    )
                }
            }
        }
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

    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT

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
        userScrollEnabled = !isPortrait,
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
                    add(
                        when (mediaStoreDownloadState) {
                            is AlbumMediaStoreDownloadStatus.Completed ->
                                Material3MenuItemData(
                                    icon = { Icon(painterResource(R.drawable.download), null, Modifier.size(24.dp)) },
                                    title = {
                                        Text(
                                            text = stringResource(R.string.downloaded_to_device),
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    },
                                    onClick = {
                                        // TODO: Option to remove from MediaStore
                                        onDismiss()
                                    },
                                )
                            is AlbumMediaStoreDownloadStatus.Downloading -> {
                                val progress = (mediaStoreDownloadState as AlbumMediaStoreDownloadStatus.Downloading).progress
                                Material3MenuItemData(
                                    icon = {
                                        CircularProgressIndicator(
                                            progress = { progress },
                                            modifier = Modifier.size(24.dp),
                                            strokeWidth = 2.dp
                                        )
                                    },
                                    title = { Text(text = stringResource(R.string.downloading_to_device)) },
                                    description = { Text(text = "${(progress * 100).toInt()}%") },
                                    onClick = {
                                        songs.forEach { song ->
                                            downloadUtil.cancelMediaStoreDownload(song.id)
                                        }
                                        onDismiss()
                                    },
                                )
                            }
                            is AlbumMediaStoreDownloadStatus.Failed ->
                                Material3MenuItemData(
                                    icon = { Icon(painterResource(R.drawable.info), null, Modifier.size(24.dp)) },
                                    title = {
                                        Text(
                                            text = stringResource(R.string.download_failed),
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    },
                                    description = { Text(text = stringResource(R.string.retry_download)) },
                                    onClick = {
                                        songs.forEach { song ->
                                            downloadUtil.retryMediaStoreDownload(song.id)
                                        }
                                        onDismiss()
                                    },
                                )
                            AlbumMediaStoreDownloadStatus.NotDownloaded ->
                                Material3MenuItemData(
                                    icon = { Icon(painterResource(R.drawable.download), null, Modifier.size(24.dp)) },
                                    title = { Text(text = stringResource(R.string.download_to_device)) },
                                    onClick = {
                                        songs.forEach { song ->
                                            downloadUtil.downloadToMediaStore(song)
                                        }
                                        onDismiss()
                                    },
                                )
                        }
                    )
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

private sealed class AlbumMediaStoreDownloadStatus {
    object NotDownloaded : AlbumMediaStoreDownloadStatus()
    object Completed : AlbumMediaStoreDownloadStatus()
    data class Downloading(val progress: Float) : AlbumMediaStoreDownloadStatus()
    object Failed : AlbumMediaStoreDownloadStatus()
}
