package com.jtech.zemer.ui.screens.player

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.net.ConnectivityManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.navigation.NavController
import com.jtech.zemer.R
import com.jtech.zemer.constants.AudioQuality
import com.jtech.zemer.utils.YTPlayerUtils
import com.metrolist.innertube.utils.ResilientDns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.jtech.zemer.constants.FloatingMiniPlayerKey
import com.jtech.zemer.utils.rememberPreference
import com.jtech.zemer.LocalDownloadUtil
import java.io.File
import android.os.Environment

@Composable
fun VideoPlayerScreen(
    navController: NavController,
    videoId: String,
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val connectivityManager = remember {
        context.getSystemService(ConnectivityManager::class.java)
    }
    val (_, setFloatingMiniPlayerEnabled) = rememberPreference(FloatingMiniPlayerKey, defaultValue = true)
    val downloadUtil = LocalDownloadUtil.current
    val scope = rememberCoroutineScope()
    var showDownloadDialog by remember { mutableStateOf(false) }
    var forceLandscape by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        activity?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    LaunchedEffect(forceLandscape) {
        activity?.requestedOrientation = if (forceLandscape) {
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        } else {
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
    val maxVideoBitrateKbps = remember(connectivityManager) {
        if (connectivityManager?.isActiveNetworkMetered == true) 1500 else 6000
    }

    val videoHttpClient = remember(videoId) {
        OkHttpClient.Builder()
            .dns(ResilientDns())
            .build()
    }
    val videoDataSourceFactory = remember(videoId) {
        val okHttpFactory = OkHttpDataSource.Factory(videoHttpClient)
        DefaultDataSource.Factory(context, okHttpFactory)
    }
    val exoPlayer = remember(videoId) {
        ExoPlayer.Builder(context, DefaultRenderersFactory(context))
            .setMediaSourceFactory(DefaultMediaSourceFactory(videoDataSourceFactory))
            .build()
    }

    DisposableEffect(Unit) {
        // Disable floating mini player while video screen is visible
        setFloatingMiniPlayerEnabled(false)
        onDispose {
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
            exoPlayer.release()
            activity?.window?.let {
                WindowCompat.getInsetsController(it, it.decorView)?.show(WindowInsetsCompat.Type.systemBars())
                it.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            setFloatingMiniPlayerEnabled(true)
        }
    }

    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var isPlaying by remember { mutableStateOf(false) }
    var isImmersive by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(true) }
    var rotated by remember { mutableStateOf(false) }
    var videoAspectRatio by remember { mutableStateOf(16f / 9f) }
    val downloadVideo: suspend (Int) -> Unit = { maxBitrateKbps ->
        val moviesDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.filesDir
        val targetFile = File(moviesDir, "$videoId.mp4")
        withContext(Dispatchers.IO) {
            val playbackResult = YTPlayerUtils.playerResponseForPlayback(
                videoId = videoId,
                audioQuality = AudioQuality.HIGH,
                connectivityManager = connectivityManager ?: return@withContext,
                preferVideo = true,
                maxVideoBitrateKbps = maxBitrateKbps,
            )
            playbackResult.onFailure {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Download failed: ${it.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
                return@withContext
            }
            val playback = playbackResult.getOrThrow()
            val request = Request.Builder().url(playback.streamUrl).build()
            videoHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Download failed (${response.code})", Toast.LENGTH_SHORT).show()
                    }
                    return@use
                }
                response.body?.byteStream()?.use { input ->
                    targetFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Downloaded to ${targetFile.absolutePath}", Toast.LENGTH_LONG).show()
            }
        }
    }

    val windowInsetsController = remember(activity) {
        activity?.window?.let { WindowCompat.getInsetsController(it, it.decorView) }
    }

    LaunchedEffect(isImmersive) {
        windowInsetsController?.let { controller ->
            if (isImmersive) {
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    LaunchedEffect(videoId, connectivityManager) {
        isLoading = true
        loadError = null
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        val result = withContext(Dispatchers.IO) {
            YTPlayerUtils.playerResponseForPlayback(
                videoId = videoId,
                audioQuality = AudioQuality.HIGH,
                connectivityManager = connectivityManager ?: return@withContext Result.failure(Exception("No connectivity manager")),
                preferVideo = true,
                maxVideoBitrateKbps = maxVideoBitrateKbps,
            )
        }
        result.onSuccess { playback ->
            val mediaItem = MediaItem.Builder()
                .setUri(playback.streamUrl)
                .setMimeType(playback.format.mimeType)
                .build()
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
            isLoading = false
            isPlaying = true
        }.onFailure {
            loadError = it.localizedMessage ?: "Playback error"
            isLoading = false
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    videoAspectRatio = videoSize.width.toFloat() / videoSize.height.toFloat()
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
        }
    }

    LaunchedEffect(videoId, exoPlayer) {
        while (true) {
            position = exoPlayer.currentPosition
            duration = exoPlayer.duration.coerceAtLeast(0L)
            isPlaying = exoPlayer.isPlaying
            delay(300)
        }
    }

    LaunchedEffect(showControls, isPlaying) {
        if (showControls && isPlaying) {
            delay(5000)
            showControls = false
        }
    }

    BackHandler {
        exoPlayer.pause()
        exoPlayer.clearMediaItems()
        activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        // Try to enter PiP mode
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && activity != null) {
            try {
                val pipParams = android.app.PictureInPictureParams.Builder()
                    .setAspectRatio(android.util.Rational((videoAspectRatio * 100).toInt(), 100))
                    .build()
                activity?.enterPictureInPictureMode(pipParams)
            } catch (e: Exception) {
                // If PiP fails, just go back normally
                navController.popBackStack()
            }
        } else {
            navController.popBackStack()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(showControls) {
                detectTapGestures(
                    onTap = {
                        showControls = !showControls
                    }
                )
            }
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                factory = { viewContext ->
                    PlayerView(viewContext).apply {
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        player = exoPlayer
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(
                        if (rotated) 1f / videoAspectRatio
                        else videoAspectRatio
                    )
                    .graphicsLayer {
                        rotationZ = if (rotated) 90f else 0f
                        cameraDistance = 8 * density
                    }
            )
        }

        AnimatedVisibility(
            visible = showControls,
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(),
                color = Color.Black.copy(alpha = 0.6f),
                tonalElevation = 0.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isLoading) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    loadError?.let {
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    Slider(
                        value = if (duration > 0) position.toFloat() / duration.toFloat() else 0f,
                        onValueChange = { fraction ->
                            val seekPos = (fraction * duration).toLong()
                            exoPlayer.seekTo(seekPos)
                            position = seekPos
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatTime(position),
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium
                        )
                        Text(
                            text = formatTime(duration),
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                    IconButton(
                        onClick = {
                            exoPlayer.seekTo((exoPlayer.currentPosition - 30_000).coerceAtLeast(0))
                        }
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.fast_forward),
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier
                                .size(28.dp)
                                .graphicsLayer { rotationY = 180f }
                        )
                    }
                        IconButton(
                            onClick = {
                                if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                            }
                        ) {
                            Icon(
                                painter = painterResource(if (isPlaying) R.drawable.pause else R.drawable.play),
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    IconButton(
                        onClick = {
                            val newPos = exoPlayer.currentPosition + 30_000
                            exoPlayer.seekTo(newPos.coerceAtMost(duration))
                        }
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.fast_forward),
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            val clip = ClipData.newPlainText("Video link", "https://youtu.be/$videoId")
                            val clipboard = context.getSystemService(ClipboardManager::class.java)
                            clipboard?.setPrimaryClip(clip)
                            Toast.makeText(context, R.string.link_copied, Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(
                                painter = painterResource(R.drawable.link),
                                contentDescription = null,
                                tint = Color.White
                            )
                        }

                        IconButton(onClick = { showDownloadDialog = true }) {
                            Icon(
                                painter = painterResource(R.drawable.download),
                                contentDescription = null,
                                tint = Color.White
                            )
                        }

                        IconButton(onClick = {
                            rotated = !rotated
                        }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_rotate_screen),
                                contentDescription = null,
                                tint = Color.White
                            )
                        }

                        IconButton(onClick = { forceLandscape = !forceLandscape }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_fullscreen),
                                contentDescription = null,
                                tint = Color.White
                            )
                        }
                    }
                }
            }
        }

        IconButton(
            onClick = { navController.popBackStack() },
            modifier = Modifier
                .padding(12.dp)
                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(50))
                .size(40.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.arrow_back),
                contentDescription = null,
                tint = Color.White
            )
        }
    }

    if (showDownloadDialog) {
        AlertDialog(
            onDismissRequest = { showDownloadDialog = false },
            confirmButton = {},
            title = { Text("Download video") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Choose a quality to download")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = {
                            showDownloadDialog = false
                            scope.launch {
                                downloadVideo(1500)
                            }
                        }) {
                            Text("360p (~1.5Mbps)")
                        }
                        TextButton(onClick = {
                            showDownloadDialog = false
                            scope.launch {
                                downloadVideo(4000)
                            }
                        }) {
                            Text("720p (~4Mbps)")
                        }
                    }
                }
            }
        )
    }
}

private fun formatTime(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
