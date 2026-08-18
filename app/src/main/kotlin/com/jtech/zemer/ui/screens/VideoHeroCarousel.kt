@file:OptIn(
    ExperimentalFoundationApi::class,
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
)

package com.jtech.zemer.ui.screens

import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.carousel.CarouselDefaults
import androidx.compose.material3.carousel.CarouselItemScope
import androidx.compose.material3.carousel.HorizontalUncontainedCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.jtech.zemer.R
import com.jtech.zemer.models.toMediaMetadata
import com.jtech.zemer.models.MediaMetadata
import com.jtech.zemer.playback.PlayerConnection
import com.jtech.zemer.playback.queues.ZemerRadioQueue
import com.jtech.zemer.tracking.Tracker
import com.jtech.zemer.ui.component.ActiveBoxAlpha
import com.jtech.zemer.ui.component.HeroTitleOverlay
import com.jtech.zemer.ui.component.MenuState
import com.jtech.zemer.ui.component.NavigationTitle
import com.jtech.zemer.ui.component.PlayingIndicatorBox
import com.jtech.zemer.ui.component.PreparingOverlay
import com.jtech.zemer.ui.component.focusVisualsEnabled
import com.jtech.zemer.ui.component.rememberIsPreparing
import com.jtech.zemer.ui.menu.YouTubeSongMenu
import com.jtech.zemer.viewmodels.HomeSeeAllRow
import com.metrolist.innertube.models.SongItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * The Videos-tab lead shelf: a Material 3 Expressive multi-browse carousel of FULL 16:9 video heroes
 * (cover-fill artwork with the title + artist over a scrim), the video sibling of the Home Latest
 * Releases carousel. Replaces the small-square [videoSongsRow] treatment for Trending Videos only.
 *
 * It preserves every video-tab rule the row had: the relabel-aware header ([HomeSeeAllRow.displayTitleRes])
 * + see-all arrow, audio-first taps declaring [playSource], long-press menus audio-gated when videos are
 * blocked, and per-item impressions on [surface] - reported when a hero SETTLES centered (see
 * [VideoHeroCarouselItem]), so the exposure-dampener signal is not lost by moving off a LazyRow.
 */
fun LazyListScope.videoHeroCarousel(
    row: HomeSeeAllRow,
    keyPrefix: String,
    surface: String,
    playSource: String?,
    videos: List<SongItem>,
    blockVideos: Boolean,
    navController: NavController,
    playerConnection: PlayerConnection,
    menuState: MenuState,
    haptic: HapticFeedback,
    scope: CoroutineScope,
    mediaMetadata: MediaMetadata?,
    isPlaying: Boolean,
) {
    val unique = videos.distinctBy { it.id }
    if (unique.isEmpty()) return
    item(key = "${keyPrefix}_title", contentType = "header") {
        NavigationTitle(
            title = stringResource(row.displayTitleRes(blockVideos)),
            onClick = { navController.navigate("home_see_all/${row.slug}") },
            modifier = Modifier.animateItem(),
        )
    }
    item(key = "${keyPrefix}_carousel", contentType = "video_hero_carousel") {
        val carouselState = rememberCarouselState { unique.size }
        // ONE big 16:9 hero that nearly fills the width with a small peek of the next - a uniform-size
        // (UNCONTAINED) carousel, NOT multi-browse (which shrinks the neighbours into small cards).
        val screenWidthDp = LocalConfiguration.current.screenWidthDp
        val heroW = (screenWidthDp - 52).coerceAtLeast(240)
        val heroH = heroW * 9 / 16
        HorizontalUncontainedCarousel(
            state = carouselState,
            itemWidth = heroW.dp,
            itemSpacing = 8.dp,
            // One item per swipe at a fixed speed, matching the Latest Releases carousel.
            flingBehavior = CarouselDefaults.singleAdvanceFlingBehavior(
                carouselState,
                tween(durationMillis = 400),
            ),
            contentPadding = PaddingValues(horizontal = 12.dp),
            modifier = Modifier
                .padding(vertical = 12.dp)
                .height(heroH.dp)
                .animateItem(),
        ) { index ->
            val video = unique[index]
            VideoHeroCarouselItem(
                video = video,
                isActive = mediaMetadata?.id == video.id,
                isPlaying = isPlaying,
                surface = surface,
                onClick = {
                    // Audio-first always (I2); video is a per-play in-player toggle (D3).
                    playerConnection.playQueue(
                        if (playSource != null) {
                            ZemerRadioQueue.song(video.toMediaMetadata(), playerConnection.service, playSource)
                        } else {
                            ZemerRadioQueue.song(video.toMediaMetadata(), playerConnection.service)
                        }
                    )
                },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    menuState.show {
                        YouTubeSongMenu(
                            song = video,
                            navController = navController,
                            onDismiss = menuState::dismiss,
                            // A blocked-video user gets the audio-only menu (no watch/download-video).
                            isVideo = video.isVideo && !blockVideos,
                        )
                    }
                },
            )
        }
    }
}

