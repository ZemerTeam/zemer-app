package com.jtech.zemer.ui.menu

import android.content.Intent
import android.media.audiofx.AudioEffect
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.media3.common.PlaybackParameters
import androidx.navigation.NavController
import com.jtech.zemer.LocalDatabase
import com.jtech.zemer.LocalDownloadUtil
import com.jtech.zemer.LocalPlayerConnection
import com.jtech.zemer.R
import com.jtech.zemer.constants.BlockVideosKey
import com.jtech.zemer.constants.PlaybackMode
import com.jtech.zemer.constants.PlaybackModeKey
import com.jtech.zemer.extensions.isPersonalAccountSignedIn
import com.jtech.zemer.extensions.copyToClipboard
import com.jtech.zemer.extensions.toast
import com.jtech.zemer.models.MediaMetadata
import com.jtech.zemer.playback.DownloadMenuLogic
import com.jtech.zemer.playback.DownloadStateResolver
import com.jtech.zemer.playback.MediaStoreDownloadManager
import com.jtech.zemer.ui.component.DefaultDialog
import com.jtech.zemer.ui.component.BigSeekBar
import com.jtech.zemer.ui.component.BottomSheetState
import com.jtech.zemer.ui.component.ArtistChoice
import com.jtech.zemer.ui.component.SelectArtistDialog
import com.jtech.zemer.ui.component.NewAction
import com.jtech.zemer.ui.component.NewActionGrid
import com.jtech.zemer.ui.component.Material3MenuGroup
import com.jtech.zemer.ui.component.Material3MenuItemData
import com.jtech.zemer.ui.utils.navigateToArtist
import com.jtech.zemer.utils.VideoLinkBuilder
import com.jtech.zemer.utils.OfflineModeState
import com.jtech.zemer.utils.rememberEnumPreference
import com.jtech.zemer.utils.rememberPreference
import com.metrolist.innertube.YouTube
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.round

