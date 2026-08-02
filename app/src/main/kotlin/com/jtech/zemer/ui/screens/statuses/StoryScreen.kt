package com.jtech.zemer.ui.screens.statuses

import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.jtech.zemer.LocalPlayerConnection
import com.jtech.zemer.ui.component.BackNavigationIcon
import com.jtech.zemer.ui.component.VerifiedBadge
import com.jtech.zemer.statuses.StatusPost
import com.jtech.zemer.statuses.statusAvatarUrl
import com.jtech.zemer.statuses.statusMediaUrl
import com.jtech.zemer.viewmodels.StoryViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

// A status video whose position hasn't advanced for this long while playback is intended (buffering
// forever at the start OR stalling mid-stream, with no error) is treated as failed and skipped.
private const val VIDEO_STALL_TIMEOUT_MS = 12_000L

private val tsFmt = DateTimeFormatter.ofPattern("MMM d · h:mm a", Locale.US)

// Convert the post's UTC/offset timestamp to the DEVICE's local zone before formatting, or the shown
// time is off by the user's UTC offset (which read as "wrong times").
private fun formatPostedAt(postedAt: String): String = try {
    tsFmt.format(ZonedDateTime.parse(postedAt).withZoneSameInstant(ZoneId.systemDefault()))
} catch (_: Exception) { "" }

/** The post's DEVICE-local calendar date ("YYYY-MM-DD"), or null if unparseable. */
private fun localDate(postedAt: String): String? = try {
    ZonedDateTime.parse(postedAt).withZoneSameInstant(ZoneId.systemDefault()).toLocalDate().toString()
} catch (_: Exception) { null }

private val dowFmt = DateTimeFormatter.ofPattern("EEE", Locale.US)
private val dayFmt = DateTimeFormatter.ofPattern("d", Locale.US)

/** One date the creator posted on: its ISO date, the index of its first post, and its post count. */
private data class StatusDateGroup(val iso: String, val startIndex: Int, val count: Int)

/**
 * Group a creator's posts (sorted oldest-first, so a date's posts are contiguous) by local date, in
 * chronological order — the data behind the "jump to date" sheet.
 */