/**
 * One 16:9 video hero. The full artwork fills the card (ContentScale.Crop trims YouTube's 4:3 letterbox
 * to a clean 16:9), with the title + artist over a bottom scrim, a D-pad focus ring (drawn OVER the art
 * via the carousel's [maskBorder] so it follows the item morph), the now-playing indicator and the
 * tap-to-play [rememberIsPreparing] spinner.
 *
 * Impressions: a hero reports its own impression on [surface] once it is the fully-revealed, SETTLED
 * item for ~300ms (never a hero a fling passed through). [Tracker.impression] dedups per
 * (surface, videoId), so re-settling on the same hero never double-counts.
 */
@Composable
fun CarouselItemScope.VideoHeroCarouselItem(
    video: SongItem,
    isActive: Boolean,
    isPlaying: Boolean,
    surface: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val itemShape = MaterialTheme.shapes.extraLarge
    var focused by remember { mutableStateOf(false) }
    val ringColor by animateColorAsState(
        targetValue = if (focused && focusVisualsEnabled()) MaterialTheme.colorScheme.primary else Color.Transparent,
        label = "video_hero_focus",
    )
    val preparing = rememberIsPreparing(video.id)
    val showActive = isActive && !preparing

    // Report an impression when this hero is the fully-revealed, settled item (~300ms). carouselItemDrawInfo
    // exposes the item's live revealed size; size >= maxSize means it is the centred hero.
    val drawInfo = carouselItemDrawInfo
    LaunchedEffectImpression(video.id, surface, drawInfo::size, drawInfo::maxSize)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .maskClip(itemShape)
            .maskBorder(BorderStroke(3.dp, ringColor), itemShape)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        AsyncImage(
            model = video.thumbnail,
            contentDescription = video.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        PlayingIndicatorBox(
            isActive = showActive,
            playWhenReady = isPlaying,
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = ActiveBoxAlpha), shape = itemShape),
        )
        HeroTitleOverlay(
            title = video.title,
            subtitle = video.artists.joinToString { it.name }.ifEmpty { null },
            titleStyle = MaterialTheme.typography.titleMedium,
            subtitleStyle = MaterialTheme.typography.bodyMedium,
        )
        if (preparing) {
            PreparingOverlay(shape = itemShape)
        }
    }
}

/**
 * Fires [Tracker.impression] for [videoId] on [surface] once the item has been fully revealed (its live
 * [size] reached [maxSize]) and stayed there ~300ms - the strict "settled, on-screen" impression rule.
 * A fling that passes the item through cancels before the 300ms elapses (collectLatest).
 */
@Composable
private fun LaunchedEffectImpression(
    videoId: String,
    surface: String,
    size: () -> Float,
    maxSize: () -> Float,
) {
    androidx.compose.runtime.LaunchedEffect(videoId, surface) {
        snapshotFlow { size() >= maxSize() - 0.5f }
            .distinctUntilChanged()
            .collectLatest { settled ->
                if (settled) {
                    delay(300)
                    Tracker.impression(listOf(videoId), surface)
                }
            }
    }
}
