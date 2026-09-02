package com.jtech.zemer.ui.player

import android.annotation.SuppressLint
import android.app.Activity
import android.content.res.Configuration
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Player.STATE_ENDED
import androidx.media3.common.Player.STATE_READY
import androidx.core.view.WindowInsetsControllerCompat
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import com.jtech.zemer.ui.component.focusVisualsEnabled
import com.jtech.zemer.LocalPlayerConnection
import com.jtech.zemer.R
import com.jtech.zemer.playback.PlayerConnection
import com.jtech.zemer.ui.component.focusBorder
import com.jtech.zemer.ui.component.gentleMarquee
import com.jtech.zemer.constants.DarkModeKey
import com.jtech.zemer.constants.FloatingMiniPlayerKey
import com.jtech.zemer.constants.PlayerBackgroundStyle
import com.jtech.zemer.constants.PlayerBackgroundStyleKey
import com.jtech.zemer.constants.PlayerButtonsStyle
import com.jtech.zemer.constants.PlayerButtonsStyleKey
import com.jtech.zemer.constants.PlayerHorizontalPadding
import com.jtech.zemer.constants.QueuePeekHeight
import com.jtech.zemer.constants.SliderStyle
import com.jtech.zemer.constants.SliderStyleKey
import com.jtech.zemer.extensions.repeatModeIconRes
import com.jtech.zemer.extensions.shuffleIconRes
import com.jtech.zemer.extensions.toast
import com.jtech.zemer.extensions.toggleRepeatMode
import com.jtech.zemer.extensions.shareText
import com.jtech.zemer.extensions.copyToClipboard
import com.jtech.zemer.models.MediaMetadata
import com.jtech.zemer.models.withResolvedNavIds
import com.jtech.zemer.playback.PlayerVideoUiLogic
import com.jtech.zemer.ui.component.DefaultDialog
import com.jtech.zemer.ui.component.BottomSheet
import com.jtech.zemer.ui.component.BottomSheetState
import com.jtech.zemer.ui.component.LocalBottomSheetPageState
import com.jtech.zemer.ui.component.LocalMenuState
import androidx.activity.compose.BackHandler
import com.jtech.zemer.ui.component.PlayerSliderTrack
import com.jtech.zemer.ui.component.ResizableIconButton
import com.jtech.zemer.ui.component.rememberPopScale
import androidx.compose.ui.graphics.graphicsLayer
import com.jtech.zemer.ui.component.rememberBottomSheetState
import com.jtech.zemer.ui.menu.PlayerMenu
import com.jtech.zemer.ui.screens.settings.DarkMode
import com.jtech.zemer.ui.theme.PlayerSliderColors
import com.jtech.zemer.ui.utils.ShowMediaInfo
import com.jtech.zemer.ui.utils.navigateToArtist
import com.jtech.zemer.ui.menu.viewCollectionRoute
import com.jtech.zemer.utils.VideoLinkBuilder
import com.jtech.zemer.utils.makeTimeString
import com.jtech.zemer.utils.rememberEnumPreference
import com.jtech.zemer.utils.rememberPreference
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import me.saket.squiggles.SquigglySlider
import com.jtech.zemer.tracking.Tracker
import com.jtech.zemer.tracking.TrackingActionKind

