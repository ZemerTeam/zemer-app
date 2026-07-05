@file:Suppress("LocalVariableName")

package com.jtech.zemer.ui.menu

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.jtech.zemer.LocalDatabase
import com.jtech.zemer.LocalDownloadUtil
import com.jtech.zemer.LocalPlayerConnection
import com.jtech.zemer.LocalSyncUtils
import com.jtech.zemer.R
import com.jtech.zemer.constants.BlockVideosKey
import com.jtech.zemer.db.entities.Event
import com.jtech.zemer.db.entities.PlaylistSong
import com.jtech.zemer.db.entities.Song
import com.jtech.zemer.extensions.isPersonalAccountSignedIn
import com.jtech.zemer.extensions.toMediaItem
import com.jtech.zemer.models.toMediaMetadata
import com.jtech.zemer.playback.DownloadMenuLogic
import com.jtech.zemer.playback.DownloadStateResolver
import com.jtech.zemer.playback.queues.YouTubeQueue
import com.jtech.zemer.ui.component.AlreadyInPlaylistDialog
import com.jtech.zemer.ui.component.ArtistChoice
import com.jtech.zemer.ui.component.LocalBottomSheetPageState
import com.jtech.zemer.ui.component.Material3MenuGroup
import com.jtech.zemer.ui.component.Material3MenuItemData
import com.jtech.zemer.ui.component.NewAction
import com.jtech.zemer.ui.component.NewActionGrid
import com.jtech.zemer.ui.component.SelectArtistDialog
import com.jtech.zemer.ui.component.SongListItem
import com.jtech.zemer.ui.component.TextFieldDialog
import com.jtech.zemer.ui.utils.ShowMediaInfo
import com.jtech.zemer.utils.PermissionHelper
import com.jtech.zemer.utils.rememberPreference
import com.jtech.zemer.viewmodels.CachePlaylistViewModel
import com.metrolist.innertube.YouTube
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.jtech.zemer.tracking.Tracker
import com.jtech.zemer.tracking.TrackingActionKind

