package com.jtech.zemer.distribution

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.UriHandler
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.jtech.zemer.App
import com.jtech.zemer.BuildConfig
import com.jtech.zemer.R
import com.jtech.zemer.constants.CheckForUpdatesKey
import com.jtech.zemer.constants.InstallerTypeKey
import com.jtech.zemer.constants.UpdateNotificationsEnabledKey
import com.jtech.zemer.ui.component.UpdateDownloadDialog
import com.jtech.zemer.ui.screens.settings.UpdaterScreen
import com.jtech.zemer.utils.UpdateChecker
import com.jtech.zemer.utils.Updater
import com.jtech.zemer.utils.dataStore
import com.jtech.zemer.utils.get
import com.jtech.zemer.utils.hasNotificationPermission
import com.jtech.zemer.utils.rememberPreference
import com.jtech.zemer.utils.updater.InstallResult
import com.jtech.zemer.utils.updater.InstallerType
import com.jtech.zemer.utils.updater.rememberApkInstallController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.days

/**
 * GitHub flavor: real in-app updater integration. The updater code lives in src/github only; the
 * Play flavor provides no-op equivalents of these seams. See docs/play/PLAN.md §1.1.
 */
object UpdateStartup {
    /** Startup update check (called from App.onCreate). Stashes any available update on App. */
    suspend fun check(app: App) {
        val settings = app.dataStore.data.first()
        if (settings[CheckForUpdatesKey] != true) return
        when (val result = UpdateChecker.checkForUpdates()) {
            is UpdateChecker.UpdateResult.UpdateAvailable -> {
                App.pendingUpdateVersion = result.latestVersion
                App.pendingUpdateNotes = result.notes
            }
            else -> { /* No action needed */ }
        }
    }
}

/**
 * Periodically checks for a newer release and posts an "update available" notification, reporting
 * the latest version name back via [onLatestVersion] (drives the "new version" row in settings).
 */
@SuppressLint("MissingPermission")
@Composable
fun UpdateAvailabilityNotifier(onLatestVersion: (String) -> Unit) {
    val context = LocalContext.current
    val checkForUpdates by rememberPreference(CheckForUpdatesKey, defaultValue = false)

    LaunchedEffect(checkForUpdates) {
        if (checkForUpdates) {
            withContext(Dispatchers.IO) {
                if (System.currentTimeMillis() - Updater.lastCheckTime > 1.days.inWholeMilliseconds) {
                    val updatesEnabled = context.dataStore.get(CheckForUpdatesKey, false)
                    val notifEnabled = context.dataStore.get(UpdateNotificationsEnabledKey, false)
                    if (!updatesEnabled || !hasNotificationPermission(context)) return@withContext
                    Updater.getLatestUpdate().onSuccess { info ->
                        onLatestVersion(info.versionName)
                        if (info.versionName != BuildConfig.VERSION_NAME && notifEnabled) {
                            if (!hasNotificationPermission(context)) return@onSuccess
                            val intent = Intent(Intent.ACTION_VIEW, info.downloadUrl.toUri())
                            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                            val pending = PendingIntent.getActivity(context, 1001, intent, flags)
                            val notif = NotificationCompat.Builder(context, "updates")
                                .setSmallIcon(R.drawable.update)
                                .setContentTitle(context.getString(R.string.update_available_title))
                                .setContentText(info.versionName)
                                .setContentIntent(pending)
                                .setAutoCancel(true)
                                .build()
                            runCatching {
                                NotificationManagerCompat.from(context).notify(1001, notif)
                            }
                        }
                    }
                }
            }
        } else {
            // When the user disables updates, reset to the current version so the app considers
            // itself up to date.
            onLatestVersion(BuildConfig.VERSION_NAME)
        }
    }
}

/** Shows the update download/install dialog when a startup check stashed a pending update. */
@Composable
fun UpdateDownloadPrompt() {
    val context = LocalContext.current
    var showUpdateDialog by rememberSaveable { mutableStateOf(false) }
    var pendingUpdateVersion by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingUpdateNotes by rememberSaveable { mutableStateOf<String?>(null) }
    var downloadState by remember { mutableStateOf<UpdateChecker.DownloadState>(UpdateChecker.DownloadState.Idle) }
    var installError by remember { mutableStateOf<String?>(null) }
    val updateScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        App.pendingUpdateVersion?.let { version ->
            pendingUpdateVersion = version
            pendingUpdateNotes = App.pendingUpdateNotes
            showUpdateDialog = true
            App.clearPendingUpdate()
        }
    }

    // Auto-install when download completes, honoring the chosen install method.
    val (installerTypeOrdinal) = rememberPreference(InstallerTypeKey, defaultValue = InstallerType.NATIVE.ordinal)
    val installController = rememberApkInstallController(InstallerType.fromOrdinal(installerTypeOrdinal)) { result ->
        when (result) {
            is InstallResult.Success -> {
                downloadState = UpdateChecker.DownloadState.Idle
                installError = null
            }
            is InstallResult.RequiresUserAction -> Unit // system installer UI takes over
            is InstallResult.Error -> installError = result.message
        }
    }
    LaunchedEffect(downloadState) {
        val downloaded = downloadState as? UpdateChecker.DownloadState.Downloaded ?: return@LaunchedEffect
        installError = null
        installController.install(downloaded.apkFile)
    }

    if (showUpdateDialog && pendingUpdateVersion != null) {
        UpdateDownloadDialog(
            currentVersion = BuildConfig.VERSION_NAME,
            latestVersion = pendingUpdateVersion!!,
            notes = pendingUpdateNotes,
            downloadState = downloadState,
            isInstalling = installController.isInstalling,
            installError = installError,
            installerType = InstallerType.fromOrdinal(installerTypeOrdinal),
            onDownload = {
                downloadState = UpdateChecker.DownloadState.Downloading(0f)
                installError = null
                updateScope.launch {
                    UpdateChecker.downloadUpdate(context).collect { state ->
                        downloadState = state
                    }
                }
            },
            onInstall = { apk -> installController.install(apk) },
            onDismiss = {
                showUpdateDialog = false
                downloadState = UpdateChecker.DownloadState.Idle
                installError = null
            },
        )
    }
}

/** Opens the cached release download URL in the browser (the "new version available" row). */
fun openLatestDownloadUrl(uriHandler: UriHandler) {
    Updater.getCachedDownloadUrl()?.let { uriHandler.openUri(it) }
}

/** Registers the in-app updater settings screen route. */
@OptIn(ExperimentalMaterial3Api::class)
fun NavGraphBuilder.updaterSettingsRoute(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    composable("settings/updater") {
        UpdaterScreen(navController, scrollBehavior)
    }
}
