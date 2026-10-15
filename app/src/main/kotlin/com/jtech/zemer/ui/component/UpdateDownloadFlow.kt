package com.jtech.zemer.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.jtech.zemer.BuildConfig
import com.jtech.zemer.constants.InstallerTypeKey
import com.jtech.zemer.utils.UpdateChecker
import com.jtech.zemer.utils.rememberPreference
import com.jtech.zemer.utils.updater.InstallResult
import com.jtech.zemer.utils.updater.InstallerType
import com.jtech.zemer.utils.updater.rememberApkInstallController
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch

/**
 * The whole "update available -> download -> install" flow around [UpdateDownloadDialog]: download state,
 * cancel-with-cleanup, and auto-install on completion honoring the chosen install method (the shared
 * controller gates the Standard installer's permission and shows the silent-install heads-up). Used by
 * the startup update prompt and the version gate so both behave identically. The dialog cannot be
 * dismissed while downloading or installing, so [onDismiss] never abandons a running download.
 */
@Composable
fun UpdateDownloadFlow(
    latestVersion: String,
    isNightly: Boolean,
    notes: String?,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var downloadState by remember { mutableStateOf<UpdateChecker.DownloadState>(UpdateChecker.DownloadState.Idle) }
    var installError by remember { mutableStateOf<String?>(null) }
    var downloadJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()

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

    UpdateDownloadDialog(
        currentVersion = BuildConfig.VERSION_NAME,
        latestVersion = latestVersion,
        isNightly = isNightly,
        notes = notes,
        downloadState = downloadState,
        isInstalling = installController.isInstalling,
        installError = installError,
        installerType = InstallerType.fromOrdinal(installerTypeOrdinal),
        onDownload = {
            downloadState = UpdateChecker.DownloadState.Downloading(0f)
            installError = null
            downloadJob = scope.launch {
                UpdateChecker.downloadUpdate(context, isNightly).collect { state -> downloadState = state }
            }
        },
        onCancelDownload = {
            // Wait for the cancelled download to finish its cleanup (delete its part file) before
            // returning to Idle, so a retry can start clean and the teardown can never touch its files.
            val job = downloadJob
            downloadJob = null
            scope.launch {
                job?.cancelAndJoin()
                downloadState = UpdateChecker.DownloadState.Idle
            }
        },
        onInstall = { apk -> installController.install(apk) },
        onDismiss = {
            downloadState = UpdateChecker.DownloadState.Idle
            installError = null
            onDismiss()
        },
    )
}
