package com.jtech.zemer.ui.screens.statuses

import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jtech.zemer.R
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.jtech.zemer.LocalPlayerConnection
import com.jtech.zemer.ui.component.AppBarTitle
import com.jtech.zemer.ui.component.BackNavigationIcon
import com.jtech.zemer.statuses.StatusCreator
import com.jtech.zemer.statuses.StatusDateGroup
import com.jtech.zemer.statuses.StatusPost
import com.jtech.zemer.statuses.formatPostedAt
import com.jtech.zemer.statuses.resumePos
import com.jtech.zemer.statuses.statusAvatarUrl
import com.jtech.zemer.statuses.statusDateGroups
import com.jtech.zemer.statuses.statusMediaUrl
import com.jtech.zemer.ui.theme.HeaderFontFamily
import com.jtech.zemer.viewmodels.StoryViewModel
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

// A status video whose position hasn't advanced for this long while playback is intended (buffering
// forever at the start OR stalling mid-stream, with no error) is treated as failed and skipped.
private const val VIDEO_STALL_TIMEOUT_MS = 12_000L

private val dowFmt = DateTimeFormatter.ofPattern("EEE", Locale.US)
private val dayFmt = DateTimeFormatter.ofPattern("d", Locale.US)

/**
 * Warm Coil's cache with the image a neighbor creator will show on the cube face (its resume status's
 * media / video thumbnail), so swiping to it reveals a real frame instead of a black-loading flash.
 * Prefetching the POSTS alone is not enough - the thumbnail bytes must be cached too.
 */
private fun prefetchStatusImage(
    context: android.content.Context,
    posts: List<StatusPost>,
    seen: Set<String>,
    todayIso: String,
) {
    if (posts.isEmpty()) return
    val post = posts[resumePos(posts, seen, todayIso).index]
    val url = when (post.kind) {
        "image" -> statusMediaUrl(post.mediaPath)
        "video" -> post.thumbPath?.let { statusMediaUrl(it) }
        else -> null
    } ?: return
    context.imageLoader.enqueue(ImageRequest.Builder(context).data(url).build())
}

/**
 * Full-screen WhatsApp/Stories-style viewer for JewishStatus creators. Reachable only from the Home
 * "Music Status" row; [initialCreatorId] is the tapped creator's STABLE id (not an index, which would
 * remap to the wrong creator after a process-death re-fetch under the recency sort). Advances across
 * creators, tap-left-35% = back / tap-right = forward, auto-advance (video plays to its end, image/text
 * hold [StatusPost.durationSeconds] or 7s).
 *
 * App-themed (colors from `colorScheme`, text from `typography`). White is used ONLY for chrome that
 * overlays the media (progress bars, header, caption over the scrim), where a fixed light-on-scrim is
 * the correct theme-agnostic choice.
 *
 * Its OWN short-lived ExoPlayer, independent of MusicService. The Zemer music player is paused while
 * the viewer is up and RESUMED on close (mirrors [com.jtech.zemer.ui.screens.player.VideoPlayerScreen]'s
 * cast handling, plus the local resume the owner asked for).
 */