@Suppress("LocalVariableName")
@SuppressLint("ConfigurationScreenWidthHeight")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomSheetPlayer(
    state: BottomSheetState,
    navController: NavController,
    modifier: Modifier = Modifier,
    pureBlack: Boolean,
    floatingMiniPlayerEnabledOverride: Boolean? = null,
    miniPlayerFocusTargets: MiniPlayerFocusTargets? = null,
) {
    val context = LocalContext.current
    val menuState = LocalMenuState.current
    val bottomSheetPageState = LocalBottomSheetPageState.current
    val playerConnection = LocalPlayerConnection.current ?: return

    val (floatingMiniPlayerPref, _) = rememberPreference(
        FloatingMiniPlayerKey,
        defaultValue = true
    )
    val floatingMiniPlayerEnabled =
        floatingMiniPlayerEnabledOverride ?: floatingMiniPlayerPref
    val playerBackgroundPref by rememberEnumPreference(
        key = PlayerBackgroundStyleKey,
        defaultValue = PlayerBackgroundStyle.DEFAULT
    )
    // Render against the *effective* style: BLUR downgrades to DEFAULT below Android 12 (the
    // RenderEffect blur is a no-op there), so the full player never shows bright artwork under
    // the light-on-dark transport. Single source of truth shared with the mini player.
    val playerBackground = playerBackgroundPref.effective()
    val playerButtonsStyle by rememberEnumPreference(
        key = PlayerButtonsStyleKey,
        defaultValue = PlayerButtonsStyle.DEFAULT
    )

    val isSystemInDarkTheme = isSystemInDarkTheme()
    val darkTheme by rememberEnumPreference(DarkModeKey, defaultValue = DarkMode.AUTO)
    val useDarkTheme = remember(darkTheme, isSystemInDarkTheme) {
        if (darkTheme == DarkMode.AUTO) isSystemInDarkTheme else darkTheme == DarkMode.ON
    }
    val onBackgroundColor = when (playerBackground) {
        PlayerBackgroundStyle.DEFAULT -> MaterialTheme.colorScheme.secondary
        else ->
            if (useDarkTheme)
                MaterialTheme.colorScheme.onSurface
            else
                MaterialTheme.colorScheme.onPrimary
    }
    val useBlackBackground =
        remember(isSystemInDarkTheme, darkTheme, pureBlack) {
            val useDarkTheme =
                if (darkTheme == DarkMode.AUTO) isSystemInDarkTheme else darkTheme == DarkMode.ON
            useDarkTheme && pureBlack
        }

    val playbackState by playerConnection.playbackState.collectAsState()
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val isCasting by playerConnection.isCasting.collectAsState()
    val rawMediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val currentSong by playerConnection.currentSong.collectAsState(initial = null)
    // Fill navigation ids (artist / album) the wire item lacked from the played song's DB row, so the
    // title/artist taps and the player menu's view rows work on name-only Zemer surfaces too.
    val mediaMetadata = remember(rawMediaMetadata, currentSong) {
        rawMediaMetadata?.withResolvedNavIds(currentSong)
    }
    val repeatMode by playerConnection.repeatMode.collectAsState()
    val shuffleModeEnabled by playerConnection.shuffleModeEnabled.collectAsState()
    val canSkipPrevious by playerConnection.canSkipPrevious.collectAsState()
    val canSkipNext by playerConnection.canSkipNext.collectAsState()
    val isStationBroadcast by playerConnection.isStationBroadcast.collectAsState()
    val sliderStyle by rememberEnumPreference(SliderStyleKey, SliderStyle.DEFAULT)

    // Video mode (the in-player Song/Video toggle). videoModeAvailable already encodes blocked +
    // casting + rendition availability (VideoModeController) — read it, never re-derive those.
    val videoModeAvailable by playerConnection.videoModeAvailable.collectAsState()
    val isVideoMode by playerConnection.isVideoMode.collectAsState()
    var isFullscreen by rememberSaveable { mutableStateOf(false) }

    // Fullscreen is a per-play, in-video affordance: exit it the instant video mode ends (a track
    // advance/skip/error revert — I2/D4) or the sheet collapses. isVideoMode flipping false here is
    // exactly what makes "track end in fullscreen → advance as audio" fall out for free.
    LaunchedEffect(isVideoMode, state.isExpanded) {
        if (PlayerVideoUiLogic.shouldExitFullscreen(isFullscreen, isVideoMode, state.isExpanded)) {
            isFullscreen = false
        }
    }

    // A video-mode playback failure reverted to audio (I8) — surface it once.
    LaunchedEffect(Unit) {
        playerConnection.videoErrorEvents.collect {
            context.toast(R.string.video_playback_error)
        }
    }

    // Kick the on-demand counterpart lookup when the expanded player shows a new item (a no-op today —
    // the counterpart source is dormant per step 3 — but the call site is kept for when it re-lights).
    // When the item is already video-capable (the pill is showing), also PREFETCH the rendition in the
    // background: the resolution + full quality-ladder URL table are warm before the user taps Video,
    // so entering video mode (at any target quality) starts with a single CDN range request.
    LaunchedEffect(state.isExpanded, mediaMetadata?.id, videoModeAvailable) {
        val id = mediaMetadata?.id
        if (state.isExpanded && id != null) {
            playerConnection.requestVideoAvailability(id)
            // Debounce the prefetch: only warm the rendition once the item has DWELLED ~1.2s, so
            // skipping through video-capable tracks doesn't fire a full /player resolution + cipher
            // work per track (the effect re-keys on id, cancelling this before the delay elapses).
            if (videoModeAvailable && !isVideoMode) {
                delay(1200)
                playerConnection.prefetchVideoRendition(id)
            }
        }
    }

    var position by rememberSaveable(playbackState) {
        mutableLongStateOf(playerConnection.player.currentPosition)
    }
    var duration by rememberSaveable(playbackState) {
        mutableLongStateOf(playerConnection.player.duration)
    }
    var sliderPosition by remember {
        mutableStateOf<Long?>(null)
    }
    // Track if we're in control focus mode (showing outlines)

    val fallbackColor = MaterialTheme.colorScheme.surface.toArgb()
    // Shared, bounded, deduped gradient extraction (see rememberPlayerGradient).
    val gradientColors = rememberPlayerGradient(
        mediaId = mediaMetadata?.id,
        thumbnailUrl = mediaMetadata?.thumbnailUrl,
        enabled = playerBackground == PlayerBackgroundStyle.GRADIENT,
        fallbackColor = fallbackColor,
    )

    val accentColor = MaterialTheme.colorScheme.primary

    // Status-bar icon legibility: a dark blur/gradient player background needs light (white)
    // status-bar icons — but ONLY while that dark background actually covers the screen, i.e. when
    // the sheet is expanded. Collapsed/dragging (the mini player floating over the app) must follow
    // the theme, otherwise white icons land on a light Home/Library/Search screen and vanish. Re-key
    // on the theme and expansion so changes re-apply, and always hand the bar back to the
    // theme-correct appearance — matching MainActivity.setSystemBarAppearance
    // (isAppearanceLightStatusBars = !isDark) — on dispose, rather than a stale snapshot.
    val view = LocalView.current
    DisposableEffect(playerBackground, useDarkTheme, state.isExpanded) {
        val window = (view.context as? Activity)?.window
        if (window != null) {
            WindowInsetsControllerCompat(window, view).isAppearanceLightStatusBars =
                if (state.isExpanded) {
                    when (playerBackground) {
                        PlayerBackgroundStyle.BLUR, PlayerBackgroundStyle.GRADIENT -> false
                        PlayerBackgroundStyle.DEFAULT -> !useDarkTheme
                    }
                } else {
                    !useDarkTheme
                }
            onDispose {
                WindowInsetsControllerCompat(window, view).isAppearanceLightStatusBars = !useDarkTheme
            }
        } else {
            onDispose { }
        }
    }

    val TextBackgroundColor =
        when (playerBackground) {
            PlayerBackgroundStyle.DEFAULT -> MaterialTheme.colorScheme.onBackground
            PlayerBackgroundStyle.BLUR -> Color.White
            PlayerBackgroundStyle.GRADIENT -> Color.White
        }

    val icBackgroundColor =
        when (playerBackground) {
            PlayerBackgroundStyle.DEFAULT -> MaterialTheme.colorScheme.surface
            PlayerBackgroundStyle.BLUR -> Color.Black
            PlayerBackgroundStyle.GRADIENT -> Color.Black
        }

    val (textButtonColor, iconButtonColor) = when (playerButtonsStyle) {
        PlayerButtonsStyle.DEFAULT -> Pair(TextBackgroundColor, icBackgroundColor)
        PlayerButtonsStyle.SECONDARY -> Pair(
            MaterialTheme.colorScheme.secondary,
            MaterialTheme.colorScheme.onSecondary
        )
    }

    // Richer button matrix: the play button stays emphasized (textButtonColor); prev/next read as
    // lower-emphasis tonal containers, background-aware (legible over blur/gradient). All crossfade
    // smoothly via animateColorAsState when the background/style changes.
    val playButtonContainerColor by animateColorAsState(textButtonColor, label = "playBtnContainer")
    val playButtonContentColor by animateColorAsState(iconButtonColor, label = "playBtnContent")
    val sideButtonContainerColor by animateColorAsState(
        targetValue = when (playerBackground) {
            PlayerBackgroundStyle.DEFAULT -> MaterialTheme.colorScheme.surfaceVariant
            PlayerBackgroundStyle.BLUR, PlayerBackgroundStyle.GRADIENT -> Color.White.copy(alpha = 0.15f)
        },
        label = "sideBtnContainer",
    )
    val sideButtonContentColor by animateColorAsState(
        targetValue = when (playerBackground) {
            PlayerBackgroundStyle.DEFAULT -> MaterialTheme.colorScheme.onSurfaceVariant
            PlayerBackgroundStyle.BLUR, PlayerBackgroundStyle.GRADIENT -> Color.White
        },
        label = "sideBtnContent",
    )

    LaunchedEffect(playbackState, isCasting) {
        if (playbackState == STATE_READY || isCasting) {
            while (isActive) {
                delay(500)
                // The single cast-aware position/duration source (remote clock while casting, else local)
                // — shared with Lyrics/Thumbnail so the seek bar can't drift from the other surfaces.
                position = playerConnection.currentPositionMs()
                duration = playerConnection.currentDurationMs()
            }
        }
    }

    val dismissedBound = QueuePeekHeight + WindowInsets.systemBars.asPaddingValues().calculateBottomPadding()

    val queueSheetState = rememberBottomSheetState(
        dismissedBound = dismissedBound,
        expandedBound = state.expandedBound,
        collapsedBound = dismissedBound + 1.dp,
        initialAnchor = 1
    )

    val lyricsSheetState = rememberBottomSheetState(
        dismissedBound = 0.dp,
        expandedBound = state.expandedBound,
        collapsedBound = 0.dp,
        initialAnchor = 1
    )

    // Opening lyrics over an inline video would leave the video decoding invisibly behind the sheet
    // (DESIGN §4) — revert to audio (position-continuous). Video is a per-play opt-in; closing lyrics
    // does not auto-restore it.
    LaunchedEffect(lyricsSheetState.isExpanded, isVideoMode) {
        if (PlayerVideoUiLogic.shouldRevertVideoForLyrics(lyricsSheetState.isExpanded, isVideoMode)) {
            playerConnection.setVideoMode(false)
        }
    }

    val bottomSheetBackgroundColor = when (playerBackground) {
        PlayerBackgroundStyle.BLUR, PlayerBackgroundStyle.GRADIENT -> 
            MaterialTheme.colorScheme.surfaceContainer
        else -> 
            if (useBlackBackground) Color.Black 
            else MaterialTheme.colorScheme.surfaceContainer
    }

    val backgroundAlpha = state.progress.coerceIn(0f, 1f)

    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    BottomSheet(
        state = state,
        modifier = modifier,
        background = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(bottomSheetBackgroundColor)
            ) {
                when (playerBackground) {
                    PlayerBackgroundStyle.BLUR -> {
                        AnimatedContent(
                            targetState = mediaMetadata?.thumbnailUrl,
                            transitionSpec = {
                                fadeIn(tween(800)).togetherWith(fadeOut(tween(800)))
                            },
                            label = "blurBackground"
                        ) { thumbnailUrl ->
                            if (thumbnailUrl != null) {
                                Box(modifier = Modifier.alpha(backgroundAlpha)) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(thumbnailUrl)
                                            .size(100, 100)
                                            .allowHardware(false)
                                            .build(),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .blur(if (useDarkTheme) 150.dp else 100.dp)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black.copy(alpha = 0.3f))
                                    )
                                }
                            }
                        }
                    }
                    PlayerBackgroundStyle.GRADIENT -> {
                        AnimatedContent(
                            targetState = gradientColors,
                            transitionSpec = {
                                fadeIn(tween(800)).togetherWith(fadeOut(tween(800)))
                            },
                            label = "gradientBackground"
                        ) { colors ->
                            if (colors.isNotEmpty()) {
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .alpha(backgroundAlpha)
                                        .background(Brush.verticalGradient(colorStops = playerGradientStops(colors)))
                                        .background(Color.Black.copy(alpha = 0.2f))
                                )
                            }
                        }
                    }
                    else -> {
                        PlayerBackgroundStyle.DEFAULT
                    }
                }
            }
        },
        onDismiss = { /* keep playback running when sheet is dismissed */ },
        collapsedContent = {
            if (floatingMiniPlayerEnabled) {
                MiniPlayer(
                    position = { position },
                    duration = { duration },
                    pureBlack = pureBlack,
                    allowFocus = false,
                    focusTargets = miniPlayerFocusTargets
                )
            }
        },
    ) {
        val controlsContent: @Composable ColumnScope.(MediaMetadata) -> Unit = { mediaMetadata ->
            // The Song/Video toggle now lives as an icon pill overlaid on the art slot (see
            // Thumbnail's showVideoToggle / VideoModePill, D7) so higher display densities can't
            // clip it — it is no longer part of this controls column.
            val playPauseRoundness by animateDpAsState(
                targetValue = if (isPlaying) 24.dp else 36.dp,
                animationSpec = tween(durationMillis = 90, easing = LinearEasing),
                label = "playPauseRoundness",
            )

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PlayerHorizontalPadding)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    val titleFocused = remember { mutableStateOf(false) }
                    val titleBorderColor = animateColorAsState(
                        targetValue = if (titleFocused.value && focusVisualsEnabled()) accentColor else Color.Transparent,
                        label = "title_focus"
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(2.dp, titleBorderColor.value, RoundedCornerShape(4.dp))
                            .padding(4.dp)
                            .focusable()
                            .onFocusChanged { titleFocused.value = it.isFocused }
                    ) {
                        AnimatedContent(
                            targetState = mediaMetadata.title,
                            transitionSpec = { fadeIn() togetherWith fadeOut() },
                            label = "",
                        ) { title ->
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = TextBackgroundColor,
                                modifier =
                                Modifier
                                    .gentleMarquee()
                                    .combinedClickable(
                                        enabled = true,
                                        indication = null,
                                        interactionSource = remember { MutableInteractionSource() },
                                        onClick = {
                                            // An episode's `album` is its owning podcast SHOW (an MPSP id),
                                            // which the music album route can't open — route through the
                                            // shared episode-aware decision (same as the menus' view row).
                                            viewCollectionRoute(mediaMetadata.isEpisode, mediaMetadata.album?.id)?.let {
                                                navController.navigate(it)
                                                state.collapseSoft()
                                            }
                                        },
                                        onLongClick = {
                                            context.copyToClipboard(context.getString(R.string.clip_label_title), title)
                                        }
                                    )
                                ,
                            )
                        }
                    }

                    Spacer(Modifier.height(6.dp))

                    if (mediaMetadata.artists.any { it.name.isNotBlank() }) {
                        val annotatedString = buildAnnotatedString {
                            mediaMetadata.artists.forEachIndexed { index, artist ->
                                val tag = "artist_${artist.id.orEmpty()}"
                                pushStringAnnotation(tag = tag, annotation = artist.id.orEmpty())
                                withStyle(SpanStyle(color = TextBackgroundColor, fontSize = MaterialTheme.typography.titleMedium.fontSize)) {
                                    append(artist.name)
                                }
                                pop()
                                if (index != mediaMetadata.artists.lastIndex) append(", ")
                            }
                        }

                        val artistFocused = remember { mutableStateOf(false) }
                        val artistBorderColor = animateColorAsState(
                            targetValue = if (artistFocused.value && focusVisualsEnabled()) accentColor else Color.Transparent,
                            label = "artist_focus"
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(2.dp, artistBorderColor.value, RoundedCornerShape(4.dp))
                                .padding(4.dp)
                                .gentleMarquee()
                                .focusable()
                                .onFocusChanged { artistFocused.value = it.isFocused }
                        ) {
                            var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
                            var clickOffset by remember { mutableStateOf<Offset?>(null) }
                            Text(
                                text = annotatedString,
                                style = MaterialTheme.typography.titleMedium.copy(color = TextBackgroundColor),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                onTextLayout = { layoutResult = it },
                                modifier = Modifier
                                    .pointerInput(Unit) {
                                        awaitPointerEventScope {
                                            while (true) {
                                                val event = awaitPointerEvent()
                                                val tapPosition = event.changes.firstOrNull()?.position
                                                if (tapPosition != null) {
                                                    clickOffset = tapPosition
                                                }
                                            }
                                        }
                                    }
                                    .combinedClickable(
                                        enabled = true,
                                        indication = null,
                                        interactionSource = remember { MutableInteractionSource() },
                                        onClick = {
                                            val tapPosition = clickOffset
                                            val layout = layoutResult
                                            if (tapPosition != null && layout != null) {
                                                val offset = layout.getOffsetForPosition(tapPosition)
                                                annotatedString
                                                    .getStringAnnotations(offset, offset)
                                                    .firstOrNull()
                                                    ?.let { ann ->
                                                        val artistId = ann.item
                                                        if (artistId.isNotBlank()) {
                                                            // An episode's author is a podcast HOST channel:
                                                            // the flag routes it to the podcast channel page
                                                            // (/podcast-channel), not the music artist page.
                                                            navController.navigateToArtist(artistId, isPodcastChannel = mediaMetadata.isEpisode)
                                                            state.collapseSoft()
                                                        }
                                                    }
                                            }
                                        },
                                        onLongClick = {
                                            context.copyToClipboard(context.getString(R.string.clip_label_artist), annotatedString)
                                        }
                                    )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                    val shareShape = RoundedCornerShape(
                        topStart = 50.dp, bottomStart = 50.dp,
                        topEnd = 5.dp, bottomEnd = 5.dp
                    )

                    val favShape = RoundedCornerShape(
                        topStart = 5.dp, bottomStart = 5.dp,
                        topEnd = 50.dp, bottomEnd = 50.dp
                    )

                    val shareFocused = remember { mutableStateOf(false) }
                    val shareBorderColor = animateColorAsState(
                        targetValue = if (shareFocused.value && focusVisualsEnabled()) accentColor else Color.Transparent,
                        label = "share_focus"
                    )
                    val favFocused = remember { mutableStateOf(false) }
                    val favBorderColor = animateColorAsState(
                        targetValue = if (favFocused.value && focusVisualsEnabled()) accentColor else Color.Transparent,
                        label = "fav_focus"
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(shareShape)
                                .background(textButtonColor)
                                .border(3.dp, shareBorderColor.value, shareShape)
                                .focusable()
                                .onFocusChanged { shareFocused.value = it.isFocused }
                                .clickable {
                                    Tracker.action(TrackingActionKind.SHARE, mediaMetadata.id)
                                    context.shareText(
                                        VideoLinkBuilder.shareLink(mediaMetadata.id, mediaMetadata.isEpisode, mediaMetadata.album?.id),
                                    )
                                }
                        ) {
                            Image(
                                painter = painterResource(R.drawable.share),
                                contentDescription = null,
                                colorFilter = ColorFilter.tint(iconButtonColor),
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .size(24.dp)
                            )
                        }

                        var favPop by remember { mutableIntStateOf(0) }
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(favShape)
                                .background(textButtonColor)
                                .border(3.dp, favBorderColor.value, favShape)
                                .focusable()
                                .onFocusChanged { favFocused.value = it.isFocused }
                                .clickable {
                                    // Pop on the USER'S tap, not on the liked flag - that flag also flips
                                    // on every track transition, which bounced the heart on plain skips.
                                    favPop++
                                    playerConnection.toggleLike()
                                }
                        ) {
                            val likePop = rememberPopScale(favPop)
                            Image(
                                painter = painterResource(
                                    if (currentSong?.song?.isSavedForPlayer == true)
                                        R.drawable.favorite
                                    else R.drawable.favorite_border
                                ),
                                contentDescription = null,
                                colorFilter = ColorFilter.tint(iconButtonColor),
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .size(24.dp)
                                    .graphicsLayer { scaleX = likePop; scaleY = likePop }
                            )
                        }
                    }
            }

            Spacer(Modifier.height(12.dp))

            PlayerSeekBar(
                sliderStyle = sliderStyle, isStationBroadcast = isStationBroadcast, position = position, duration = duration,
                sliderPosition = sliderPosition, isPlaying = isPlaying, accentColor = accentColor, textColor = TextBackgroundColor,
                playerBackground = playerBackground, useDarkTheme = useDarkTheme,
                onSliderPositionChange = { sliderPosition = it },
                onSeek = { playerConnection.seekTo(it); position = it },
            )

            Spacer(Modifier.height(12.dp))

            // Episode-only controls (podcasts are long): playback speed + 30s skip back/forward.
            // Hidden for music so the normal transport is unchanged.
            if (mediaMetadata.isEpisode) {
                EpisodePlaybackControls(
                    playerConnection = playerConnection,
                    contentColor = TextBackgroundColor,
                    onSeekTo = { target ->
                        // Optimistic: move the progress bar to the target NOW. The position poll
                        // only runs in STATE_READY, so a skip into an unbuffered region would
                        // otherwise freeze the bar at the old position until the seek loads.
                        // PlayerConnection.seekTo, not player.seekTo: while casting the seek must go
                        // to the receiver (the local player is paused/frozen), same as the slider.
                        playerConnection.seekTo(target)
                        position = target
                    },
                )
                Spacer(Modifier.height(12.dp))
            }

            if (isLandscape) {
                Spacer(modifier = Modifier.weight(1f))
            }

                PlayerTransportRow(
                    isPlaying = isPlaying, ended = playbackState == STATE_ENDED, canSkipPrevious = canSkipPrevious, canSkipNext = canSkipNext,
                    accentColor = accentColor, playButtonContainerColor = playButtonContainerColor, playButtonContentColor = playButtonContentColor,
                    sideButtonContainerColor = sideButtonContainerColor, sideButtonContentColor = sideButtonContentColor,
                    onPlayPause = { playerConnection.playPauseOrReplay(playbackState == STATE_ENDED) },
                    onPrevious = playerConnection::seekToPrevious, onNext = playerConnection::seekToNext,
                )
        }

        when (LocalConfiguration.current.orientation) {
            Configuration.ORIENTATION_LANDSCAPE -> {
                Row(
                    modifier =
                    Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal))
                        .padding(bottom = queueSheetState.collapsedBound + 48.dp),
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.weight(1f),
                    ) {
                        val screenWidth = LocalConfiguration.current.screenWidthDp
                        val thumbnailSize = (screenWidth * 0.4).dp
                                                    Thumbnail(
                                sliderPositionProvider = { sliderPosition },
                                modifier = Modifier.size(thumbnailSize),
                                isPlayerExpanded = state.isExpanded,
                                showVideo = PlayerVideoUiLogic.showInlineVideo(isVideoMode, isFullscreen),
                                onEnterFullscreen = { isFullscreen = true },
                                showVideoToggle = videoModeAvailable,
                                isVideoMode = isVideoMode,
                                onToggleVideoMode = { playerConnection.setVideoMode(it) },
                            )
                    }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top)),
                    ) {
                        mediaMetadata?.let {
                            controlsContent(it)
                        }
                    }
                }
            }

            else -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier =
                    Modifier
                        .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal))
                        .padding(bottom = queueSheetState.collapsedBound),
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.weight(1f),
                    ) {
                                                    Thumbnail(
                                sliderPositionProvider = { sliderPosition },
                                modifier = Modifier.nestedScroll(state.preUpPostDownNestedScrollConnection),
                                isPlayerExpanded = state.isExpanded,
                                showVideo = PlayerVideoUiLogic.showInlineVideo(isVideoMode, isFullscreen),
                                onEnterFullscreen = { isFullscreen = true },
                                showVideoToggle = videoModeAvailable,
                                isVideoMode = isVideoMode,
                                onToggleVideoMode = { playerConnection.setVideoMode(it) },
                            )
                    }

                    mediaMetadata?.let {
                        controlsContent(it)
                    }

                    Spacer(Modifier.height(30.dp))
                }
            }
        }

        Queue(
            state = queueSheetState,
            playerBottomSheetState = state,
            navController = navController,
            background =
            if (useBlackBackground) {
                Color.Black
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            },
            onBackgroundColor = onBackgroundColor,
            TextBackgroundColor = TextBackgroundColor,
            textButtonColor = textButtonColor,
            iconButtonColor = iconButtonColor,
            onShowLyrics = { lyricsSheetState.expandSoft() },
            pureBlack = pureBlack,
        )

        mediaMetadata?.let { metadata ->
            BottomSheet(
                state = lyricsSheetState,
                background = { Box(Modifier.fillMaxSize().background(Color.Unspecified)) },
                onDismiss = { },
                collapsedContent = {
                }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            MaterialTheme.colorScheme.surface.copy(
                                alpha = lyricsSheetState.progress.coerceIn(0f, 1f)
                            )
                        )
                ) {
                    LyricsScreen(
                        mediaMetadata = metadata,
                        onBackClick = { lyricsSheetState.collapseSoft() },
                        backgroundAlpha = lyricsSheetState.progress.coerceIn(0f, 1f)
                    )
                }
            }
        }

        // Fullscreen video overlay — drawn last so it covers the expanded player (I6: same surface,
        // re-parented). Only while expanded + in video mode + fullscreen requested.
        if (PlayerVideoUiLogic.showFullscreenVideo(state.isExpanded, isVideoMode, isFullscreen)) {
            PlayerVideoFullscreen(onExit = { isFullscreen = false })
        }
    }
}