@Composable
fun PlayerMenu(
    mediaMetadata: MediaMetadata?,
    navController: NavController,
    playerBottomSheetState: BottomSheetState,
    isQueueTrigger: Boolean? = false,
    onShowDetailsDialog: () -> Unit,
    onDismiss: () -> Unit,
) {
    mediaMetadata ?: return
    val context = LocalContext.current
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val playerVolume = playerConnection.service.playerVolume.collectAsState()
    val isCasting by playerConnection.isCasting.collectAsState()
    val isStationBroadcast by playerConnection.isStationBroadcast.collectAsState()
    val remoteVolume by playerConnection.service.discoveryHandler.remoteVolume.collectAsState()
    val activityResultLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
    val coroutineScope = rememberCoroutineScope()
    val downloadUtil = LocalDownloadUtil.current

    val mediaStoreDownload by downloadUtil.getMediaStoreDownload(mediaMetadata.id)
        .collectAsState(initial = null)

    val librarySong by database.song(mediaMetadata.id).collectAsState(initial = null)

    val (blockVideos, _) = rememberPreference(BlockVideosKey, false)
    // Relay is source-opaque: the details sheet is DIRECT-only info (see ShowMediaInfo), so hide the row
    // entirely for a relay session. Normal (Google/anonymous) logins are unaffected.
    val playbackMode by rememberEnumPreference(PlaybackModeKey, PlaybackMode.DIRECT)

    val artists =
        remember(mediaMetadata.artists) {
            mediaMetadata.artists.filter { it.id != null }
        }

    var showChoosePlaylistDialog by rememberSaveable {
        mutableStateOf(false)
    }

    AddToPlaylistDialog(
        isVisible = showChoosePlaylistDialog,
        onGetSong = { playlist ->
            database.transaction {
                insert(mediaMetadata)
            }
            // Anonymous (pooled) sessions are local-only — only a personal account writes to remote.
            if (isPersonalAccountSignedIn) {
                coroutineScope.launch(Dispatchers.IO) {
                    playlist.playlist.browseId?.let { YouTube.addToPlaylist(it, mediaMetadata.id) }
                }
            }
            listOf(mediaMetadata.id)
        },
        onDismiss = {
            showChoosePlaylistDialog = false
        }
    )

    var showSelectArtistDialog by rememberSaveable {
        mutableStateOf(false)
    }
    var showReportDialog by remember { mutableStateOf(false) }

    if (showSelectArtistDialog) {
        SelectArtistDialog(
            artists = artists.map { ArtistChoice(it.id!!, it.name) },
            onDismiss = { showSelectArtistDialog = false },
            onArtistClick = { artistId ->
                // An episode's author is a podcast HOST channel — route to the podcast channel page.
                navController.navigateToArtist(artistId, isPodcastChannel = mediaMetadata.isEpisode)
                showSelectArtistDialog = false
                playerBottomSheetState.collapseSoft()
                onDismiss()
            },
        )
    }

    if (showReportDialog) {
        ReportContentDialog(
            subject = mapOf(
                "artistId" to (mediaMetadata.artists.firstOrNull()?.id ?: ""),
                "artistName" to (mediaMetadata.artists.firstOrNull()?.name ?: ""),
                "songId" to mediaMetadata.id,
                "songTitle" to mediaMetadata.title,
            ),
            onDismiss = { showReportDialog = false },
        )
    }

    var showPitchTempoDialog by rememberSaveable {
        mutableStateOf(false)
    }

    if (showPitchTempoDialog) {
        TempoPitchDialog(
            onDismiss = { showPitchTempoDialog = false },
        )
    }

    if (isQueueTrigger != true) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(top = 24.dp, bottom = 6.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.volume_up),
                contentDescription = null,
                modifier = Modifier.size(28.dp),
            )

            BigSeekBar(
                progressProvider = { if (isCasting) remoteVolume.toFloat() else playerVolume.value },
                onProgressChange = {
                    if (isCasting) playerConnection.service.discoveryHandler.setVolume(it.toDouble())
                    else playerConnection.service.playerVolume.value = it
                },
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp), // Reduced height from default (assumed ~48.dp) to 36.dp
            )
        }
    }

    Spacer(modifier = Modifier.height(20.dp))

    HorizontalDivider()

    Spacer(modifier = Modifier.height(12.dp))

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
                    // Hidden during a live station: the broadcast IS radio, and swapping it for a
                    // personal /radio queue from here would be a confusing silent exit.
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
                            context.toast(context.getString(R.string.starting_radio))
                            playerConnection.startRadioSeamlessly()
                            onDismiss()
                        }
                        // Also hidden for an episode (an episode must never seed music radio around
                        // its videoId, the ListQueue.episode rule) and in offline mode (the radio
                        // fill is an online function).
                    ).takeUnless { isStationBroadcast || mediaMetadata.isEpisode || OfflineModeState.enabled },
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
                                painter = painterResource(R.drawable.link),
                                contentDescription = null,
                                modifier = Modifier.size(28.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        text = stringResource(R.string.copy_link),
                        onClick = {
                            context.copyToClipboard(
                                context.getString(R.string.clip_label_song_link),
                                // Episode links carry the owning show so the receiver routes to the
                                // podcast screen, not the (artist-whitelisted) music play path.
                                VideoLinkBuilder.shareLink(mediaMetadata.id, mediaMetadata.isEpisode, mediaMetadata.album?.id),
                                R.string.link_copied,
                            )
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
                    // Not for episodes: an episode's "artist" is a podcast HOST channel, and the
                    // report would land in the music artist-report pipeline mislabeled.
                    if (!mediaMetadata.isEpisode) add(
                        Material3MenuItemData(
                            icon = { Icon(painterResource(R.drawable.warning), null, Modifier.size(24.dp)) },
                            title = { Text(stringResource(R.string.report_artist)) },
                            onClick = { showReportDialog = true },
                        )
                    )
                    if (artists.isNotEmpty()) add(
                        Material3MenuItemData(
                            icon = { Icon(painterResource(R.drawable.artist), null, Modifier.size(24.dp)) },
                            title = { Text(stringResource(R.string.view_artist)) },
                            onClick = {
                                // Only artists with a real id are navigable (a null/blank id would
                                // navigate to a dead "artist/" route).
                                val valid = mediaMetadata.artists.filter { !it.id.isNullOrBlank() }
                                when {
                                    valid.size == 1 -> {
                                        navController.navigateToArtist(valid[0].id, isPodcastChannel = mediaMetadata.isEpisode)
                                        playerBottomSheetState.collapseSoft()
                                        onDismiss()
                                    }
                                    valid.size > 1 -> showSelectArtistDialog = true
                                }
                            },
                        )
                    )
                    viewCollectionMenuItem(
                        isEpisode = mediaMetadata.isEpisode,
                        collectionId = mediaMetadata.album?.id,
                        navController = navController,
                        onDismiss = onDismiss,
                        beforeNavigate = { playerBottomSheetState.collapseSoft() },
                    )?.let { add(it) }
                    // Unified download row: persisted-or-live state, live progress, video-aware, and the
                    // menu stays open so it animates Download -> progress -> Remove. (DownloadMenuItems.kt)
                    // Option A: a video-capable item downloads its MUXED video (audio+video) so the
                    // Song/Video toggle then works offline (LOCAL) and never streams. currentItemIsVideo
                    // adds the playback-truth signal (musicVideoType) for items the classification missed.
                    // The muxed file still plays as ordinary audio in the music queue (Mp4Extractor) and
                    // the shared song row stays addable to music playlists. Gated on !blockVideos: a
                    // blocked user can never watch, so their download is plain audio (songRow would
                    // otherwise HIDE the row for a video item, leaving no download at all).
                    val currentItemIsVideo by playerConnection.currentItemIsVideo.collectAsState()
                    val songIsVideo = !blockVideos &&
                        (librarySong?.song?.isVideo == true || mediaMetadata.isVideo || currentItemIsVideo)
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
                                database.transaction { insert(mediaMetadata) }
                                val song = database.song(mediaMetadata.id).first()
                                song?.let {
                                    if (songIsVideo) {
                                        downloadUtil.downloadVideoToMediaStore(
                                            it,
                                            // The session's in-player quality pick (or the persisted
                                            // default target) — the download saves what the user is
                                            // watching, not silently the automatic rung.
                                            requestedQuality = playerConnection.downloadVideoQuality(it.id),
                                        )
                                    } else {
                                        downloadUtil.downloadToMediaStore(it)
                                    }
                                }
                            }
                        },
                        onCancel = { coroutineScope.launch { downloadUtil.cancelMediaStoreDownload(mediaMetadata.id) } },
                        onRetry = { downloadUtil.retryMediaStoreDownload(mediaMetadata.id) },
                        onRemove = { coroutineScope.launch { downloadUtil.removeDownload(mediaMetadata.id) } },
                    )?.let { add(it) }
                    if (playbackMode != PlaybackMode.RELAY) {
                        add(
                            Material3MenuItemData(
                                icon = { Icon(painterResource(R.drawable.info), null, Modifier.size(24.dp)) },
                                title = { Text(stringResource(R.string.details)) },
                                onClick = {
                                    onShowDetailsDialog()
                                    onDismiss()
                                },
                            )
                        )
                    }
                    if (isQueueTrigger != true) {
                        add(
                            Material3MenuItemData(
                                icon = { Icon(painterResource(R.drawable.equalizer), null, Modifier.size(24.dp)) },
                                title = { Text(stringResource(R.string.equalizer)) },
                                onClick = {
                                    val intent = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
                                        putExtra(AudioEffect.EXTRA_AUDIO_SESSION, playerConnection.player.audioSessionId)
                                        putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                                        putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
                                    }
                                    if (intent.resolveActivity(context.packageManager) != null) {
                                        activityResultLauncher.launch(intent)
                                    }
                                    onDismiss()
                                },
                            )
                        )
                        add(
                            Material3MenuItemData(
                                icon = { Icon(painterResource(R.drawable.tune), null, Modifier.size(24.dp)) },
                                title = { Text(stringResource(R.string.advanced)) },
                                onClick = { showPitchTempoDialog = true },
                            )
                        )
                    }
                },
            )
        }
    }
}

