@file:OptIn(
    ExperimentalFoundationApi::class,
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
)

package com.jtech.zemer.ui.screens

import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.carousel.CarouselDefaults
import androidx.compose.material3.carousel.CarouselItemScope
import androidx.compose.material3.carousel.HorizontalUncontainedCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.jtech.zemer.models.toMediaMetadata
import com.jtech.zemer.models.MediaMetadata
import com.jtech.zemer.playback.PlayerConnection
import com.jtech.zemer.playback.queues.ZemerRadioQueue
import com.jtech.zemer.tracking.Tracker
import com.jtech.zemer.ui.component.CarouselHeroFrame
import com.jtech.zemer.ui.component.HeroTitleOverlay
import com.jtech.zemer.ui.component.MenuState
import com.jtech.zemer.ui.component.NavigationTitle
import com.jtech.zemer.ui.component.PreparingOverlay
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
    parentListState: LazyListState,
    navController: NavController,
    playerConnection: PlayerConnection,
    menuState: MenuState,
    haptic: HapticFeedback,
    scope: CoroutineScope,
    mediaMetadata: MediaMetadata?,
    isPlaying: Boolean,
) {
    // Callers pass a pre-deduped list (uniqueFeaturedVideos); no second dedup pass here.
    if (videos.isEmpty()) return
    item(key = "${keyPrefix}_title", contentType = "header") {
        NavigationTitle(
            title = stringResource(row.displayTitleRes(blockVideos)),
            onClick = { navController.navigate("home_see_all/${row.slug}") },
            modifier = Modifier.animateItem(),
        )
    }
    item(key = "${keyPrefix}_carousel", contentType = "video_hero_carousel") {
        val carouselKey = "${keyPrefix}_carousel"
        val carouselState = rememberCarouselState { videos.size }
        // The carousel item's OWN visibility in the Home column: impressions must not fire while the shelf
        // is composed-ahead but off-screen (the strict on-screen impression rule the videoSongsRow gated
        // via its parentListState).
        val onScreen by remember(parentListState, carouselKey) {
            derivedStateOf { parentListState.layoutInfo.visibleItemsInfo.any { it.key == carouselKey } }
        }
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
            val video = videos[index]
            VideoHeroCarouselItem(
                video = video,
                isActive = mediaMetadata?.id == video.id,
                isPlaying = isPlaying,
                surface = surface,
                onScreen = { onScreen },
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
    onScreen: () -> Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val itemShape = MaterialTheme.shapes.extraLarge
    val preparing = rememberIsPreparing(video.id)
    val showActive = isActive && !preparing

    // Report an impression when this hero is the fully-revealed, settled item (~300ms). carouselItemDrawInfo
    // exposes the item's live revealed size; size >= maxSize means it is the centred hero.
    val drawInfo = carouselItemDrawInfo
    LaunchedEffectImpression(video.id, surface, onScreen, drawInfo::size, drawInfo::maxSize)

    CarouselHeroFrame(
        thumbnailUrl = video.thumbnail,
        contentDescription = video.title,
        isActive = showActive,
        isPlaying = isPlaying,
        shape = itemShape,
        focusLabel = "video_hero_focus",
        onClick = onClick,
        onLongClick = onLongClick,
    ) {
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
    onScreen: () -> Boolean,
    size: () -> Float,
    maxSize: () -> Float,
) {
    androidx.compose.runtime.LaunchedEffect(videoId, surface) {
        // Fully revealed (size reached a REAL maxSize) AND the carousel is on-screen in the Home column.
        // maxSize() > 0 rejects the initial 0/0 draw-info state, which would otherwise read as "settled".
        snapshotFlow { onScreen() && maxSize() > 0f && size() >= maxSize() - 0.5f }
            .distinctUntilChanged()
            .collectLatest { settled ->
                if (settled) {
                    delay(300)
                    Tracker.impression(listOf(videoId), surface)
                }
            }
    }
}
