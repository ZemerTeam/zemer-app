package com.jtech.zemer.ui.player

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Player.STATE_ENDED
import androidx.media3.common.Player.STATE_READY
import androidx.navigation.NavController
import androidx.palette.graphics.Palette
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.jtech.zemer.LocalPlayerConnection
import com.jtech.zemer.R
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
import com.jtech.zemer.constants.UseNewPlayerDesignKey
import com.jtech.zemer.extensions.togglePlayPause
import com.jtech.zemer.extensions.toggleRepeatMode
import com.jtech.zemer.models.MediaMetadata
import com.jtech.zemer.ui.component.BottomSheet
import com.jtech.zemer.ui.component.BottomSheetState
import com.jtech.zemer.ui.component.LocalBottomSheetPageState
import com.jtech.zemer.ui.component.LocalMenuState
import com.jtech.zemer.ui.component.PlayerSliderTrack
import com.jtech.zemer.ui.component.ResizableIconButton
import com.jtech.zemer.ui.component.rememberBottomSheetState
import com.jtech.zemer.ui.menu.PlayerMenu
import com.jtech.zemer.ui.screens.settings.DarkMode
import com.jtech.zemer.ui.theme.PlayerColorExtractor
import com.jtech.zemer.ui.theme.PlayerSliderColors
import com.jtech.zemer.ui.utils.ShowMediaInfo
import com.jtech.zemer.utils.makeTimeString
import com.jtech.zemer.utils.rememberEnumPreference
import com.jtech.zemer.utils.rememberPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.saket.squiggles.SquigglySlider
import kotlin.math.roundToInt

