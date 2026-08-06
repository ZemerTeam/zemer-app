package com.jtech.zemer.ui.screens.onboarding

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.jtech.zemer.R
import com.jtech.zemer.utils.PermissionHelper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.jtech.zemer.ui.component.OnboardingStepTitle
import com.jtech.zemer.ui.component.OnboardingActionButton
import com.jtech.zemer.ui.component.OnboardingPrimaryButton
import com.jtech.zemer.ui.component.OnboardingTextButton
import com.jtech.zemer.ui.screens.DisposableLifecycle

@Composable
internal fun PermissionsScreen(
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
                OnboardingStepTitle(
                    text = stringResource(R.string.onboarding_permissions_title),
                )
                Text(
                    text = if (allGranted) {
                        stringResource(R.string.onboarding_all_set)
                    } else {
                        stringResource(R.string.onboarding_permissions_subtitle)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (allGranted) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
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

                OnboardingPrimaryButton(
                    text = stringResource(R.string.onboarding_continue),
                    onClick = onComplete,
                    enabled = requiredGranted,
                    modifier = Modifier.fillMaxWidth(),
                )

                OnboardingTextButton(
                    text = stringResource(R.string.onboarding_back),
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth(),
                )
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
            .fillMaxWidth(),
        colors = onboardingCardColors(active = granted),
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

            OnboardingActionButton(
                text = actionLabel,
                onClick = onAction,
                modifier = Modifier.fillMaxWidth(),
            )
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
