package com.jtech.zemer.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.jtech.zemer.LocalPlayerAwareWindowInsets
import com.jtech.zemer.LocalPlayerConnection
import com.jtech.zemer.R
import com.jtech.zemer.playback.AudioEffectsEngine
import com.jtech.zemer.playback.EqualizerState
import com.jtech.zemer.ui.component.IconButton
import com.jtech.zemer.ui.component.PreferenceGroupTitle
import com.jtech.zemer.ui.utils.backToMain

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerSettings(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val engine = playerConnection.service.audioEffects
    val eqState by engine.equalizerState.collectAsState()

    EqualizerScreenContent(
        eqState = eqState,
        onEnabledChange = engine::setEqualizerEnabled,
        onPresetSelected = engine::setPreset,
        onBandChange = engine::setBand,
        onBandCommit = engine::commitEqualizer,
        navController = navController,
        scrollBehavior = scrollBehavior,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EqualizerScreenContent(
    eqState: EqualizerState,
    onEnabledChange: (Boolean) -> Unit,
    onPresetSelected: (String) -> Unit,
    onBandChange: (Int, Float) -> Unit,
    onBandCommit: () -> Unit,
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val backFocus = remember { FocusRequester() }
    val firstFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) { firstFocus.requestFocus() }

    Column(
        Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
            .verticalScroll(rememberScrollState()),
    ) {
        SwitchRow(
            title = stringResource(R.string.equalizer),
            description = stringResource(R.string.equalizer_desc),
            checked = eqState.enabled,
            onCheckedChange = onEnabledChange,
            modifier = Modifier.focusRequester(firstFocus),
        )

        val presets = AudioEffectsEngine.presetNames()
        PreferenceGroupTitle(title = stringResource(R.string.equalizer_preset))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            presets.forEach { name ->
                val selected = name == eqState.preset
                FilterChip(
                    selected = selected,
                    onClick = { onPresetSelected(name) },
                    label = {
                        Text(
                            text = presetLabel(name),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    },
                )
            }
        }

        PreferenceGroupTitle(title = stringResource(R.string.equalizer))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            AudioEffectsEngine.EQ_BAND_FREQS.forEachIndexed { index, freq ->
                val value = eqState.bands.getOrElse(index) { 0f }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(R.string.equalizer_band_hz, freq),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.weight(0.28f),
                    )
                    Slider(
                        value = value,
                        onValueChange = { onBandChange(index, it) },
                        onValueChangeFinished = onBandCommit,
                        valueRange = -15f..15f,
                        steps = 60,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(),
                        enabled = eqState.enabled,
                    )
                    Text(
                        text = "%.1f".format(value),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.weight(0.22f),
                    )
                }
            }
        }
    }

    TopAppBar(
        title = { Text(stringResource(R.string.equalizer)) },
        navigationIcon = {
            IconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain,
            ) {
                Icon(
                    painterResource(R.drawable.arrow_back),
                    contentDescription = null,
                    modifier = Modifier
                        .focusRequester(backFocus)
                        .focusProperties { down = firstFocus },
                )
            }
        },
    )
}

@Composable
private fun SwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun presetLabel(name: String): String {
    if (name == "flat") return "Flat"
    if (name == "custom") return "Custom"
    return name.replaceFirstChar { it.uppercase() }
}
