package com.jtech.zemer.utils.updater

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/** How long the "installing…" heads-up shows before a silent install kills the process. */
private const val SILENT_INSTALL_HEADS_UP_MS = 1200L

/**
 * Drives an APK install from a Composable: gates the Standard installer behind
 * the "install unknown apps" permission (retrying once it is granted) and, for
 * silent installs (root) that complete in-process, schedules an app restart.
 *
 * Shizuku finishes asynchronously via [InstallReceiver], which schedules its own
 * restart on success — so a [InstallResult.RequiresUserAction] result here does
 * not (and must not) trigger a restart.
 *
 * Both the updater settings screen and the startup update dialog use this so the
 * install behaviour stays identical across entry points.
 */
@Stable
class ApkInstallController internal constructor() {
    internal var launch: (File) -> Unit = {}

    var isInstalling by mutableStateOf(false)
        internal set

    fun install(apkFile: File) = launch(apkFile)
}

@Composable
fun rememberApkInstallController(
    installerType: InstallerType,
    onResult: (InstallResult) -> Unit,
): ApkInstallController {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val controller = remember { ApkInstallController() }
    var pendingApk by remember { mutableStateOf<File?>(null) }

    fun runInstall(apkFile: File) {
        scope.launch {
            controller.isInstalling = true
            // Silent installs (root/Shizuku) kill this process the moment the package is
            // replaced, so the UI would vanish with no warning. Give the "installing…"
            // heads-up a beat to render first — root then restarts itself (chained am start);
            // Shizuku closes and the user reopens.
            if (installerType != InstallerType.NATIVE) {
                delay(SILENT_INSTALL_HEADS_UP_MS)
            }
            val result = AppInstaller.install(context, apkFile, installerType)
            controller.isInstalling = false
            onResult(result)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        val apkFile = pendingApk
        pendingApk = null
        if (apkFile != null && AppInstaller.canInstallPackages(context)) {
            runInstall(apkFile)
        }
    }

    // Reassigned every recomposition so it always sees the current installerType.
    controller.launch = { apkFile ->
        if (installerType == InstallerType.NATIVE && !AppInstaller.canInstallPackages(context)) {
            pendingApk = apkFile
            permissionLauncher.launch(AppInstaller.getInstallPermissionIntent(context))
        } else {
            runInstall(apkFile)
        }
    }

    // Shizuku finishes asynchronously in InstallReceiver (the install() call only commits the
    // session and returns RequiresUserAction). Forward that real outcome so a failure shows in
    // the UI instead of only as a toast.
    val currentOnResult by rememberUpdatedState(onResult)
    LaunchedEffect(Unit) {
        InstallReceiver.events.collect { result ->
            controller.isInstalling = false
            currentOnResult(result)
        }
    }

    return controller
}
