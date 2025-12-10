package com.jtech.zemer.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.edit
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieClipSpec
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.airbnb.lottie.compose.rememberLottieDynamicProperties
import com.airbnb.lottie.compose.rememberLottieDynamicProperty
import com.jtech.zemer.R
import com.jtech.zemer.constants.DensityScale
import com.jtech.zemer.utils.PermissionHelper

private enum class OnboardingStep { Welcome, Density, Permissions, BottomNavSetup, Loading }
private enum class LegalKind { TOS, PRIVACY }

@Composable
fun OnboardingFlow(
    onFinished: () -> Unit,
) {
    val context = LocalContext.current
    val densityAlreadySet = remember {
        val prefs = context.getSharedPreferences("metrolist_settings", Context.MODE_PRIVATE)
        prefs.getFloat("density_scale_factor", 1.0f) != 1.0f
    }
    var step by rememberSaveable { mutableStateOf(OnboardingStep.Welcome) }

    when (step) {
        OnboardingStep.Welcome -> WelcomeScreen(
            onContinue = {
                step = if (densityAlreadySet) OnboardingStep.Permissions else OnboardingStep.Density
            }
        )

        OnboardingStep.Density -> DensityScreen(
            onSkip = { step = OnboardingStep.Permissions },
            onBack = { step = OnboardingStep.Welcome }
        )

        OnboardingStep.Permissions -> PermissionsScreen(
            onBack = { step = if (densityAlreadySet) OnboardingStep.Welcome else OnboardingStep.Density },
            onComplete = { step = OnboardingStep.BottomNavSetup }
        )

        OnboardingStep.BottomNavSetup -> BottomNavSetupScreen(
            onBack = { step = OnboardingStep.Permissions },
            onComplete = { step = OnboardingStep.Loading }
        )

        OnboardingStep.Loading -> LoadingScreen(
            onFinished = onFinished
        )
    }
}

@Composable
private fun WelcomeScreen(
    onContinue: () -> Unit,
) {
    val gradient = Brush.verticalGradient(
        listOf(
            MaterialTheme.colorScheme.surface,
            MaterialTheme.colorScheme.surface
        )
    )
    var legal by remember { mutableStateOf<LegalKind?>(null) }
    var agreed by rememberSaveable { mutableStateOf(false) }
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.welcome))
    val animationState = animateLottieCompositionAsState(
        composition = composition,
        iterations = 1,
        clipSpec = LottieClipSpec.Progress(0f, 0.5f),
        restartOnPlay = false
    )
    val lottieColors = rememberLottieDynamicProperties(
        rememberLottieDynamicProperty(
            property = LottieProperty.COLOR_FILTER,
            value = PorterDuffColorFilter(MaterialTheme.colorScheme.primary.toArgb(), PorterDuff.Mode.SRC_ATOP),
            keyPath = arrayOf("**")
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(gradient),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .fillMaxHeight()
                .padding(vertical = 48.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                LottieAnimation(
                    composition = composition,
                    progress = { animationState.progress },
                    dynamicProperties = lottieColors,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp)
                )
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { agreed = !agreed }
                ) {
                    Checkbox(checked = agreed, onCheckedChange = { agreed = it })
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.onboarding_agree_label),
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.onboarding_view_tos),
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.clickable { legal = LegalKind.TOS }
                            )
                            Text(
                                text = "•",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall
                            )
                            Text(
                                text = stringResource(R.string.onboarding_view_privacy),
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.clickable { legal = LegalKind.PRIVACY }
                            )
                        }
                    }
                }

                Button(
                    onClick = onContinue,
                    enabled = agreed,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.onboarding_continue),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }

        legal?.let { kind ->
            LegalOverlay(
                title = stringResource(
                    if (kind == LegalKind.TOS) R.string.onboarding_tos_title else R.string.onboarding_privacy_title
                ),
                body = stringResource(
                    if (kind == LegalKind.TOS) R.string.onboarding_tos_body else R.string.onboarding_privacy_body
                ),
                onDismiss = { legal = null }
            )
        }
    }
}

