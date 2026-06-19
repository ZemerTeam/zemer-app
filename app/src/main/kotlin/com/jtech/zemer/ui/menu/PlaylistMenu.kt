@file:Suppress("VariableNeverRead")

package com.jtech.zemer.ui.menu

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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jtech.zemer.LocalDatabase
import com.jtech.zemer.LocalDownloadUtil
import com.jtech.zemer.LocalPlayerConnection
import com.jtech.zemer.R
import com.jtech.zemer.db.entities.Playlist
import com.jtech.zemer.db.entities.PlaylistSong
import com.jtech.zemer.db.entities.Song
import com.jtech.zemer.extensions.toMediaItem
import kotlinx.coroutines.launch
import com.jtech.zemer.playback.DownloadMenuLogic
import com.jtech.zemer.playback.DownloadStateResolver
import com.jtech.zemer.playback.MediaStoreDownloadManager
import com.jtech.zemer.playback.queues.ListQueue
import com.jtech.zemer.ui.component.Material3MenuGroup
import com.jtech.zemer.ui.component.Material3MenuItemData
import com.jtech.zemer.ui.component.NewAction
import com.jtech.zemer.ui.component.NewActionGrid
import com.jtech.zemer.ui.component.PlaylistListItem
import kotlinx.coroutines.CoroutineScope

@Composable
fun PlaylistMenu(
    playlist: Playlist,
    coroutineScope: CoroutineScope,
    onDismiss: () -> Unit,
    autoPlaylist: Boolean? = false,
    downloadPlaylist: Boolean? = false,
    songList: List<Song>? = emptyList(),
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val downloadUtil = LocalDownloadUtil.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val dbPlaylist by database.playlist(playlist.id).collectAsState(initial = playlist)
    var songs by remember { mutableStateOf(emptyList<Song>()) }
    var showReportDialog by remember { mutableStateOf(false) }
    val mediaStoreDownloads by downloadUtil.getAllMediaStoreDownloads().collectAsState()
    val downloadScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        if (autoPlaylist == false) {
            database.playlistSongs(playlist.id).collect {
                songs = it.map(PlaylistSong::song)
            }
        } else {
            songList?.let { songs = it }
        }
    }

    PlaylistListItem(
        playlist = playlist,
        trailingContent = {
            IconButton(
                onClick = {
                    database.query {
                        dbPlaylist?.playlist?.toggleLike()?.let { update(it) }
                    }
                }
            ) {
                Icon(
                    painter = painterResource(if (dbPlaylist?.playlist?.bookmarkedAt != null) R.drawable.favorite else R.drawable.favorite_border),
                    tint = if (dbPlaylist?.playlist?.bookmarkedAt != null) MaterialTheme.colorScheme.error else LocalContentColor.current,
                    contentDescription = null
                )
            }
        },
    )

    HorizontalDivider()
    Spacer(modifier = Modifier.height(12.dp))

    if (showReportDialog) {
        ReportContentDialog(
            subject = mapOf(
                "playlistId" to (dbPlaylist?.playlist?.browseId ?: ""),
                "playlistName" to dbPlaylist?.playlist?.name,
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
                            songs.takeIf { it.isNotEmpty() }?.let {
                                playerConnection.playQueue(
                                    ListQueue(
                                        title = playlist.playlist.name,
                                        items = it.map(Song::toMediaItem)
                                    )
                                )
                                onDismiss()
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
                            songs.takeIf { it.isNotEmpty() }?.let {
                                playerConnection.playQueue(
                                    ListQueue(
                                        title = playlist.playlist.name,
                                        items = it.shuffled().map(Song::toMediaItem)
                                    )
                                )
                                onDismiss()
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
                            val intent = Intent().apply {
                                action = Intent.ACTION_SEND
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, "https://music.zemer.io/playlist?list=${dbPlaylist?.playlist?.browseId}")
                            }
                            context.startActivity(Intent.createChooser(intent, null))
                            onDismiss()
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
                            icon = { Icon(painterResource(R.drawable.queue_music), null, Modifier.size(24.dp)) },
                            title = { Text(stringResource(R.string.add_to_queue)) },
                            onClick = {
                                songs.takeIf { it.isNotEmpty() }?.let {
                                    playerConnection.addToQueue(it.map(Song::toMediaItem))
                                }
                                onDismiss()
                            },
                        )
                    )
                    run {
                        val dlStatus = DownloadStateResolver.aggregateSongs(songs, mediaStoreDownloads)
                        val dlProgress = DownloadStateResolver.aggregateProgress(songs, mediaStoreDownloads)
                        downloadMenuItem(
                            kind = DownloadMenuLogic.collectionRow(dlStatus),
                            progress = dlProgress,
                            onDownload = { songs.forEach { downloadUtil.downloadToMediaStore(it) } },
                            onCancel = { songs.forEach { downloadUtil.cancelMediaStoreDownload(it.id) } },
                            onRetry = { songs.forEach { downloadUtil.retryMediaStoreDownload(it.id) } },
                            onRemove = { downloadScope.launch { songs.forEach { downloadUtil.removeDownload(it.id) } } },
                        )?.let { add(it) }
                    }
                    add(
                        Material3MenuItemData(
                            icon = { Icon(painterResource(R.drawable.warning), null, Modifier.size(24.dp)) },
                            title = { Text(stringResource(R.string.report_artist)) },
                            onClick = { showReportDialog = true },
                        )
                    )
                },
            )
        }
    }
}