@Composable
fun TempoPitchDialog(onDismiss: () -> Unit) {
    val playerConnection = LocalPlayerConnection.current ?: return
    var tempo by remember {
        mutableFloatStateOf(playerConnection.player.playbackParameters.speed)
    }
    var transposeValue by remember {
        mutableIntStateOf(round(12 * log2(playerConnection.player.playbackParameters.pitch)).toInt())
    }
    val updatePlaybackParameters = {
        playerConnection.player.playbackParameters =
            PlaybackParameters(tempo, 2f.pow(transposeValue.toFloat() / 12))
    }

    DefaultDialog(
        onDismiss = onDismiss,
        horizontalAlignment = Alignment.Start,
        title = {
            Text(stringResource(R.string.tempo_and_pitch))
        },
        content = {
            ValueAdjuster(
                icon = R.drawable.speed,
                currentValue = tempo,
                values = (0..35).map { round((0.25f + it * 0.05f) * 100) / 100 },
                onValueUpdate = {
                    tempo = it
                    updatePlaybackParameters()
                },
                valueText = { "x$it" },
                modifier = Modifier.padding(bottom = 12.dp),
            )
            ValueAdjuster(
                icon = R.drawable.discover_tune,
                currentValue = transposeValue,
                values = (-12..12).toList(),
                onValueUpdate = {
                    transposeValue = it
                    updatePlaybackParameters()
                },
                valueText = { "${if (it > 0) "+" else ""}$it" },
            )
        },
        buttons = {
            TextButton(
                onClick = {
                    tempo = 1f
                    transposeValue = 0
                    updatePlaybackParameters()
                },
            ) {
                Text(stringResource(R.string.reset))
            }

            TextButton(
                onClick = onDismiss,
            ) {
                Text(stringResource(android.R.string.ok))
            }
        },
    )
}

@Composable
fun <T> ValueAdjuster(
    @DrawableRes icon: Int,
    currentValue: T,
    values: List<T>,
    onValueUpdate: (T) -> Unit,
    valueText: (T) -> String,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(28.dp),
        )

        IconButton(
            enabled = currentValue != values.first(),
            onClick = {
                onValueUpdate(values[values.indexOf(currentValue) - 1])
            },
        ) {
            Icon(
                painter = painterResource(R.drawable.remove),
                contentDescription = null,
            )
        }

        Text(
            text = valueText(currentValue),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(80.dp),
        )

        IconButton(
            enabled = currentValue != values.last(),
            onClick = {
                onValueUpdate(values[values.indexOf(currentValue) + 1])
            },
        ) {
            Icon(
                painter = painterResource(R.drawable.add),
                contentDescription = null,
            )
        }
    }
}
