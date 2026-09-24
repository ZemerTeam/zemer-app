package com.jtech.zemer.ui.menu

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import com.jtech.zemer.LocalDatabase
import com.jtech.zemer.LocalDownloadUtil
import com.jtech.zemer.LocalPlayerConnection
import com.jtech.zemer.LocalSyncUtils
import com.jtech.zemer.R
import com.jtech.zemer.db.entities.PlaylistSongMap
import com.jtech.zemer.db.entities.Song
import com.jtech.zemer.extensions.isPersonalAccountSignedIn
import com.jtech.zemer.extensions.toMediaItem
import com.jtech.zemer.models.MediaMetadata
import com.jtech.zemer.models.toMediaMetadata
import com.jtech.zemer.playback.DownloadMenuLogic
import com.jtech.zemer.playback.DownloadStateResolver
import com.jtech.zemer.playback.MediaStoreDownloadManager
import com.jtech.zemer.playback.queues.ListQueue
import com.jtech.zemer.ui.component.Material3MenuGroup
import com.jtech.zemer.ui.component.Material3MenuItemData
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

    // Episodes are never `liked` (saved-for-later is inLibrary) — Like all operates on the songs
    // in the selection only, and reads its all-liked state from them too.
    val likeableSongs = remember(songSelection) { songSelection.filter { !it.song.isEpisode } }
    val allLiked by remember(songSelection) {
        mutableStateOf(
            likeableSongs.isNotEmpty() && likeableSongs.all {
                it.song.liked
            },
        )
    }

    val mediaStoreDownloads by downloadUtil.getAllMediaStoreDownloads().collectAsState()

    var showChoosePlaylistDialog by rememberSaveable {
        mutableStateOf(false)
    }

    AddToPlaylistDialog(
        isVisible = showChoosePlaylistDialog,
        onGetSong = { _ ->
            // The remote write is owned by AddToPlaylistDialog (single writer); callers return ids only.
            songSelection.map { it.id }
        },
        onDismiss = {
            showChoosePlaylistDialog = false
        },
    )

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
        contentPadding = PaddingValues(
            start = 0.dp,
            top = 0.dp,
            end = 0.dp,
            bottom = 8.dp + WindowInsets.systemBars.asPaddingValues().calculateBottomPadding(),
        ),
    ) {
        item {
            NewActionGrid(
                actions = listOfNotNull(
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
                            // First non-episode song: an episode's "artist" is a podcast HOST channel
                            // and must never enter the music artist-report pipeline (the same gate as
                            // the single-item menus).
                            likeableSongs.firstOrNull()?.let { targetSong = it; showReportDialog = true }
                        }
                        // Hidden when the selection holds no reportable (non-episode) songs.
                    ).takeIf { likeableSongs.isNotEmpty() }
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
                            icon = { Icon(painterResource(R.drawable.play), null, Modifier.size(24.dp)) },
                            title = { Text(stringResource(R.string.play)) },
                            onClick = {
                                onDismiss()
                                playerConnection.playQueue(
                                    ListQueue(
                                        title = selectionQueueTitle,
                                        items = songSelection.map { it.toMediaItem() },
                                    ),
                                )
                                clearAction()
                            },
                        )
                    )
                    add(
                        Material3MenuItemData(
                            icon = { Icon(painterResource(R.drawable.shuffle), null, Modifier.size(24.dp)) },
                            title = { Text(stringResource(R.string.shuffle)) },
                            onClick = {
                                onDismiss()
                                playerConnection.playQueue(
                                    ListQueue(
                                        title = selectionQueueTitle,
                                        items = songSelection.shuffled().map { it.toMediaItem() },
                                    ),
                                )
                                clearAction()
                            },
                        )
                    )
                    add(
                        Material3MenuItemData(
                            icon = { Icon(painterResource(R.drawable.queue_music), null, Modifier.size(24.dp)) },
                            title = { Text(stringResource(R.string.add_to_queue)) },
                            onClick = {
                                onDismiss()
                                playerConnection.addToQueue(songSelection.map { it.toMediaItem() })
                                clearAction()
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
                    add(
                        libraryMenuItem(
                            inLibrary = allInLibrary,
                            onToggle = {
                                if (allInLibrary) {
                                    database.query {
                                        songSelection.forEach { song ->
                                            inLibrary(song.id, null)
                                        }
                                    }
                                    // Anonymous (pooled) sessions are local-only — only a personal account writes to remote.
                                    if (isPersonalAccountSignedIn) {
                                        coroutineScope.launch {
                                            val tokens = songSelection.mapNotNull { it.song.libraryRemoveToken }
                                            tokens.chunked(20).forEach {
                                                YouTube.feedback(it)
                                            }
                                        }
                                    }
                                } else {
                                    database.transaction {
                                        songSelection.forEach { song ->
                                            insert(song.toMediaMetadata())
                                            inLibrary(song.id, LocalDateTime.now())
                                        }
                                    }
                                    // Anonymous (pooled) sessions are local-only — only a personal account writes to remote.
                                    if (isPersonalAccountSignedIn) {
                                        coroutineScope.launch {
                                            val tokens = songSelection.filter {it.song.inLibrary == null}.mapNotNull { it.song.libraryAddToken }
                                            tokens.chunked(20).forEach {
                                                YouTube.feedback(it)
                                            }
                                        }
                                    }
                                }
                            },
                        )
                    )
                    // Unified download row: persisted-or-live aggregate state + live progress, menu
                    // stays open so it animates Download -> progress -> Remove. (DownloadMenuItems.kt)
                    run {
                        val dlStatus = DownloadStateResolver.aggregateSongs(songSelection, mediaStoreDownloads)
                        val dlProgress = DownloadStateResolver.aggregateProgress(songSelection, mediaStoreDownloads)
                        downloadMenuItem(
                            kind = DownloadMenuLogic.collectionRow(dlStatus),
                            progress = dlProgress,
                            onDownload = { songSelection.forEach { downloadUtil.downloadToMediaStore(it) } },
                            onCancel = { songSelection.forEach { downloadUtil.cancelMediaStoreDownload(it.id) } },
                            onRetry = { songSelection.forEach { downloadUtil.retryMediaStoreDownload(it.id) } },
                            onRemove = {
                                coroutineScope.launch {
                                    songSelection.forEach { downloadUtil.removeDownload(it.id) }
                                }
                            },
                        )?.let { add(it) }
                    }
                    // Hidden for an episode-only selection: episodes are never music-liked, so the
                    // row would be an enabled action that silently does nothing.
                    if (likeableSongs.isNotEmpty()) add(
                        Material3MenuItemData(
                            icon = {
                                Icon(
                                    painterResource(
                                        if (allLiked) R.drawable.favorite else R.drawable.favorite_border
                                    ),
                                    null,
                                    Modifier.size(24.dp),
                                )
                            },
                            title = {
                                Text(
                                    stringResource(
                                        if (allLiked) R.string.dislike_all else R.string.like_all
                                    )
                                )
                            },
                            onClick = {
                                // Songs only — an episode in the selection is never music-liked.
                                // Fresh read (not the remembered allLiked) so a stale composition
                                // can't invert the action; named to not shadow the label's state.
                                val allLikedNow = likeableSongs.all { it.song.liked }
                                onDismiss()
                                database.query {
                                    likeableSongs.forEach { song ->
                                        if ((!allLikedNow && !song.song.liked) || allLikedNow) {
                                            val s = song.song.toggleLike()
                                            update(s)
                                            syncUtils.likeSong(s)
                                        }
                                    }
                                }
                            },
                        )
                    )
                    val isNotEmpty = false
                    if (isNotEmpty) {
                        add(
                            Material3MenuItemData(
                                icon = { Icon(painterResource(R.drawable.delete), null, Modifier.size(24.dp)) },
                                title = { Text(stringResource(R.string.delete)) },
                                onClick = {
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
                                },
                            )
                        )
                    }
                },
            )
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

    // Episodes are never `liked` (saved-for-later is inLibrary) — Like all operates on the songs
    // in the selection only (the queue multi-select can hold episodes).
    val likeableSongs = remember(songSelection) { songSelection.filter { !it.isEpisode } }
    val allLiked by remember(songSelection) {
        mutableStateOf(likeableSongs.isNotEmpty() && likeableSongs.all { it.liked })
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

    val mediaStoreDownloads by downloadUtil.getAllMediaStoreDownloads().collectAsState()

    // The selection is online MediaMetadata; load the persisted Room rows so the aggregate state can
    // honor songs downloaded in a previous session (which aren't in the live session map).
    val selectionIds = songSelection.map { it.id }
    val dbSongs by produceState(
        initialValue = emptyList<com.jtech.zemer.db.entities.Song>(),
        key1 = selectionIds,
    ) {
        value = database.getSongsByIds(selectionIds)
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
            Material3MenuGroup(
                modifier = Modifier.padding(horizontal = 4.dp),
                items = buildList {
                    if (currentItems.isNotEmpty()) {
                        add(
                            Material3MenuItemData(
                                icon = { Icon(painterResource(R.drawable.delete), null, Modifier.size(24.dp)) },
                                title = { Text(stringResource(R.string.delete)) },
                                onClick = {
                                    onDismiss()
                                    var i = 0
                                    currentItems.forEach { cur ->
                                        if (playerConnection.player.availableCommands.contains(Player.COMMAND_CHANGE_MEDIA_ITEMS)) {
                                            playerConnection.player.removeMediaItem(cur.firstPeriodIndex - i++)
                                        }
                                    }
                                    clearAction()
                                },
                            )
                        )
                    }
                    add(
                        Material3MenuItemData(
                            icon = { Icon(painterResource(R.drawable.play), null, Modifier.size(24.dp)) },
                            title = { Text(stringResource(R.string.play)) },
                            onClick = {
                                onDismiss()
                                playerConnection.playQueue(
                                    ListQueue(
                                        title = selectionQueueTitle,
                                        items = songSelection.map { it.toMediaItem() },
                                    ),
                                )
                                clearAction()
                            },
                        )
                    )
                    add(
                        Material3MenuItemData(
                            icon = { Icon(painterResource(R.drawable.shuffle), null, Modifier.size(24.dp)) },
                            title = { Text(stringResource(R.string.shuffle)) },
                            onClick = {
                                onDismiss()
                                playerConnection.playQueue(
                                    ListQueue(
                                        title = selectionQueueTitle,
                                        items = songSelection.shuffled().map { it.toMediaItem() },
                                    ),
                                )
                                clearAction()
                            },
                        )
                    )
                    add(
                        Material3MenuItemData(
                            icon = { Icon(painterResource(R.drawable.queue_music), null, Modifier.size(24.dp)) },
                            title = { Text(stringResource(R.string.add_to_queue)) },
                            onClick = {
                                onDismiss()
                                playerConnection.addToQueue(songSelection.map { it.toMediaItem() })
                                clearAction()
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
                    // Hidden for an episode-only selection: episodes are never music-liked, so the
                    // row would be an enabled action that silently does nothing.
                    if (likeableSongs.isNotEmpty()) add(
                        Material3MenuItemData(
                            icon = {
                                Icon(
                                    painterResource(
                                        if (allLiked) R.drawable.favorite else R.drawable.favorite_border
                                    ),
                                    null,
                                    Modifier.size(24.dp),
                                )
                            },
                            title = { Text(stringResource(R.string.like_all)) },
                            onClick = {
                                database.query {
                                    // Queue multi-select holds rowless MediaMetadata (a relay corpus song
                                    // is never in the local DB), so a bare update() would no-op. insert()
                                    // is OnConflictStrategy.IGNORE, so it only fills in a missing parent row
                                    // (never clobbers an existing one) before we flip liked.
                                    // Songs only — an episode in the selection is never music-liked.
                                    val toToggle =
                                        if (allLiked) likeableSongs else likeableSongs.filter { !it.liked }
                                    toToggle.forEach { song ->
                                        insert(song)
                                        update(song.toSongEntity().toggleLike())
                                    }
                                }
                            },
                        )
                    )
                    // Unified download row: aggregate over the persisted Room rows + live progress,
                    // menu stays open so it animates Download -> progress -> Remove. (DownloadMenuItems.kt)
                    run {
                        // Aggregate over EVERY selected id off the live map (+ the persisted-downloaded
                        // snapshot), not just the rows already in Room — otherwise online songs not yet
                        // in the DB are invisible to the all/any check and the status reads wrong.
                        val ids = songSelection.map { it.id }
                        val persistedDownloaded = dbSongs.filter { it.song.isDownloaded }.map { it.id }.toSet()
                        val dlStatus = DownloadStateResolver.aggregateByIds(ids, mediaStoreDownloads, persistedDownloaded)
                        val dlProgress = DownloadStateResolver.aggregateProgressByIds(ids, mediaStoreDownloads, persistedDownloaded)
                        downloadMenuItem(
                            kind = DownloadMenuLogic.collectionRow(dlStatus),
                            progress = dlProgress,
                            onDownload = {
                                // Online MediaMetadata aren't Room entities yet — persist each, then
                                // download, so the first tap downloads (a bare database.song() lookup
                                // returns null for a not-yet-persisted id and silently does nothing).
                                coroutineScope.launch(Dispatchers.IO) {
                                    songSelection.forEach { mediaMetadata ->
                                        database.transaction { insert(mediaMetadata) }
                                        database.song(mediaMetadata.id).first()?.let {
                                            downloadUtil.downloadToMediaStore(it)
                                        }
                                    }
                                }
                            },
                            onCancel = { songSelection.forEach { downloadUtil.cancelMediaStoreDownload(it.id) } },
                            onRetry = { songSelection.forEach { downloadUtil.retryMediaStoreDownload(it.id) } },
                            onRemove = {
                                coroutineScope.launch {
                                    songSelection.forEach { downloadUtil.removeDownload(it.id) }
                                }
                            },
                        )?.let { add(it) }
                    }
                },
            )
        }
    }
}