@Suppress("unused")
@Composable
fun SongMenu(
    originalSong: Song,
    event: Event? = null,
    navController: NavController,
    playlistSong: PlaylistSong? = null,
    playlistBrowseId: String? = null,
    onDismiss: () -> Unit,
    isFromCache: Boolean = false,
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val songState = database.song(originalSong.id).collectAsState(initial = originalSong)
    val song = songState.value ?: originalSong
    val downloadUtil = LocalDownloadUtil.current
    val mediaStoreDownload by downloadUtil.getMediaStoreDownload(originalSong.id)
        .collectAsState(initial = null)
    val coroutineScope = rememberCoroutineScope()
    val syncUtils = LocalSyncUtils.current
    val scope = rememberCoroutineScope()
    var refetchIconDegree by remember { mutableFloatStateOf(0f) }
    var showReportDialog by remember { mutableStateOf(false) }

    val cacheViewModel = hiltViewModel<CachePlaylistViewModel>()
    val (blockVideos, _) = rememberPreference(BlockVideosKey, false)

    // Track whether user requested video download (for permission callback)
    var pendingVideoDownload by remember { mutableStateOf(false) }

    // Permission launcher for storage access
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.all { it }) {
            // All permissions granted, proceed with download based on user's choice
            if (pendingVideoDownload) {
                downloadUtil.downloadVideoToMediaStore(song)
            } else {
                downloadUtil.downloadToMediaStore(song)
            }
            onDismiss()
        } else {
            // Permissions denied - show error message
            android.widget.Toast.makeText(
                context,
                context.getString(R.string.storage_permission_required),
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }

    val rotationAnimation by animateFloatAsState(
        targetValue = refetchIconDegree,
        animationSpec = tween(durationMillis = 800),
        label = "",
    )

    var showEditDialog by rememberSaveable {
        mutableStateOf(false)
    }

    val TextFieldValueSaver: Saver<TextFieldValue, *> = Saver(
        save = { it.text },
        restore = { text -> TextFieldValue(text, TextRange(text.length)) }
    )

    var titleField by rememberSaveable(stateSaver = TextFieldValueSaver) {
        mutableStateOf(TextFieldValue(song.song.title))
    }

    var artistField by rememberSaveable(stateSaver = TextFieldValueSaver) {
        mutableStateOf(TextFieldValue(song.artists.firstOrNull()?.name.orEmpty()))
    }

    if (showEditDialog) {
        TextFieldDialog(
            icon = {
                Icon(
                    painter = painterResource(R.drawable.edit),
                    contentDescription = null
                )
            },
            title = {
                Text(text = stringResource(R.string.edit_song))
            },
            textFields = listOf(
                stringResource(R.string.song_title) to titleField,
                stringResource(R.string.artist_name) to artistField
            ),
            onTextFieldsChange = { index, newValue ->
                if (index == 0) titleField = newValue
                else artistField = newValue
            },
            onDoneMultiple = { values ->
                val newTitle = values[0]
                val newArtist = values[1]

                coroutineScope.launch {
                    database.query {
                        update(song.song.copy(title = newTitle))
                        val artist = song.artists.firstOrNull()
                        if (artist != null) {
                            update(artist.copy(name = newArtist))
                        }
                    }

                    showEditDialog = false
                    onDismiss()
                }
            },
            onDismiss = { showEditDialog = false }
        )
    }

    var showChoosePlaylistDialog by rememberSaveable {
        mutableStateOf(false)
    }

    if (showReportDialog) {
        ReportContentDialog(
            subject = mapOf(
                "artistId" to (song.artists.firstOrNull()?.id ?: ""),
                "artistName" to (song.artists.firstOrNull()?.name ?: ""),
                "songId" to song.id,
                "songTitle" to song.song.title,
            ),
            onDismiss = { showReportDialog = false },
        )
    }

    var showErrorPlaylistAddDialog by rememberSaveable {
        mutableStateOf(false)
    }

    AddToPlaylistDialog(
        isVisible = showChoosePlaylistDialog,
        onGetSong = { playlist ->
            // Anonymous (pooled) sessions are local-only — only a personal account writes to remote.
            if (isPersonalAccountSignedIn) {
                coroutineScope.launch(Dispatchers.IO) {
                    playlist.playlist.browseId?.let { browseId ->
                        YouTube.addToPlaylist(browseId, song.id)
                    }
                }
            }
            listOf(song.id)
        },
        onDismiss = {
            showChoosePlaylistDialog = false
        },
    )

    if (showErrorPlaylistAddDialog) {
        AlreadyInPlaylistDialog(onDismiss = { showErrorPlaylistAddDialog = false }) {
            items(listOf(song)) { song ->
                SongListItem(song = song)
            }
        }
    }

    var showSelectArtistDialog by rememberSaveable {
        mutableStateOf(false)
    }

    if (showSelectArtistDialog) {
        SelectArtistDialog(
            artists = song.artists.distinctBy { it.id }.map { ArtistChoice(it.id, it.name, it.thumbnailUrl) },
            onDismiss = { showSelectArtistDialog = false },
            onArtistClick = { artistId ->
                navController.navigate("artist/$artistId")
                onDismiss()
            },
        )
    }

    SongListItem(
        song = song,
        badges = {},
        trailingContent = {
            IconButton(
                onClick = {
                    val s = song.song.toggleLike()
                    database.query {
                        update(s)
                    }
                    syncUtils.likeSong(s)
                },
            ) {
                Icon(
                    painter = painterResource(if (song.song.liked) R.drawable.favorite else R.drawable.favorite_border),
                    tint = if (song.song.liked) MaterialTheme.colorScheme.error else LocalContentColor.current,
                    contentDescription = null,
                )
            }
        },
    )

    HorizontalDivider()

    Spacer(modifier = Modifier.height(12.dp))

    val bottomSheetPageState = LocalBottomSheetPageState.current

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
                                painter = painterResource(R.drawable.edit),
                                contentDescription = null,
                                modifier = Modifier.size(28.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        text = stringResource(R.string.edit),
                        onClick = { showEditDialog = true }
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
                        onClick = { showChoosePlaylistDialog = true }
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
                            Tracker.action(TrackingActionKind.SHARE, song.id)
                            val intent = Intent().apply {
                                action = Intent.ACTION_SEND
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, "https://music.zemer.io/watch?v=${song.id}")
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
                            icon = { Icon(painterResource(R.drawable.radio), null, Modifier.size(24.dp)) },
                            title = { Text(stringResource(R.string.start_radio)) },
                            onClick = {
                                onDismiss()
                                playerConnection.playQueue(YouTubeQueue.radio(song.toMediaMetadata(), database))
                            },
                        )
                    )
                    add(
                        Material3MenuItemData(
                            icon = { Icon(painterResource(R.drawable.playlist_play), null, Modifier.size(24.dp)) },
                            title = { Text(stringResource(R.string.play_next)) },
                            onClick = {
                                onDismiss()
                                playerConnection.playNext(song.toMediaItem())
                            },
                        )
                    )
                    add(
                        Material3MenuItemData(
                            icon = { Icon(painterResource(R.drawable.queue_music), null, Modifier.size(24.dp)) },
                            title = { Text(stringResource(R.string.add_to_queue)) },
                            onClick = {
                                onDismiss()
                                playerConnection.addToQueue(song.toMediaItem())
                            },
                        )
                    )
                    add(
                        libraryMenuItem(
                            inLibrary = song.song.inLibrary != null,
                            onToggle = {
                                val currentSong = song.song
                                val isInLibrary = currentSong.inLibrary != null
                                val token = if (isInLibrary) currentSong.libraryRemoveToken else currentSong.libraryAddToken

                                // Anonymous (pooled) sessions are local-only — only a personal account writes to remote.
                                if (isPersonalAccountSignedIn) {
                                    token?.let {
                                        coroutineScope.launch {
                                            YouTube.feedback(listOf(it))
                                        }
                                    }
                                }

                                database.query {
                                    update(song.song.toggleLibrary())
                                }
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
                    if (event != null) add(
                        Material3MenuItemData(
                            icon = { Icon(painterResource(R.drawable.delete), null, Modifier.size(24.dp)) },
                            title = { Text(stringResource(R.string.remove_from_history)) },
                            onClick = {
                                onDismiss()
                                database.query {
                                    delete(event)
                                }
                            },
                        )
                    )
                    if (playlistSong != null) add(
                        Material3MenuItemData(
                            icon = { Icon(painterResource(R.drawable.delete), null, Modifier.size(24.dp)) },
                            title = { Text(stringResource(R.string.remove_from_playlist)) },
                            onClick = {
                                database.transaction {
                                    // Anonymous (pooled) sessions are local-only — only a personal account writes to remote.
                                    if (isPersonalAccountSignedIn) {
                                        coroutineScope.launch {
                                            playlistBrowseId?.let { playlistId ->
                                                if (playlistSong.map.setVideoId != null) {
                                                    YouTube.removeFromPlaylist(
                                                        playlistId, playlistSong.map.songId, playlistSong.map.setVideoId
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    move(playlistSong.map.playlistId, playlistSong.map.position, Int.MAX_VALUE)
                                    delete(playlistSong.map.copy(position = Int.MAX_VALUE))
                                }
                                onDismiss()
                            },
                        )
                    )
                    if (isFromCache) add(
                        Material3MenuItemData(
                            icon = { Icon(painterResource(R.drawable.delete), null, Modifier.size(24.dp)) },
                            title = { Text(stringResource(R.string.remove_from_cache)) },
                            onClick = {
                                onDismiss()
                                cacheViewModel.removeSongFromCache(song.id)
                            },
                        )
                    )
                    // Unified download row: persisted-or-live state, live progress, video-aware, and the
                    // menu stays open so it animates Download -> progress -> Remove. (DownloadMenuItems.kt)
                    val downloadStatus = DownloadStateResolver.forSong(song.song.isDownloaded, mediaStoreDownload)
                    val downloadProgress = when {
                        song.song.isDownloaded ||
                            mediaStoreDownload?.status == com.jtech.zemer.playback.MediaStoreDownloadManager.DownloadState.Status.COMPLETED -> 1f
                        else -> mediaStoreDownload?.progress ?: 0f
                    }
                    val downloadFailed =
                        mediaStoreDownload?.status == com.jtech.zemer.playback.MediaStoreDownloadManager.DownloadState.Status.FAILED
                    downloadMenuItem(
                        kind = DownloadMenuLogic.songRow(downloadStatus, downloadFailed, song.song.isVideo, blockVideos),
                        progress = downloadProgress,
                        error = mediaStoreDownload?.error,
                        onDownload = {
                            pendingVideoDownload = song.song.isVideo
                            if (PermissionHelper.hasMediaStoreWritePermission(context)) {
                                if (song.song.isVideo) downloadUtil.downloadVideoToMediaStore(song)
                                else downloadUtil.downloadToMediaStore(song)
                            } else {
                                permissionLauncher.launch(PermissionHelper.getRequiredWritePermissions())
                            }
                        },
                        onCancel = { downloadUtil.cancelMediaStoreDownload(song.id) },
                        onRetry = { downloadUtil.retryMediaStoreDownload(song.id) },
                        onRemove = { scope.launch { downloadUtil.removeDownload(song.id) } },
                    )?.let { add(it) }
                    add(
                        Material3MenuItemData(
                            icon = { Icon(painterResource(R.drawable.artist), null, Modifier.size(24.dp)) },
                            title = { Text(stringResource(R.string.view_artist)) },
                            onClick = {
                                if (song.artists.size == 1) {
                                    navController.navigate("artist/${song.artists[0].id}")
                                    onDismiss()
                                } else {
                                    showSelectArtistDialog = true
                                }
                            },
                        )
                    )
                    if (song.song.albumId != null) add(
                        Material3MenuItemData(
                            icon = { Icon(painterResource(R.drawable.album), null, Modifier.size(24.dp)) },
                            title = { Text(stringResource(R.string.view_album)) },
                            onClick = {
                                onDismiss()
                                navController.navigate("album/${song.song.albumId}")
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
                                    YouTube.queue(listOf(song.id)).onSuccess {
                                        val newSong = it.firstOrNull()
                                        if (newSong != null) {
                                            database.transaction {
                                                update(song, newSong.toMediaMetadata())
                                            }
                                        }
                                    }
                                }
                            },
                        )
                    )
                    add(
                        Material3MenuItemData(
                            icon = { Icon(painterResource(R.drawable.info), null, Modifier.size(24.dp)) },
                            title = { Text(stringResource(R.string.details)) },
                            onClick = {
                                onDismiss()
                                bottomSheetPageState.show {
                                    ShowMediaInfo(song.id)
                                }
                            },
                        )
                    )
                }
            )
        }
    }
}
