package com.jtech.zemer.ui.menu

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.Configuration
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.exoplayer.offline.Download
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.jtech.zemer.LocalDatabase
import com.jtech.zemer.LocalDownloadUtil
import com.jtech.zemer.LocalPlayerConnection
import com.jtech.zemer.LocalSyncUtils
import com.jtech.zemer.R
import com.jtech.zemer.constants.BlockVideosKey
import com.jtech.zemer.constants.ListItemHeight
import com.jtech.zemer.constants.ListThumbnailSize
import com.jtech.zemer.constants.ThumbnailCornerRadius
import com.jtech.zemer.db.entities.SongEntity
import com.jtech.zemer.extensions.toMediaItem
import com.jtech.zemer.models.MediaMetadata
import com.jtech.zemer.models.toMediaMetadata
import com.jtech.zemer.playback.MediaStoreDownloadManager
import com.jtech.zemer.playback.queues.YouTubeQueue
import com.jtech.zemer.ui.component.ListDialog
import com.jtech.zemer.utils.VideoLinkBuilder
import com.jtech.zemer.ui.component.LocalBottomSheetPageState
import com.jtech.zemer.ui.component.NewAction
import com.jtech.zemer.ui.component.NewActionGrid
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
import kotlinx.coroutines.tasks.await
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
    val auth = remember { FirebaseAuth.getInstance() }
    val firestore = remember { FirebaseFirestore.getInstance() }
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val downloadUtil = LocalDownloadUtil.current
    val librarySong by database.song(song.id).collectAsState(initial = null)
    val mediaStoreDownload by downloadUtil.getMediaStoreDownload(song.id).collectAsState(initial = null)
    val download by downloadUtil.getDownload(song.id).collectAsState(initial = null)
    val coroutineScope = rememberCoroutineScope()
    val syncUtils = LocalSyncUtils.current
    var showReportDialog by remember { mutableStateOf(false) }
    var selectedReason by remember { mutableStateOf("") }
    var comment by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    val (blockVideos, _) = rememberPreference(BlockVideosKey, false)
    val artists = remember {
        song.artists.mapNotNull {
            it.id?.let { artistId ->
                MediaMetadata.Artist(id = artistId, name = it.name)
            }
        }
    }

    if (showReportDialog) {
        val reasons = listOf(
            "female" to stringResource(R.string.report_reason_female),
            "gentile" to stringResource(R.string.report_reason_gentile),
            "bad_playlists" to stringResource(R.string.report_reason_bad_playlists),
            "bad_images" to stringResource(R.string.report_reason_bad_images),
            "other" to stringResource(R.string.report_reason_other),
        )
        AlertDialog(
            onDismissRequest = { if (!isSubmitting) showReportDialog = false },
            title = { Text(stringResource(R.string.report_artist)) },
            text = {
                Column {
                    reasons.forEach { (value, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedReason = value }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedReason == value,
                                onClick = { selectedReason = value }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = label)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = comment,
                        onValueChange = { comment = it },
                        label = { Text(stringResource(R.string.report_optional_comment)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = false,
                        maxLines = 3
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (selectedReason.isBlank()) {
                            Toast.makeText(context, context.getString(R.string.report_choose_reason), Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        coroutineScope.launch {
                            isSubmitting = true
                            try {
                                val uid = auth.currentUser?.uid ?: "anon"
                                val payload = hashMapOf(
                                    "artistId" to (song.artists.firstOrNull()?.id ?: ""),
                                    "artistName" to (song.artists.firstOrNull()?.name ?: ""),
                                    "songId" to song.id,
                                    "songTitle" to song.title,
                                    "reason" to selectedReason,
                                    "comment" to comment,
                                    "status" to "pending",
                                    "reporterUid" to uid,
                                    "createdAt" to FieldValue.serverTimestamp()
                                )
                                firestore.collection("artistReports").add(payload).await()
                                Toast.makeText(context, context.getString(R.string.report_success), Toast.LENGTH_SHORT).show()
                                showReportDialog = false
                                selectedReason = ""
                                comment = ""
                            } catch (e: Exception) {
                                Toast.makeText(context, context.getString(R.string.report_failure), Toast.LENGTH_SHORT).show()
                            } finally {
                                isSubmitting = false
                            }
                        }
                    },
                    enabled = !isSubmitting
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(stringResource(R.string.report_submit))
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { if (!isSubmitting) showReportDialog = false },
                    enabled = !isSubmitting
                ) {
                    Text(stringResource(R.string.report_cancel))
                }
            }
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
            coroutineScope.launch(Dispatchers.IO) {  
                playlist.playlist.browseId?.let { browseId ->  
                    YouTube.addToPlaylist(browseId, song.id)  
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
        ListDialog(  
            onDismiss = { showSelectArtistDialog = false },  
        ) {  
            items(artists) { artist ->  
                var isFocused by remember { mutableStateOf(false) }
                val backgroundColor by animateColorAsState(
                    targetValue = if (isFocused) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
                    label = "artist_dialog_focus_bg"
                )
                Row(  
                    verticalAlignment = Alignment.CenterVertically,  
                    modifier =  
                    Modifier  
                        .height(ListItemHeight)
                        .fillMaxWidth()
                        .onFocusChanged { isFocused = it.isFocused }
                        .focusable()
                        .clickable {  
                            navController.navigate("artist/${artist.id}")  
                            showSelectArtistDialog = false  
                            onDismiss()  
                        }
                        .background(backgroundColor)
                        .padding(horizontal = 12.dp),  
                ) {  
                    Box(  
                        contentAlignment = Alignment.CenterStart,  
                        modifier =  
                        Modifier  
                            .fillParentMaxWidth()  
                            .height(ListItemHeight)  
                            .clickable {  
                                navController.navigate("artist/${artist.id}")  
                                showSelectArtistDialog = false  
                                onDismiss()  
                            }  
                            .padding(horizontal = 24.dp),  
                    ) {  
                        Text(  
                            text = artist.name,  
                            fontSize = 18.sp,  
                            fontWeight = FontWeight.Bold,  
                            maxLines = 1,  
                            overflow = TextOverflow.Ellipsis,  
                        )  
                    }  
                }  
            }  
        }  
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
            ListItem(
                headlineContent = { Text(text = stringResource(R.string.start_radio)) },
                leadingContent = {
                    Icon(
                        painter = painterResource(R.drawable.radio),
                        contentDescription = null,
                    )
                },
                modifier = Modifier.clickable {
                    playerConnection.playQueue(YouTubeQueue.radio(song.toMediaMetadata(), database))
                    onDismiss()
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
                    playerConnection.addToQueue(song.toMediaItem())
                    onDismiss()
                }
            )
        }
        item {
            ListItem(
                headlineContent = { Text(text = stringResource(R.string.report_artist)) },
                leadingContent = {
                    Icon(
                        painter = painterResource(R.drawable.warning),
                        contentDescription = null,
                    )
                },
                modifier = Modifier.clickable {
                    showReportDialog = true
                }
            )
        }
        if (song.historyRemoveToken != null) {
            item {
                ListItem(
                    headlineContent = { Text(text = stringResource(R.string.remove_from_history)) },
                    leadingContent = {
                        Icon(
                            painter = painterResource(R.drawable.delete),
                            contentDescription = null,
                        )
                    },
                    modifier = Modifier.clickable {
                        coroutineScope.launch {
                            YouTube.feedback(listOf(song.historyRemoveToken!!))

                            delay(500)

                            onHistoryRemoved()

                            onDismiss()
                        }
                    }
                )
            }
        }
        item {
            ListItem(
                headlineContent = { 
                    Text(text = if (librarySong?.song?.inLibrary != null) stringResource(R.string.remove_from_library) else stringResource(R.string.add_to_library))
                },
                leadingContent = {
                    Icon(
                        painter = painterResource(if (librarySong?.song?.inLibrary != null) R.drawable.library_add_check else R.drawable.library_add),
                        contentDescription = null,
                    )
                },
                modifier = Modifier.clickable {
                    val isInLibrary = librarySong?.song?.inLibrary != null
                    val token = if (isInLibrary) song.libraryRemoveToken else song.libraryAddToken

                    token?.let {
                        coroutineScope.launch {
                            YouTube.feedback(listOf(it))
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
                }
            )
        }
        item {
            when (mediaStoreDownload?.status) {
                MediaStoreDownloadManager.DownloadState.Status.COMPLETED -> {
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
                            coroutineScope.launch {
                                downloadUtil.removeDownload(song.id)
                            }
                        }
                    )
                }

                MediaStoreDownloadManager.DownloadState.Status.QUEUED,
                MediaStoreDownloadManager.DownloadState.Status.DOWNLOADING -> {
                    val progress = mediaStoreDownload?.progress ?: 0f
                    ListItem(
                        headlineContent = {
                            Text(
                                text = stringResource(R.string.downloading)
                            )
                        },
                        supportingContent = {
                            Text(text = "${(progress * 100).toInt()}%")
                        },
                        leadingContent = {
                            CircularProgressIndicator(
                                progress = { progress.coerceIn(0f, 1f) },
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        },
                        modifier = Modifier.clickable {
                            coroutineScope.launch {
                                downloadUtil.cancelMediaStoreDownload(song.id)
                            }
                        }
                    )
                }

                MediaStoreDownloadManager.DownloadState.Status.FAILED,
                MediaStoreDownloadManager.DownloadState.Status.CANCELLED,
                null -> {
                    when (download?.state) {
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
                                    coroutineScope.launch {
                                        downloadUtil.removeDownload(song.id)
                                    }
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
                                    coroutineScope.launch {
                                        downloadUtil.removeDownload(song.id)
                                    }
                                }
                            )
                        }
                        else -> {
                            // Skip showing download option for videos when blocked
                            if (isVideo && blockVideos) {
                                null
                            } else {
                                ListItem(
                                    headlineContent = {
                                        Text(text = if (isVideo)
                                            stringResource(R.string.download_video_to_device)
                                        else
                                            stringResource(R.string.action_download))
                                    },
                                    leadingContent = {
                                        Icon(
                                            painter = painterResource(R.drawable.download),
                                            contentDescription = null,
                                        )
                                    },
                                    modifier = Modifier.clickable {
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
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
        if (artists.isNotEmpty()) {
            item {
                ListItem(
                    headlineContent = { Text(text = stringResource(R.string.view_artist)) },
                    leadingContent = {
                        Icon(
                            painter = painterResource(R.drawable.artist),
                            contentDescription = null,
                        )
                    },
                    modifier = Modifier.clickable {
                        if (artists.size == 1) {
                            navController.navigate("artist/${artists[0].id}")
                            onDismiss()
                        } else {
                            showSelectArtistDialog = true
                        }
                    }
                )
            }
        }
        song.album?.let { album ->
            item {
                ListItem(
                    headlineContent = { Text(text = stringResource(R.string.view_album)) },
                    leadingContent = {
                        Icon(
                            painter = painterResource(R.drawable.album),
                            contentDescription = null,
                        )
                    },
                    modifier = Modifier.clickable {
                        navController.navigate("album/${album.id}")
                        onDismiss()
                    }
                )
            }
        }
        item {
             ListItem(
                 headlineContent = { Text(text = stringResource(R.string.details)) },
                 leadingContent = {
                     Icon(
                         painter = painterResource(R.drawable.info),
                         contentDescription = null,
                     )
                 },
                 modifier = Modifier.clickable {
                      onDismiss()
                      bottomSheetPageState.show {
                          ShowMediaInfo(song.id)
                      }
                 }
             )
        }
    }
}