@Composable
private fun DensityScreen(
    onSkip: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
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
                .fillMaxWidth(0.92f)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.onboarding_density_title),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.primary
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
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(10.dp)
                    ),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
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
                                    "Custom (${(customDensityValue * 100).toInt()}%)"
                                } else {
                                    density.label
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
                    Button(
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(
                            text = stringResource(R.string.onboarding_apply_density),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }

                OutlinedButton(
                    onClick = onSkip,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Text(
                        text = stringResource(R.string.onboarding_skip),
                        style = MaterialTheme.typography.labelMedium
                    )
                }

                TextButton(
                    onClick = onBack,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(32.dp)
                ) {
                    Text(
                        text = stringResource(R.string.onboarding_back),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .padding(20.dp),
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 6.dp,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.restart_required),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(R.string.density_restart_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(android.R.string.cancel))
                    }
                    Button(
                        onClick = onRestart,
                        modifier = Modifier.padding(start = 8.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(stringResource(R.string.restart))
                    }
                }
            }
        }
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .padding(20.dp)
                .clickable(enabled = false) { },
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 6.dp,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.custom_density_title),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(R.string.custom_density_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(android.R.string.cancel))
                    }
                    Button(
                        onClick = {
                            val intValue = textValue.toIntOrNull()
                            if (intValue != null && intValue in 50..120) {
                                onConfirm(intValue / 100f)
                            }
                        },
                        enabled = !isError && textValue.isNotEmpty(),
                        modifier = Modifier.padding(start = 8.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(stringResource(R.string.ok))
                    }
                }
            }
        }
    }
}

private fun restartApp(context: Context) {
    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    }
    context.startActivity(intent)
    Runtime.getRuntime().exit(0)
}

