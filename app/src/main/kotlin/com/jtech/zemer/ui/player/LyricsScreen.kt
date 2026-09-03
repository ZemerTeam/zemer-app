package com.jtech.zemer.ui.player

import android.app.Activity
import android.content.res.Configuration
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.media3.common.C
import androidx.media3.common.Player
import coil3.compose.AsyncImage
import com.jtech.zemer.LocalDatabase
import com.jtech.zemer.LocalPlayerConnection
import com.jtech.zemer.R
import com.jtech.zemer.constants.PlayerBackgroundStyle
import com.jtech.zemer.constants.PlayerBackgroundStyleKey
import com.jtech.zemer.constants.SliderStyle
import com.jtech.zemer.constants.SliderStyleKey
import com.jtech.zemer.db.entities.LyricsEntity
import com.jtech.zemer.lyrics.LyricsUtils
import com.jtech.zemer.di.LyricsHelperEntryPoint
import com.jtech.zemer.extensions.repeatModeContentDescriptionRes
import com.jtech.zemer.extensions.repeatModeIconRes
import com.jtech.zemer.extensions.shuffleIconRes
import com.jtech.zemer.extensions.toggleRepeatMode
import com.jtech.zemer.models.MediaMetadata
import com.jtech.zemer.ui.component.LocalMenuState
import com.jtech.zemer.ui.component.Lyrics
import com.jtech.zemer.ui.component.lyrics.LyricsNowPlayingBar
import com.jtech.zemer.ui.component.lyrics.LyricsSourceHeader
import com.jtech.zemer.ui.menu.LyricsMenu
import com.jtech.zemer.utils.rememberEnumPreference
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import timber.log.Timber

/**
 * The dedicated full-screen lyrics view (opened from the player's lyrics button as a sheet). It REUSES the
 * player's transport: [PlayerSeekBar] and [PlayerTransportRow] are the same composables the full player
 * draws — one implementation, two hosts. Lyrics-specific pieces: [LyricsSourceHeader] (provenance),
 * the shared [Lyrics] pane, and [LyricsNowPlayingBar]. Portrait and landscape compose the same parts.
 */
