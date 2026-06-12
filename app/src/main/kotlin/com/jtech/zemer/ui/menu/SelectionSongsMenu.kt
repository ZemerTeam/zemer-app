package com.jtech.zemer.ui.menu

import android.annotation.SuppressLint
import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.offline.Download
import com.jtech.zemer.LocalDatabase
import com.jtech.zemer.LocalDownloadUtil
import com.jtech.zemer.LocalPlayerConnection
import com.jtech.zemer.LocalSyncUtils
import com.jtech.zemer.R
import com.jtech.zemer.db.entities.PlaylistSongMap
import com.jtech.zemer.db.entities.Song
import com.jtech.zemer.extensions.toMediaItem
import com.jtech.zemer.models.MediaMetadata
import com.jtech.zemer.models.toMediaMetadata
import com.jtech.zemer.playback.queues.ListQueue
import com.jtech.zemer.ui.component.DefaultDialog
import com.jtech.zemer.ui.component.NewAction
import com.jtech.zemer.ui.component.NewActionGrid
import com.metrolist.innertube.YouTube
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime

@SuppressLint("MutableCollectionMutableState")
@Composable
fun SelectionSongMenu(
    songSelection: List<Song>,
    onDismiss: () -> Unit,
    clearAction: () -> Unit,
    songPosition: List<PlaylistSongMap>? = emptyList(),
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val downloadUtil = LocalDownloadUtil.current
    val coroutineScope = rememberCoroutineScope()
    val playerConnection = LocalPlayerConnection.current ?: return
    val selectionQueueTitle = stringResource(R.string.queue_selection)
    val syncUtils = LocalSyncUtils.current
    var showReportDialog by remember { mutableStateOf(false) }
    var targetSong by remember { mutableStateOf<Song?>(null) }

    
    val allInLibrary by remember {
        mutableStateOf(
            songSelection.all {
                it.song.inLibrary != null
            },
        )
    }

    val allLiked by remember(songSelection) {
        mutableStateOf(
            songSelection.isNotEmpty() && songSelection.all {
                it.song.liked
            },
        )
    }

    var downloadState by remember {
        mutableIntStateOf(Download.STATE_STOPPED)
    }

    LaunchedEffect(songSelection) {
        if (songSelection.isEmpty()) return@LaunchedEffect
        downloadUtil.downloads.collect { downloads ->
            downloadState =
                if (songSelection.all { downloads[it.id]?.state == Download.STATE_COMPLETED }) {
                    Download.STATE_COMPLETED
                } else if (songSelection.all {
                        downloads[it.id]?.state == Download.STATE_QUEUED ||
                                downloads[it.id]?.state == Download.STATE_DOWNLOADING ||
                                downloads[it.id]?.state == Download.STATE_COMPLETED
                    }
                ) {
                    Download.STATE_DOWNLOADING
                } else {
                    Download.STATE_STOPPED
                }
        }
    }

    var showChoosePlaylistDialog by rememberSaveable {
        mutableStateOf(false)
    }

    AddToPlaylistDialog(
        isVisible = showChoosePlaylistDialog,
        onGetSong = { playlist ->
            coroutineScope.launch(Dispatchers.IO) {
                songSelection.forEach { song ->
                    playlist.playlist.browseId?.let { browseId ->
                        YouTube.addToPlaylist(browseId, song.id)
                    }
                }
            }
            songSelection.map { it.id }
        },
        onDismiss = {
            showChoosePlaylistDialog = false
        },
    )

    var showRemoveDownloadDialog by remember {
        mutableStateOf(false)
    }

    if (showRemoveDownloadDialog) {
        DefaultDialog(
            onDismiss = { showRemoveDownloadDialog = false },
            content = {
                Text(
                    text = stringResource(R.string.remove_download_playlist_confirm, "selection"),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 18.dp),
                )
            },
            buttons = {
                TextButton(
                    onClick = {
                        showRemoveDownloadDialog = false
                    },
                ) {
                    Text(text = stringResource(android.R.string.cancel))
                }

                TextButton(
                    onClick = {
                        showRemoveDownloadDialog = false
                        songSelection.forEach { song ->
                            coroutineScope.launch {
                                downloadUtil.removeDownload(song.song.id)
                            }
                        }
                    },
                ) {
                    Text(text = stringResource(android.R.string.ok))
                }
            },
        )
    }

    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT


    val reportTarget = targetSong
    if (showReportDialog && reportTarget != null) {
        ReportContentDialog(
            subject = mapOf(
                "artistId" to (reportTarget.artists.firstOrNull()?.id ?: ""),
                "artistName" to (reportTarget.artists.firstOrNull()?.name ?: ""),
                "songId" to reportTarget.id,
                "songTitle" to reportTarget.song.title,
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
                            playerConnection.playQueue(
                                ListQueue(
                                    title = selectionQueueTitle,
                                    items = songSelection.map { it.toMediaItem() },
                                ),
                            )
                            clearAction()
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
                            playerConnection.playQueue(
                                ListQueue(
                                    title = selectionQueueTitle,
                                    items = songSelection.shuffled().map { it.toMediaItem() },
                                ),
                            )
                            clearAction()
                        }
                    ),
                    NewAction(
                        icon = {
                            Icon(
                                painter = painterResource(R.drawable.playlist_add),
                                contentDescription = null,
                                modifier = Modifier.size(28.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        text = stringResource(R.string.add_to_playlist),
                        onClick = {
                            showChoosePlaylistDialog = true
                        }
                    ),
                    NewAction(
                        icon = {
                            Icon(
                                painter = painterResource(R.drawable.warning),
                                contentDescription = null,
                                modifier = Modifier.size(28.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        text = stringResource(R.string.report_artist),
                        onClick = {
                            // Use first song in selection for the report dialog
                            if (songSelection.isNotEmpty()) {
                                run { targetSong = songSelection.firstOrNull(); showReportDialog = true }
                            }
                        }
                    )
                ),
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 16.dp)
            )
        }

        item {
            ListItem(
                headlineContent = { Text(text = stringResource(R.string.play)) },
                leadingContent = {
                    Icon(
                        painter = painterResource(R.drawable.play),
                        contentDescription = null,
                    )
                },
                modifier = Modifier.clickable {
                    onDismiss()
                    playerConnection.playQueue(
                        ListQueue(
                            title = selectionQueueTitle,
                            items = songSelection.map { it.toMediaItem() },
                        ),
                    )
                    clearAction()
                }
            )
        }
        item {
            ListItem(
                headlineContent = { Text(text = stringResource(R.string.shuffle)) },
                leadingContent = {
                    Icon(
                        painter = painterResource(R.drawable.shuffle),
                        contentDescription = null,
                    )
                },
                modifier = Modifier.clickable {
                    onDismiss()
                    playerConnection.playQueue(
                        ListQueue(
                            title = selectionQueueTitle,
                            items = songSelection.shuffled().map { it.toMediaItem() },
                        ),
                    )
                    clearAction()
                }
            )
        }
        item {
            ListItem(
                headlineContent = { Text(text = stringResource(R.string.add_to_queue)) },
                leadingContent = {
                    Icon(
                        painter = painterResource(R.drawable.queue_music),
                        contentDescription = null,
                    )
                },
                modifier = Modifier.clickable {
                    onDismiss()
                    playerConnection.addToQueue(songSelection.map { it.toMediaItem() })
                    clearAction()
                }
            )
        }
        item {
            ListItem(
                headlineContent = { Text(text = stringResource(R.string.add_to_playlist)) },
                leadingContent = {
                    Icon(
                        painter = painterResource(R.drawable.playlist_add),
                        contentDescription = null,
                    )
                },
                modifier = Modifier.clickable {
                    showChoosePlaylistDialog = true
                }
            )
        }
        item {
            ListItem(
                headlineContent = {
                    Text(
                        text = stringResource(
                            if (allInLibrary) R.string.remove_from_library else R.string.add_to_library
                        )
                    )
                },
                leadingContent = {
                    Icon(
                        painter = painterResource(
                            if (allInLibrary) R.drawable.library_add_check else R.drawable.library_add
                        ),
                        contentDescription = null,
                    )
                },
                modifier = Modifier.clickable {
                    if (allInLibrary) {
                        database.query {
                            songSelection.forEach { song ->
                                inLibrary(song.id, null)
                            }
                        }
                        coroutineScope.launch {
                            val tokens = songSelection.mapNotNull { it.song.libraryRemoveToken }
                            tokens.chunked(20).forEach {
                                YouTube.feedback(it)
                            }
                        }
                    } else {
                        database.transaction {
                            songSelection.forEach { song ->
                                insert(song.toMediaMetadata())
                                inLibrary(song.id, LocalDateTime.now())
                            }
                        }
                        coroutineScope.launch {
                            val tokens = songSelection.filter {it.song.inLibrary == null}.mapNotNull { it.song.libraryAddToken }
                            tokens.chunked(20).forEach {
                                YouTube.feedback(it)
                            }
                        }
                    }
                }
            )
        }
        item {
            when (downloadState) {
                Download.STATE_COMPLETED -> {
                    ListItem(
                        headlineContent = {
                            Text(
                                text = stringResource(R.string.remove_download),
                                color = MaterialTheme.colorScheme.error
                            )
                        },
                        leadingContent = {
                            Icon(
                                painter = painterResource(R.drawable.offline),
                                contentDescription = null,
                            )
                        },
                        modifier = Modifier.clickable {
                            showRemoveDownloadDialog = true
                        }
                    )
                }
                Download.STATE_QUEUED, Download.STATE_DOWNLOADING -> {
                    ListItem(
                        headlineContent = { Text(text = stringResource(R.string.downloading)) },
                        leadingContent = {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        },
                        modifier = Modifier.clickable {
                            showRemoveDownloadDialog = true
                        }
                    )
                }
                else -> {
                    ListItem(
                        headlineContent = { Text(text = stringResource(R.string.action_download)) },
                        leadingContent = {
                            Icon(
                                painter = painterResource(R.drawable.download),
                                contentDescription = null,
                            )
                        },
                        modifier = Modifier.clickable {
                            songSelection.forEach { song ->
                                downloadUtil.downloadToMediaStore(song)
                            }
                        }
                    )
                }
            }
        }
        item {
            ListItem(
                headlineContent = {
                    Text(
                        text = stringResource(
                            if (allLiked) R.string.dislike_all else R.string.like_all
                        )
                    )
                },
                leadingContent = {
                    Icon(
                        painter = painterResource(
                            if (allLiked) R.drawable.favorite else R.drawable.favorite_border
                        ),
                        contentDescription = null,
                    )
                },
                modifier = Modifier.clickable {
                    val allLiked = songSelection.all { it.song.liked }
                    onDismiss()
                    database.query {
                        songSelection.forEach { song ->
                            if ((!allLiked && !song.song.liked) || allLiked) {
                                val s = song.song.toggleLike()
                                update(s)
                                syncUtils.likeSong(s)
                            }
                        }
                    }
                }
            )
        }
        val isNotEmpty = false
        if (isNotEmpty) {
            item {
                ListItem(
                    headlineContent = { Text(text = stringResource(R.string.delete)) },
                    leadingContent = {
                        Icon(
                            painter = painterResource(R.drawable.delete),
                            contentDescription = null,
                        )
                    },
                    modifier = Modifier.clickable {
                        onDismiss()
                        var i = 0
                        database.query {
                            songPosition?.forEach { cur ->
                                move(cur.playlistId, cur.position - i, Int.MAX_VALUE)
                                delete(cur.copy(position = Int.MAX_VALUE))
                                i++
                            }
                        }
                        clearAction()
                    }
                )
            }
        }
    }
}

@SuppressLint("MutableCollectionMutableState")
@Composable
fun SelectionMediaMetadataMenu(
    songSelection: List<MediaMetadata>,
    currentItems: List<Timeline.Window>,
    onDismiss: () -> Unit,
    clearAction: () -> Unit,
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val downloadUtil = LocalDownloadUtil.current
    val coroutineScope = rememberCoroutineScope()
    val playerConnection = LocalPlayerConnection.current ?: return
    val selectionQueueTitle = stringResource(R.string.queue_selection)

    val allLiked by remember(songSelection) {
        mutableStateOf(songSelection.isNotEmpty() && songSelection.all { it.liked })
    }

    var showChoosePlaylistDialog by rememberSaveable {
        mutableStateOf(false)
    }

    AddToPlaylistDialog(
        isVisible = showChoosePlaylistDialog,
        onGetSong = { _ ->
            withContext(Dispatchers.IO) {
                songSelection.forEach {
                    database.insert(it)
                }
            }
            songSelection.map { it.id }
        },
        onDismiss = { showChoosePlaylistDialog = false }
    )

    var downloadState by remember {
        mutableIntStateOf(Download.STATE_STOPPED)
    }

    LaunchedEffect(songSelection) {
        if (songSelection.isEmpty()) return@LaunchedEffect
        downloadUtil.downloads.collect { downloads ->
            downloadState =
                if (songSelection.all { downloads[it.id]?.state == Download.STATE_COMPLETED }) {
                    Download.STATE_COMPLETED
                } else if (songSelection.all {
                        downloads[it.id]?.state == Download.STATE_QUEUED ||
                                downloads[it.id]?.state == Download.STATE_DOWNLOADING ||
                                downloads[it.id]?.state == Download.STATE_COMPLETED
                    }
                ) {
                    Download.STATE_DOWNLOADING
                } else {
                    Download.STATE_STOPPED
                }
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
                    text = stringResource(R.string.remove_download_playlist_confirm, "selection"),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 18.dp),
                )
            },
            buttons = {
                TextButton(
                    onClick = {
                        showRemoveDownloadDialog = false
                    },
                ) {
                    Text(text = stringResource(android.R.string.cancel))
                }

                TextButton(
                    onClick = {
                        showRemoveDownloadDialog = false
                        songSelection.forEach { song ->
                            coroutineScope.launch {
                                downloadUtil.removeDownload(song.id)
                            }
                        }
                    },
                ) {
                    Text(text = stringResource(android.R.string.ok))
                }
            },
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
        if (currentItems.isNotEmpty()) {
            item {
                ListItem(
                    headlineContent = { Text(text = stringResource(R.string.delete)) },
                    leadingContent = {
                        Icon(
                            painter = painterResource(R.drawable.delete),
                            contentDescription = null,
                        )
                    },
                    modifier = Modifier.clickable {
                        onDismiss()
                        var i = 0
                        currentItems.forEach { cur ->
                            if (playerConnection.player.availableCommands.contains(Player.COMMAND_CHANGE_MEDIA_ITEMS)) {
                                playerConnection.player.removeMediaItem(cur.firstPeriodIndex - i++)
                            }
                        }
                        clearAction()
                    }
                )
            }
        }
        item {
            ListItem(
                headlineContent = { Text(text = stringResource(R.string.play)) },
                leadingContent = {
                    Icon(
                        painter = painterResource(R.drawable.play),
                        contentDescription = null,
                    )
                },
                modifier = Modifier.clickable {
                    onDismiss()
                    playerConnection.playQueue(
                        ListQueue(
                            title = selectionQueueTitle,
                            items = songSelection.map { it.toMediaItem() },
                        ),
                    )
                    clearAction()
                }
            )
        }
        item {
            ListItem(
                headlineContent = { Text(text = stringResource(R.string.shuffle)) },
                leadingContent = {
                    Icon(
                        painter = painterResource(R.drawable.shuffle),
                        contentDescription = null,
                    )
                },
                modifier = Modifier.clickable {
                    onDismiss()
                    playerConnection.playQueue(
                        ListQueue(
                            title = selectionQueueTitle,
                            items = songSelection.shuffled().map { it.toMediaItem() },
                        ),
                    )
                    clearAction()
                }
            )
        }
        item {
            ListItem(
                headlineContent = { Text(text = stringResource(R.string.add_to_queue)) },
                leadingContent = {
                    Icon(
                        painter = painterResource(R.drawable.queue_music),
                        contentDescription = null,
                    )
                },
                modifier = Modifier.clickable {
                    onDismiss()
                    playerConnection.addToQueue(songSelection.map { it.toMediaItem() })
                    clearAction()
                }
            )
        }
        item {
            ListItem(
                headlineContent = { Text(text = stringResource(R.string.add_to_playlist)) },
                leadingContent = {
                    Icon(
                        painter = painterResource(R.drawable.playlist_add),
                        contentDescription = null,
                    )
                },
                modifier = Modifier.clickable {
                    showChoosePlaylistDialog = true
                }
            )
        }
        item {
            ListItem(
                headlineContent = {
                    Text(
                        text = stringResource(R.string.like_all)
                    )
                },
                leadingContent = {
                    Icon(
                        painter = painterResource(
                            if (allLiked) R.drawable.favorite else R.drawable.favorite_border
                        ),
                        contentDescription = null,
                    )
                },
                modifier = Modifier.clickable {
                    database.query {
                        if (allLiked) {
                            songSelection.forEach { song ->
                                update(song.toSongEntity().toggleLike())
                            }
                        } else {
                            songSelection.filter { !it.liked }.forEach { song ->
                                update(song.toSongEntity().toggleLike())
                            }
                        }
                    }
                }
            )
        }
        item {
            when (downloadState) {
                Download.STATE_COMPLETED -> {
                    ListItem(
                        headlineContent = {
                            Text(
                                text = stringResource(R.string.remove_download),
                                color = MaterialTheme.colorScheme.error
                            )
                        },
                        leadingContent = {
                            Icon(
                                painter = painterResource(R.drawable.offline),
                                contentDescription = null,
                            )
                        },
                        modifier = Modifier.clickable {
                            showRemoveDownloadDialog = true
                        }
                    )
                }
                Download.STATE_QUEUED, Download.STATE_DOWNLOADING -> {
                    ListItem(
                        headlineContent = { Text(text = stringResource(R.string.downloading)) },
                        leadingContent = {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        },
                        modifier = Modifier.clickable {
                            showRemoveDownloadDialog = true
                        }
                    )
                }
                else -> {
                    ListItem(
                        headlineContent = { Text(text = stringResource(R.string.action_download)) },
                        leadingContent = {
                            Icon(
                                painter = painterResource(R.drawable.download),
                                contentDescription = null,
                            )
                        },
                        modifier = Modifier.clickable {
                            coroutineScope.launch(Dispatchers.IO) {
                                songSelection.forEach { mediaMetadata ->
                                    val song = database.song(mediaMetadata.id).first()
                                    song?.let { downloadUtil.downloadToMediaStore(it) }
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}