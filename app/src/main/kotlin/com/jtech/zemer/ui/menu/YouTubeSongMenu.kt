package com.jtech.zemer.ui.menu

import android.annotation.SuppressLint
import android.content.Intent
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.jtech.zemer.LocalDatabase
import com.jtech.zemer.LocalDownloadUtil
import com.jtech.zemer.LocalPlayerConnection
import com.jtech.zemer.LocalSyncUtils
import com.jtech.zemer.R
import com.jtech.zemer.constants.BlockVideosKey
import com.jtech.zemer.constants.ListThumbnailSize
import com.jtech.zemer.constants.ThumbnailCornerRadius
import com.jtech.zemer.db.entities.SongEntity
import com.jtech.zemer.extensions.isPersonalAccountSignedIn
import com.jtech.zemer.extensions.toMediaItem
import com.jtech.zemer.models.MediaMetadata
import com.jtech.zemer.models.toMediaMetadata
import com.jtech.zemer.playback.DownloadMenuLogic
import com.jtech.zemer.playback.DownloadStateResolver
import com.jtech.zemer.playback.MediaStoreDownloadManager
import com.jtech.zemer.playback.queues.YouTubeQueue
import com.jtech.zemer.ui.component.ArtistChoice
import com.jtech.zemer.utils.VideoLinkBuilder
import com.jtech.zemer.ui.component.LocalBottomSheetPageState
import com.jtech.zemer.ui.component.Material3MenuGroup
import com.jtech.zemer.ui.component.Material3MenuItemData
import com.jtech.zemer.ui.component.NewAction
import com.jtech.zemer.ui.component.NewActionGrid
import com.jtech.zemer.ui.component.SelectArtistDialog
import com.jtech.zemer.ui.utils.ShowMediaInfo
import com.jtech.zemer.ui.utils.resize
import com.jtech.zemer.utils.joinByBullet
import com.jtech.zemer.utils.makeTimeString
import com.jtech.zemer.utils.rememberPreference
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.SongItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDateTime

