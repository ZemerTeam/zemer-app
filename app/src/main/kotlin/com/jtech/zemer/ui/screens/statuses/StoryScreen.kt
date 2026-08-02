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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jtech.zemer.R
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
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

private val tsFmt = DateTimeFormatter.ofPattern("MMM d · h:mm a", Locale.US)

// Convert the post's UTC/offset timestamp to the DEVICE's local zone before formatting, or the shown
// time is off by the user's UTC offset (which read as "wrong times").
private fun formatPostedAt(postedAt: String): String = try {
    tsFmt.format(ZonedDateTime.parse(postedAt).withZoneSameInstant(ZoneId.systemDefault()))
} catch (_: Exception) { "" }

/**
 * Full-screen WhatsApp/Stories-style viewer for JewishStatus creators. Reachable only from the Home
 * "Music Statuses" row; [initialCreatorIdx] is the tapped creator's index in the row's list. Advances
 * across creators, tap-left-35% = back / tap-right = forward, auto-advance (video plays to its end,
 * image/text hold [StatusPost.durationSeconds] or 7s).
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
    initialCreatorIdx: Int,
) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    val scrim = colorScheme.scrim.copy(alpha = 0.8f)
    val viewModel: StoryViewModel = hiltViewModel()
    val creators by viewModel.creators.collectAsState()
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

    // Creators come from the shared session cache (warm from the Home row), so this resolves within a
    // frame; guard the empty window before it lands.
    if (creators.isEmpty()) {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp)
        }
        return
    }

    var creatorIdx by remember { mutableIntStateOf(initialCreatorIdx.coerceIn(0, creators.lastIndex)) }
    var postIdx by remember { mutableIntStateOf(0) }
    var progress by remember { mutableFloatStateOf(0f) }
    var currentPosts by remember { mutableStateOf<List<StatusPost>?>(null) }

    // Load posts for the current creator; preload the next in the background.
    LaunchedEffect(creatorIdx) {
        currentPosts = null
        postIdx = 0
        progress = 0f
        exoPlayer.stop()
        val id = creators.getOrNull(creatorIdx)?.id ?: return@LaunchedEffect
        val loaded = viewModel.loadPosts(id)
        // Resume at the first UNSEEN status (WhatsApp), so reopening does not restart from the top.
        // All-seen falls back to 0 (replay from the newest). Snapshot the seen set at load time.
        val seen = viewModel.seenPostIds.value
        postIdx = loaded.indexOfFirst { it.id !in seen }.takeIf { it >= 0 } ?: 0
        currentPosts = loaded
        creators.getOrNull(creatorIdx + 1)?.id?.let { nextId -> launch { viewModel.loadPosts(nextId) } }
    }

    fun advance() {
        val posts = currentPosts ?: return
        progress = 0f
        if (postIdx < posts.lastIndex) {
            postIdx++
        } else {
            val next = creatorIdx + 1
            if (next < creators.size) creatorIdx = next else onClose()
        }
    }

    fun goBack() {
        progress = 0f
        if (postIdx > 0) postIdx-- else if (creatorIdx > 0) creatorIdx--
    }

    val posts = currentPosts
    LaunchedEffect(posts, postIdx) {
        if (posts.isNullOrEmpty()) return@LaunchedEffect
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
            while (true) {
                delay(80)
                val dur = exoPlayer.duration
                val pos = exoPlayer.currentPosition
                if (dur > 0L) progress = (pos.toFloat() / dur).coerceIn(0f, 1f)
                if (exoPlayer.playbackState == Player.STATE_ENDED || progress >= 0.99f) break
            }
            exoPlayer.stop()
        } else {
            // Image/text: hold the API-provided duration (the site defaults to 7s).
            val durationMs = (post.durationSeconds ?: 7) * 1000L
            val tickMs = 80L
            var elapsed = 0L
            while (elapsed < durationMs) {
                delay(tickMs)
                elapsed += tickMs
                progress = (elapsed.toFloat() / durationMs).coerceIn(0f, 1f)
            }
        }
        advance()
    }

    val creator = creators.getOrNull(creatorIdx)
    val currentPost = posts?.getOrNull(postIdx)
    val hasCaption = currentPost?.kind != "text" && !currentPost?.caption.isNullOrBlank()
    // Posts are chronological (oldest first); "Today" jumps to the FIRST status posted today. Compare on
    // the device-local date so it matches the timestamps shown (the raw posted_at is UTC).
    val todayIso = remember { LocalDate.now().toString() }
    val todayIndex = posts?.indexOfFirst {
        runCatching {
            ZonedDateTime.parse(it.postedAt).withZoneSameInstant(ZoneId.systemDefault())
                .toLocalDate().toString() == todayIso
        }.getOrDefault(false)
    }?.takeIf { it >= 0 }

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
            // Controls: a clear back button on the left, and a jump-to-today shortcut on the right. The
            // shared BackNavigationIcon (navigateUp) is forced white for legibility over the media.
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                CompositionLocalProvider(LocalContentColor provides Color.White) {
                    BackNavigationIcon(navController = navController)
                }
                Spacer(Modifier.weight(1f))
                if (todayIndex != null && todayIndex != postIdx) {
                    Text(
                        text = stringResource(R.string.jump_to_today),
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color.White.copy(alpha = 0.2f))
                            .clickable { postIdx = todayIndex; progress = 0f }
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(Modifier.fillMaxWidth()) {
                val count = posts?.size ?: 1
                repeat(count) { i ->
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
                        Box(
                            Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(colorScheme.primary),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "✓",
                                color = colorScheme.onPrimary,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                            )
                        }
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
                                "LIVE",
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

        // Bottom caption (over the media scrim). Inset above the system navigation bar so the text is
        // not clipped by the gesture pill.
        if (hasCaption) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(scrim)
                    .windowInsetsPadding(WindowInsets.navigationBars)
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
}
