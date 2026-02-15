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
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.font.FontWeight
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
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.common.MediaItem
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.YouTubeClient
import androidx.media3.ui.PlayerView
import android.view.ViewGroup
import androidx.compose.ui.viewinterop.AndroidView
import com.jtech.zemer.LocalDatabase
import com.jtech.zemer.LocalPlayerConnection
import com.jtech.zemer.R
import com.jtech.zemer.constants.AudioQuality
import com.jtech.zemer.constants.BlockVideosKey
import com.jtech.zemer.db.entities.SongEntity
import com.jtech.zemer.utils.MediaStoreHelper
import com.jtech.zemer.utils.UrlValidator
import com.jtech.zemer.utils.VideoLinkBuilder
import com.jtech.zemer.utils.YTPlayerUtils
import com.jtech.zemer.utils.rememberPreference
import com.metrolist.innertube.utils.ResilientDns
import io.sanghun.compose.video.RepeatMode
import io.sanghun.compose.video.VideoPlayer
import io.sanghun.compose.video.controller.VideoPlayerControllerConfig
import io.sanghun.compose.video.uri.VideoPlayerMediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer
import java.time.LocalDateTime
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
    val scope = rememberCoroutineScope()
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
    // Adaptive playback data for 1080p+ support
    var adaptiveData by remember { mutableStateOf<YTPlayerUtils.AdaptiveVideoData?>(null) }
    var adaptiveQualities by remember { mutableStateOf<List<YTPlayerUtils.VideoQualityInfo>>(emptyList()) }
    var selectedQualityHeight by remember { mutableStateOf(1080) } // Default to 1080p
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

    val httpClient = remember {
        OkHttpClient.Builder()
            .dns(ResilientDns())
            .build()
    }

    DisposableEffect(Unit) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Pause any music playback while the user is watching video
    LaunchedEffect(Unit) {
        playerConnection?.player?.pause()
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

    LaunchedEffect(videoId, maxVideoBitrateKbps, reloadKey, selectedQualityHeight) {
        isLoading = true
        loadError = null
        videoItem = null
        adaptiveData = null

        // Try adaptive playback first (for 1080p+ support)
        val adaptiveResult = withContext(Dispatchers.IO) {
            YTPlayerUtils.getAdaptiveVideoData(videoId, targetHeight = selectedQualityHeight)
        }

        adaptiveResult.onSuccess { adaptive ->
            val videoUrl = UrlValidator.validateAndParseUrl(adaptive.videoUrl)?.toString()
            val audioUrl = UrlValidator.validateAndParseUrl(adaptive.audioUrl)?.toString()

            if (videoUrl != null && audioUrl != null) {
                adaptiveData = adaptive
                adaptiveQualities = adaptive.availableQualities

                val titleFromPlayback = adaptive.videoDetails?.title?.takeIf { it.isNotBlank() }
                val resolvedTitle = titleFromPlayback ?: currentTitle ?: videoId
                currentTitle = resolvedTitle

                // Get artist name, avoiding channel IDs (which start with "UC" and have no spaces)
                val authorFromPlayback = adaptive.videoDetails?.author?.takeIf {
                    it.isNotBlank() && !it.isChannelId()
                }
                val artistFromTitle = resolvedTitle.extractArtistFromTitle()

                if (artistName.isNullOrBlank() || artistName?.isChannelId() == true) {
                    artistName = authorFromPlayback ?: artistFromTitle ?: "Unknown Artist"
                }
                val thumbnail = adaptive.videoDetails?.thumbnail?.thumbnails?.lastOrNull()?.url

                val mediaMetadata = MediaMetadata.Builder()
                    .setTitle(resolvedTitle)
                    .apply {
                        thumbnail?.let { setArtworkUri(Uri.parse(it)) }
                        artistName?.let { setArtist(it) }
                    }
                    .build()

                // Use video URL as placeholder - we'll set MergingMediaSource after player is created
                videoItem = VideoPlayerMediaItem.NetworkMediaItem(
                    url = videoUrl,
                    mediaMetadata = mediaMetadata,
                    mimeType = adaptive.videoFormat.mimeType ?: "video/mp4",
                    drmConfiguration = null
                )
                isLoading = false
                return@LaunchedEffect
            }
        }

        // Fallback to progressive playback (limited to 720p)
        // Uses isVideoFallback=true to skip TVHTML5 (already tried for adaptive)
        val result = withContext(Dispatchers.IO) {
            val cm = connectivityManager ?: error("No connectivity manager")
            YTPlayerUtils.playerResponseForPlayback(
                videoId = videoId,
                audioQuality = AudioQuality.HIGH,
                connectivityManager = cm,
                preferVideo = true,
                maxVideoBitrateKbps = maxVideoBitrateKbps,
                isVideoFallback = true,
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
            val resolvedTitle = titleFromPlayback ?: currentTitle ?: videoId
            currentTitle = resolvedTitle

            // Get artist name, avoiding channel IDs
            val authorFromPlayback = playback.videoDetails?.author?.takeIf {
                it.isNotBlank() && !it.isChannelId()
            }
            val artistFromTitle = resolvedTitle.extractArtistFromTitle()

            if (artistName.isNullOrBlank() || artistName?.isChannelId() == true) {
                artistName = authorFromPlayback ?: artistFromTitle ?: "Unknown Artist"
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

    val mediaStoreHelper = remember { MediaStoreHelper(context) }

    val downloadVideo: (Int) -> Unit = { targetHeight ->
        showDownloadDialog = false
        scope.launch {
            try {
                // For 720p and below, use progressive streams (combined video+audio)
                // For 1080p+, use adaptive streams (video only without FFmpeg muxing)
                val useProgressive = targetHeight <= 720

                if (useProgressive) {
                    // Use progressive stream for combined video+audio
                    val playback = withContext(Dispatchers.IO) {
                        val cm = connectivityManager ?: error("No connectivity manager")
                        // Map height to approximate bitrate
                        val bitrateKbps = when {
                            targetHeight >= 720 -> 2500
                            targetHeight >= 480 -> 1000
                            else -> 500
                        }
                        YTPlayerUtils.playerResponseForPlayback(
                            videoId = videoId,
                            audioQuality = AudioQuality.HIGH,
                            connectivityManager = cm,
                            preferVideo = true,
                            maxVideoBitrateKbps = bitrateKbps,
                        ).getOrThrow()
                    }

                    val stream = UrlValidator.validateAndParseUrl(playback.streamUrl)?.toString()
                        ?: error("Invalid stream URL")

                    val fullTitle = playback.videoDetails?.title ?: currentTitle ?: "Video"
                    val videoArtist = fullTitle.extractArtistFromTitle()
                        ?: playback.videoDetails?.author?.takeIf { !it.isChannelId() }
                        ?: artistName?.takeIf { !it.isChannelId() }
                        ?: "Unknown Artist"
                    // Extract just the song name (without artist prefix) for filename
                    val songName = fullTitle.extractSongName()
                    val videoDuration = playback.videoDetails?.lengthSeconds?.toIntOrNull() ?: 0
                    val qualityLabel = playback.format.height?.let { "${it}p" } ?: "${targetHeight}p"

                    Toast.makeText(context, "Downloading $qualityLabel video...", Toast.LENGTH_SHORT).show()

                    val tempFile = File(context.cacheDir, "temp_video_$videoId.mp4")

                    val uri = withContext(Dispatchers.IO) {
                        val request = Request.Builder().url(stream).build()
                        httpClient.newCall(request).execute().use { response ->
                            if (!response.isSuccessful) error("HTTP ${response.code}")
                            response.body?.byteStream()?.use { input ->
                                tempFile.outputStream().use { output ->
                                    input.copyTo(output)
                                }
                            }
                        }

                        // Filename is just song name (artist is in folder path)
                        val fileName = "$songName ($qualityLabel).mp4"
                        val savedUri = mediaStoreHelper.saveVideoFileToMediaStore(
                            tempFile = tempFile,
                            fileName = fileName,
                            mimeType = "video/mp4",
                            title = songName,
                            artist = videoArtist,
                            durationMs = videoDuration * 1000L
                        )
                        tempFile.delete()
                        savedUri
                    }

                    if (uri != null) {
                        database.query {
                            upsert(
                                SongEntity(
                                    id = videoId,
                                    title = fullTitle,
                                    duration = videoDuration,
                                    thumbnailUrl = playback.videoDetails?.thumbnail?.thumbnails?.lastOrNull()?.url,
                                    explicit = false,
                                    dateDownload = LocalDateTime.now(),
                                    isDownloaded = true,
                                    isVideo = true,
                                    mediaStoreUri = uri.toString()
                                )
                            )
                        }
                        Toast.makeText(context, "Video saved to Movies/Zemer ($qualityLabel)", Toast.LENGTH_LONG).show()
                    } else {
                        error("Failed to save video to MediaStore")
                    }
                } else {
                    // Use adaptive streams for 1080p+ and mux video+audio with MediaMuxer
                    val adaptiveResult = withContext(Dispatchers.IO) {
                        YTPlayerUtils.getAdaptiveVideoData(videoId, targetHeight = targetHeight)
                    }

                    adaptiveResult.onSuccess { adaptive ->
                        val videoUrl = UrlValidator.validateAndParseUrl(adaptive.videoUrl)?.toString()
                            ?: error("Invalid video URL")
                        val audioUrl = UrlValidator.validateAndParseUrl(adaptive.audioUrl)?.toString()
                            ?: error("Invalid audio URL")

                        val fullTitle = adaptive.videoDetails?.title ?: currentTitle ?: "Video"
                        val videoArtist = fullTitle.extractArtistFromTitle()
                            ?: adaptive.videoDetails?.author?.takeIf { !it.isChannelId() }
                            ?: artistName?.takeIf { !it.isChannelId() }
                            ?: "Unknown Artist"
                        // Extract just the song name (without artist prefix) for filename
                        val songName = fullTitle.extractSongName()
                        val videoDuration = adaptive.videoDetails?.lengthSeconds?.toIntOrNull() ?: 0
                        val qualityLabel = "${adaptive.videoFormat.height}p"

                        Toast.makeText(context, "Downloading $qualityLabel video...", Toast.LENGTH_SHORT).show()

                        val downloadClient = OkHttpClient.Builder()
                            .dns(ResilientDns())
                            .proxy(YouTube.proxy)
                            .connectTimeout(30, TimeUnit.SECONDS)
                            .readTimeout(5, TimeUnit.MINUTES)
                            .build()

                        val requestHeaders = mutableMapOf(
                            "Origin" to "https://www.youtube.com",
                            "Referer" to "https://www.youtube.com/",
                            "User-Agent" to YouTubeClient.USER_AGENT_WEB
                        )
                        YouTube.cookie?.let { requestHeaders["Cookie"] = it }

                        val tempVideoFile = File(context.cacheDir, "temp_video_${videoId}_video.mp4")
                        val tempAudioFile = File(context.cacheDir, "temp_video_${videoId}_audio.m4a")
                        val tempMuxedFile = File(context.cacheDir, "temp_video_${videoId}_muxed.mp4")

                        val uri = withContext(Dispatchers.IO) {
                            // Download video stream
                            Timber.tag("VideoPlayer").d("Downloading video stream...")
                            val videoRequest = Request.Builder()
                                .url(videoUrl)
                                .apply { requestHeaders.forEach { (k, v) -> addHeader(k, v) } }
                                .build()

                            downloadClient.newCall(videoRequest).execute().use { response ->
                                if (!response.isSuccessful) error("Video download failed: HTTP ${response.code}")
                                response.body?.byteStream()?.use { input ->
                                    tempVideoFile.outputStream().use { output ->
                                        input.copyTo(output)
                                    }
                                }
                            }

                            // Download audio stream
                            Timber.tag("VideoPlayer").d("Downloading audio stream...")
                            val audioRequest = Request.Builder()
                                .url(audioUrl)
                                .apply { requestHeaders.forEach { (k, v) -> addHeader(k, v) } }
                                .build()

                            downloadClient.newCall(audioRequest).execute().use { response ->
                                if (!response.isSuccessful) error("Audio download failed: HTTP ${response.code}")
                                response.body?.byteStream()?.use { input ->
                                    tempAudioFile.outputStream().use { output ->
                                        input.copyTo(output)
                                    }
                                }
                            }

                            // Mux video and audio using MediaMuxer
                            Timber.tag("VideoPlayer").d("Muxing video and audio...")
                            muxVideoAudio(tempVideoFile, tempAudioFile, tempMuxedFile)

                            // Filename is just song name (artist is in folder path)
                            val fileName = "$songName ($qualityLabel).mp4"
                            val savedUri = mediaStoreHelper.saveVideoFileToMediaStore(
                                tempFile = tempMuxedFile,
                                fileName = fileName,
                                mimeType = "video/mp4",
                                title = songName,
                                artist = videoArtist,
                                durationMs = videoDuration * 1000L
                            )

                            // Clean up temp files
                            tempVideoFile.delete()
                            tempAudioFile.delete()
                            tempMuxedFile.delete()

                            savedUri
                        }

                        if (uri != null) {
                            database.query {
                                upsert(
                                    SongEntity(
                                        id = videoId,
                                        title = fullTitle,
                                        duration = videoDuration,
                                        thumbnailUrl = adaptive.videoDetails?.thumbnail?.thumbnails?.lastOrNull()?.url,
                                        explicit = false,
                                        dateDownload = LocalDateTime.now(),
                                        isDownloaded = true,
                                        isVideo = true,
                                        mediaStoreUri = uri.toString()
                                    )
                                )
                            }
                            Toast.makeText(context, "Video saved to Movies/Zemer ($qualityLabel)", Toast.LENGTH_LONG).show()
                        } else {
                            error("Failed to save video to MediaStore")
                        }
                    }.onFailure { e ->
                        throw e
                    }
                }
            } catch (e: Exception) {
                Timber.tag("VideoPlayer").e(e, "Download failed")
                Toast.makeText(
                    context,
                    "Download failed: ${e.localizedMessage ?: "Unknown error"}",
                    Toast.LENGTH_SHORT
                ).show()
            }
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
                Toast.makeText(context, "Unable to start PiP", Toast.LENGTH_SHORT).show()
            }
        } catch (e: IllegalStateException) {
            Toast.makeText(context, "PiP unavailable: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
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
                        Text(text = loadError ?: "Playback error", color = Color.White)
                        TextButton(onClick = { reloadKey++ }) {
                            Text("Retry", color = MaterialTheme.colorScheme.primary)
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
                            // Use custom ExoPlayer for adaptive playback (1080p+)
                            if (adaptiveData != null) {
                                val adaptive = adaptiveData!!

                                // Create stable OkHttpClient and DataSourceFactory
                                val okHttpClient = remember {
                                    OkHttpClient.Builder()
                                        .dns(ResilientDns())
                                        .proxy(YouTube.proxy)
                                        .build()
                                }

                                val dataSourceFactory = remember(okHttpClient) {
                                    val requestHeaders = mutableMapOf<String, String>()
                                    requestHeaders["Origin"] = "https://www.youtube.com"
                                    requestHeaders["Referer"] = "https://www.youtube.com/"
                                    YouTube.cookie?.let { requestHeaders["Cookie"] = it }

                                    OkHttpDataSource.Factory(okHttpClient)
                                        .setUserAgent(YouTubeClient.USER_AGENT_WEB)
                                        .setDefaultRequestProperties(requestHeaders)
                                }

                                // Create player once per videoId (not per quality change)
                                val adaptivePlayer = remember(videoId) {
                                    ExoPlayer.Builder(context).build().apply {
                                        playWhenReady = true
                                        addListener(object : androidx.media3.common.Player.Listener {
                                            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                                                Timber.tag("VideoPlayer").e(error, "Playback error: ${error.message}")
                                            }
                                        })
                                    }.also {
                                        Timber.tag("VideoPlayer").d("Created stable ExoPlayer for videoId=$videoId")
                                    }
                                }

                                // Update media source when URLs change (quality switch)
                                LaunchedEffect(adaptive.videoUrl, adaptive.audioUrl) {
                                    val currentPos = adaptivePlayer.currentPosition
                                    val wasPlaying = adaptivePlayer.isPlaying

                                    val videoSource = ProgressiveMediaSource.Factory(dataSourceFactory)
                                        .createMediaSource(MediaItem.fromUri(adaptive.videoUrl))
                                    val audioSource = ProgressiveMediaSource.Factory(dataSourceFactory)
                                        .createMediaSource(MediaItem.fromUri(adaptive.audioUrl))

                                    val mergingSource = MergingMediaSource(videoSource, audioSource)

                                    adaptivePlayer.setMediaSource(mergingSource)
                                    adaptivePlayer.prepare()

                                    // Restore position on quality changes (not initial load)
                                    if (currentPos > 0) {
                                        adaptivePlayer.seekTo(currentPos)
                                    }
                                    adaptivePlayer.playWhenReady = wasPlaying || currentPos == 0L

                                    Timber.tag("VideoPlayer").d("Updated media source: video=${adaptive.videoFormat.height}p, restored pos=${currentPos}ms")
                                }

                                // Set playerInstance for controls
                                LaunchedEffect(adaptivePlayer) {
                                    playerInstance = adaptivePlayer
                                }

                                // Cleanup only when videoId changes or screen is disposed
                                DisposableEffect(videoId) {
                                    onDispose {
                                        Timber.tag("VideoPlayer").d("Releasing ExoPlayer for videoId=$videoId")
                                        adaptivePlayer.release()
                                        if (playerInstance == adaptivePlayer) {
                                            playerInstance = null
                                        }
                                    }
                                }

                                AndroidView(
                                    factory = { ctx ->
                                        PlayerView(ctx).apply {
                                            useController = false
                                            player = adaptivePlayer
                                            layoutParams = ViewGroup.LayoutParams(
                                                ViewGroup.LayoutParams.MATCH_PARENT,
                                                ViewGroup.LayoutParams.MATCH_PARENT
                                            )
                                        }
                                    },
                                    update = { playerView ->
                                        if (playerView.player != adaptivePlayer) {
                                            playerView.player = adaptivePlayer
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .pointerInput(adaptivePlayer) {
                                            detectTapGestures {
                                                // Tap toggles controls visibility
                                                showControls = !showControls
                                                if (showControls) {
                                                    lastInteraction = System.currentTimeMillis()
                                                }
                                            }
                                        }
                                        .pointerInput(adaptivePlayer, durationMs) {
                                            detectDragGestures(
                                                onDrag = { _, dragAmount ->
                                                    dragAccum += dragAmount.x
                                                    markInteraction()
                                                },
                                                onDragEnd = {
                                                    // Swipe left/right to seek
                                                    when {
                                                        dragAccum > dragSkipThresholdPx -> seekByMs(10_000)
                                                        dragAccum < -dragSkipThresholdPx -> seekByMs(-10_000)
                                                    }
                                                    dragAccum = 0f
                                                },
                                                onDragCancel = {
                                                    dragAccum = 0f
                                                }
                                            )
                                        }
                                )
                            } else if (videoItem != null) {
                                // Fallback to library's VideoPlayer for progressive streams (720p max)
                                VideoPlayer(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .pointerInput(playerInstance) {
                                            detectTapGestures {
                                                // Tap toggles controls visibility
                                                showControls = !showControls
                                                if (showControls) {
                                                    lastInteraction = System.currentTimeMillis()
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
                                                    // Swipe left/right to seek
                                                    when {
                                                        dragAccum > dragSkipThresholdPx -> seekByMs(10_000)
                                                        dragAccum < -dragSkipThresholdPx -> seekByMs(-10_000)
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
                                                val clip = ClipData.newPlainText("Video link", VideoLinkBuilder.videoLink(videoId))
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
                                                text = artistName ?: "Unknown artist",
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
                                                    showDownloadDialog = true
                                                }) {
                                                    Icon(
                                                        painter = painterResource(R.drawable.download),
                                                        contentDescription = "Download"
                                                    )
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
                                                        contentDescription = "Quality"
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
                                                contentDescription = "Speed"
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
                                                contentDescription = "Previous"
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
                                                contentDescription = if (isPlaying) "Pause" else "Play"
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
                                                contentDescription = "Next"
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
                                                contentDescription = "Fullscreen"
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

    // State for download qualities
    var downloadQualities by remember { mutableStateOf<List<YTPlayerUtils.VideoQualityInfo>>(emptyList()) }
    var isLoadingDownloadQualities by remember { mutableStateOf(false) }

    // Fetch all download qualities when dialog opens
    LaunchedEffect(showDownloadDialog) {
        if (showDownloadDialog && downloadQualities.isEmpty() && !isLoadingDownloadQualities) {
            isLoadingDownloadQualities = true
            val result = withContext(Dispatchers.IO) {
                YTPlayerUtils.getAdaptiveVideoData(videoId, targetHeight = null)
            }
            result.onSuccess { data ->
                downloadQualities = data.availableQualities
            }
            isLoadingDownloadQualities = false
        }
    }

    if (showDownloadDialog) {
        AlertDialog(
            onDismissRequest = { showDownloadDialog = false },
            title = { Text("Download video") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Choose a quality", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    if (isLoadingDownloadQualities) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Loading qualities...", style = MaterialTheme.typography.bodySmall)
                        }
                    } else if (downloadQualities.isNotEmpty()) {
                        downloadQualities.forEach { quality ->
                            TextButton(
                                onClick = { downloadVideo(quality.height) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = quality.label,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    } else {
                        // Fallback to standard qualities
                        listOf(1080, 720, 480, 360).forEach { height ->
                            TextButton(
                                onClick = { downloadVideo(height) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(text = "${height}p", modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showDownloadDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    if (showSpeedDialog) {
        val speeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
        AlertDialog(
            onDismissRequest = { showSpeedDialog = false },
            title = { Text("Playback speed") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    speeds.forEach { speed ->
                        TextButton(onClick = {
                            playerInstance?.setPlaybackSpeed(speed)
                            showSpeedDialog = false
                        }) {
                            Text(if (speed == 1f) "1.0x (Normal)" else "${speed}x")
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showSpeedDialog = false }) { Text("Close") }
            }
        )
    }

    if (showQualityDialog) {
        AlertDialog(
            onDismissRequest = { showQualityDialog = false },
            title = { Text("Video quality") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Show current playing quality
                    val playingQuality = adaptiveData?.videoFormat?.height ?: selectedQualityHeight
                    Text(
                        text = "Playing: ${playingQuality ?: "?"}p",
                        style = MaterialTheme.typography.labelMedium
                    )

                    // Use adaptive qualities if available (1080p+ support)
                    if (adaptiveQualities.isNotEmpty()) {
                        // Quality options (no "auto" - default is 1080p or highest below)
                        adaptiveQualities.forEach { quality ->
                            TextButton(
                                onClick = {
                                    selectedQualityHeight = quality.height
                                    showQualityDialog = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(quality.label)
                                    if (playingQuality == quality.height) {
                                        Text("✓", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    } else if (availableQualities.isNotEmpty()) {
                        // Fallback to ExoPlayer track selection (progressive streams)
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
                            Text("Auto")
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
                                Text(option.label.ifBlank { "Track ${option.trackIndex + 1}" })
                            }
                        }
                    } else {
                        Text("No quality options available", style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showQualityDialog = false }) { Text("Close") }
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

/**
 * Mux separate video and audio files into a single MP4 using Android's MediaMuxer.
 * This allows downloading 1080p+ videos with audio without needing FFmpeg.
 */
private fun muxVideoAudio(videoFile: File, audioFile: File, outputFile: File) {
    val videoExtractor = MediaExtractor()
    val audioExtractor = MediaExtractor()

    try {
        videoExtractor.setDataSource(videoFile.absolutePath)
        audioExtractor.setDataSource(audioFile.absolutePath)

        // Find video track
        var videoTrackIndex = -1
        var videoFormat: MediaFormat? = null
        for (i in 0 until videoExtractor.trackCount) {
            val format = videoExtractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
            if (mime.startsWith("video/")) {
                videoTrackIndex = i
                videoFormat = format
                break
            }
        }

        // Find audio track
        var audioTrackIndex = -1
        var audioFormat: MediaFormat? = null
        for (i in 0 until audioExtractor.trackCount) {
            val format = audioExtractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
            if (mime.startsWith("audio/")) {
                audioTrackIndex = i
                audioFormat = format
                break
            }
        }

        if (videoFormat == null || audioFormat == null) {
            error("Could not find video or audio track")
        }

        // Create muxer
        val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

        // Add tracks to muxer
        val muxerVideoTrack = muxer.addTrack(videoFormat)
        val muxerAudioTrack = muxer.addTrack(audioFormat)

        muxer.start()

        // Select and copy video track
        videoExtractor.selectTrack(videoTrackIndex)
        val videoBuffer = ByteBuffer.allocate(1024 * 1024) // 1MB buffer
        val videoBufferInfo = MediaCodec.BufferInfo()

        while (true) {
            videoBufferInfo.offset = 0
            videoBufferInfo.size = videoExtractor.readSampleData(videoBuffer, 0)
            if (videoBufferInfo.size < 0) break

            videoBufferInfo.presentationTimeUs = videoExtractor.sampleTime
            videoBufferInfo.flags = videoExtractor.sampleFlags
            muxer.writeSampleData(muxerVideoTrack, videoBuffer, videoBufferInfo)
            videoExtractor.advance()
        }

        // Select and copy audio track
        audioExtractor.selectTrack(audioTrackIndex)
        val audioBuffer = ByteBuffer.allocate(256 * 1024) // 256KB buffer
        val audioBufferInfo = MediaCodec.BufferInfo()

        while (true) {
            audioBufferInfo.offset = 0
            audioBufferInfo.size = audioExtractor.readSampleData(audioBuffer, 0)
            if (audioBufferInfo.size < 0) break

            audioBufferInfo.presentationTimeUs = audioExtractor.sampleTime
            audioBufferInfo.flags = audioExtractor.sampleFlags
            muxer.writeSampleData(muxerAudioTrack, audioBuffer, audioBufferInfo)
            audioExtractor.advance()
        }

        muxer.stop()
        muxer.release()

        Timber.tag("VideoPlayer").d("Muxing completed: ${outputFile.length()} bytes")
    } finally {
        videoExtractor.release()
        audioExtractor.release()
    }
}

/**
 * Check if a string looks like a YouTube channel ID.
 * Channel IDs start with "UC" and are alphanumeric with no spaces.
 */
private fun String.isChannelId(): Boolean {
    return this.startsWith("UC") && this.length >= 20 && !this.contains(" ")
}

/**
 * Extract artist name from video title.
 * Assumes format "Artist - Title" or "Artist | Title".
 */
private fun String.extractArtistFromTitle(): String? {
    // Try common separators
    val separators = listOf(" - ", " – ", " — ", " | ", " // ")
    for (separator in separators) {
        if (this.contains(separator)) {
            val artist = this.substringBefore(separator).trim()
            // Make sure we got something reasonable (not empty, not too long)
            if (artist.isNotBlank() && artist.length < 100) {
                return artist
            }
        }
    }
    return null
}

/**
 * Extract song/video name from title (removes artist prefix).
 * If title is "Artist - Song Name", returns "Song Name".
 * If no separator found, returns the full title.
 */
private fun String.extractSongName(): String {
    val separators = listOf(" - ", " – ", " — ", " | ", " // ")
    for (separator in separators) {
        if (this.contains(separator)) {
            val songPart = this.substringAfter(separator).trim()
            if (songPart.isNotBlank()) {
                return songPart
            }
        }
    }
    return this // Return full title if no separator found
}
