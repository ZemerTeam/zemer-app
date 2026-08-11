package com.jtech.zemer.ui.screens.statuses

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.jtech.zemer.R
import com.jtech.zemer.extensions.copyToClipboard
import com.jtech.zemer.statuses.StatusDownload
import com.jtech.zemer.statuses.formatPostedAt
import com.jtech.zemer.statuses.statusAvatarUrl
import com.jtech.zemer.ui.component.ExpandableStatusCaption
import com.jtech.zemer.ui.component.StatusCopyButton
import com.jtech.zemer.ui.component.StatusLoadingIndicator
import com.jtech.zemer.ui.component.StatusStoryTopOverlay
import com.jtech.zemer.ui.component.StatusVideoSurface
import com.jtech.zemer.ui.utils.ForceLightStatusBarIcons
import com.jtech.zemer.ui.utils.PauseMusicWhileActive
import com.jtech.zemer.ui.utils.cubeFace
import com.jtech.zemer.viewmodels.SavedCreatorStatuses
import com.jtech.zemer.viewmodels.SavedStatusViewModel
import kotlinx.coroutines.launch

// Image/text statuses (text is saved as an image) hold this long before auto-advancing, matching the
// live story viewer's default.
private const val SAVED_IMAGE_HOLD_MS = 7000f

/**
 * Full-screen viewer for SAVED (local) statuses, at FULL PARITY with the live [StoryScreen]: creators are
 * cube-pager pages you swipe between, each status auto-advances (video to its end, image/text hold), tap
 * left-35% = back / right = forward, press-and-hold pauses, and the shared overlays render the segment
 * bars + header and the caption panel (Read more / links / copy) - only the media comes from the
 * downloaded files instead of the network. A text status keeps its own copy affordance since its body has
 * no caption band. Reuses [StatusStoryTopOverlay], [ExpandableStatusCaption], [StatusVideoSurface],
 * [StatusCopyButton] and the shared [cubeFace] transform.
 */