@Suppress("LocalVariableName")
@SuppressLint("ConfigurationScreenWidthHeight")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomSheetPlayer(
    state: BottomSheetState,
    navController: NavController,
    modifier: Modifier = Modifier,
    pureBlack: Boolean,
    miniPlayerFocusTargets: MiniPlayerFocusTargets? = null,
) {
    val context = LocalContext.current
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val menuState = LocalMenuState.current
    val bottomSheetPageState = LocalBottomSheetPageState.current
    val playerConnection = LocalPlayerConnection.current ?: return

    val (useNewPlayerDesign, _) = rememberPreference(
        UseNewPlayerDesignKey,
        defaultValue = true
    )
    val (floatingMiniPlayerEnabled) = rememberPreference(
        FloatingMiniPlayerKey,
        defaultValue = true
    )
    val playerBackground by rememberEnumPreference(
        key = PlayerBackgroundStyleKey,
        defaultValue = PlayerBackgroundStyle.DEFAULT
    )
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
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val currentSong by playerConnection.currentSong.collectAsState(initial = null)
    val automix by playerConnection.service.automixItems.collectAsState()
    val repeatMode by playerConnection.repeatMode.collectAsState()
    val canSkipPrevious by playerConnection.canSkipPrevious.collectAsState()
    val canSkipNext by playerConnection.canSkipNext.collectAsState()
    val sliderStyle by rememberEnumPreference(SliderStyleKey, SliderStyle.DEFAULT)

    var position by rememberSaveable(playbackState) {
        mutableLongStateOf(playerConnection.player.currentPosition)
    }
    var duration by rememberSaveable(playbackState) {
        mutableLongStateOf(playerConnection.player.duration)
    }
    var sliderPosition by remember {
        mutableStateOf<Long?>(null)
    }
    var gradientColors by remember {
        mutableStateOf<List<Color>>(emptyList())
    }
    val gradientColorsCache = remember { mutableMapOf<String, List<Color>>() }

    // Track if we're in control focus mode (showing outlines)

    if (!canSkipNext && automix.isNotEmpty()) {
        playerConnection.service.addToQueueAutomix(automix[0], 0)
    }

    listOf(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.surfaceVariant)
    val fallbackColor = MaterialTheme.colorScheme.surface.toArgb()

    LaunchedEffect(mediaMetadata?.id, playerBackground) {
        if (playerBackground == PlayerBackgroundStyle.GRADIENT) {
            val currentMetadata = mediaMetadata
            if (currentMetadata != null && currentMetadata.thumbnailUrl != null) {
                val cachedColors = gradientColorsCache[currentMetadata.id]
                if (cachedColors != null) {
                    gradientColors = cachedColors
                    return@LaunchedEffect
                }
                withContext(Dispatchers.IO) {
                    val request = ImageRequest.Builder(context)
                        .data(currentMetadata.thumbnailUrl)
                        .size(100, 100)
                        .allowHardware(false)
                        .memoryCacheKey("gradient_${currentMetadata.id}")
                        .build()

                    val result = runCatching { context.imageLoader.execute(request) }.getOrNull()
                    if (result != null) {
                        val bitmap = result.image?.toBitmap()
                        if (bitmap != null) {
                            val palette = withContext(Dispatchers.Default) {
                                Palette.from(bitmap)
                                    .maximumColorCount(8)
                                    .resizeBitmapArea(100 * 100)
                                    .generate()
                            }
                            val extractedColors = PlayerColorExtractor.extractGradientColors(
                                palette = palette,
                                fallbackColor = fallbackColor
                            )
                            gradientColorsCache[currentMetadata.id] = extractedColors
                            withContext(Dispatchers.Main) { gradientColors = extractedColors }
                        }
                    }
                }
            }
        } else {
            gradientColors = emptyList()
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

    val sleepTimerEnabled =
        remember(
            playerConnection.service.sleepTimer.triggerTime,
            playerConnection.service.sleepTimer.pauseWhenSongEnd
        ) {
            playerConnection.service.sleepTimer.isActive
        }

    LaunchedEffect(sleepTimerEnabled) {
        if (sleepTimerEnabled) {
            while (isActive) {
                delay(1000L)
            }
        }
    }

    var showSleepTimerDialog by remember {
        mutableStateOf(false)
    }

    var sleepTimerValue by remember {
        mutableFloatStateOf(30f)
    }
    if (showSleepTimerDialog) {
        AlertDialog(
            properties = DialogProperties(usePlatformDefaultWidth = false),
            onDismissRequest = { showSleepTimerDialog = false },
            icon = {
                Icon(
                    painter = painterResource(R.drawable.bedtime),
                    contentDescription = null
                )
            },
            title = { Text(stringResource(R.string.sleep_timer)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSleepTimerDialog = false
                        playerConnection.service.sleepTimer.start(sleepTimerValue.roundToInt())
                    },
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showSleepTimerDialog = false },
                ) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = pluralStringResource(
                            R.plurals.minute,
                            sleepTimerValue.roundToInt(),
                            sleepTimerValue.roundToInt()
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                    )

                    Slider(
                        value = sleepTimerValue,
                        onValueChange = { sleepTimerValue = it },
                        valueRange = 5f..120f,
                        steps = (120 - 5) / 5 - 1,
                    )

                    OutlinedIconButton(
                        onClick = {
                            showSleepTimerDialog = false
                            playerConnection.service.sleepTimer.start(-1)
                        },
                    ) {
                        Text(stringResource(R.string.end_of_song))
                    }
                }
            },
        )
    }

    LaunchedEffect(playbackState) {
        if (playbackState == STATE_READY) {
            while (isActive) {
                delay(500)
                position = playerConnection.player.currentPosition
                duration = playerConnection.player.duration
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

    val bottomSheetBackgroundColor = when (playerBackground) {
        PlayerBackgroundStyle.BLUR, PlayerBackgroundStyle.GRADIENT -> 
            MaterialTheme.colorScheme.surfaceContainer
        else -> 
            if (useBlackBackground) Color.Black 
            else MaterialTheme.colorScheme.surfaceContainer
    }

    val backgroundAlpha = state.progress.coerceIn(0f, 1f)

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
                                val gradientColorStops = if (colors.size >= 3) {
                                    arrayOf(
                                        0.0f to colors[0],
                                        0.5f to colors[1],
                                        1.0f to colors[2]
                                    )
                                } else {
                                    arrayOf(
                                        0.0f to colors[0],
                                        0.6f to colors[0].copy(alpha = 0.7f),
                                        1.0f to Color.Black
                                    )
                                }
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .alpha(backgroundAlpha)
                                        .background(Brush.verticalGradient(colorStops = gradientColorStops))
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
                    position = position,
                    duration = duration,
                    pureBlack = pureBlack,
                    allowFocus = false,
                    focusTargets = miniPlayerFocusTargets
                )
            }
        },
    ) {
        val controlsContent: @Composable ColumnScope.(MediaMetadata) -> Unit = { mediaMetadata ->
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
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp).copy(alpha = 0.94f),
                                MaterialTheme.colorScheme.surfaceColorAtElevation(12.dp).copy(alpha = 0.85f)
                            )
                        )
                    )
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                        shape = RoundedCornerShape(20.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = stringResource(R.string.now_playing_label),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )

                    val titleFocused = remember { mutableStateOf(false) }
                    val titleBorderColor = animateColorAsState(
                        targetValue = if (titleFocused.value) MaterialTheme.colorScheme.primary else Color.Transparent,
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
                                    .basicMarquee(iterations = 1, initialDelayMillis = 3000, velocity = 30.dp)
                                    .combinedClickable(
                                        enabled = true,
                                        indication = null,
                                        interactionSource = remember { MutableInteractionSource() },
                                        onClick = {
                                            if (mediaMetadata.album != null) {
                                                navController.navigate("album/${mediaMetadata.album.id}")
                                                state.collapseSoft()
                                            }
                                        },
                                        onLongClick = {
                                            val clip = ClipData.newPlainText("Copied Title", title)
                                            clipboardManager.setPrimaryClip(clip)
                                            Toast
                                                .makeText(context, "Copied Title", Toast.LENGTH_SHORT)
                                                .show()
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
                                withStyle(SpanStyle(color = TextBackgroundColor, fontSize = 16.sp)) {
                                    append(artist.name)
                                }
                                pop()
                                if (index != mediaMetadata.artists.lastIndex) append(", ")
                            }
                        }

                        val artistFocused = remember { mutableStateOf(false) }
                        val artistBorderColor = animateColorAsState(
                            targetValue = if (artistFocused.value) MaterialTheme.colorScheme.primary else Color.Transparent,
                            label = "artist_focus"
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(2.dp, artistBorderColor.value, RoundedCornerShape(4.dp))
                                .padding(4.dp)
                                .basicMarquee(iterations = 1, initialDelayMillis = 3000, velocity = 30.dp)
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
                                                            navController.navigate("artist/$artistId")
                                                            state.collapseSoft()
                                                        }
                                                    }
                                            }
                                        },
                                        onLongClick = {
                                            val clip =
                                                ClipData.newPlainText("Copied Artist", annotatedString)
                                            clipboardManager.setPrimaryClip(clip)
                                            Toast
                                                .makeText(
                                                    context,
                                                    "Copied Artist",
                                                    Toast.LENGTH_SHORT
                                                )
                                                .show()
                                        }
                                    )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                if (useNewPlayerDesign) {
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
                        targetValue = if (shareFocused.value) MaterialTheme.colorScheme.primary else Color.Transparent,
                        label = "share_focus"
                    )
                    val favFocused = remember { mutableStateOf(false) }
                    val favBorderColor = animateColorAsState(
                        targetValue = if (favFocused.value) MaterialTheme.colorScheme.primary else Color.Transparent,
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
                                    val intent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        type = "text/plain"
                                        putExtra(
                                            Intent.EXTRA_TEXT,
                                            "https://music.youtube.com/watch?v=${mediaMetadata.id}"
                                        )
                                    }
                                    context.startActivity(Intent.createChooser(intent, null))
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

                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(favShape)
                                .background(textButtonColor)
                                .border(3.dp, favBorderColor.value, favShape)
                                .focusable()
                                .onFocusChanged { favFocused.value = it.isFocused }
                                .clickable {
                                    playerConnection.toggleLike()
                                }
                        ) {
                            Image(
                                painter = painterResource(
                                    if (currentSong?.song?.liked == true)
                                        R.drawable.favorite
                                    else R.drawable.favorite_border
                                ),
                                contentDescription = null,
                                colorFilter = ColorFilter.tint(iconButtonColor),
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .size(24.dp)
                            )
                        }
                    }
                } else {
                    val oldShareFocused = remember { mutableStateOf(false) }
                    val oldShareBorderColor = animateColorAsState(
                        targetValue = if (oldShareFocused.value) MaterialTheme.colorScheme.primary else Color.Transparent,
                        label = "old_share_focus"
                    )
                    val oldMenuFocused = remember { mutableStateOf(false) }
                    val oldMenuBorderColor = animateColorAsState(
                        targetValue = if (oldMenuFocused.value) MaterialTheme.colorScheme.primary else Color.Transparent,
                        label = "old_menu_focus"
                    )
                    Box(
                        modifier =
                        Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(textButtonColor)
                            .border(3.dp, oldShareBorderColor.value, RoundedCornerShape(24.dp))
                            .focusable()
                            .onFocusChanged { oldShareFocused.value = it.isFocused }
                            .clickable {
                                val intent =
                                    Intent().apply {
                                        action = Intent.ACTION_SEND
                                        type = "text/plain"
                                        putExtra(
                                            Intent.EXTRA_TEXT,
                                            "https://music.youtube.com/watch?v=${mediaMetadata.id}"
                                        )
                                    }
                                context.startActivity(Intent.createChooser(intent, null))
                            },
                    ) {
                        Image(
                            painter = painterResource(R.drawable.share),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(iconButtonColor),
                            modifier =
                            Modifier
                                .align(Alignment.Center)
                                .size(24.dp),
                        )
                    }

                    Spacer(modifier = Modifier.size(12.dp))

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier =
                        Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(textButtonColor)
                            .border(3.dp, oldMenuBorderColor.value, RoundedCornerShape(24.dp))
                            .focusable()
                            .onFocusChanged { oldMenuFocused.value = it.isFocused }
                            .clickable {
                                menuState.show {
                                    PlayerMenu(
                                        mediaMetadata = mediaMetadata,
                                        navController = navController,
                                        playerBottomSheetState = state,
                                        onShowDetailsDialog = {
                                            mediaMetadata.id.let {
                                                bottomSheetPageState.show {
                                                    ShowMediaInfo(it)
                                                }
                                            }
                                        },
                                        onDismiss = menuState::dismiss,
                                    )
                                }
                            },
                    ) {
                        Image(
                            painter = painterResource(R.drawable.more_horiz),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(iconButtonColor),
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PlayerHorizontalPadding - 8.dp)
            ) {
                when (sliderStyle) {
                    SliderStyle.DEFAULT -> {
                        Slider(
                            value = (sliderPosition ?: position).toFloat(),
                            valueRange = 0f..(if (duration == C.TIME_UNSET) 0f else duration.toFloat()),
                            onValueChange = {
                                sliderPosition = it.toLong()
                            },
                            onValueChangeFinished = {
                                sliderPosition?.let {
                                    playerConnection.player.seekTo(it)
                                    position = it
                                }
                                sliderPosition = null
                            },
                            colors = PlayerSliderColors.defaultSliderColors(textButtonColor, playerBackground, useDarkTheme),
                            modifier = Modifier.padding(horizontal = PlayerHorizontalPadding - 8.dp),
                        )
                    }

                    SliderStyle.SQUIGGLY -> {
                        SquigglySlider(
                            value = (sliderPosition ?: position).toFloat(),
                            valueRange = 0f..(if (duration == C.TIME_UNSET) 0f else duration.toFloat()),
                            onValueChange = {
                                sliderPosition = it.toLong()
                            },
                            onValueChangeFinished = {
                                sliderPosition?.let {
                                    playerConnection.player.seekTo(it)
                                    position = it
                                }
                                sliderPosition = null
                            },
                            colors = PlayerSliderColors.squigglySliderColors(textButtonColor, playerBackground, useDarkTheme),
                            modifier = Modifier.padding(horizontal = PlayerHorizontalPadding - 8.dp),
                            squigglesSpec =
                            SquigglySlider.SquigglesSpec(
                                amplitude = if (isPlaying) (2.dp).coerceAtLeast(2.dp) else 0.dp,
                                strokeWidth = 3.dp,
                            ),
                        )
                    }

                    SliderStyle.SLIM -> {
                        Slider(
                            value = (sliderPosition ?: position).toFloat(),
                            valueRange = 0f..(if (duration == C.TIME_UNSET) 0f else duration.toFloat()),
                            onValueChange = {
                                sliderPosition = it.toLong()
                            },
                            onValueChangeFinished = {
                                sliderPosition?.let {
                                    playerConnection.player.seekTo(it)
                                    position = it
                                }
                                sliderPosition = null
                            },
                            thumb = { Spacer(modifier = Modifier.size(0.dp)) },
                            track = { sliderState ->
                                PlayerSliderTrack(
                                    sliderState = sliderState,
                                    colors = PlayerSliderColors.slimSliderColors(textButtonColor, playerBackground, useDarkTheme)
                                )
                            },
                            modifier = Modifier.padding(horizontal = PlayerHorizontalPadding - 8.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PlayerHorizontalPadding + 4.dp),
            ) {
                Text(
                    text = makeTimeString(sliderPosition ?: position),
                    style = MaterialTheme.typography.labelMedium,
                    color = TextBackgroundColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Text(
                    text = if (duration != C.TIME_UNSET) makeTimeString(duration) else "",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextBackgroundColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.height(12.dp))

            if (useNewPlayerDesign) {
                BoxWithConstraints(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val maxW = maxWidth
                    val playButtonHeight = maxW / 6f
                    val playButtonWidth = playButtonHeight * 1.6f
                    val sideButtonHeight = playButtonHeight * 0.8f
                    val sideButtonWidth = sideButtonHeight * 1.3f
                    val coroutineScope = rememberCoroutineScope()

                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {

                        val skipPrevFocused = remember { mutableStateOf(false) }
                        val skipPrevBorderColor = animateColorAsState(
                            targetValue = if (skipPrevFocused.value) MaterialTheme.colorScheme.primary else Color.Transparent,
                            label = "skip_prev_focus"
                        )
                        var skipPrevJob by remember { mutableStateOf<Job?>(null) }

                        FilledTonalIconButton(
                            onClick = {
                                skipPrevJob?.cancel()
                                playerConnection.seekToPrevious()
                            },
                            enabled = canSkipPrevious,
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = textButtonColor,
                                contentColor = iconButtonColor
                            ),
                            modifier = Modifier
                                .size(width = sideButtonWidth, height = sideButtonHeight)
                                .clip(RoundedCornerShape(32.dp))
                                .border(3.dp, skipPrevBorderColor.value, RoundedCornerShape(32.dp))
                                .focusable()
                                .onFocusChanged { skipPrevFocused.value = it.isFocused }
                                .combinedClickable(
                                    onClick = {
                                        skipPrevJob?.cancel()
                                        playerConnection.seekToPrevious()
                                    },
                                    onLongClick = {
                                        if (canSkipPrevious) {
                                            skipPrevJob = coroutineScope.launch {
                                                while (isActive) {
                                                    playerConnection.seekToPrevious()
                                                    delay(200)
                                                }
                                            }
                                        }
                                    }
                                )
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.skip_previous),
                                contentDescription = null,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        val playButtonFocused = remember { mutableStateOf(false) }
                        val playButtonBorderColor = animateColorAsState(
                            targetValue = if (playButtonFocused.value) MaterialTheme.colorScheme.primary else Color.Transparent,
                            label = "play_button_focus"
                        )
                        FilledIconButton(
                            onClick = {
                                if (playbackState == STATE_ENDED) {
                                    playerConnection.player.seekTo(0, 0)
                                    playerConnection.player.playWhenReady = true
                                } else {
                                    playerConnection.player.togglePlayPause()
                                }
                            },
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = textButtonColor,
                                contentColor = iconButtonColor
                            ),
                            modifier = Modifier
                                .size(width = playButtonWidth, height = playButtonHeight)
                                .clip(RoundedCornerShape(32.dp))
                                .border(3.dp, playButtonBorderColor.value, RoundedCornerShape(32.dp))
                                .focusable()
                                .onFocusChanged { playButtonFocused.value = it.isFocused }
                        ) {
                            Icon(
                                painter = painterResource(
                                    when {
                                        playbackState == STATE_ENDED -> R.drawable.replay
                                        isPlaying -> R.drawable.pause
                                        else -> R.drawable.play
                                    }
                                ),
                                contentDescription = null,
                                modifier = Modifier.size(42.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        val skipNextFocused = remember { mutableStateOf(false) }
                        val skipNextBorderColor = animateColorAsState(
                            targetValue = if (skipNextFocused.value) MaterialTheme.colorScheme.primary else Color.Transparent,
                            label = "skip_next_focus"
                        )
                        var skipNextJob by remember { mutableStateOf<Job?>(null) }

                        FilledTonalIconButton(
                            onClick = {
                                skipNextJob?.cancel()
                                playerConnection.seekToNext()
                            },
                            enabled = canSkipNext,
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = textButtonColor,
                                contentColor = iconButtonColor
                            ),
                            modifier = Modifier
                                .size(width = sideButtonWidth, height = sideButtonHeight)
                                .clip(RoundedCornerShape(32.dp))
                                .border(3.dp, skipNextBorderColor.value, RoundedCornerShape(32.dp))
                                .focusable()
                                .onFocusChanged { skipNextFocused.value = it.isFocused }
                                .combinedClickable(
                                    onClick = {
                                        skipNextJob?.cancel()
                                        playerConnection.seekToNext()
                                    },
                                    onLongClick = {
                                        if (canSkipNext) {
                                            skipNextJob = coroutineScope.launch {
                                                while (isActive) {
                                                    playerConnection.seekToNext()
                                                    delay(200)
                                                }
                                            }
                                        }
                                    }
                                )
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.skip_next),
                                contentDescription = null,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = PlayerHorizontalPadding),
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        ResizableIconButton(
                            icon = when (repeatMode) {
                                Player.REPEAT_MODE_OFF, Player.REPEAT_MODE_ALL -> R.drawable.repeat
                                Player.REPEAT_MODE_ONE -> R.drawable.repeat_one
                                else -> throw IllegalStateException()
                            },
                            color = TextBackgroundColor,
                            modifier = Modifier
                                .size(32.dp)
                                .padding(4.dp)
                                .align(Alignment.Center)
                                .alpha(if (repeatMode == Player.REPEAT_MODE_OFF) 0.5f else 1f),
                            onClick = {
                                playerConnection.player.toggleRepeatMode()
                            },
                        )
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        ResizableIconButton(
                            icon = R.drawable.skip_previous,
                            enabled = canSkipPrevious,
                            color = TextBackgroundColor,
                            modifier =
                            Modifier
                                .size(32.dp)
                                .align(Alignment.Center),
                            onClick = {
                                playerConnection.seekToPrevious()
                            },
                        )
                    }

                    Spacer(Modifier.width(8.dp))

                    val landscapePlayFocused = remember { mutableStateOf(false) }
                    val landscapePlayBorderColor = animateColorAsState(
                        targetValue = if (landscapePlayFocused.value) MaterialTheme.colorScheme.primary else Color.Transparent,
                        label = "landscape_play_focus"
                    )
                    Box(
                        modifier =
                        Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(playPauseRoundness))
                            .background(textButtonColor)
                            .border(3.dp, landscapePlayBorderColor.value, RoundedCornerShape(playPauseRoundness))
                            .focusable()
                            .onFocusChanged { landscapePlayFocused.value = it.isFocused }
                            .clickable {
                                if (playbackState == STATE_ENDED) {
                                    playerConnection.player.seekTo(0, 0)
                                    playerConnection.player.playWhenReady = true
                                } else {
                                    playerConnection.player.togglePlayPause()
                                }
                            },
                    ) {
                        Image(
                            painter =
                            painterResource(
                                if (playbackState ==
                                    STATE_ENDED
                                ) {
                                    R.drawable.replay
                                } else if (isPlaying) {
                                    R.drawable.pause
                                } else {
                                    R.drawable.play
                                },
                            ),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(iconButtonColor),
                            modifier =
                            Modifier
                                .align(Alignment.Center)
                                .size(36.dp),
                        )
                    }

                    Spacer(Modifier.width(8.dp))

                    Box(modifier = Modifier.weight(1f)) {
                        ResizableIconButton(
                            icon = R.drawable.skip_next,
                            enabled = canSkipNext,
                            color = TextBackgroundColor,
                            modifier =
                            Modifier
                                .size(32.dp)
                                .align(Alignment.Center),
                            onClick = {
                                playerConnection.seekToNext()
                            },
                        )
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        ResizableIconButton(
                            icon = if (currentSong?.song?.liked == true) R.drawable.favorite else R.drawable.favorite_border,
                            color = if (currentSong?.song?.liked == true) MaterialTheme.colorScheme.error else TextBackgroundColor,
                            modifier =
                            Modifier
                                .size(32.dp)
                                .padding(4.dp)
                                .align(Alignment.Center),
                            onClick = playerConnection::toggleLike,
                        )
                    }
                }
            }
        }

        when (LocalConfiguration.current.orientation) {
            Configuration.ORIENTATION_LANDSCAPE -> {
                Row(
                    modifier =
                    Modifier
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
                            isPlayerExpanded = state.isExpanded
                        )
                    }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier =
                        Modifier
                            .weight(1f)
                            .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top)),
                    ) {
                        Spacer(Modifier.weight(1f))

                        mediaMetadata?.let {
                            controlsContent(it)
                        }

                        Spacer(Modifier.weight(1f))
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
                            isPlayerExpanded = state.isExpanded
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
                        navController = navController,
                        backgroundAlpha = lyricsSheetState.progress.coerceIn(0f, 1f)
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun BottomSheetPlayerPreview() {
    val state = rememberBottomSheetState(
        dismissedBound = 80.dp,
        expandedBound = 500.dp,
        collapsedBound = 80.dp + 1.dp,
        initialAnchor = 1
    )
    Box(modifier = Modifier.fillMaxSize()) {
        BottomSheetPlayer(
            state = state,
            navController = androidx.navigation.compose.rememberNavController(),
            pureBlack = false
        )
    }
}