@Composable
fun StoryScreen(
    navController: NavController,
    initialCreatorId: String,
) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    val scrim = colorScheme.scrim.copy(alpha = 0.8f)
    val viewModel: StoryViewModel = hiltViewModel()
    val creators by viewModel.creators.collectAsState()
    val seenPostIds by viewModel.seenPostIds.collectAsState()
    val loadAttempted by viewModel.loadAttempted.collectAsState()
    // Re-keys the post loader below so toggling "hide text/image status" re-filters the open viewer.
    val contentFilter by viewModel.contentFilter.collectAsState()
    val playerConnection = LocalPlayerConnection.current

    val onClose = { navController.navigateUp(); Unit }
    BackHandler(onBack = onClose)

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_OFF
            volume = 1f
        }
    }
    DisposableEffect(Unit) { onDispose { exoPlayer.release() } }

    // Silence the music while the viewer is up; resume it on close if it was playing. While casting,
    // pause/resume the receiver too and route volume keys to this local video (VideoPlayerScreen pattern).
    DisposableEffect(playerConnection) {
        val wasPlaying = playerConnection?.isPlaying?.value == true
        playerConnection?.player?.pause()
        playerConnection?.setVideoPlaybackActive(true)
        val pausedCast = playerConnection?.pauseCastForVideo() == true
        onDispose {
            playerConnection?.setVideoPlaybackActive(false)
            playerConnection?.resumeCastAfterVideo(pausedCast)
            if (wasPlaying) playerConnection?.player?.play()
        }
    }

    // Pause the status video when the app is backgrounded (the composable is not disposed, only stopped),
    // so it doesn't keep playing audio off-screen; resume when it returns.
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

    // Creators come from the shared cache (warm from the Home row). Show a spinner only while the load is
    // still in flight; once it has been attempted and there is still nothing (feed down / creator gone),
    // close instead of spinning forever.
    if (creators.isEmpty()) {
        if (loadAttempted) {
            LaunchedEffect(Unit) { onClose() }
        } else {
            Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp)
            }
        }
        return
    }

    // Creators live in a HorizontalPager so switching them is a finger-tracked CUBE rotation (WhatsApp/
    // Instagram). The SETTLED page is the active creator; `creatorIdx` drives all per-creator state, and
    // programmatic creator changes (tap past the last status, tap-back at the entry date) animate the pager.
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(
        initialPage = creators.indexOfFirst { it.id == initialCreatorId }.coerceAtLeast(0),
    ) { creators.size }
    val creatorIdx = pagerState.settledPage
    var postIdx by remember { mutableIntStateOf(0) }
    var progress by remember { mutableFloatStateOf(0f) }
    var currentPosts by remember { mutableStateOf<List<StatusPost>?>(null) }
    // Which creator `currentPosts`/`postIdx` currently belong to. `creatorIdx` (= settledPage) flips one
    // frame BEFORE the driver reloads, so without this the active face would render the PREVIOUS creator's
    // status (then black + reload) on every settle - the flash. The active face waits for this to match.
    var postsCreatorIdx by remember { mutableIntStateOf(-1) }
    // Press-and-hold anywhere pauses the current status (Instagram/JewishStatus); release resumes.
    var paused by remember { mutableStateOf(false) }

    // True once the CURRENT video has actually drawn its first frame. The thumbnail is held over the
    // player until then (not merely until progress ticks) so there is no black gap between the thumbnail
    // and the video - the "blurry, flash, then play" the user saw. Reset per status by the play effect.
    var videoRendered by remember { mutableStateOf(false) }
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() { videoRendered = true }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    // Reflect the hold onto the video: pause while held, resume on release (image/text honor `paused`
    // inside their timer loop). A video that is already playing is left alone.
    LaunchedEffect(paused) {
        if (paused) {
            exoPlayer.pause()
        } else if (exoPlayer.mediaItemCount > 0 && exoPlayer.playbackState != Player.STATE_ENDED) {
            exoPlayer.play()
        }
    }
    // Like JewishStatus, the viewer opens on a single date (today by default) and the "jump to date" sheet
    // sets a different STARTING date. Statuses are grouped by local date; a date's posts are contiguous
    // (posts are asc). The progress bars show the CURRENT post's date window (derived from `postIdx`), so
    // finishing a date rolls FORWARD into the next date of the SAME creator along the timeline; only the
    // creator's newest status advances to the next creator. `floorIndex` is the entry date's first index,
    // so tapping back never descends below where you started (use the sheet to go earlier).
    val todayIso = remember { LocalDate.now().toString() }
    val dateGroups = remember(currentPosts) { statusDateGroups(currentPosts) }
    var showDateSheet by remember(creatorIdx) { mutableStateOf(false) }
    var floorIndex by remember(creatorIdx) { mutableIntStateOf(0) }
    val currentGroup = dateGroups.firstOrNull { postIdx >= it.startIndex && postIdx < it.startIndex + it.count }
    val windowStart = currentGroup?.startIndex ?: 0
    val windowEnd = currentGroup?.let { it.startIndex + it.count - 1 } ?: (currentPosts?.lastIndex ?: 0)

    // Load posts for the current creator; preload both neighbors. Re-runs when the content filter changes
    // so hiding/showing text or image statuses re-filters (and re-resumes) the creator on screen.
    LaunchedEffect(creatorIdx, contentFilter) {
        progress = 0f
        exoPlayer.stop()
        val id = creators.getOrNull(creatorIdx)?.id ?: run { currentPosts = emptyList(); return@LaunchedEffect }
        // Resolve the resume position EXACTLY ONCE (a re-resume after the play effect marks the status
        // seen would skip it and jump/flash). Cached creators (all YidStatus + prefetched JewishStatus)
        // seed instantly, but the seen set MUST be the AWAITED persisted snapshot, NOT the seenPostIds
        // StateFlow: a freshly-opened viewer has a new ViewModel whose StateFlow is still emptySet for the
        // first frames, which made resume see "nothing seen" and always restart at the first status.
        val cached = viewModel.cachedPosts(id)
        val loaded = cached ?: run { currentPosts = null; postIdx = 0; viewModel.loadPosts(id) }
        val rp = resumePos(loaded, viewModel.seenSnapshot(), todayIso)
        floorIndex = rp.floor
        postIdx = rp.index
        currentPosts = loaded
        postsCreatorIdx = creatorIdx // now the active face may render - its state matches this creator
        // Pull THIS creator's newest statuses right now (the one the user just tapped) and extend the
        // timeline in place if new ones arrived. Auto-cancelled if they swipe away. Playback is NOT
        // disturbed: the play effect keys on the current status id and new statuses append at the end.
        launch {
            val fresh = viewModel.refreshPosts(id)
            if (fresh.isNotEmpty() && fresh != currentPosts) {
                currentPosts = fresh
                postIdx = postIdx.coerceIn(0, fresh.lastIndex)
                floorIndex = floorIndex.coerceIn(0, fresh.lastIndex)
            }
        }
        // Prefetch BOTH neighbors - their POSTS and the THUMBNAIL image of the status they will show - so
        // swiping either way reveals a real frame immediately instead of a black-loading flash.
        listOf(creatorIdx - 1, creatorIdx + 1).forEach { n ->
            creators.getOrNull(n)?.id?.let { pid ->
                launch {
                    val neighborPosts = viewModel.loadPosts(pid)
                    prefetchStatusImage(context, neighborPosts, seenPostIds, todayIso)
                }
            }
        }
    }

    fun advance() {
        // Ignore taps while this creator's posts are still loading; without this the ?: 0 fallback would
        // read last = 0 and step straight to the adjacent creator, skipping the one that was tapped.
        val last = currentPosts?.lastIndex ?: return
        progress = 0f
        // Continue FORWARD along this creator's timeline (across date boundaries); only its newest status
        // rolls on to the next creator - animated as a cube via the pager (or close past the last).
        if (postIdx < last) {
            postIdx++
        } else {
            val next = creatorIdx + 1
            if (next < creators.size) scope.launch { pagerState.animateScrollToPage(next) } else onClose()
        }
    }

    fun goBack() {
        if (currentPosts == null) return // still loading; see advance()
        progress = 0f
        // Don't step below the entry date (use the jump-to-date sheet to go earlier); else previous creator.
        if (postIdx > floorIndex) postIdx-- else if (creatorIdx > 0) scope.launch {
            pagerState.animateScrollToPage(creatorIdx - 1)
        }
    }

    val posts = currentPosts
    // Key on the CURRENT status's identity (not the whole list), so a background refresh that merely
    // appends newer statuses does NOT re-fire this effect and restart the playing video. `posts == null`
    // distinguishes "loading" from "empty" (both have a null current-post id).
    LaunchedEffect(posts?.getOrNull(postIdx)?.id, posts == null) {
        if (posts == null) return@LaunchedEffect // still loading this creator
        // A creator whose posts failed to load or has none: skip to the next creator (or close on the
        // last) instead of getting stuck on a blank, never-advancing screen.
        if (posts.isEmpty()) {
            advance()
            return@LaunchedEffect
        }
        val post = posts.getOrNull(postIdx) ?: return@LaunchedEffect

        // WhatsApp "seen": mark the status viewed as soon as it is shown (persisted; mutes the ring).
        viewModel.markSeen(post.id)

        progress = 0f
        videoRendered = false // hold the thumbnail until THIS status's video draws its first frame
        exoPlayer.stop()

        if (post.kind == "video" && post.mediaPath != null) {
            val uri = statusMediaUrl(post.mediaPath) ?: return@LaunchedEffect
            exoPlayer.setMediaItem(MediaItem.fromUri(uri))
            exoPlayer.prepare()
            exoPlayer.play()
            var lastPos = -1L
            var stalledMs = 0f
            var prevFrame = 0L
            while (true) {
                // Drive on the DISPLAY frame clock (not a fixed 80ms tick) so the bar is perfectly smooth.
                withFrameNanos { now ->
                    val dt = if (prevFrame == 0L) 0f else (now - prevFrame) / 1_000_000f
                    prevFrame = now
                    val pos = exoPlayer.currentPosition
                    // Stall detector: accrue real time only while playback is INTENDED (playWhenReady) and
                    // the position is not advancing; the dt cap ignores background gaps (frame clock pauses
                    // when off-screen, then resumes with a huge dt). Reset the moment position advances.
                    if (exoPlayer.playWhenReady && pos <= lastPos) {
                        if (dt in 0f..100f) stalledMs += dt
                    } else {
                        stalledMs = 0f
                    }
                    lastPos = pos
                }
                val dur = exoPlayer.duration
                if (dur > 0L) progress = (exoPlayer.currentPosition.toFloat() / dur).coerceIn(0f, 1f)
                val ended = exoPlayer.playbackState == Player.STATE_ENDED
                val failed = exoPlayer.playerError != null // bad/expired URL: sets playerError, never ENDs
                if (ended || failed || stalledMs >= VIDEO_STALL_TIMEOUT_MS || progress >= 0.99f) break
            }
            exoPlayer.stop()
        } else {
            // Image/text: hold the API-provided duration (the site defaults to 7s). Advance real elapsed
            // time on the display frame clock for a perfectly smooth bar; the dt cap freezes it while
            // backgrounded (frame clock pauses) and the `paused` check freezes it during a press-hold.
            val durationMs = (post.durationSeconds ?: 7) * 1000f
            var elapsed = 0f
            var prevFrame = 0L
            while (elapsed < durationMs) {
                withFrameNanos { now ->
                    val dt = if (prevFrame == 0L) 0f else (now - prevFrame) / 1_000_000f
                    prevFrame = now
                    if (!paused && dt in 0f..100f) elapsed += dt
                }
                progress = (elapsed / durationMs).coerceIn(0f, 1f)
            }
        }
        advance()
    }

    val creator = creators.getOrNull(creatorIdx)
    val currentPost = posts?.getOrNull(postIdx)
    val hasCaption = currentPost?.kind != "text" && !currentPost?.caption.isNullOrBlank()
    // The "jump to date" affordance shows only when the creator posted on more than one date.
    val canJumpDate = posts != null && dateGroups.size > 1

    // Each creator is a pager page; scrolling between them turns a 3D cube. Only the SETTLED creator gets
    // the live face (media + progress + gestures); neighbors show a lightweight avatar face so the cube has
    // something to reveal mid-swipe. Horizontal swipe -> creator (handled by the pager); tap -> status.
    HorizontalPager(
        state = pagerState,
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        beyondViewportPageCount = 1,
    ) { page ->
      Box(
          Modifier
              .fillMaxSize()
              .graphicsLayer {
                  val pageOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                  val off = pageOffset.coerceIn(-1f, 1f)
                  cameraDistance = 20f * density
                  transformOrigin = TransformOrigin(if (off < 0f) 0f else 1f, 0.5f)
                  rotationY = (if (off < 0f) 90f else -90f) * abs(off)
              },
      ) {
        // Show the live face only when the loaded posts belong to THIS creator; until the driver catches
        // up after a settle (and for every non-active page) show the correct static preview, which loads
        // this creator's resume status independently. This hands the cube's thumbnail straight to the
        // active face with no stale-content or black flash in between.
        if (page != creatorIdx || postsCreatorIdx != creatorIdx) {
            StatusPreviewFace(creators[page], viewModel, seenPostIds, todayIso)
            return@Box
        }
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(creatorIdx, postIdx) {
                    detectTapGestures(
                        // Pause the moment a finger is down; resume on release. onLongPress (even empty)
                        // must be set so releasing a HOLD does not also fire onTap and navigate.
                        onPress = {
                            paused = true
                            tryAwaitRelease()
                            paused = false
                        },
                        onLongPress = {},
                        onTap = { offset ->
                            if (offset.x < size.width * 0.35f) goBack() else advance()
                        },
                    )
                },
        ) {
        // Content.
        if (posts == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp)
            }
        } else {
            Crossfade(targetState = currentPost, label = "post") { post ->
                when (post?.kind) {
                    "video" -> Box(Modifier.fillMaxSize()) {
                        AndroidView(
                            factory = { ctx ->
                                PlayerView(ctx).apply {
                                    player = exoPlayer
                                    useController = false
                                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                    layoutParams = ViewGroup.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                        // Hold the thumbnail over the (black) player surface until the video actually
                        // draws its first frame, so settling on a video shows a frame, never a black gap.
                        if (!videoRendered) {
                            post.thumbPath?.let { thumb ->
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(statusMediaUrl(thumb))
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                    }
                    "image" -> AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(statusMediaUrl(post.mediaPath))
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                    // A pure-text status: its body is `text_body` (NOT caption), on the post's own
                    // `text_bg_color` when present (parsed at runtime), else a themed surface.
                    "text" -> {
                        val parsedBg = post.textBgColor?.let {
                            runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull()
                        }
                        Box(
                            Modifier.fillMaxSize().background(parsedBg ?: colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = post.textBody ?: post.caption ?: "",
                                color = if (parsedBg != null) Color.White else colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(32.dp),
                            )
                        }
                    }
                    else -> Box(Modifier.fillMaxSize().background(colorScheme.surface))
                }
            }
        }

        // Top overlay: segment progress bars + creator header (over the media scrim). The viewer is
        // edge-to-edge, so inset the header below the system status bar.
        Column(
            Modifier
                .fillMaxWidth()
                .background(scrim)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 8.dp, vertical = 8.dp),
        ) {
            // Header bar: the shared back button + "Music Status" title, like every other screen. Forced
            // white for legibility over the media.
            CompositionLocalProvider(LocalContentColor provides Color.White) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    BackNavigationIcon(navController = navController)
                    AppBarTitle(stringResource(R.string.statuses), modifier = Modifier.weight(1f))
                }
            }

            Spacer(Modifier.height(8.dp))

            // One segment per status in the CURRENT date's window. `progress` is updated on the display
            // frame clock (see the driver), so the active segment fills perfectly smoothly.
            Row(Modifier.fillMaxWidth()) {
                for (i in windowStart..windowEnd) {
                    val fill = when {
                        posts == null -> 0f
                        i < postIdx -> 1f
                        i == postIdx -> progress
                        else -> 0f
                    }
                    Box(
                        Modifier
                            .weight(1f)
                            .height(2.dp)
                            .padding(horizontal = 1.dp)
                            .clip(RoundedCornerShape(1.dp))
                            .background(Color.White.copy(alpha = 0.35f)),
                    ) {
                        Box(Modifier.fillMaxHeight().fillMaxWidth(fill).background(Color.White))
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(statusAvatarUrl(creator?.avatarPath))
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(colorScheme.surfaceVariant),
                )

                Spacer(Modifier.width(10.dp))

                Column {
                    Text(
                        text = creator?.displayName ?: "",
                        color = Color.White,
                        style = MaterialTheme.typography.titleSmall,
                        fontFamily = HeaderFontFamily,
                        fontWeight = FontWeight.SemiBold,
                    )
                    val ts = currentPost?.postedAt?.let { formatPostedAt(it) }
                    if (!ts.isNullOrEmpty()) {
                        Text(
                            text = ts,
                            color = Color.White.copy(alpha = 0.55f),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }

        // Bottom overlay: a "jump to date" chevron (when the creator posted on more than one day), then
        // the caption. Inset above the system navigation bar so nothing is clipped by the gesture pill.
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (canJumpDate && !showDateSheet) {
                Row(
                    Modifier
                        .padding(bottom = 12.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.2f))
                        .clickable { showDateSheet = true }
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.expand_less),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.jump_to_date),
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            if (hasCaption) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(scrim)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Text(
                        text = currentPost?.caption ?: "",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        // "Jump to date" sheet (JewishStatus-style): a dismiss scrim + a bottom panel of the creator's
        // post dates (day-of-week, day number, post count). Tapping a date switches the visible window.
        if (showDateSheet) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { showDateSheet = false },
            )
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(colorScheme.surface)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 16.dp, vertical = 16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.jump_to_date),
                        color = colorScheme.onSurface,
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = HeaderFontFamily,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.weight(1f))
                    Icon(
                        painter = painterResource(R.drawable.close),
                        contentDescription = stringResource(R.string.close),
                        tint = colorScheme.onSurface,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { showDateSheet = false }
                            .padding(4.dp),
                    )
                }
                Spacer(Modifier.height(12.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(dateGroups, key = { it.iso }) { group ->
                        StatusDateCard(
                            group = group,
                            selected = group.iso == currentGroup?.iso,
                            onClick = {
                                // Start the timeline at this date; browsing forward rolls into later dates.
                                postIdx = group.startIndex
                                floorIndex = group.startIndex
                                progress = 0f
                                showDateSheet = false
                            },
                        )
                    }
                }
            }
        }
        } // active-creator face
      } // cube page
    } // HorizontalPager
}

