@file:OptIn(
    ExperimentalFoundationApi::class,
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
)

package com.jtech.zemer.latestreleases

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.carousel.CarouselItemScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.jtech.zemer.LocalDatabase
import com.jtech.zemer.di.zemerSearchRepository
import com.jtech.zemer.models.MediaMetadata
import com.jtech.zemer.playback.PlayerConnection
import com.jtech.zemer.ui.component.AlbumPlayButton
import com.jtech.zemer.ui.component.CarouselHeroFrame
import com.jtech.zemer.ui.component.HeroTitleOverlay
import com.jtech.zemer.ui.component.LocalMenuState
import com.jtech.zemer.ui.component.OverlayPlayButton
import com.jtech.zemer.ui.component.expressivePlayingShape
import com.jtech.zemer.ui.component.rememberActivationPopScale
import com.jtech.zemer.ui.component.rememberIsPreparing
import com.jtech.zemer.ui.menu.ytItemMenu
import com.jtech.zemer.utils.joinByBullet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * One Latest Releases card as the Home shelf renders it today: a Material 3 Expressive multi-browse
 * carousel hero (a large cover with shape-morphing, peeking neighbours). It exists as ONE component so
 * the hero and the "See all" list ([LatestReleaseCard]) can't drift on the shared behaviour - the same
 * tap ([openOrPlay]: play a single / open an album), long-press [ytItemMenu], now-playing state, and the
 * direct album play button.
 *
 * A [CarouselItemScope] extension so it can [maskClip]/[maskBorder] against the carousel's live item
 * mask. It restores the two things the cover-only rewrite dropped:
 * - **D-pad focus** - [focusable] plus a [maskBorder] ring (accent, following the carousel morph) that
 *   shows only in a key-driven session ([focusVisualsEnabled]); the shelf is otherwise invisible to a
 *   TV remote, breaking the app's 100%-D-pad-navigable rule.
 * - **Library badges** - the [ReleaseBadges] row (download progress / liked / explicit) the card
 *   carried, forced white so it reads on the cover's dark scrim.
 */
@Composable
fun CarouselItemScope.LatestReleaseCarouselItem(
    release: LatestRelease,
    navController: NavController,
    playerConnection: PlayerConnection,
    mediaMetadata: MediaMetadata?,
    isPlaying: Boolean,
    coroutineScope: CoroutineScope,
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val isActive = release.isNowPlaying(mediaMetadata)
    // A single that is resolving/buffering shows the loading spinner instead of the play icon (an album
    // resolves its own spinner inside AlbumPlayButton). `showActive` withholds the now-playing treatment
    // until audio actually starts.
    val preparing = rememberIsPreparing(if (release.isPlayableSingle()) release.sampleVideoId else null)
    val showActive = isActive && !preparing

    val itemShape = MaterialTheme.shapes.extraLarge
    // A playing release pops once as it BECOMES active (rising-edge only) and morphs its cover to the
    // same scalloped expressive shape the card thumbnails use. Keyed to the release: the carousel is
    // index-keyed, so without the key a slot reused for a different release (or the now-playing release
    // scrolling between slots) would fire a spurious pop.
    val activePop = rememberActivationPopScale(showActive, key = release.browseId)
    val playingShape = expressivePlayingShape()

    CarouselHeroFrame(
        thumbnailUrl = release.thumbnail,
        contentDescription = release.title,
        isActive = showActive,
        isPlaying = isPlaying,
        shape = itemShape,
        activeShape = playingShape,
        activePop = activePop,
        focusLabel = "latest_carousel_focus",
        onClick = { release.openOrPlay(navController, playerConnection) },
        onLongClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            menuState.show(
                ytItemMenu(
                    item = release.toAlbumItem(),
                    navController = navController,
                    coroutineScope = coroutineScope,
                    onDismiss = menuState::dismiss,
                )
            )
        },
    ) {
        HeroTitleOverlay(
            title = release.title,
            subtitle = joinByBullet(release.artistName, release.relativeDateLabel()).ifEmpty { null },
        ) {
            // The library badges (download progress / liked / explicit) the card carried before this
            // shelf became a cover-only carousel; forced white so they read on the dark scrim.
            CompositionLocalProvider(LocalContentColor provides Color.White) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    ReleaseBadges(release)
                }
            }
        }
        // Drawn LAST so the play button sits ON TOP of the bottom title gradient (a single gets the
        // centred "tap to play" icon, an album the corner button that plays it directly).
        if (release.isPlayableSingle()) {
            OverlayPlayButton(visible = !showActive, loading = preparing)
        } else {
            AlbumPlayButton(
                visible = !showActive,
                // The carousel is index-keyed, so scope the disc's loading state to this release (else a
                // feed refresh could bleed a spinning state onto a different album at the same slot).
                itemKey = release.browseId,
                onClick = {
                    coroutineScope.launch {
                        release.playAlbum(
                            playerConnection = playerConnection,
                            database = database,
                            zemerRepository = context.zemerSearchRepository(),
                            context = context,
                            navController = navController,
                        )
                    }
                },
            )
        }
    }
}
