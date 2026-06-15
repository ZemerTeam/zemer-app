package com.jtech.zemer.ui.screens.recognition

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.jtech.zemer.R
import com.jtech.zemer.recognition.RecognitionAudioCapture
import com.jtech.zemer.ui.theme.ZemerTheme
import com.jtech.zemer.ui.utils.resize
import com.jtech.zemer.viewmodels.RecognizeMusicViewModel
import com.jtech.zemer.viewmodels.RecognizeUiState
import com.metrolist.innertube.models.SongItem
import dagger.hilt.android.AndroidEntryPoint

/**
 * Transparent, Zemer-branded "Recognize music" popup — a small centered card over the home screen
 * (à la Google's Sound Search), launched by the recognizer widget. Because the activity is visible,
 * it can use the microphone directly (while-in-use) without a foreground service.
 */
@AndroidEntryPoint
class RecognizeMusicDialogActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ZemerTheme {
                RecognizeDialog(
                    onDismiss = { finish() },
                    onPlay = { song ->
                        startActivity(
                            Intent(this, com.jtech.zemer.MainActivity::class.java).apply {
                                action = Intent.ACTION_VIEW
                                data = "https://music.zemer.io/watch?v=${song.id}".toUri()
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                            },
                        )
                        finish()
                    },
                    onOpenHistory = {
                        startActivity(
                            Intent(this, com.jtech.zemer.MainActivity::class.java).apply {
                                action = Intent.ACTION_VIEW
                                data = "https://music.zemer.io/recognition_history".toUri()
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                            },
                        )
                        finish()
                    },
                )
            }
        }
    }
}

@Composable
private fun RecognizeDialog(
    onDismiss: () -> Unit,
    onPlay: (SongItem) -> Unit,
    onOpenHistory: () -> Unit,
    viewModel: RecognizeMusicViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) viewModel.start() }

    val attempt: () -> Unit = {
        if (RecognitionAudioCapture.hasRecordPermission(context)) viewModel.start()
        else permissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
    }

    // rememberSaveable so a config change (rotation) doesn't reset the flag and restart capture /
    // re-prompt for the mic permission — the hiltViewModel survives recreation and keeps running.
    var started by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!started) {
            started = true
            attempt()
        }
    }

    // Scrim — tap outside dismisses.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = colorResource(R.color.widget_background),
            tonalElevation = 0.dp,
            modifier = Modifier
                .fillMaxWidth(0.86f)
                // Swallow taps so tapping the card doesn't dismiss.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 28.dp),
            ) {
                ZemerBrandHeader(onHistory = onOpenHistory)
                Spacer(Modifier.height(24.dp))
                when (val current = state) {
                    is RecognizeUiState.Result -> DialogResult(current.song, onPlay = { onPlay(current.song) }, onRetry = attempt)
                    else -> DialogStatus(current, onAction = attempt, onClose = onDismiss)
                }
            }
        }
    }
}

@Composable
private fun ZemerBrandHeader(onHistory: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = R.mipmap.ic_launcher,
            contentDescription = null,
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape),
        )
        Spacer(Modifier.size(8.dp))
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = colorResource(R.color.widget_text_primary),
        )
        Spacer(Modifier.weight(1f))
        Icon(
            painter = painterResource(R.drawable.history),
            contentDescription = stringResource(R.string.recognition_history),
            tint = colorResource(R.color.widget_text_secondary),
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .clickable(onClick = onHistory),
        )
    }
}

@Composable
private fun DialogStatus(
    state: RecognizeUiState,
    onAction: () -> Unit,
    onClose: () -> Unit,
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

    val pulse = rememberInfiniteTransition(label = "dialog_mic_pulse")
    val scale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = if (listening) 1.12f else 1f,
        animationSpec = infiniteRepeatable(animation = tween(700), repeatMode = RepeatMode.Reverse),
        label = "dialog_mic_scale",
    )

    Box(
        modifier = Modifier
            .size(112.dp)
            .scale(if (listening) scale else 1f)
            .clip(CircleShape)
            .background(colorResource(R.color.widget_accent))
            .clickable(enabled = tappable, onClick = onAction),
        contentAlignment = Alignment.Center,
    ) {
        if (working) {
            CircularProgressIndicator(
                color = colorResource(R.color.widget_text_primary),
                modifier = Modifier.size(44.dp),
            )
        } else {
            Icon(
                painter = painterResource(R.drawable.mic),
                contentDescription = stringResource(R.string.recognize_music_mic_button),
                tint = colorResource(R.color.widget_text_primary),
                modifier = Modifier.size(48.dp),
            )
        }
    }

    Spacer(Modifier.height(20.dp))
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = colorResource(R.color.widget_text_primary),
        textAlign = TextAlign.Center,
    )
    if (hint != null) {
        Spacer(Modifier.height(6.dp))
        Text(
            text = hint,
            style = MaterialTheme.typography.bodyMedium,
            color = colorResource(R.color.widget_text_secondary),
            textAlign = TextAlign.Center,
        )
    }
    if (tappable) {
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onClose) { Text(stringResource(R.string.recognize_music_close)) }
    }
}

@Composable
private fun DialogResult(
    song: SongItem,
    onPlay: () -> Unit,
    onRetry: () -> Unit,
) {
    AsyncImage(
        model = song.thumbnail.resize(540, 540),
        contentDescription = null,
        modifier = Modifier
            .size(160.dp)
            .clip(RoundedCornerShape(16.dp)),
    )
    Spacer(Modifier.height(16.dp))
    Text(
        text = song.title,
        style = MaterialTheme.typography.titleLarge,
        color = colorResource(R.color.widget_text_primary),
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
    Spacer(Modifier.height(4.dp))
    Text(
        text = song.artists.joinToString(" • ") { it.name },
        style = MaterialTheme.typography.bodyMedium,
        color = colorResource(R.color.widget_text_secondary),
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
    Spacer(Modifier.height(24.dp))
    Button(onClick = onPlay, modifier = Modifier.fillMaxWidth()) {
        Icon(painterResource(R.drawable.play), contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.size(8.dp))
        Text(stringResource(R.string.recognize_music_play))
    }
    Spacer(Modifier.height(4.dp))
    TextButton(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.recognize_music_try_again))
    }
}
