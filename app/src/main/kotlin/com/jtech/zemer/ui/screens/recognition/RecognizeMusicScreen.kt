package com.jtech.zemer.ui.screens.recognition

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.jtech.zemer.LocalDatabase
import com.jtech.zemer.LocalPlayerAwareWindowInsets
import com.jtech.zemer.LocalPlayerConnection
import com.jtech.zemer.R
import com.jtech.zemer.constants.ThumbnailCornerRadius
import com.jtech.zemer.models.toMediaMetadata
import com.jtech.zemer.playback.queues.YouTubeQueue
import com.jtech.zemer.recognition.RecognitionAudioCapture
import com.jtech.zemer.ui.component.IconButton
import com.jtech.zemer.ui.utils.backToMain
import com.jtech.zemer.ui.utils.resize
import com.jtech.zemer.viewmodels.RecognizeMusicViewModel
import com.jtech.zemer.viewmodels.RecognizeUiState
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.WatchEndpoint

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecognizeMusicScreen(
    navController: NavController,
    viewModel: RecognizeMusicViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) viewModel.start()
    }

    // Single entry point for "begin listening": records straight away if we already hold the
    // permission, otherwise shows the system permission prompt and starts once granted.
    val attempt: () -> Unit = {
        if (RecognitionAudioCapture.hasRecordPermission(context)) {
            viewModel.start()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.recognize_music)) },
                navigationIcon = {
                    IconButton(
                        onClick = navController::navigateUp,
                        onLongClick = navController::backToMain,
                    ) {
                        Icon(
                            painterResource(R.drawable.arrow_back),
                            contentDescription = null,
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(LocalPlayerAwareWindowInsets.current.asPaddingValues())
                .padding(horizontal = 32.dp),
            contentAlignment = Alignment.Center,
        ) {
            when (val current = state) {
                is RecognizeUiState.Result -> ResultContent(
                    song = current.song,
                    onTryAgain = attempt,
                )

                else -> StatusContent(
                    state = current,
                    onListen = attempt,
                )
            }
        }
    }
}

@Composable
private fun StatusContent(
    state: RecognizeUiState,
    onListen: () -> Unit,
) {
    val listening = state is RecognizeUiState.Listening
    val working = state is RecognizeUiState.Identifying || state is RecognizeUiState.Searching
    val tappable = state is RecognizeUiState.Idle ||
        state is RecognizeUiState.PermissionRequired ||
        state is RecognizeUiState.NoMatch ||
        state is RecognizeUiState.Error

    val title = when (state) {
        is RecognizeUiState.Listening -> stringResource(R.string.recognize_music_listening)
        is RecognizeUiState.Identifying -> stringResource(R.string.recognize_music_identifying)
        is RecognizeUiState.Searching -> stringResource(R.string.recognize_music_searching)
        is RecognizeUiState.PermissionRequired -> stringResource(R.string.recognize_music_permission_title)
        is RecognizeUiState.NoMatch -> stringResource(R.string.recognize_music_no_match)
        is RecognizeUiState.Error -> stringResource(R.string.recognize_music_error)
        else -> stringResource(R.string.recognize_music_tap_to_start)
    }
    val hint = when (state) {
        is RecognizeUiState.Listening -> stringResource(R.string.recognize_music_listening_hint)
        is RecognizeUiState.PermissionRequired -> stringResource(R.string.recognize_music_permission_rationale)
        is RecognizeUiState.NoMatch -> stringResource(R.string.recognize_music_no_match_hint)
        is RecognizeUiState.Error -> stringResource(R.string.recognize_music_error_hint)
        else -> null
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth(),
    ) {
        MicButton(
            listening = listening,
            working = working,
            enabled = tappable,
            onClick = onListen,
        )

        Spacer(Modifier.height(32.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )

        if (hint != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = hint,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }

        if (state is RecognizeUiState.PermissionRequired) {
            Spacer(Modifier.height(24.dp))
            Button(onClick = onListen) {
                Text(stringResource(R.string.recognize_music_grant_permission))
            }
        }
    }
}

@Composable
private fun MicButton(
    listening: Boolean,
    working: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }

    val pulse = rememberInfiniteTransition(label = "mic_pulse")
    val scale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = if (listening) 1.12f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(700),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "mic_scale",
    )

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(enabled) {
        if (enabled) runCatching { focusRequester.requestFocus() }
    }

    Box(
        modifier = Modifier
            .size(148.dp)
            .scale(if (listening) scale else 1f)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .border(
                width = 3.dp,
                color = if (focused) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = CircleShape,
            )
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused }
            .focusable(enabled = enabled)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (working) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(56.dp),
            )
        } else {
            Icon(
                painter = painterResource(R.drawable.mic),
                contentDescription = stringResource(R.string.recognize_music_mic_button),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(64.dp),
            )
        }
    }
}

@Composable
private fun ResultContent(
    song: SongItem,
    onTryAgain: () -> Unit,
) {
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth(),
    ) {
        AsyncImage(
            model = song.thumbnail.resize(720, 720),
            contentDescription = null,
            modifier = Modifier
                .size(220.dp)
                .clip(RoundedCornerShape(ThumbnailCornerRadius * 2)),
        )

        Spacer(Modifier.height(24.dp))

        Text(
            text = song.title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(4.dp))

        Text(
            text = song.artists.joinToString(" • ") { it.name },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = {
                playerConnection?.playQueue(
                    YouTubeQueue(
                        WatchEndpoint(videoId = song.id),
                        song.toMediaMetadata(),
                        database,
                    ),
                )
            },
        ) {
            Icon(
                painter = painterResource(R.drawable.play),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.recognize_music_play))
        }

        Spacer(Modifier.height(12.dp))

        OutlinedButton(onClick = onTryAgain) {
            Text(stringResource(R.string.recognize_music_try_again))
        }
    }
}
