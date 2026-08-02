package com.jtech.zemer.ui.screens.statuses

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.jtech.zemer.statuses.formatPostedAt
import com.jtech.zemer.statuses.statusAvatarUrl
import com.jtech.zemer.ui.component.StatusStoryTopOverlay
import com.jtech.zemer.ui.component.StatusVideoSurface
import com.jtech.zemer.ui.utils.PauseMusicWhileActive
import com.jtech.zemer.viewmodels.SavedStatusViewModel

// Image/text statuses (text is saved as an image) hold this long before auto-advancing, matching the
// live story viewer's default.
private const val SAVED_IMAGE_HOLD_MS = 7000f

/**
 * Full-screen viewer for a creator's SAVED (local) statuses, presented EXACTLY like the live story
 * viewer: the shared [StatusStoryTopOverlay] (segment bars + avatar/name/date), full-screen media,
 * tap-left-35% = back / tap-right = forward, press-and-hold to pause, auto-advance (video plays to its
 * end, image/text hold [SAVED_IMAGE_HOLD_MS]). Reuses [StatusVideoSurface] and pauses the music player
 * while up. Local files, so no network-stall handling is needed.
 */
@Composable
fun SavedStatusScreen(
    navController: NavController,
    startId: String?,
    viewModel: SavedStatusViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val items by viewModel.items.collectAsState()

    val onClose = { navController.navigateUp(); Unit }
    BackHandler(onBack = onClose)

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply { repeatMode = Player.REPEAT_MODE_OFF }
    }
    DisposableEffect(Unit) { onDispose { exoPlayer.release() } }
    PauseMusicWhileActive()

    var index by remember { mutableIntStateOf(0) }
    var progress by remember { mutableFloatStateOf(0f) }
    var paused by remember { mutableStateOf(false) }

    // Open at the tapped status once the list loads (only the first time).
    var didInitialIndex by remember { mutableStateOf(false) }
    LaunchedEffect(items) {
        if (!didInitialIndex && items.isNotEmpty()) {
            index = items.indexOfFirst { it.id == startId }.coerceAtLeast(0)
            didInitialIndex = true
        }
        // Close once the saved set becomes empty (last item removed).
        if (didInitialIndex && items.isEmpty()) onClose()
    }

    fun advance() {
        progress = 0f
        if (index < items.lastIndex) index++ else onClose()
    }

    fun goBack() {
        progress = 0f
        if (index > 0) index--
    }

    // Reflect the press-hold pause onto a playing video (image/text honor `paused` in their timer loop).
    LaunchedEffect(paused) {
        if (paused) {
            exoPlayer.pause()
        } else if (exoPlayer.mediaItemCount > 0 && exoPlayer.playbackState != Player.STATE_ENDED) {
            exoPlayer.play()
        }
    }

    val current = items.getOrNull(index)

    // Driver: play a video to its end, hold an image/text, then auto-advance. Progress runs on the
    // display frame clock so the segment bar fills smoothly (same as the live viewer).
    LaunchedEffect(index, items.size) {
        progress = 0f
        exoPlayer.stop()
        val item = items.getOrNull(index) ?: return@LaunchedEffect
        if (item.kind == "video") {
            exoPlayer.setMediaItem(MediaItem.fromUri(item.mediaUri))
            exoPlayer.prepare()
            exoPlayer.play()
            while (true) {
                withFrameNanos { }
                val dur = exoPlayer.duration
                if (dur > 0L) progress = (exoPlayer.currentPosition.toFloat() / dur).coerceIn(0f, 1f)
                val ended = exoPlayer.playbackState == Player.STATE_ENDED
                val failed = exoPlayer.playerError != null
                if (ended || failed || progress >= 0.999f) break
            }
            exoPlayer.stop()
        } else {
            var elapsed = 0f
            var prevFrame = 0L
            while (elapsed < SAVED_IMAGE_HOLD_MS) {
                withFrameNanos { now ->
                    val dt = if (prevFrame == 0L) 0f else (now - prevFrame) / 1_000_000f
                    prevFrame = now
                    if (!paused && dt in 0f..100f) elapsed += dt
                }
                progress = (elapsed / SAVED_IMAGE_HOLD_MS).coerceIn(0f, 1f)
            }
        }
        advance()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(index) {
                detectTapGestures(
                    onPress = {
                        paused = true
                        tryAwaitRelease()
                        paused = false
                    },
                    onLongPress = {},
                    onTap = { offset -> if (offset.x < size.width * 0.35f) goBack() else advance() },
                )
            },
    ) {
        Crossfade(targetState = current, label = "saved-status") { item ->
            when (item?.kind) {
                null -> {}
                "video" -> StatusVideoSurface(player = exoPlayer, modifier = Modifier.fillMaxSize())
                else -> AsyncImage(
                    // Image AND text-as-image are stored as pictures, so both render here.
                    model = ImageRequest.Builder(context).data(item.mediaUri).crossfade(true).build(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        StatusStoryTopOverlay(
            navController = navController,
            avatarUrl = statusAvatarUrl(current?.creatorAvatar),
            creatorName = current?.creatorName ?: "",
            subtitle = current?.postedAt?.let { formatPostedAt(it) },
            segmentCount = items.size,
            currentSegment = index,
            progress = progress,
        )
    }
}