@Composable
fun SavedStatusScreen(
    navController: NavController,
    initialCreatorId: String,
    startId: String?,
    viewModel: SavedStatusViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val creators by viewModel.creators.collectAsState()
    val scope = rememberCoroutineScope()

    val onClose = { navController.navigateUp(); Unit }
    BackHandler(onBack = onClose)

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply { repeatMode = Player.REPEAT_MODE_OFF }
    }
    DisposableEffect(Unit) { onDispose { exoPlayer.release() } }
    PauseMusicWhileActive()

    // Full-bleed media runs behind the status bar and the top overlay is now a fade, not an opaque band
    // (#394) — keep the system clock/battery icons white for the viewer's lifetime, light theme included.
    ForceLightStatusBarIcons()

    // Hold a poster over the video until its first frame draws, so switching status (or creator) never
    // flashes a black / stale-previous surface. Reset per status by the driver.
    var videoRendered by remember { mutableStateOf(false) }
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() { videoRendered = true }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    // Pause the status video when the app is backgrounded (the composable is not disposed, only stopped),
    // so it never keeps playing off-screen; resume when it returns.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> exoPlayer.pause()
                Lifecycle.Event.ON_START ->
                    if (exoPlayer.mediaItemCount > 0 && exoPlayer.playbackState != Player.STATE_ENDED) exoPlayer.play()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (creators.isEmpty()) {
        StatusLoadingIndicator(Modifier.fillMaxSize().background(Color.Black))
        return
    }

    val pagerState = rememberPagerState(
        initialPage = creators.indexOfFirst { it.creatorId == initialCreatorId }.coerceAtLeast(0),
    ) { creators.size }
    val creatorIdx = pagerState.settledPage

    var postIdx by remember { mutableIntStateOf(0) }
    var progress by remember { mutableFloatStateOf(0f) }
    var paused by remember { mutableStateOf(false) }
    var captionExpanded by remember(creatorIdx, postIdx) { mutableStateOf(false) }
    // The creator whose LIVE face is ready to render. Until the driver catches up after a settle (and for
    // every non-settled page) the static preview face shows instead - no stale-content flash on swipe.
    var faceCreator by remember { mutableIntStateOf(-1) }
    // Show the video loading state only if it is STILL not ready ~0.75s after the status started (reset
    // synchronously per status so a fast load never flashes a spinner, even across a Crossfade).
    var showVideoLoading by remember(creatorIdx, postIdx) { mutableStateOf(false) }
    LaunchedEffect(creatorIdx, postIdx) {
        kotlinx.coroutines.delay(750L)
        showVideoLoading = true
    }

    // Position within a creator: on the FIRST settle open at the tapped status; on any later creator
    // change (swipe or programmatic) restart that creator at its first status.
    var didInit by remember { mutableStateOf(false) }
    LaunchedEffect(creatorIdx) {
        val c = creators.getOrNull(creatorIdx)
        postIdx = if (!didInit && c != null) {
            didInit = true
            c.statuses.indexOfFirst { it.id == startId }.coerceAtLeast(0)
        } else {
            0
        }
    }
    // If everything was removed while open, leave.
    LaunchedEffect(creators) { if (didInit && creators.isEmpty()) onClose() }

    val creator = creators.getOrNull(creatorIdx)
    val statuses = creator?.statuses.orEmpty()
    val current = statuses.getOrNull(postIdx)

    fun advance() {
        progress = 0f
        when {
            postIdx < statuses.lastIndex -> postIdx++
            creatorIdx < creators.lastIndex -> scope.launch { pagerState.animateScrollToPage(creatorIdx + 1) }
            else -> onClose()
        }
    }

    fun goBack() {
        progress = 0f
        if (postIdx > 0) postIdx-- else if (creatorIdx > 0) scope.launch { pagerState.animateScrollToPage(creatorIdx - 1) }
    }

    // Reflect the press-hold (or an expanded caption) onto a playing video; image/text honor the same
    // flags in their timer loop.
    LaunchedEffect(paused, captionExpanded) {
        if (paused || captionExpanded) {
            exoPlayer.pause()
        } else if (exoPlayer.mediaItemCount > 0 && exoPlayer.playbackState != Player.STATE_ENDED) {
            exoPlayer.play()
        }
    }

    // Driver: play a video to its end, hold an image/text, then auto-advance.
    LaunchedEffect(creatorIdx, postIdx) {
        progress = 0f
        videoRendered = false // hold the poster until THIS status's video draws its first frame
        exoPlayer.stop()
        val item = statuses.getOrNull(postIdx) ?: return@LaunchedEffect
        faceCreator = creatorIdx // this creator's live face may now render
        if (item.kind == "video") {
            exoPlayer.setMediaItem(MediaItem.fromUri(item.mediaUri.toUri()))
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
                    if (!paused && !captionExpanded && dt in 0f..100f) elapsed += dt
                }
                progress = (elapsed / SAVED_IMAGE_HOLD_MS).coerceIn(0f, 1f)
            }
        }
        advance()
    }

    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize().background(Color.Black),
        beyondViewportPageCount = 1,
    ) { page ->
        Box(
            Modifier
                .fillMaxSize()
                .cubeFace((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction),
        ) {
            // Live face only for the SETTLED creator once the driver has caught up; otherwise a static
            // preview (the creator's first saved status), so a swipe never flashes stale content.
            if (page != creatorIdx || faceCreator != creatorIdx) {
                SavedPreviewFace(creators.getOrNull(page))
                return@Box
            }

            // Media + tap gestures (only the settled creator gets the live face).
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(creatorIdx, postIdx) {
                        detectTapGestures(
                            onPress = { paused = true; tryAwaitRelease(); paused = false },
                            onLongPress = {},
                            onTap = { offset -> if (offset.x < size.width * 0.35f) goBack() else advance() },
                        )
                    },
            ) {
                Crossfade(targetState = current, label = "saved-status") { item ->
                    when (item?.kind) {
                        null -> {}
                        "video" -> Box(Modifier.fillMaxSize()) {
                            StatusVideoSurface(player = exoPlayer, modifier = Modifier.fillMaxSize())
                            // A black cover holds the surface until the video draws; the loading state
                            // only appears if it is STILL not ready after 0.75s, so a fast load goes
                            // straight to playing with no spinner/poster flash.
                            if (!videoRendered) {
                                Box(Modifier.fillMaxSize().background(Color.Black)) {
                                    if (showVideoLoading) {
                                        StatusLoadingIndicator(
                                            Modifier.fillMaxSize(),
                                            avatarUrl = statusAvatarUrl(item?.creatorAvatar),
                                        )
                                    }
                                }
                            }
                        }
                        else -> AsyncImage(
                            // Image AND text-as-image are stored as pictures, so both render here.
                            model = ImageRequest.Builder(context).data(item.mediaUri).crossfade(true).build(),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }

            StatusStoryTopOverlay(
                navController = navController,
                avatarUrl = statusAvatarUrl(current?.creatorAvatar),
                creatorName = current?.creatorName ?: "",
                subtitle = current?.postedAt?.let { formatPostedAt(it) },
                segmentCount = statuses.size,
                currentSegment = postIdx,
                progress = progress,
            )

            // Bottom overlay, mirroring the live viewer: a text status keeps a copy pill (its body has no
            // caption band), while an image/video with a caption shows the full caption panel.
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val textBody = current?.takeIf { it.kind == "text" }?.let { it.textBody ?: it.caption }?.takeIf { it.isNotBlank() }
                if (textBody != null) {
                    StatusCopyButton(
                        onClick = { context.copyToClipboard(context.getString(R.string.statuses), textBody) },
                        modifier = Modifier.padding(bottom = 24.dp).size(40.dp),
                    )
                }
                val cap = current?.takeIf { it.kind != "text" }?.caption?.takeIf { it.isNotBlank() }
                if (cap != null) {
                    ExpandableStatusCaption(
                        caption = cap,
                        expanded = captionExpanded,
                        onExpandedChange = { captionExpanded = it },
                        onCopy = { context.copyToClipboard(context.getString(R.string.statuses), cap) },
                    )
                }
            }
        }
    }
}

/**
 * A non-active cube face: the creator's first saved status. A picture (image / text-as-image) shows
 * statically; a VIDEO shows the shared loading spinner instead of a poster frame, so a swipe onto a video
 * creator reads as "loading" straight through to playback rather than flashing a thumbnail then the video.
 */
@Composable
private fun SavedPreviewFace(creator: SavedCreatorStatuses?) {
    val first = creator?.statuses?.firstOrNull()
    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        when (first?.kind) {
            // A video stays BLACK here; the live face owns the video's delayed loading state, so the
            // preview never flashes a spinner before it. An image/text shows its still.
            null, "video" -> {}
            else -> AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(first.mediaUri).crossfade(true).build(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