/**
 * Episode-only transport extras (podcasts are long): a playback-speed pill that cycles
 * 1×→1.25×→1.5×→1.75×→2× and 30-second skip-back / skip-forward. Shown only when an episode is
 * playing; music keeps its normal transport, and MusicService resets speed to 1× when a non-episode
 * starts so episode speed never leaks into songs.
 */
@Composable
private fun EpisodePlaybackControls(
    playerConnection: PlayerConnection,
    contentColor: Color,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Live speed, not a one-shot snapshot: the Tempo & Pitch dialog writes playbackParameters too,
    // and a stale cached value made the pill label lie and the next tap override the user's choice.
    val playbackParameters by playerConnection.playbackParameters.collectAsState()
    val speed = playbackParameters.speed
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .focusBorder(RoundedCornerShape(50))
                .clickable {
                    playerConnection.player.setPlaybackSpeed(nextEpisodeSpeed(speed))
                }
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.speed),
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(text = episodeSpeedLabel(speed), color = contentColor, style = MaterialTheme.typography.labelLarge)
        }
        IconButton(
            onClick = {
                // Cast-aware clocks (currentPositionMs/DurationMs): the LOCAL player's clock is
                // frozen while casting, so reading it would compute the skip from a stale position.
                onSeekTo(episodeSkipTarget(playerConnection.currentPositionMs(), playerConnection.currentDurationMs(), forward = false))
            },
            modifier = Modifier.focusBorder(RoundedCornerShape(50)),
        ) {
            Icon(painter = painterResource(R.drawable.fast_rewind), contentDescription = null, tint = contentColor)
        }
        IconButton(
            onClick = {
                onSeekTo(episodeSkipTarget(playerConnection.currentPositionMs(), playerConnection.currentDurationMs(), forward = true))
            },
            modifier = Modifier.focusBorder(RoundedCornerShape(50)),
        ) {
            Icon(painter = painterResource(R.drawable.fast_forward), contentDescription = null, tint = contentColor)
        }
    }
}

/**
 * A circular skip-previous / skip-next button for the new-design transport cluster: the standard
 * D-pad accent focus border, a spring "pump" while pressed, and tap + long-press-to-seek (the long
 * press repeats [onSkip] every 200 ms). A tap fires the button's own onClick; the combinedClickable
 * adds the long press. Both are gated by [enabled], so a disabled skip cannot be triggered.
 * Extracted so prev and next share one definition instead of two ~48-line copies.
 */
