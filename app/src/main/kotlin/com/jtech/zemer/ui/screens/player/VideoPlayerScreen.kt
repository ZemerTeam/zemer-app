package com.jtech.zemer.ui.screens.player

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Rational
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Surface
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.media3.common.C
import androidx.media3.common.MediaMetadata
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.exoplayer.ExoPlayer
import com.jtech.zemer.LocalDatabase
import com.jtech.zemer.LocalPlayerConnection
import com.jtech.zemer.R
import com.jtech.zemer.ui.component.DefaultDialog
import com.jtech.zemer.constants.AudioQuality
import com.jtech.zemer.constants.BlockVideosKey
import androidx.compose.runtime.collectAsState
import com.jtech.zemer.LocalDownloadUtil
import com.jtech.zemer.db.entities.Song
import com.jtech.zemer.db.entities.SongEntity
import com.jtech.zemer.playback.DownloadStatus
import com.jtech.zemer.ui.component.rememberSongDownloadProgress
import com.jtech.zemer.ui.component.rememberSongDownloadStatus
import com.jtech.zemer.utils.UrlValidator
import com.jtech.zemer.utils.VideoLinkBuilder
import com.jtech.zemer.utils.YTPlayerUtils
import com.jtech.zemer.utils.rememberPreference
import io.sanghun.compose.video.RepeatMode
import io.sanghun.compose.video.VideoPlayer
import io.sanghun.compose.video.controller.VideoPlayerControllerConfig
import io.sanghun.compose.video.uri.VideoPlayerMediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoPlayerScreen(
    navController: NavController,
    videoId: String,
    title: String? = null,
    artist: String? = null,
) {
    val context = LocalContext.current
    val (blockVideos, _) = rememberPreference(BlockVideosKey, false)

    // Check if videos are blocked and show blocking message
    if (blockVideos) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_video_hd),
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.videos_blocked),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.videos_blocked_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            TextButton(
                onClick = { navController.navigateUp() }
            ) {
                Text(stringResource(R.string.onboarding_back))
            }
        }
        return
    }

    val activity = context as? Activity
    val clipboard = remember { context.getSystemService(ClipboardManager::class.java) }
    val connectivityManager = remember { context.getSystemService(ConnectivityManager::class.java) }
    val database = LocalDatabase.current
    val downloadUtil = LocalDownloadUtil.current
    val scope = rememberCoroutineScope()
    val downloadedSong by remember(videoId) { database.song(videoId) }.collectAsState(initial = null)
    val videoDownloadStatus = rememberSongDownloadStatus(videoId, downloadedSong?.song?.isDownloaded == true)
    val videoDownloadProgress = rememberSongDownloadProgress(videoId, downloadedSong?.song?.isDownloaded == true)
    val playerConnection = LocalPlayerConnection.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var videoItem by remember { mutableStateOf<VideoPlayerMediaItem.NetworkMediaItem?>(null) }
    var playerInstance by remember { mutableStateOf<ExoPlayer?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var showDownloadDialog by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showQualityDialog by remember { mutableStateOf(false) }
    var currentTitle by remember(videoId, title) { mutableStateOf(title?.takeIf { it.isNotBlank() }) }
    var reloadKey by remember { mutableStateOf(0) }
    var availableQualities by remember { mutableStateOf<List<QualityOption>>(emptyList()) }
    var selectedQualityId by remember { mutableStateOf("auto") }
    var playbackInfo by remember { mutableStateOf<String?>(null) }
    var isInPipMode by remember { mutableStateOf(activity?.isInPictureInPictureMode == true) }
    var artistName by remember(videoId, artist) { mutableStateOf(artist?.takeIf { it.isNotBlank() }) }
    var positionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }
    var isPlaying by remember { mutableStateOf(false) }
    var isScrubbing by remember { mutableStateOf(false) }
    var isFullscreen by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(true) }
    var lastInteraction by remember { mutableStateOf(System.currentTimeMillis()) }
    var videoBottomPx by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(videoId) {
        val mappedSong = withContext(Dispatchers.IO) {
            val direct = database.getSongById(videoId)
            if (direct != null) return@withContext direct
            val setVideo = database.getSetVideoId(videoId)?.setVideoId
            if (setVideo != null) database.getSongById(setVideo) else null
        }
        mappedSong?.let { song ->
            if (currentTitle.isNullOrBlank()) {
                currentTitle = song.title
            }
            if (artistName.isNullOrBlank()) {
                val artistDisplay = song.artists.joinToString(" • ") { it.name }
                artistName = artistDisplay.ifBlank { null }
            }
        }
    }

    DisposableEffect(Unit) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Video takes over the phone's audio, so silence anything else: pause the local player, and — while
    // casting — pause the receiver too (the local pause is a no-op there; the audio is on the receiver,
    // driven through discoveryHandler). Resume the receiver when the screen closes, but only if we're the
    // one that paused an active cast and it's still connected.
    DisposableEffect(playerConnection) {
        playerConnection?.player?.pause()
        val pausedCast = playerConnection?.pauseCastForVideo() == true
        onDispose {
            playerConnection?.resumeCastAfterVideo(pausedCast)
        }
    }

    DisposableEffect(playerInstance) {
        val player = playerInstance ?: return@DisposableEffect onDispose { }
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onTracksChanged(tracks: Tracks) {
                val qualities = tracks.groups
                    .filter { it.type == C.TRACK_TYPE_VIDEO }
                    .flatMap { group ->
                        val mtg = group.mediaTrackGroup
                        (0 until group.length).map { index ->
                            val format = group.getTrackFormat(index)
                            val height = format.height.takeIf { it > 0 }
                            val bitrate = format.bitrate.takeIf { it > 0 }
                            val label = buildString {
                                if (height != null) append("${height}p ")
                                if (bitrate != null) append("(${bitrate / 1000}kbps) ")
                                if (!format.codecs.isNullOrBlank()) append(format.codecs)
                            }.ifBlank { "Video" }
                            QualityOption(
                                id = "${mtg.hashCode()}_$index",
                                label = label.trim(),
                                height = height,
                                width = format.width.takeIf { it > 0 },
                                bitrate = bitrate,
                                codecs = format.codecs,
                                mimeType = format.sampleMimeType,
                                group = mtg,
                                trackIndex = index
                            )
                        }
                    }
                    .sortedByDescending { it.height ?: 0 }
                availableQualities = qualities

                val currentOverrideEntry = player.trackSelectionParameters.overrides.entries.firstOrNull { entry ->
                    qualities.any { it.group == entry.key }
                }
                selectedQualityId = currentOverrideEntry?.let { entry ->
                    val match = qualities.firstOrNull { opt ->
                        opt.group == entry.key && entry.value.trackIndices.contains(opt.trackIndex)
                    }
                    match?.id
                } ?: "auto"

                val format = player.videoFormat
                playbackInfo = format?.let { f ->
                    val resolution = if (f.width > 0 && f.height > 0) "${f.width}x${f.height}" else null
                    val bitrateKbps = f.bitrate.takeIf { it > 0 }?.div(1000)
                    val codec = when {
                        !f.codecs.isNullOrBlank() -> f.codecs
                        !f.sampleMimeType.isNullOrBlank() -> f.sampleMimeType
                        else -> null
                    }
                    buildString {
                        resolution?.let { append(it) }
                        bitrateKbps?.let {
                            if (isNotEmpty()) append(" • ")
                            append("${it}kbps")
                        }
                        codec?.let {
                            if (isNotEmpty()) append(" • ")
                            append(it)
                        }
                    }.ifBlank { null }
                }
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    LaunchedEffect(playerConnection?.mediaMetadata?.value, videoId) {
        val meta = playerConnection?.mediaMetadata?.value ?: return@LaunchedEffect
        if (meta.id == videoId || meta.setVideoId == videoId) {
            currentTitle = meta.title
            val artistDisplay = meta.artists.joinToString(" • ") { it.name }
            artistName = artistDisplay.ifBlank { artistName }
        }
    }

    val maxVideoBitrateKbps = remember(connectivityManager) {
        if (connectivityManager?.isActiveNetworkMetered == true) 1500 else 6000
    }
    val supportsPip = remember(activity) {
        activity?.packageManager?.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE) == true
    }
    val canEnterPip by remember {
        derivedStateOf {
            supportsPip &&
                lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
                videoItem != null &&
                loadError == null
        }
    }

    DisposableEffect(lifecycleOwner, activity) {
        val observer = LifecycleEventObserver { _, _ ->
            isInPipMode = activity?.isInPictureInPictureMode == true
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(activity) {
        onDispose { activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }

    LaunchedEffect(videoId, maxVideoBitrateKbps, reloadKey) {
        isLoading = true
        loadError = null
        videoItem = null

        val result = withContext(Dispatchers.IO) {
            val cm = connectivityManager ?: error("No connectivity manager")
            YTPlayerUtils.playerResponseForPlayback(
                videoId = videoId,
                audioQuality = AudioQuality.HIGH,
                connectivityManager = cm,
                preferVideo = true,
                maxVideoBitrateKbps = maxVideoBitrateKbps,
            )
        }

        result.onSuccess { playback ->
            val validatedUrl = UrlValidator.validateAndParseUrl(playback.streamUrl)?.toString()
            if (validatedUrl == null) {
                loadError = "Invalid stream URL"
                isLoading = false
                return@onSuccess
            }

            val titleFromPlayback = playback.videoDetails?.title?.takeIf { it.isNotBlank() }
            val artistFromPlayback = playback.videoDetails?.author?.takeIf { it.isNotBlank() }
                ?: playback.videoDetails?.channelId
            val resolvedTitle = titleFromPlayback ?: currentTitle ?: videoId
            currentTitle = resolvedTitle
            if (!artistFromPlayback.isNullOrBlank()) {
                artistName = artistFromPlayback
            } else if (artistName.isNullOrBlank()) {
                artistName = playback.videoDetails?.channelId
            }
            val thumbnail = playback.videoDetails?.thumbnail?.thumbnails?.lastOrNull()?.url

            val mediaMetadata = MediaMetadata.Builder()
                .setTitle(resolvedTitle)
                .apply {
                    thumbnail?.let { setArtworkUri(Uri.parse(it)) }
                    artistName?.let { setArtist(it) }
                }
                .build()

            videoItem = VideoPlayerMediaItem.NetworkMediaItem(
                url = validatedUrl,
                mediaMetadata = mediaMetadata,
                mimeType = playback.format.mimeType ?: "",
                drmConfiguration = null
            )
            isLoading = false
        }.onFailure {
            loadError = it.localizedMessage ?: "Playback error"
            isLoading = false
        }
    }

    // Unified video download: go through the same MediaStoreDownloadManager every other surface uses
    // (tracked state, live progress, correct mediaStoreUri, retry/cancel) — not a per-screen
    // re-implementation. The selected bitrate is forwarded through the manager.
    val downloadVideo: (Int) -> Unit = { targetBitrate ->
        showDownloadDialog = false
        scope.launch {
            val existing = withContext(Dispatchers.IO) { database.getSongById(videoId) }
            val songToDownload = existing?.copy(song = existing.song.copy(isVideo = true)) ?: Song(
                song = SongEntity(
                    id = videoId,
                    title = currentTitle ?: videoId,
                    isVideo = true,
                ),
                artists = emptyList(),
            )
            downloadUtil.downloadVideoToMediaStore(songToDownload, targetBitrate)
        }
    }

    LaunchedEffect(playerInstance) {
        val player = playerInstance ?: return@LaunchedEffect
        while (isActive) {
            if (!isScrubbing) {
                positionMs = player.currentPosition
            }
            val d = player.duration
            if (d > 0) durationMs = d
            isPlaying = player.isPlaying
            kotlinx.coroutines.delay(500)
        }
    }

    val enterPip: () -> Unit = pip@{
        val act = activity ?: return@pip
        if (!canEnterPip) return@pip
        val params = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
        } else {
            null
        }
        try {
            @Suppress("DEPRECATION")
            val entered = if (params != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                act.enterPictureInPictureMode(params)
            } else {
                act.enterPictureInPictureMode()
                true
            }
            if (!entered) {
                Toast.makeText(context, context.getString(R.string.pip_unable), Toast.LENGTH_SHORT).show()
            }
        } catch (e: IllegalStateException) {
            Toast.makeText(context, context.getString(R.string.pip_unavailable, e.localizedMessage), Toast.LENGTH_SHORT).show()
        }
    }

    val markInteraction: () -> Unit = {
        showControls = true
        lastInteraction = System.currentTimeMillis()
    }

    val togglePlayPause: () -> Unit = {
        playerInstance?.let { player ->
            if (player.isPlaying) {
                player.pause()
                showControls = true
            } else {
                player.play()
                markInteraction()
            }
        }
    }

    val seekByMs: (Long) -> Unit = { delta ->
        playerInstance?.let { player ->
            val durationLimit = if (durationMs > 0) durationMs else Long.MAX_VALUE
            val newPos = (player.currentPosition + delta).coerceIn(0, durationLimit)
            player.seekTo(newPos)
            positionMs = newPos
            lastInteraction = System.currentTimeMillis()
            showControls = true
        }
    }

    val toggleFullscreen: () -> Unit = fullscreen@{
        val act = activity ?: return@fullscreen
        val next = !isFullscreen
        isFullscreen = next
        act.requestedOrientation = if (next) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    val density = LocalDensity.current
    val dragSkipThresholdPx = remember(density) { with(density) { 80.dp.toPx() } }
    var dragAccum by remember { mutableStateOf(0f) }

    LaunchedEffect(isPlaying) {
        if (!isPlaying) {
            showControls = true
        } else {
            markInteraction()
        }
    }

    LaunchedEffect(showControls, lastInteraction, isPlaying) {
        if (!showControls) return@LaunchedEffect
        if (!isPlaying) return@LaunchedEffect
        kotlinx.coroutines.delay(4000)
        if (System.currentTimeMillis() - lastInteraction >= 3800 && isPlaying) {
            showControls = false
        }
    }

    BackHandler(enabled = !isInPipMode) {
        navController.popBackStack()
    }

    Scaffold(containerColor = Color.Black) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color.Black)
        ) {
            when {
                isLoading -> {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                loadError != null -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(text = loadError ?: stringResource(R.string.video_playback_error), color = Color.White)
                        TextButton(onClick = { reloadKey++ }) {
                            Text(stringResource(R.string.retry), color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }

                videoItem != null -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp)
                            .padding(vertical = if (isInPipMode) 0.dp else 12.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            tonalElevation = 6.dp,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                            modifier = Modifier
                                .align(Alignment.Center)
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                                .onGloballyPositioned { coords ->
                                    videoBottomPx = coords.boundsInParent().bottom.toInt()
                                }
                        ) {
                            VideoPlayer(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pointerInput(playerInstance) {
                                        detectTapGestures {
                                            if (!showControls) {
                                                markInteraction()
                                            } else {
                                                togglePlayPause()
                                            }
                                        }
                                    }
                                    .pointerInput(playerInstance, durationMs) {
                                        detectDragGestures(
                                            onDrag = { _, dragAmount ->
                                                dragAccum += dragAmount.x
                                                markInteraction()
                                            },
                                            onDragEnd = {
                                                when {
                                                    dragAccum > dragSkipThresholdPx -> seekByMs(10_000)
                                                    dragAccum < -dragSkipThresholdPx -> seekByMs(-10_000)
                                                    else -> togglePlayPause()
                                                }
                                                dragAccum = 0f
                                            },
                                            onDragCancel = {
                                                dragAccum = 0f
                                            }
                                        )
                                    },
                                mediaItems = listOf(videoItem!!),
                                handleLifecycle = false,
                                autoPlay = true,
                                usePlayerController = false,
                                controllerConfig = VideoPlayerControllerConfig.Default,
                                repeatMode = RepeatMode.NONE,
                                enablePip = false,
                                enablePipWhenBackPressed = false,
                                playerInstance = { playerInstance = this }
                            )
                        }

                        if (!isInPipMode) {
                            val outlineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.9f)
                            val pillBg = MaterialTheme.colorScheme.surface
                            val pillBorder = outlineColor
                            val chipRowOffsetPx = videoBottomPx?.plus(with(density) { 8.dp.roundToPx() })

                            AnimatedVisibility(
                                visible = showControls,
                                enter = fadeIn(),
                                exit = fadeOut(),
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = 0.dp)
                            ) {
                                Surface(
                                    shape = RectangleShape,
                                    color = Color.Black.copy(alpha = 0.8f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .padding(horizontal = 16.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        IconButton(
                                            onClick = {
                                                markInteraction()
                                                navController.popBackStack()
                                            },
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(Color.White.copy(alpha = 0.08f))
                                        ) {
                                            Icon(
                                                painter = painterResource(R.drawable.arrow_back),
                                                contentDescription = null,
                                                tint = Color.White
                                            )
                                        }
                                        Text(
                                            text = currentTitle ?: videoId,
                                            color = Color.White,
                                            style = MaterialTheme.typography.titleMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (supportsPip) {
                                            IconButton(
                                                onClick = {
                                                    markInteraction()
                                                    enterPip()
                                                },
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .clip(CircleShape)
                                                    .background(Color.White.copy(alpha = 0.08f))
                                            ) {
                                                Icon(
                                                    painter = painterResource(R.drawable.ic_pip),
                                                    contentDescription = null,
                                                    tint = Color.White
                                                )
                                            }
                                        }
                                        IconButton(
                                            onClick = {
                                                markInteraction()
                                                val clip = ClipData.newPlainText(context.getString(R.string.clip_label_video_link), VideoLinkBuilder.videoLink(videoId))
                                                clipboard?.setPrimaryClip(clip)
                                                Toast.makeText(context, R.string.link_copied, Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(Color.White.copy(alpha = 0.08f))
                                        ) {
                                            Icon(
                                                painter = painterResource(R.drawable.link),
                                                contentDescription = null,
                                                tint = Color.White
                                            )
                                        }
                                    }
                                }
                            }

                            if (chipRowOffsetPx != null) {
                                AnimatedVisibility(
                                    visible = showControls,
                                enter = fadeIn(),
                                exit = fadeOut(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .offset { IntOffset(0, chipRowOffsetPx) }
                                        .padding(horizontal = 8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(
                                            verticalArrangement = Arrangement.spacedBy(2.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(
                                                text = currentTitle ?: videoId,
                                                color = Color.White,
                                                style = MaterialTheme.typography.titleMedium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = artistName ?: stringResource(R.string.unknown_artist),
                                                color = Color.LightGray,
                                                style = MaterialTheme.typography.labelMedium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            val leftShape = RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp, topEnd = 6.dp, bottomEnd = 6.dp)
                                            val rightShape = RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp, topEnd = 24.dp, bottomEnd = 24.dp)
                                            Surface(
                                                shape = leftShape,
                                                color = pillBg,
                                                border = BorderStroke(1.dp, pillBorder),
                                                modifier = Modifier.size(44.dp)
                                            ) {
                                                IconButton(onClick = {
                                                    markInteraction()
                                                    when (videoDownloadStatus) {
                                                        DownloadStatus.DOWNLOADED -> scope.launch { downloadUtil.removeDownload(videoId) }
                                                        DownloadStatus.DOWNLOADING -> downloadUtil.cancelMediaStoreDownload(videoId)
                                                        DownloadStatus.NOT_DOWNLOADED -> showDownloadDialog = true
                                                    }
                                                }) {
                                                    when (videoDownloadStatus) {
                                                        DownloadStatus.DOWNLOADED -> Icon(
                                                            painter = painterResource(R.drawable.offline),
                                                            contentDescription = stringResource(R.string.remove_download),
                                                        )
                                                        DownloadStatus.DOWNLOADING -> if (videoDownloadProgress > 0f) {
                                                            CircularProgressIndicator(
                                                                progress = { videoDownloadProgress },
                                                                modifier = Modifier.size(24.dp),
                                                                strokeWidth = 2.dp,
                                                            )
                                                        } else {
                                                            CircularProgressIndicator(
                                                                modifier = Modifier.size(24.dp),
                                                                strokeWidth = 2.dp,
                                                            )
                                                        }
                                                        DownloadStatus.NOT_DOWNLOADED -> Icon(
                                                            painter = painterResource(R.drawable.download),
                                                            contentDescription = stringResource(R.string.action_download),
                                                        )
                                                    }
                                                }
                                            }
                                            Surface(
                                                shape = rightShape,
                                                color = pillBg,
                                                border = BorderStroke(1.dp, pillBorder),
                                                modifier = Modifier.size(44.dp)
                                            ) {
                                                IconButton(onClick = {
                                                    markInteraction()
                                                    showQualityDialog = true
                                                }) {
                                                    Icon(
                                                        painter = painterResource(R.drawable.ic_video_hd),
                                                        contentDescription = stringResource(R.string.video_quality)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            AnimatedVisibility(
                                visible = showControls,
                                enter = fadeIn(),
                                exit = fadeOut(),
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 12.dp)
                            ) {
                                val sliderValue =
                                    if (durationMs > 0) positionMs.toFloat() / durationMs.toFloat() else 0f
                                val durationText = if (durationMs > 0) formatTime(durationMs) else "--:--"
                                val positionText = formatTime(positionMs)

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 12.dp)
                                        .clip(RoundedCornerShape(18.dp))
                                        .border(
                                            width = 1.dp,
                                            color = outlineColor,
                                            shape = RoundedCornerShape(18.dp)
                                        )
                                        .background(Color.Black.copy(alpha = 0.6f))
                                        .padding(horizontal = 12.dp, vertical = 12.dp)
                                ) {
                                    val buttonColors = IconButtonDefaults.outlinedIconButtonColors(
                                        contentColor = Color.White,
                                        containerColor = Color.Black.copy(alpha = 0.35f)
                                    )
                                    val buttonBorder = BorderStroke(1.dp, outlineColor)

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceEvenly,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedIconButton(
                                            onClick = { showSpeedDialog = true },
                                            modifier = Modifier.size(36.dp),
                                            colors = buttonColors,
                                            border = buttonBorder,
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Icon(
                                                painter = painterResource(R.drawable.ic_speedometer),
                                                contentDescription = stringResource(R.string.video_playback_speed)
                                            )
                                        }
                                        OutlinedIconButton(
                                            onClick = { seekByMs(-10_000) },
                                            modifier = Modifier.size(52.dp),
                                            colors = buttonColors,
                                            border = buttonBorder,
                                            shape = RoundedCornerShape(18.dp)
                                        ) {
                                            Icon(
                                                painter = painterResource(R.drawable.skip_previous),
                                                contentDescription = stringResource(R.string.cd_previous)
                                            )
                                        }
                                        OutlinedIconButton(
                                            onClick = togglePlayPause,
                                            modifier = Modifier.size(64.dp),
                                            colors = buttonColors,
                                            border = buttonBorder,
                                            shape = RoundedCornerShape(22.dp)
                                        ) {
                                            Icon(
                                                painter = painterResource(if (isPlaying) R.drawable.pause else R.drawable.play),
                                                contentDescription = if (isPlaying) stringResource(R.string.cd_pause) else stringResource(R.string.play)
                                            )
                                        }
                                        OutlinedIconButton(
                                            onClick = { seekByMs(10_000) },
                                            modifier = Modifier.size(52.dp),
                                            colors = buttonColors,
                                            border = buttonBorder,
                                            shape = RoundedCornerShape(18.dp)
                                        ) {
                                            Icon(
                                                painter = painterResource(R.drawable.skip_next),
                                                contentDescription = stringResource(R.string.cd_next)
                                            )
                                        }
                                        OutlinedIconButton(
                                            onClick = toggleFullscreen,
                                            modifier = Modifier.size(36.dp),
                                            colors = buttonColors,
                                            border = buttonBorder,
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Icon(
                                                painter = painterResource(R.drawable.ic_fullscreen),
                                                contentDescription = stringResource(R.string.cd_fullscreen)
                                            )
                                        }
                                    }

                                    Slider(
                                        value = sliderValue,
                                        onValueChange = { value ->
                                            if (durationMs > 0) {
                                                isScrubbing = true
                                                positionMs = (durationMs * value).toLong().coerceIn(0, durationMs)
                                            }
                                            lastInteraction = System.currentTimeMillis()
                                        },
                                        onValueChangeFinished = {
                                            if (durationMs > 0) {
                                                playerInstance?.seekTo(positionMs)
                                            }
                                            isScrubbing = false
                                        },
                                        enabled = durationMs > 0,
                                        colors = SliderDefaults.colors(
                                            thumbColor = MaterialTheme.colorScheme.primary,
                                            activeTrackColor = MaterialTheme.colorScheme.primary,
                                            inactiveTrackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                        )
                                    )

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(positionText, color = Color.White, style = MaterialTheme.typography.labelSmall)
                                        Text(durationText, color = Color.White, style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDownloadDialog) {
        DefaultDialog(
            onDismiss = { showDownloadDialog = false },
            horizontalAlignment = Alignment.Start,
            title = { Text(stringResource(R.string.video_download_title)) },
            content = {
                Text(stringResource(R.string.video_choose_quality))
                Spacer(modifier = Modifier.height(8.dp))
                if (availableQualities.isNotEmpty()) {
                    availableQualities.forEach { quality ->
                        val bitrateKbps = quality.bitrate?.div(1000) ?: 4000
                        TextButton(
                            onClick = { downloadVideo(bitrateKbps) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = quality.label,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                } else {
                    // Fallback if qualities not yet loaded
                    Text(stringResource(R.string.video_loading_qualities), style = MaterialTheme.typography.bodySmall)
                }
            },
            buttons = {
                TextButton(onClick = { showDownloadDialog = false }) {
                    Text(stringResource(R.string.close))
                }
            }
        )
    }

    if (showSpeedDialog) {
        val speeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
        DefaultDialog(
            onDismiss = { showSpeedDialog = false },
            horizontalAlignment = Alignment.Start,
            title = { Text(stringResource(R.string.video_playback_speed)) },
            content = {
                speeds.forEach { speed ->
                    TextButton(onClick = {
                        playerInstance?.setPlaybackSpeed(speed)
                        showSpeedDialog = false
                    }) {
                        Text(if (speed == 1f) stringResource(R.string.video_speed_normal) else "${speed}x")
                    }
                }
            },
            buttons = {
                TextButton(onClick = { showSpeedDialog = false }) { Text(stringResource(R.string.close)) }
            }
        )
    }

    if (showQualityDialog) {
        DefaultDialog(
            onDismiss = { showQualityDialog = false },
            horizontalAlignment = Alignment.Start,
            title = { Text(stringResource(R.string.video_quality)) },
            content = {
                Text(
                    text = if (selectedQualityId == "auto") stringResource(R.string.video_quality_current_auto) else availableQualities.firstOrNull { it.id == selectedQualityId }?.label
                        ?: stringResource(R.string.video_quality_current_auto),
                    style = MaterialTheme.typography.labelMedium
                )
                TextButton(onClick = {
                    playerInstance?.let { player ->
                        val params = player.trackSelectionParameters
                            .buildUpon()
                            .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                            .build()
                        player.trackSelectionParameters = params
                    }
                    selectedQualityId = "auto"
                    showQualityDialog = false
                }) {
                    Text(stringResource(R.string.video_quality_auto))
                }
                availableQualities.forEach { option ->
                    TextButton(onClick = {
                        playerInstance?.let { player ->
                            val builder = player.trackSelectionParameters
                                .buildUpon()
                                .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                                .setOverrideForType(
                                    TrackSelectionOverride(option.group, listOf(option.trackIndex))
                                )
                            player.trackSelectionParameters = builder.build()
                            selectedQualityId = option.id
                        }
                        showQualityDialog = false
                    }) {
                        Text(option.label.ifBlank { stringResource(R.string.video_quality_track, option.trackIndex + 1) })
                    }
                }
            },
            buttons = {
                TextButton(onClick = { showQualityDialog = false }) { Text(stringResource(R.string.close)) }
            }
        )
    }
}

private data class QualityOption(
    val id: String,
    val label: String,
    val height: Int?,
    val width: Int?,
    val bitrate: Int?,
    val codecs: String?,
    val mimeType: String?,
    val group: TrackGroup,
    val trackIndex: Int,
)

@Composable
private fun formatTime(ms: Long): String {
    val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(ms.coerceAtLeast(0))
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