@Composable
fun LyricsScreen(
    mediaMetadata: MediaMetadata,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundAlpha: Float = 1f,
) {
    val context = LocalContext.current
    val activity = context as? Activity
    DisposableEffect(Unit) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    val playerConnection = LocalPlayerConnection.current ?: return
    val menuState = LocalMenuState.current
    val database = LocalDatabase.current

    val playbackState by playerConnection.playbackState.collectAsState()
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val isStationBroadcast by playerConnection.isStationBroadcast.collectAsState()
    val canSkipPrevious by playerConnection.canSkipPrevious.collectAsState()
    val canSkipNext by playerConnection.canSkipNext.collectAsState()
    val repeatMode by playerConnection.repeatMode.collectAsState()
    val shuffleModeEnabled by playerConnection.shuffleModeEnabled.collectAsState()
    val sliderStyle by rememberEnumPreference(SliderStyleKey, SliderStyle.DEFAULT)
    val currentLyrics by playerConnection.currentLyrics.collectAsState(initial = null)

    // Opening the screen fetches when nothing is cached, and re-resolves a legacy (pre-provider) row once so
    // its provenance becomes known (LyricsEntity.needsFetch / resolved: the same policy as the service
    // prefetch). Episodes have no lyrics — never fetch/store for them.
    val needsFetch = LyricsEntity.needsFetch(currentLyrics)
    LaunchedEffect(mediaMetadata.id, needsFetch) {
        if (needsFetch && !mediaMetadata.isEpisode) {
            delay(500)
            try {
                val cached = currentLyrics
                val fetched = EntryPointAccessors.fromApplication(context.applicationContext, LyricsHelperEntryPoint::class.java).lyricsHelper().getLyrics(mediaMetadata)
                database.query { upsert(LyricsEntity.resolved(mediaMetadata.id, cached, fetched.lyrics, fetched.provider)) }
            } catch (e: Exception) {
                Timber.w(e, "lyrics fetch failed for ${mediaMetadata.id}")
            }
        }
    }

    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(C.TIME_UNSET) }
    var sliderPosition by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(playbackState) {
        if (playbackState == Player.STATE_READY) {
            while (isActive) {
                delay(100)
                position = playerConnection.currentPositionMs()
                duration = playerConnection.currentDurationMs()
            }
        }
    }

    val playerBackgroundPref by rememberEnumPreference(PlayerBackgroundStyleKey, PlayerBackgroundStyle.DEFAULT)
    val playerBackground = playerBackgroundPref.effective()
    val useDarkTheme = isSystemInDarkTheme()
    val fallbackColor = MaterialTheme.colorScheme.surface.toArgb()
    val gradientColors = rememberPlayerGradient(mediaId = mediaMetadata.id, thumbnailUrl = mediaMetadata.thumbnailUrl, enabled = playerBackground == PlayerBackgroundStyle.GRADIENT, fallbackColor = fallbackColor)
    val textColor = when (playerBackground) {
        PlayerBackgroundStyle.DEFAULT -> MaterialTheme.colorScheme.onBackground
        else -> if (useDarkTheme) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onPrimary
    }
    val onTextColor = when (playerBackground) {
        PlayerBackgroundStyle.DEFAULT -> MaterialTheme.colorScheme.background
        else -> if (useDarkTheme) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.primary
    }
    val accentColor = MaterialTheme.colorScheme.primary
    val lyricsBody = currentLyrics?.lyrics?.trim()
    val hasLyrics = !lyricsBody.isNullOrEmpty() && lyricsBody != LyricsEntity.LYRICS_NOT_FOUND
    val lyricsSynced = hasLyrics && LyricsUtils.isSynced(lyricsBody!!)
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val showLyricsMenu = { menuState.show { LyricsMenu(lyricsProvider = { currentLyrics }, mediaMetadataProvider = { mediaMetadata }, onDismiss = menuState::dismiss) } }

    BackHandler(onBack = onBackClick)

    Box(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize().alpha(backgroundAlpha)) {
            when (playerBackground) {
                PlayerBackgroundStyle.BLUR -> AnimatedContent(targetState = mediaMetadata.thumbnailUrl, transitionSpec = { fadeIn(tween(800)).togetherWith(fadeOut(tween(800))) }, label = "blurBackground") { url ->
                    if (url != null) {
                        AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize().blur(if (useDarkTheme) 150.dp else 100.dp))
                        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)))
                    }
                }
                PlayerBackgroundStyle.GRADIENT -> AnimatedContent(targetState = gradientColors, transitionSpec = { fadeIn(tween(800)).togetherWith(fadeOut(tween(800))) }, label = "gradientBackground") { colors ->
                    if (colors.isNotEmpty()) Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(colorStops = playerGradientStops(colors))).background(Color.Black.copy(alpha = 0.2f)))
                }
                else -> {}
            }
            if (playerBackground != PlayerBackgroundStyle.DEFAULT) Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)))
        }

        @Composable
        fun TopBar() {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp).zIndex(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                IconButton(onClick = onBackClick) { Icon(painterResource(R.drawable.expand_more), contentDescription = stringResource(R.string.close), tint = textColor) }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.now_playing), style = MaterialTheme.typography.titleMedium, color = textColor)
                    Text(mediaMetadata.title, style = MaterialTheme.typography.titleMedium, color = textColor.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = showLyricsMenu) { Icon(painterResource(R.drawable.more_horiz), contentDescription = stringResource(R.string.more_options), tint = textColor) }
            }
        }

        @Composable
        fun LyricsPane(modifier: Modifier) {
            Column(modifier = modifier) {
                if (hasLyrics) LyricsSourceHeader(provider = currentLyrics?.provider, synced = lyricsSynced, color = textColor)
                Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                    Lyrics(sliderPositionProvider = { sliderPosition })
                }
            }
        }

        @Composable
        fun Transport(modifier: Modifier) {
            Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
                LyricsNowPlayingBar(mediaMetadata = mediaMetadata, textColor = textColor, onMoreClick = showLyricsMenu)
                Spacer(Modifier.height(8.dp))
                PlayerSeekBar(
                    sliderStyle = sliderStyle, isStationBroadcast = isStationBroadcast, position = position, duration = duration,
                    sliderPosition = sliderPosition, isPlaying = isPlaying, accentColor = accentColor, textColor = textColor,
                    playerBackground = playerBackground, useDarkTheme = useDarkTheme,
                    onSliderPositionChange = { sliderPosition = it },
                    onSeek = { playerConnection.seekTo(it); position = it },
                )
                Spacer(Modifier.height(12.dp))
                PlayerTransportRow(
                    isPlaying = isPlaying, ended = playbackState == Player.STATE_ENDED, canSkipPrevious = canSkipPrevious, canSkipNext = canSkipNext,
                    accentColor = accentColor, playButtonContainerColor = textColor, playButtonContentColor = onTextColor,
                    sideButtonContainerColor = textColor.copy(alpha = 0.18f), sideButtonContentColor = textColor,
                    onPlayPause = { playerConnection.playPauseOrReplay(playbackState == Player.STATE_ENDED) },
                    onPrevious = playerConnection::seekToPrevious, onNext = playerConnection::seekToNext, isStationBroadcast = isStationBroadcast,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    IconButton(onClick = { if (!isStationBroadcast) playerConnection.player.toggleRepeatMode() }) {
                        Icon(painterResource(repeatModeIconRes(repeatMode)), contentDescription = stringResource(repeatModeContentDescriptionRes(repeatMode)), tint = if (repeatMode == Player.REPEAT_MODE_OFF) textColor.copy(alpha = 0.4f) else textColor)
                    }
                    IconButton(onClick = { if (!isStationBroadcast) playerConnection.player.shuffleModeEnabled = !shuffleModeEnabled }) {
                        Icon(painterResource(shuffleIconRes(shuffleModeEnabled)), contentDescription = stringResource(R.string.shuffle), tint = if (shuffleModeEnabled) textColor else textColor.copy(alpha = 0.4f))
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }

        Column(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
            TopBar()
            if (isLandscape) {
                Row(modifier = Modifier.fillMaxSize()) {
                    LyricsPane(Modifier.weight(1f).fillMaxHeight())
                    Transport(Modifier.weight(1f).fillMaxHeight().padding(top = 8.dp))
                }
            } else {
                LyricsPane(Modifier.weight(1f).fillMaxWidth())
                Transport(Modifier.fillMaxWidth())
            }
        }
    }
}