@Composable
private fun PermissionsScreen(
    onBack: () -> Unit,
    onComplete: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var storageGranted by remember {
        mutableStateOf(PermissionHelper.hasMediaStoreWritePermission(context))
    }

    var notificationsGranted by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                NotificationManagerCompat.from(context).areNotificationsEnabled()
            } else {
                true
            }
        )
    }

    var nearbyGranted by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.NEARBY_WIFI_DEVICES
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    var backgroundGranted by remember { mutableStateOf(isIgnoringBatteryOptimizations(context)) }
    var accessibilityGranted by remember { mutableStateOf(isAccessibilityEnabled(context)) }
    var systemAlertGranted by remember {
        mutableStateOf(
            Settings.canDrawOverlays(context)
        )
    }
    // PiP permission is declared in manifest but doesn't require runtime permission grant
    // Mark as true by default since it's available on Android 8.0+
    var pipGranted by remember { mutableStateOf(true) }

    val storagePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        storageGranted = permissions.values.all { it }
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationsGranted = granted
    }

    val nearbyLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        nearbyGranted = granted
    }

    DisposableLifecycle(onEvent = {
        backgroundGranted = isIgnoringBatteryOptimizations(context)
        accessibilityGranted = isAccessibilityEnabled(context)
        systemAlertGranted = Settings.canDrawOverlays(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationsGranted = NotificationManagerCompat.from(context).areNotificationsEnabled()
            nearbyGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.NEARBY_WIFI_DEVICES
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        storageGranted = PermissionHelper.hasMediaStoreWritePermission(context)
    }, lifecycleOwner = lifecycleOwner)

    // Required permissions that must be granted to continue
    val requiredGranted = storageGranted && notificationsGranted && backgroundGranted

    val allGranted = listOf(
        storageGranted,
        notificationsGranted,
        nearbyGranted,
        backgroundGranted,
        accessibilityGranted,
        systemAlertGranted,
        pipGranted
    ).all { it }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.9f)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.onboarding_permissions_title),
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = if (allGranted) {
                        "All set!"
                    } else {
                        stringResource(R.string.onboarding_permissions_subtitle)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (allGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Show only the first needed permission
            if (!storageGranted) {
                PermissionCard(
                    title = stringResource(R.string.onboarding_perm_storage_title),
                    description = stringResource(R.string.onboarding_perm_storage_desc),
                    granted = storageGranted,
                    actionLabel = stringResource(R.string.onboarding_grant),
                ) {
                    storagePermissionLauncher.launch(PermissionHelper.getRequiredWritePermissions())
                }
            } else if (!notificationsGranted) {
                PermissionCard(
                    title = stringResource(R.string.onboarding_perm_notifications_title),
                    description = stringResource(R.string.onboarding_perm_notifications_desc),
                    granted = notificationsGranted,
                    actionLabel = stringResource(R.string.onboarding_grant),
                ) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        openAppSettings(context)
                    }
                }
            } else if (!backgroundGranted) {
                PermissionCard(
                    title = stringResource(R.string.onboarding_perm_background_title),
                    description = stringResource(R.string.onboarding_perm_background_desc),
                    granted = backgroundGranted,
                    actionLabel = stringResource(R.string.onboarding_open_settings),
                ) {
                    openBatterySettings(context)
                }
            } else if (!accessibilityGranted) {
                PermissionCard(
                    title = stringResource(R.string.onboarding_perm_accessibility_title),
                    description = stringResource(R.string.onboarding_perm_accessibility_desc),
                    granted = accessibilityGranted,
                    actionLabel = stringResource(R.string.onboarding_open_settings),
                ) {
                    openAccessibilitySettings(context)
                }
            } else if (!systemAlertGranted) {
                PermissionCard(
                    title = stringResource(R.string.onboarding_perm_system_alert_title),
                    description = stringResource(R.string.onboarding_perm_system_alert_desc),
                    granted = systemAlertGranted,
                    actionLabel = stringResource(R.string.onboarding_open_settings),
                ) {
                    openSystemAlertSettings(context)
                }
            } else if (!nearbyGranted) {
                PermissionCard(
                    title = stringResource(R.string.onboarding_perm_nearby_title),
                    description = stringResource(R.string.onboarding_perm_nearby_desc),
                    granted = nearbyGranted,
                    actionLabel = stringResource(R.string.onboarding_grant),
                ) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        nearbyLauncher.launch(Manifest.permission.NEARBY_WIFI_DEVICES)
                    } else {
                        openAppSettings(context)
                    }
                }
            } else if (!pipGranted) {
                PermissionCard(
                    title = stringResource(R.string.onboarding_perm_pip_title),
                    description = stringResource(R.string.onboarding_perm_pip_desc),
                    granted = pipGranted,
                    actionLabel = stringResource(R.string.onboarding_grant),
                ) {
                    // PiP permission doesn't require explicit grant on most devices
                    // Just mark as granted and continue
                    pipGranted = true
                }
            }

            Spacer(Modifier.height(16.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!requiredGranted) {
                    Text(
                        text = stringResource(R.string.onboarding_permissions_required),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Button(
                    onClick = onComplete,
                    enabled = requiredGranted,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        text = stringResource(R.string.onboarding_continue),
                        style = MaterialTheme.typography.labelMedium
                    )
                }

                TextButton(
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.onboarding_back),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionCard(
    title: String,
    description: String,
    granted: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
) {
    val indicatorColor by animateColorAsState(
        targetValue = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        label = "perm_indicator"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.5.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                shape = RoundedCornerShape(12.dp)
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (granted)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = description,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                val statusText = if (granted) {
                    stringResource(R.string.onboarding_status_done)
                } else {
                    stringResource(R.string.onboarding_status_needed)
                }
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(indicatorColor.copy(alpha = 0.12f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = statusText,
                        color = indicatorColor,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            OutlinedButton(
                onClick = onAction,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp),
                shape = RoundedCornerShape(9.dp),
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun LegalOverlay(
    title: String,
    body: String,
    onDismiss: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .padding(20.dp),
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 6.dp,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = stringResource(R.string.ok),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}

@Composable
fun LoadingScreen(
    onFinished: () -> Unit,
    shouldStartSync: Boolean = true,
) {
    val syncUtils = com.jtech.zemer.LocalSyncUtils.current
    val progress by syncUtils.whitelistSyncProgress.collectAsState()
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.loading_dots_blue))
    val lottieColors = rememberLottieDynamicProperties(
        rememberLottieDynamicProperty(
            property = LottieProperty.COLOR_FILTER,
            value = PorterDuffColorFilter(MaterialTheme.colorScheme.primary.toArgb(), PorterDuff.Mode.SRC_ATOP),
            keyPath = arrayOf("**")
        )
    )

    val loopingState = animateLottieCompositionAsState(
        composition = composition,
        iterations = LottieConstants.IterateForever,
        restartOnPlay = true
    )


    LaunchedEffect(Unit) {
        if (shouldStartSync) {
            syncUtils.syncArtistWhitelist(forceSync = true)
        }
    }

    LaunchedEffect(progress.isComplete) {
        // Wait for sync to complete before finishing
        if (progress.isComplete) {
            onFinished()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .padding(horizontal = 24.dp, vertical = 24.dp)
                .fillMaxWidth()
        ) {
            LottieAnimation(
                composition = composition,
                progress = { loopingState.progress },
                dynamicProperties = lottieColors,
                modifier = Modifier
                    .size(320.dp)
            )

            Spacer(modifier = Modifier.height(48.dp))

            Text(
                text = stringResource(R.string.setting_up_library_title),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun DisposableLifecycle(
    onEvent: () -> Unit,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
) {
    LaunchedEffect(lifecycleOwner) {
        onEvent()
    }

    val observer = remember {
        LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                onEvent()
            }
        }
    }

    DisposableEffectWithLifecycle(lifecycleOwner, observer)
}

@Composable
private fun DisposableEffectWithLifecycle(
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    observer: LifecycleEventObserver,
) {
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
}

@Composable
private fun BottomNavSetupScreen(
    onBack: () -> Unit,
    onComplete: () -> Unit,
) {
    val context = LocalContext.current
    var enableBottomNav by remember { mutableStateOf(true) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.9f)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Navigation Setup",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Would you like to enable the bottom navigation bar for quick access to your favorite screens?",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Choice cards similar to permission cards
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Enable option
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 1.5.dp,
                            color = if (enableBottomNav)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.outline,
                            shape = RoundedCornerShape(12.dp)
                        ),
                    colors = CardDefaults.cardColors(
                        containerColor = if (enableBottomNav)
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                        else
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { enableBottomNav = true }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Enable Bottom Navigation",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Show bottom navigation bar for quick access",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                        androidx.compose.material3.RadioButton(
                            selected = enableBottomNav,
                            onClick = { enableBottomNav = true },
                            colors = androidx.compose.material3.RadioButtonDefaults.colors(
                                selectedColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                }

                // Disable option
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 1.5.dp,
                            color = if (!enableBottomNav)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.outline,
                            shape = RoundedCornerShape(12.dp)
                        ),
                    colors = CardDefaults.cardColors(
                        containerColor = if (!enableBottomNav)
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                        else
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { enableBottomNav = false }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "No thanks",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "I can enable it later in appearance settings",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                        androidx.compose.material3.RadioButton(
                            selected = !enableBottomNav,
                            onClick = { enableBottomNav = false },
                            colors = androidx.compose.material3.RadioButtonDefaults.colors(
                                selectedColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "You can customize which menu items appear later in appearance settings",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
                )

                Button(
                    onClick = {
                        // Save preference using SharedPreferences
                        val prefs = context.getSharedPreferences("metrolist_settings", Context.MODE_PRIVATE)
                        prefs.edit {
                            putBoolean("bottomNavigationBarEnabled", enableBottomNav)
                            // Set default items if enabling
                            if (enableBottomNav) {
                                putString("bottomNavigationItems", "home,artists,search,library")
                            }
                        }
                        onComplete()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        text = "Continue",
                        style = MaterialTheme.typography.labelMedium
                    )
                }

                TextButton(
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Back",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
    return pm.isIgnoringBatteryOptimizations(context.packageName)
}

@SuppressLint("BatteryLife", "UseKtx")
private fun openBatterySettings(context: Context) {
    runCatching {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = "package:${context.packageName}".toUri()
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}

private fun isAccessibilityEnabled(context: Context): Boolean {
    val enabledServices = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false
    return enabledServices.contains("${context.packageName}/com.jtech.zemer.accessibility.ButtonMapperAccessibilityService")
}

private fun openAccessibilitySettings(context: Context) {
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

@SuppressLint("UseKtx")
private fun openSystemAlertSettings(context: Context) {
    runCatching {
        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
            data = "package:${context.packageName}".toUri()
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}

private fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}
