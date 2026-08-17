@file:OptIn(
    ExperimentalFoundationApi::class,
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
)

package com.jtech.zemer.latestreleases

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.carousel.CarouselItemScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.jtech.zemer.LocalDatabase
import com.jtech.zemer.di.zemerSearchRepository
import com.jtech.zemer.models.MediaMetadata
import com.jtech.zemer.playback.PlayerConnection
import com.jtech.zemer.ui.component.ActiveBoxAlpha
import com.jtech.zemer.ui.component.AlbumPlayButton
import com.jtech.zemer.ui.component.LocalMenuState
import com.jtech.zemer.ui.component.OverlayPlayButton
import com.jtech.zemer.ui.component.PlayingIndicatorBox
import com.jtech.zemer.ui.component.expressivePlayingShape
import com.jtech.zemer.ui.component.focusVisualsEnabled
import com.jtech.zemer.ui.component.rememberActivationPopScale
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

    val itemShape = MaterialTheme.shapes.extraLarge
    var focused by remember { mutableStateOf(false) }
    val ringColor by animateColorAsState(
        targetValue = if (focused && focusVisualsEnabled()) MaterialTheme.colorScheme.primary else Color.Transparent,
        label = "latest_carousel_focus",
    )
    // A playing release pops once as it BECOMES active (rising-edge only) and morphs its cover to the
    // same scalloped expressive shape the card thumbnails use. Resolved once and shared by both layers.
    val activePop = rememberActivationPopScale(isActive)
    val playingShape = expressivePlayingShape()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .maskClip(itemShape)
            .maskBorder(BorderStroke(3.dp, ringColor), itemShape)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .combinedClickable(
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
            ),
    ) {
        AsyncImage(
            model = release.thumbnail,
            contentDescription = release.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { scaleX = activePop; scaleY = activePop }
                .then(if (isActive) Modifier.clip(playingShape) else Modifier),
        )
        PlayingIndicatorBox(
            isActive = isActive,
            playWhenReady = isPlaying,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { scaleX = activePop; scaleY = activePop }
                .background(Color.Black.copy(alpha = ActiveBoxAlpha), shape = playingShape),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f)))
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(
                text = release.title,
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                modifier = Modifier.basicMarquee(),
            )
            val subtitle = joinByBullet(release.artistName, release.relativeDateLabel())
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    color = Color.White.copy(alpha = 0.75f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    modifier = Modifier.basicMarquee(),
                )
            }
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
            OverlayPlayButton(visible = !isActive)
        } else {
            AlbumPlayButton(
                visible = !isActive,
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
