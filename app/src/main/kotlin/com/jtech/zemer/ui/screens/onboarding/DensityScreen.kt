package com.jtech.zemer.ui.screens.onboarding

import android.content.Context
import android.content.Intent
import androidx.core.content.edit
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jtech.zemer.R
import com.jtech.zemer.constants.DensityScale
import com.jtech.zemer.ui.component.DefaultDialog
import com.jtech.zemer.ui.component.onboardingCardColors
import com.jtech.zemer.ui.component.OnboardingStepHeader
import com.jtech.zemer.ui.component.OnboardingPrimaryButton
import com.jtech.zemer.ui.component.OnboardingTextButton

@Composable
internal fun DensityScreen(
    onContinue: () -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("metrolist_settings", Context.MODE_PRIVATE) }
    // the scale in use: the step opens on it, also when Welcome's Back returns here after one was applied
    val storedDensity = remember { prefs.getFloat("density_scale_factor", DensityScale.NATIVE.value) }
    var selectedDensity by rememberSaveable { mutableStateOf(DensityScale.fromValue(storedDensity)) }
    var customDensityValue by rememberSaveable {
        mutableStateOf(if (DensityScale.fromValue(storedDensity) == DensityScale.CUSTOM) storedDensity else 0.85f)
    }
    val selectedValue = if (selectedDensity == DensityScale.CUSTOM) customDensityValue else selectedDensity.value
    var showRestartDialog by rememberSaveable { mutableStateOf(false) }
    var showCustomDensityDialog by rememberSaveable { mutableStateOf(false) }

    val densityOptions = DensityScale.entries

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            // on a screen this list fills, keep the title clear of the status bar
            .windowInsetsPadding(WindowInsets.systemBars)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.9f)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp)
        ) {
            OnboardingStepHeader(
                title = stringResource(R.string.onboarding_density_title),
                subtitle = stringResource(R.string.onboarding_density_subtitle),
                titleStyle = MaterialTheme.typography.titleMedium,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth(),
                colors = onboardingCardColors(active = false),
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(modifier = Modifier.padding(4.dp)) {
                    densityOptions.forEach { density ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .clickable {
                                    if (density == DensityScale.CUSTOM) {
                                        showCustomDensityDialog = true
                                    } else {
                                        selectedDensity = density
                                    }
                                }
                                .padding(horizontal = 4.dp, vertical = 6.dp)
                        ) {
                            RadioButton(
                                selected = selectedDensity == density,
                                onClick = {
                                    if (density == DensityScale.CUSTOM) {
                                        showCustomDensityDialog = true
                                    } else {
                                        selectedDensity = density
                                    }
                                },
                                modifier = Modifier.size(36.dp)
                            )
                            Text(
                                text = if (density == DensityScale.CUSTOM && selectedDensity == DensityScale.CUSTOM) {
                                    stringResource(R.string.density_label_custom_value, (customDensityValue * 100).toInt())
                                } else {
                                    stringResource(density.labelRes)
                                },
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // one button: nothing here needs the network, and an unchanged size is simply continued past
            val changed = OnboardingNavigation.densityChanged(selectedValue, storedDensity)
            OnboardingPrimaryButton(
                text = stringResource(if (changed) R.string.onboarding_apply_density else R.string.onboarding_continue),
                onClick = { if (changed) showRestartDialog = true else onContinue() },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (showCustomDensityDialog) {
        CustomDensityDialog(
            initialValue = customDensityValue,
            onDismiss = { showCustomDensityDialog = false },
            onConfirm = { value ->
                customDensityValue = value
                selectedDensity = DensityScale.CUSTOM
                showCustomDensityDialog = false
            }
        )
    }

    if (showRestartDialog) {
        RestartDialog(
            onDismiss = { showRestartDialog = false },
            onRestart = {
                showRestartDialog = false
                // stored only here, and synchronously: a cancelled restart leaves no scale behind for the next
                // launch, and the process exits right after
                prefs.edit(commit = true) { putFloat("density_scale_factor", selectedValue) }
                restartApp(context)
            }
        )
    }
}

@Composable
private fun RestartDialog(
    onDismiss: () -> Unit,
    onRestart: () -> Unit,
) {
    DefaultDialog(
        onDismiss = onDismiss,
        horizontalAlignment = Alignment.Start,
        title = dialogTitle(R.string.restart_required),
        buttons = {
            OnboardingTextButton(
                text = stringResource(android.R.string.cancel),
                onClick = onDismiss,
            )
            OnboardingPrimaryButton(
                text = stringResource(R.string.restart),
                onClick = onRestart,
            )
        },
    ) {
        Text(
            text = stringResource(R.string.density_restart_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.verticalScroll(rememberScrollState()),
        )
    }
}

@Composable
private fun CustomDensityDialog(
    initialValue: Float,
    onDismiss: () -> Unit,
    onConfirm: (Float) -> Unit,
) {
    var textValue by remember { mutableStateOf((initialValue * 100).toInt().toString()) }
    var isError by remember { mutableStateOf(false) }

    DefaultDialog(
        onDismiss = onDismiss,
        horizontalAlignment = Alignment.Start,
        title = dialogTitle(R.string.custom_density_title),
        buttons = {
            OnboardingTextButton(
                text = stringResource(android.R.string.cancel),
                onClick = onDismiss,
            )
            OnboardingPrimaryButton(
                text = stringResource(R.string.ok),
                onClick = {
                    val intValue = textValue.toIntOrNull()
                    if (intValue != null && intValue in 50..120) {
                        onConfirm(intValue / 100f)
                    }
                },
                enabled = !isError && textValue.isNotEmpty(),
            )
        },
    ) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Text(
                text = stringResource(R.string.custom_density_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            androidx.compose.material3.OutlinedTextField(
                value = textValue,
                onValueChange = { newValue ->
                    textValue = newValue.filter { it.isDigit() }
                    val intValue = textValue.toIntOrNull()
                    isError = intValue == null || intValue !in 50..120
                },
                label = { Text("%") },
                isError = isError,
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * A dialog title, dropped on a screen too short for it: there the two-line headline left no room for the
 * buttons, so a phone that most needs this step could not confirm it. The body says the same thing.
 */
@Composable
private fun dialogTitle(titleRes: Int): (@Composable () -> Unit)? =
    if (LocalConfiguration.current.screenHeightDp < 480) null else ({ Text(stringResource(titleRes)) })

private fun restartApp(context: Context) {
    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    }
    context.startActivity(intent)
    Runtime.getRuntime().exit(0)
}

