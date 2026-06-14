package com.jtech.zemer.ui.menu

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.Configuration
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.exoplayer.offline.Download
import coil3.compose.AsyncImage
import com.jtech.zemer.LocalDatabase
import com.jtech.zemer.LocalDownloadUtil
import com.jtech.zemer.LocalPlayerConnection
import com.jtech.zemer.R
import com.jtech.zemer.constants.ListThumbnailSize
import com.jtech.zemer.constants.ThumbnailCornerRadius
import com.jtech.zemer.db.entities.PlaylistEntity
import com.jtech.zemer.db.entities.PlaylistSongMap
import com.jtech.zemer.extensions.toMediaItem
import com.jtech.zemer.models.MediaMetadata
import com.jtech.zemer.models.toMediaMetadata
import com.jtech.zemer.playback.queues.YouTubeQueue
import com.jtech.zemer.ui.component.DefaultDialog
import com.jtech.zemer.ui.component.ListDialog
import com.jtech.zemer.ui.component.Material3MenuGroup
import com.jtech.zemer.ui.component.Material3MenuItemData
import com.jtech.zemer.ui.component.NewAction
import com.jtech.zemer.ui.component.NewActionGrid
import com.jtech.zemer.ui.component.YouTubeListItem
import com.jtech.zemer.ui.utils.resize
import com.jtech.zemer.utils.joinByBullet
import com.jtech.zemer.utils.makeTimeString
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.utils.completed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("MutableCollectionMutableState")
@Composable
fun YouTubePlaylistMenu(
    playlist: PlaylistItem,
    songs: List<SongItem> = emptyList(),
    coroutineScope: CoroutineScope,
    onDismiss: () -> Unit,
    selectAction: () -> Unit = {},
    canSelect: Boolean = false,
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val downloadUtil = LocalDownloadUtil.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val dbPlaylist by database.playlistByBrowseId(playlist.id).collectAsState(initial = null)

    var showChoosePlaylistDialog by rememberSaveable { mutableStateOf(false) }
    var showImportPlaylistDialog by rememberSaveable { mutableStateOf(false) }
    var showErrorPlaylistAddDialog by rememberSaveable { mutableStateOf(false) }

    val notAddedList by remember {
        mutableStateOf(mutableListOf<MediaMetadata>())
    }

    AddToPlaylistDialog(
        isVisible = showChoosePlaylistDialog,
        onGetSong = { targetPlaylist ->
            val allSongs = songs
                .ifEmpty {
                    YouTube.playlist(targetPlaylist.id).completed().getOrNull()?.songs.orEmpty()
                }.map {
                    it.toMediaMetadata()
                }
            database.transaction {
                allSongs.forEach(::insert)
            }
            coroutineScope.launch(Dispatchers.IO) {
                targetPlaylist.playlist.browseId?.let { playlistId ->
                    YouTube.addPlaylistToPlaylist(playlistId, targetPlaylist.id)
                }
            }
            allSongs.map { it.id }
        },
        onDismiss = { showChoosePlaylistDialog = false },
    )

    YouTubeListItem(
        item = playlist,
        trailingContent = {
            if (playlist.id != "LM" && !playlist.isEditable) {
                IconButton(
                    onClick = {
                        if (dbPlaylist?.playlist == null) {
                            database.transaction {
                                val playlistEntity = PlaylistEntity(
                                    name = playlist.title,
                                    browseId = playlist.id,
                                    thumbnailUrl = playlist.thumbnail,
                                    isEditable = false,
                                    remoteSongCount = playlist.songCountText?.let {
                                        Regex("""\d+""").find(it)?.value?.toIntOrNull()
                                    },
                                    playEndpointParams = playlist.playEndpoint?.params,
                                    shuffleEndpointParams = playlist.shuffleEndpoint?.params,
                                    radioEndpointParams = playlist.radioEndpoint?.params
                                ).toggleLike()
                                insert(playlistEntity)
                                coroutineScope.launch(Dispatchers.IO) {
                                    songs.ifEmpty {
                                        YouTube.playlist(playlist.id).completed()
                                            .getOrNull()?.songs.orEmpty()
                                    }.map { it.toMediaMetadata() }
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
                        painter = painterResource(if (dbPlaylist?.playlist?.bookmarkedAt != null) R.drawable.favorite else R.drawable.favorite_border),
                        tint = if (dbPlaylist?.playlist?.bookmarkedAt != null) MaterialTheme.colorScheme.error else LocalContentColor.current,
                        contentDescription = null
                    )
                }
            }
        }
    )
    HorizontalDivider()

    var downloadState by remember {
        mutableIntStateOf(Download.STATE_STOPPED)
    }
    LaunchedEffect(songs) {
        if (songs.isEmpty()) return@LaunchedEffect
        downloadUtil.downloads.collect { downloads ->
            downloadState =
                if (songs.all { downloads[it.id]?.state == Download.STATE_COMPLETED })
                    Download.STATE_COMPLETED
                else if (songs.all {
                        downloads[it.id]?.state == Download.STATE_QUEUED
                                || downloads[it.id]?.state == Download.STATE_DOWNLOADING
                                || downloads[it.id]?.state == Download.STATE_COMPLETED
                    })
                    Download.STATE_DOWNLOADING
                else
                    Download.STATE_STOPPED
        }
    }
    var showRemoveDownloadDialog by remember {
        mutableStateOf(false)
    }
    if (showRemoveDownloadDialog) {
        DefaultDialog(
            onDismiss = { showRemoveDownloadDialog = false },
            content = {
                Text(
                    text = stringResource(
                        R.string.remove_download_playlist_confirm,
                        playlist.title
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 18.dp)
                )
            },
            buttons = {
                TextButton(
                    onClick = { showRemoveDownloadDialog = false }
                ) {
                    Text(text = stringResource(android.R.string.cancel))
                }
                TextButton(
                    onClick = {
                        showRemoveDownloadDialog = false
                        songs.forEach { song ->
                            coroutineScope.launch {
                                downloadUtil.removeDownload(song.id)
                            }
                        }
                    }
                ) {
                    Text(text = stringResource(android.R.string.ok))
                }
            }
        )
    }

    ImportPlaylistDialog(
        isVisible = showImportPlaylistDialog,
        onGetSong = {
            val allSongs = songs
                .ifEmpty {
                    YouTube.playlist(playlist.id).completed().getOrNull()?.songs.orEmpty()
                }.map {
                    it.toMediaMetadata()
                }
            database.transaction {
                allSongs.forEach(::insert)
            }
            allSongs.map { it.id }
        },
        playlistTitle = playlist.title,
        onDismiss = { showImportPlaylistDialog = false }
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
                    headlineContent = { Text(text = stringResource(R.string.already_in_playlist)) },
                    leadingContent = {
                        Image(
                            painter = painterResource(R.drawable.close),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onBackground),
                            modifier = Modifier.size(ListThumbnailSize),
                        )
                    },
                    modifier = Modifier.clickable { showErrorPlaylistAddDialog = false },
                )
            }

            items(notAddedList) { song ->
                ListItem(
                    headlineContent = { Text(text = song.title) },
                    leadingContent = {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(ListThumbnailSize),
                        ) {
                            AsyncImage(
                                model = song.thumbnailUrl,
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(ThumbnailCornerRadius)),
                            )
                        }
                    },
                    supportingContent = {
                        Text(
                            text = joinByBullet(
                                song.artists.joinToString { it.name },
                                makeTimeString(song.duration * 1000L),
                            )
                        )
                    },
                )
            }
        }
    }

    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT

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
                actions = buildList {
                    playlist.playEndpoint?.let { playEndpoint ->
                        add(
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
                                    playerConnection.playQueue(YouTubeQueue(playEndpoint, preloadItem = null, database))
                                    onDismiss()
                                }
                            )
                        )
                    }
                    playlist.shuffleEndpoint?.let { shuffleEndpoint ->
                        add(
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
                                    playerConnection.playQueue(YouTubeQueue(shuffleEndpoint, preloadItem = null, database))
                                    onDismiss()
                                }
                            )
                        )
                    }
                    playlist.radioEndpoint?.let { radioEndpoint ->
                        add(
                            NewAction(
                                icon = {
                                    Icon(
                                        painter = painterResource(R.drawable.radio),
                                        contentDescription = null,
                                        modifier = Modifier.size(28.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                },
                                text = stringResource(R.string.start_radio),
                                onClick = {
                                    playerConnection.playQueue(YouTubeQueue(radioEndpoint, preloadItem = null, database))
                                    onDismiss()
                                }
                            )
                        )
                    }
                },
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
                                coroutineScope.launch {
                                    songs
                                        .ifEmpty {
                                            withContext(Dispatchers.IO) {
                                                YouTube
                                                    .playlist(playlist.id)
                                                    .completed()
                                                    .getOrNull()
                                                    ?.songs
                                                    .orEmpty()
                                            }
                                        }.let { songs ->
                                            playerConnection.playNext(songs.map { it.copy(thumbnail = it.thumbnail.resize(544,544)).toMediaItem() })
                                        }
                                }
                                onDismiss()
                            },
                        )
                    )
                    add(
                        Material3MenuItemData(
                            icon = { Icon(painterResource(R.drawable.queue_music), null, Modifier.size(24.dp)) },
                            title = { Text(stringResource(R.string.add_to_queue)) },
                            onClick = {
                                coroutineScope.launch {
                                    songs
                                        .ifEmpty {
                                            withContext(Dispatchers.IO) {
                                                YouTube
                                                    .playlist(playlist.id)
                                                    .completed()
                                                    .getOrNull()
                                                    ?.songs
                                                    .orEmpty()
                                            }
                                        }.let { songs ->
                                            playerConnection.addToQueue(songs.map { it.toMediaItem() })
                                        }
                                }
                                onDismiss()
                            },
                        )
                    )
                    add(
                        Material3MenuItemData(
                            icon = { Icon(painterResource(R.drawable.playlist_add), null, Modifier.size(24.dp)) },
                            title = { Text(stringResource(R.string.add_to_playlist)) },
                            onClick = {
                                showChoosePlaylistDialog = true
                            },
                        )
                    )
                    if (songs.isNotEmpty()) {
                        when (downloadState) {
                            Download.STATE_COMPLETED -> add(
                                Material3MenuItemData(
                                    icon = { Icon(painterResource(R.drawable.offline), null, Modifier.size(24.dp)) },
                                    title = {
                                        Text(
                                            text = stringResource(R.string.remove_download),
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    },
                                    onClick = {
                                        showRemoveDownloadDialog = true
                                    },
                                )
                            )
                            Download.STATE_QUEUED, Download.STATE_DOWNLOADING -> add(
                                Material3MenuItemData(
                                    icon = {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(24.dp),
                                            strokeWidth = 2.dp
                                        )
                                    },
                                    title = { Text(stringResource(R.string.downloading)) },
                                    onClick = {
                                        showRemoveDownloadDialog = true
                                    },
                                )
                            )
                            else -> add(
                                Material3MenuItemData(
                                    icon = { Icon(painterResource(R.drawable.download), null, Modifier.size(24.dp)) },
                                    title = { Text(stringResource(R.string.action_download)) },
                                    onClick = {
                                        coroutineScope.launch(Dispatchers.IO) {
                                            songs.forEach { song ->
                                                database.transaction {
                                                    insert(song.toMediaMetadata())
                                                }
                                                val dbSong = database.song(song.id).first()
                                                dbSong?.let {
                                                    downloadUtil.downloadToMediaStore(it)
                                                }
                                            }
                                        }
                                    },
                                )
                            )
                        }
                    }
                    add(
                        Material3MenuItemData(
                            icon = { Icon(painterResource(R.drawable.share), null, Modifier.size(24.dp)) },
                            title = { Text(stringResource(R.string.share)) },
                            onClick = {
                                val intent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, playlist.shareLink)
                                }
                                context.startActivity(Intent.createChooser(intent, null))
                                onDismiss()
                            },
                        )
                    )
                    if (canSelect) add(
                        Material3MenuItemData(
                            icon = { Icon(painterResource(R.drawable.select_all), null, Modifier.size(24.dp)) },
                            title = { Text(stringResource(R.string.select)) },
                            onClick = {
                                onDismiss()
                                selectAction()
                            },
                        )
                    )
                },
            )
        }
    }
}