/**
 * The face shown for a NON-active creator during a cube swipe. Loads the creator's posts (cached) and
 * renders the SAME status the viewer will resume to (image, text, or a video's thumbnail frame) - no
 * player, no auto-advance - so both sides of the cube show real content. Falls back to the avatar while
 * the posts load or when there is nothing to show.
 */
@Composable
private fun StatusPreviewFace(
    creator: StatusCreator,
    viewModel: StoryViewModel,
    seenPostIds: Set<String>,
    todayIso: String,
) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    // Seed from the synchronous cache (neighbors are prefetched) so an already-loaded creator shows its
    // real status on the very first frame instead of flashing the avatar; produceState fills in if not.
    val initial = remember(creator.id) { viewModel.cachedPosts(creator.id) }
    val posts by produceState(initial, creator.id) {
        value = viewModel.loadPosts(creator.id)
    }
    // AWAIT the persisted seen (the passed StateFlow is emptySet for the first frames of a freshly-opened
    // viewer), so the preview resumes to the SAME status the active face will - not always the first.
    val seen by produceState(seenPostIds, Unit) { value = viewModel.seenSnapshot() }
    val post = posts?.takeIf { it.isNotEmpty() }?.let { it[resumePos(it, seen, todayIso).index] }

    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        val thumb = when (post?.kind) {
            "image" -> statusMediaUrl(post.mediaPath)
            "video" -> post.thumbPath?.let { statusMediaUrl(it) }
            else -> null
        }
        when {
            post?.kind == "text" -> {
                val parsedBg = post.textBgColor?.let {
                    runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull()
                }
                Box(
                    Modifier.fillMaxSize().background(parsedBg ?: colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = post.textBody ?: post.caption ?: "",
                        color = if (parsedBg != null) Color.White else colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(32.dp),
                    )
                }
            }
            thumb != null -> AsyncImage(
                model = ImageRequest.Builder(context).data(thumb).crossfade(true).build(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
            // Posts still loading, or a video with no thumbnail: fall back to the avatar.
            else -> AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(statusAvatarUrl(creator.avatarPath))
                    .crossfade(true)
                    .build(),
                contentDescription = creator.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(colorScheme.surfaceVariant),
            )
        }
    }
}

/** One date card in the "jump to date" sheet: day-of-week, day number, and post count. App-themed. */
@Composable
private fun StatusDateCard(group: StatusDateGroup, selected: Boolean, onClick: () -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
    val date = remember(group.iso) { runCatching { LocalDate.parse(group.iso) }.getOrNull() }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) colorScheme.primaryContainer else colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
    ) {
        Text(
            text = date?.format(dowFmt)?.uppercase(Locale.US) ?: "",
            color = if (selected) colorScheme.onPrimaryContainer else colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
            fontFamily = HeaderFontFamily,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = date?.format(dayFmt) ?: "",
            color = if (selected) colorScheme.primary else colorScheme.onSurface,
            style = MaterialTheme.typography.headlineSmall,
            fontFamily = HeaderFontFamily,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = pluralStringResource(R.plurals.n_status_post, group.count, group.count),
            color = if (selected) colorScheme.onPrimaryContainer else colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}
