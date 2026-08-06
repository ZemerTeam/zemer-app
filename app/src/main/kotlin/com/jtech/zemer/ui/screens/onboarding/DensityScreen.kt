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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jtech.zemer.R
import com.jtech.zemer.constants.DensityScale
import com.jtech.zemer.extensions.isInternetConnected
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import com.jtech.zemer.ui.component.DefaultDialog
import com.jtech.zemer.ui.component.OnboardingStepTitle
import com.jtech.zemer.ui.component.OnboardingActionButton
import com.jtech.zemer.ui.component.OnboardingPrimaryButton
import com.jtech.zemer.ui.component.OnboardingTextButton

@Composable
internal fun DensityScreen(
    onSkip: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var isConnected by remember { mutableStateOf(false) }
    var isCheckingNetwork by remember { mutableStateOf(true) }

    // Initial and periodic network checks
    LaunchedEffect(Unit) {
        // Initial check
        isConnected = withContext(Dispatchers.IO) {
            context.isInternetConnected()
        }
        isCheckingNetwork = false

        // Continue checking every 2 seconds
        while (true) {
            delay(2000)
            val newConnectionState = withContext(Dispatchers.IO) {
                context.isInternetConnected()
            }
            if (newConnectionState != isConnected) {
                isConnected = newConnectionState
            }
        }
    }
    var selectedDensity by rememberSaveable { mutableStateOf(DensityScale.NATIVE) }
    var customDensityValue by rememberSaveable { mutableStateOf(0.85f) }
    var showRestartDialog by rememberSaveable { mutableStateOf(false) }
    var showCustomDensityDialog by rememberSaveable { mutableStateOf(false) }

    val densityOptions = DensityScale.entries

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.9f)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                OnboardingStepTitle(
                    text = stringResource(R.string.onboarding_density_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.onboarding_density_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

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

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (selectedDensity != DensityScale.NATIVE) {
                    OnboardingPrimaryButton(
                        text = if (isCheckingNetwork) stringResource(R.string.network_checking)
                              else if (!isConnected) stringResource(R.string.network_internet_required)
                              else stringResource(R.string.onboarding_apply_density),
                        onClick = {
                            val densityValue = if (selectedDensity == DensityScale.CUSTOM) {
                                customDensityValue
                            } else {
                                selectedDensity.value
                            }
                            context.getSharedPreferences("metrolist_settings", Context.MODE_PRIVATE)
                                .edit {
                                    putFloat("density_scale_factor", densityValue)
                                }
                            showRestartDialog = true
                        },
                        enabled = isConnected && !isCheckingNetwork,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                OnboardingActionButton(
                    text = if (isCheckingNetwork) stringResource(R.string.network_checking)
                          else if (!isConnected) stringResource(R.string.network_internet_required)
                          else stringResource(R.string.onboarding_skip),
                    onClick = onSkip,
                    enabled = isConnected && !isCheckingNetwork,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (!isConnected || isCheckingNetwork) {
                    Text(
                        text = if (isCheckingNetwork) stringResource(R.string.network_checking_internet)
                              else stringResource(R.string.network_required_to_continue),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                OnboardingTextButton(
                    text = stringResource(R.string.onboarding_back),
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
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
        title = { Text(stringResource(R.string.restart_required)) },
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
        title = { Text(stringResource(R.string.custom_density_title)) },
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

private fun restartApp(context: Context) {
    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    }
    context.startActivity(intent)
    Runtime.getRuntime().exit(0)
}