private fun statusDateGroups(posts: List<StatusPost>?): List<StatusDateGroup> {
    if (posts.isNullOrEmpty()) return emptyList()
    val groups = mutableListOf<StatusDateGroup>()
    var i = 0
    while (i < posts.size) {
        val d = localDate(posts[i].postedAt) ?: "?"
        var j = i + 1
        while (j < posts.size && (localDate(posts[j].postedAt) ?: "?") == d) j++
        groups.add(StatusDateGroup(iso = d, startIndex = i, count = j - i))
        i = j
    }
    return groups
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
    val loadAttempted by viewModel.loadAttempted.collectAsState()
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

    // Resolve the stable id to the current index; a gone creator falls back to the first.
    var creatorIdx by remember {
        mutableIntStateOf(creators.indexOfFirst { it.id == initialCreatorId }.coerceAtLeast(0))
    }
    var postIdx by remember { mutableIntStateOf(0) }
    var progress by remember { mutableFloatStateOf(0f) }
    var currentPosts by remember { mutableStateOf<List<StatusPost>?>(null) }
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

    // Load posts for the current creator; preload the next in the background.
    LaunchedEffect(creatorIdx) {
        currentPosts = null
        postIdx = 0
        progress = 0f
        exoPlayer.stop()
        val id = creators.getOrNull(creatorIdx)?.id ?: return@LaunchedEffect
        val loaded = viewModel.loadPosts(id)
        // Default to TODAY's date window (or the newest date if none today) and resume at its first UNSEEN
        // status (WhatsApp); if all of that date is seen, land on its newest. AWAIT the persisted seen set
        // (the StateFlow snapshot is still empty for the first frames after open while DataStore loads).
        val seen = viewModel.seenSnapshot()
        postIdx = if (loaded.isEmpty()) 0 else {
            val defaultIso = if (loaded.any { localDate(it.postedAt) == todayIso }) todayIso
            else localDate(loaded.last().postedAt)
            val wStart = loaded.indexOfFirst { localDate(it.postedAt) == defaultIso }.coerceAtLeast(0)
            val wEnd = loaded.indexOfLast { localDate(it.postedAt) == defaultIso }.coerceAtLeast(wStart)
            floorIndex = wStart
            (wStart..wEnd).firstOrNull { loaded[it].id !in seen } ?: wEnd
        }
        currentPosts = loaded
        creators.getOrNull(creatorIdx + 1)?.id?.let { nextId -> launch { viewModel.loadPosts(nextId) } }
    }

    fun advance() {
        // Ignore taps while this creator's posts are still loading; without this the ?: 0 fallback would
        // read last = 0 and step straight to the adjacent creator, skipping the one that was tapped.
        val last = currentPosts?.lastIndex ?: return
        progress = 0f
        // Continue FORWARD along this creator's timeline (across date boundaries); only its newest status
        // moves on to the next creator.
        if (postIdx < last) {
            postIdx++
        } else {
            val next = creatorIdx + 1
            if (next < creators.size) creatorIdx = next else onClose()
        }
    }

    fun goBack() {
        if (currentPosts == null) return // still loading; see advance()
        progress = 0f
        // Don't step below the entry date (use the jump-to-date sheet to go earlier); else previous creator.
        if (postIdx > floorIndex) postIdx-- else if (creatorIdx > 0) creatorIdx--
    }

    val posts = currentPosts
    LaunchedEffect(posts, postIdx) {
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
        exoPlayer.stop()

        if (post.kind == "video" && post.mediaPath != null) {
            val uri = statusMediaUrl(post.mediaPath) ?: return@LaunchedEffect
            exoPlayer.setMediaItem(MediaItem.fromUri(uri))
            exoPlayer.prepare()
            exoPlayer.play()
            var lastPos = -1L
            var stalledMs = 0L
            while (true) {
                delay(80)
                val dur = exoPlayer.duration
                val pos = exoPlayer.currentPosition
                if (dur > 0L) progress = (pos.toFloat() / dur).coerceIn(0f, 1f)
                // Stall detector: count time only while playback is INTENDED (playWhenReady) but the
                // position is not advancing. This catches a clip that buffers forever with no error at
                // the start OR mid-stream, yet never fires while paused/backgrounded (the lifecycle
                // observer clears playWhenReady), so the timer freezes off-screen instead of skipping.
                if (exoPlayer.playWhenReady && pos <= lastPos) stalledMs += 80 else stalledMs = 0
                lastPos = pos
                val ended = exoPlayer.playbackState == Player.STATE_ENDED
                // A failed video (bad/expired URL) sets playerError and never ENDs.
                val failed = exoPlayer.playerError != null
                if (ended || failed || stalledMs >= VIDEO_STALL_TIMEOUT_MS || progress >= 0.99f) break
            }
            exoPlayer.stop()
        } else {
            // Image/text: hold the API-provided duration (the site defaults to 7s).
            val durationMs = (post.durationSeconds ?: 7) * 1000L
            val tickMs = 80L
            var elapsed = 0L
            while (elapsed < durationMs) {
                delay(tickMs)
                // Freeze the timer while backgrounded (the composable is stopped, not disposed), so it
                // does not advance / mark-seen subsequent statuses off-screen - mirrors the video pause.
                if (!lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) continue
                elapsed += tickMs
                progress = (elapsed.toFloat() / durationMs).coerceIn(0f, 1f)
            }
        }
        advance()
    }

    val creator = creators.getOrNull(creatorIdx)
    val currentPost = posts?.getOrNull(postIdx)
    val hasCaption = currentPost?.kind != "text" && !currentPost?.caption.isNullOrBlank()
    // The "jump to date" affordance shows only when the creator posted on more than one date.
    val canJumpDate = posts != null && dateGroups.size > 1

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(creatorIdx, postIdx) {
                detectTapGestures { offset ->
                    if (offset.x < size.width * 0.35f) goBack() else advance()
                }
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
                    "video" -> AndroidView(
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
            // Controls: a clear back button on the left. The shared BackNavigationIcon (navigateUp) is
            // forced white for legibility over the media.
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                CompositionLocalProvider(LocalContentColor provides Color.White) {
                    BackNavigationIcon(navController = navController)
                }
                Spacer(Modifier.weight(1f))
            }

            Spacer(Modifier.height(8.dp))

            // One segment per status in the CURRENT date's window.
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
                Box(contentAlignment = Alignment.BottomEnd) {
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
                    if (creator?.isVerified == true) {
                        VerifiedBadge(size = 14.dp)
                    }
                }

                Spacer(Modifier.width(10.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = creator?.displayName ?: "",
                            color = Color.White,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (creator?.liveNow == true) {
                            Spacer(Modifier.width(6.dp))
                            Text(
                                stringResource(R.string.station_live_badge),
                                color = colorScheme.primary,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    val ts = currentPost?.postedAt?.let { formatPostedAt(it) }
                    if (!ts.isNullOrEmpty()) {
                        Text(
                            text = ts,
                            color = Color.White.copy(alpha = 0.55f),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }

                Spacer(Modifier.weight(1f))
                Text(
                    text = "${creatorIdx + 1} / ${creators.size}",
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelMedium,
                )
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
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = date?.format(dayFmt) ?: "",
            color = if (selected) colorScheme.onPrimaryContainer else colorScheme.onSurface,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = pluralStringResource(R.plurals.n_status_post, group.count, group.count),
            color = if (selected) colorScheme.onPrimaryContainer else colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}