@SuppressLint("MutableCollectionMutableState")
@Composable
fun YouTubeSongMenu(
    song: SongItem,
    navController: NavController,
    onDismiss: () -> Unit,
    onHistoryRemoved: () -> Unit = {},
    isVideo: Boolean = false,
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val downloadUtil = LocalDownloadUtil.current
    val librarySong by database.song(song.id).collectAsState(initial = null)
    val mediaStoreDownload by downloadUtil.getMediaStoreDownload(song.id).collectAsState(initial = null)
    val coroutineScope = rememberCoroutineScope()
    val syncUtils = LocalSyncUtils.current
    var showReportDialog by remember { mutableStateOf(false) }
    val (blockVideos, _) = rememberPreference(BlockVideosKey, false)
    val artists = remember {
        song.artists.mapNotNull {
            it.id?.let { artistId ->
                MediaMetadata.Artist(id = artistId, name = it.name)
            }
        }
    }

    if (showReportDialog) {
        ReportContentDialog(
            subject = mapOf(
                "artistId" to (song.artists.firstOrNull()?.id ?: ""),
                "artistName" to (song.artists.firstOrNull()?.name ?: ""),
                "songId" to song.id,
                "songTitle" to song.title,
            ),
            onDismiss = { showReportDialog = false },
        )
    }

    var showChoosePlaylistDialog by rememberSaveable {  
        mutableStateOf(false)  
    }  

    AddToPlaylistDialog(  
        isVisible = showChoosePlaylistDialog,  
        onGetSong = { playlist ->  
            database.transaction {
                insert(song.toMediaMetadata())
            }
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
        onDismiss = { showChoosePlaylistDialog = false }  
    )  

    var showSelectArtistDialog by rememberSaveable {  
        mutableStateOf(false)  
    }  

    if (showSelectArtistDialog) {
        SelectArtistDialog(
            artists = artists.map { ArtistChoice(id = it.id!!, name = it.name) },
            onDismiss = { showSelectArtistDialog = false },
            onArtistClick = { artistId ->
                navController.navigate("artist/$artistId")
                onDismiss()
            },
        )
    }  

    ListItem(  
        headlineContent = {
            Text(
                text = song.title,
                modifier = Modifier.basicMarquee(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },  
        supportingContent = {  
            Text(  
                text = joinByBullet(
                    song.artists.joinToString { it.name },
                    song.duration?.let { makeTimeString(it * 1000L) },
                )
            )  
        },  
        leadingContent = {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(ListThumbnailSize)
                    .clip(RoundedCornerShape(ThumbnailCornerRadius))
            ) {
                AsyncImage(
                    model = song.thumbnail,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(ThumbnailCornerRadius))
                )
            }
        },
        trailingContent = {  
            IconButton(  
                onClick = {  
                    database.transaction {  
                        librarySong.let { librarySong ->  
                            val s: SongEntity  
                            if (librarySong == null) {  
                                insert(song.toMediaMetadata(), SongEntity::toggleLike)  
                                s = song.toMediaMetadata().toSongEntity().let(SongEntity::toggleLike)  
                            } else {  
                                s = librarySong.song.toggleLike()  
                                update(s)  
                            }  
                            syncUtils.likeSong(s)  
                        }  
                    }  
                },  
            ) {  
                Icon(  
                    painter = painterResource(if (librarySong?.song?.liked == true) R.drawable.favorite else R.drawable.favorite_border),  
                    tint = if (librarySong?.song?.liked == true) MaterialTheme.colorScheme.error else LocalContentColor.current,  
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
                                painter = painterResource(R.drawable.playlist_play),
                                contentDescription = null,
                                modifier = Modifier.size(28.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        text = stringResource(R.string.play_next),
                        onClick = {
                            playerConnection.playNext(song.copy(thumbnail = song.thumbnail.resize(544,544)).toMediaItem())
                            onDismiss()
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
                                val shareUrl = if (isVideo) {
                                    VideoLinkBuilder.videoLink(song.id)
                                } else {
                                    song.shareLink
                                }
                                putExtra(Intent.EXTRA_TEXT, shareUrl)
                            }
                            context.startActivity(Intent.createChooser(intent, null))
                            onDismiss()
                        }
                    ),
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
                                playerConnection.playQueue(YouTubeQueue.radio(song.toMediaMetadata(), database))
                                onDismiss()
                            },
                        )
                    )
                    add(
                        Material3MenuItemData(
                            icon = { Icon(painterResource(R.drawable.queue_music), null, Modifier.size(24.dp)) },
                            title = { Text(stringResource(R.string.add_to_queue)) },
                            onClick = {
                                playerConnection.addToQueue(song.toMediaItem())
                                onDismiss()
                            },
                        )
                    )
                    add(
                        Material3MenuItemData(
                            icon = { Icon(painterResource(R.drawable.warning), null, Modifier.size(24.dp)) },
                            title = { Text(stringResource(R.string.report_artist)) },
                            onClick = {
                                showReportDialog = true
                            },
                        )
                    )
                    if (song.historyRemoveToken != null) {
                        add(
                            Material3MenuItemData(
                                icon = { Icon(painterResource(R.drawable.delete), null, Modifier.size(24.dp)) },
                                title = { Text(stringResource(R.string.remove_from_history)) },
                                onClick = {
                                    coroutineScope.launch {
                                        // Anonymous (pooled) sessions are local-only — only a personal account writes to remote.
                                        if (isPersonalAccountSignedIn) {
                                            YouTube.feedback(listOf(song.historyRemoveToken!!))
                                        }

                                        delay(500)

                                        onHistoryRemoved()

                                        onDismiss()
                                    }
                                },
                            )
                        )
                    }
                    add(
                        libraryMenuItem(
                            inLibrary = librarySong?.song?.inLibrary != null,
                            onToggle = {
                                val isInLibrary = librarySong?.song?.inLibrary != null
                                val token = if (isInLibrary) song.libraryRemoveToken else song.libraryAddToken

                                // Anonymous (pooled) sessions are local-only — only a personal account writes to remote.
                                if (isPersonalAccountSignedIn) {
                                    token?.let {
                                        coroutineScope.launch {
                                            YouTube.feedback(listOf(it))
                                        }
                                    }
                                }

                                if (isInLibrary) {
                                    database.query {
                                        inLibrary(song.id, null)
                                    }
                                } else {
                                    // Set isVideo flag when adding video to library
                                    val metadata = song.toMediaMetadata().let {
                                        if (isVideo) it.copy(isVideo = true) else it
                                    }
                                    database.transaction {
                                        insert(metadata)
                                        // Ensure isVideo is set even if song already exists (insert uses IGNORE)
                                        if (isVideo) {
                                            setIsVideo(song.id, true)
                                        }
                                        inLibrary(song.id, LocalDateTime.now())
                                        addLibraryTokens(song.id, song.libraryAddToken, song.libraryRemoveToken)
                                    }
                                }
                            },
                        )
                    )
                    // Unified download row: persisted-or-live state, live progress, video-aware, and the
                    // menu stays open so it animates Download -> progress -> Remove. (DownloadMenuItems.kt)
                    val songIsVideo = librarySong?.song?.isVideo == true || isVideo
                    val downloadStatus = DownloadStateResolver.forSong(librarySong?.song?.isDownloaded == true, mediaStoreDownload)
                    val downloadProgress = when {
                        librarySong?.song?.isDownloaded == true ||
                            mediaStoreDownload?.status == MediaStoreDownloadManager.DownloadState.Status.COMPLETED -> 1f
                        else -> mediaStoreDownload?.progress ?: 0f
                    }
                    val downloadFailed =
                        mediaStoreDownload?.status == MediaStoreDownloadManager.DownloadState.Status.FAILED
                    downloadMenuItem(
                        kind = DownloadMenuLogic.songRow(downloadStatus, downloadFailed, songIsVideo, blockVideos),
                        progress = downloadProgress,
                        error = mediaStoreDownload?.error,
                        onDownload = {
                            coroutineScope.launch(Dispatchers.IO) {
                                // Insert with correct isVideo flag
                                val metadata = song.toMediaMetadata().copy(isVideo = isVideo)
                                database.transaction {
                                    insert(metadata)
                                    // Always set isVideo to match the download context
                                    setIsVideo(song.id, isVideo)
                                }
                                val dbSong = database.song(song.id).first()
                                dbSong?.let {
                                    if (isVideo) {
                                        downloadUtil.downloadVideoToMediaStore(it)
                                    } else {
                                        downloadUtil.downloadToMediaStore(it)
                                    }
                                }
                            }
                        },
                        onCancel = { coroutineScope.launch { downloadUtil.cancelMediaStoreDownload(song.id) } },
                        onRetry = { downloadUtil.retryMediaStoreDownload(song.id) },
                        onRemove = { coroutineScope.launch { downloadUtil.removeDownload(song.id) } },
                    )?.let { add(it) }
                    if (artists.isNotEmpty()) {
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
                    song.album?.let { album ->
                        add(
                            Material3MenuItemData(
                                icon = { Icon(painterResource(R.drawable.album), null, Modifier.size(24.dp)) },
                                title = { Text(stringResource(R.string.view_album)) },
                                onClick = {
                                    navController.navigate("album/${album.id}")
                                    onDismiss()
                                },
                            )
                        )
                    }
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
                },
            )
        }
    }
}
