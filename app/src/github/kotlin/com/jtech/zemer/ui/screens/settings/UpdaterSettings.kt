package com.jtech.zemer.ui.screens.settings

import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.jtech.zemer.LocalPlayerAwareWindowInsets
import com.jtech.zemer.R
import com.jtech.zemer.ui.component.DefaultDialog
import com.jtech.zemer.constants.CheckForUpdatesKey
import com.jtech.zemer.constants.InstallerTypeKey
import com.jtech.zemer.ui.component.IconButton
import com.jtech.zemer.ui.component.UpdateDownloadDialog
import com.jtech.zemer.ui.component.ListPreference
import com.jtech.zemer.ui.component.PreferenceEntry
import com.jtech.zemer.ui.component.SwitchPreference
import com.jtech.zemer.ui.utils.backToMain
import com.jtech.zemer.utils.UpdateChecker
import com.jtech.zemer.utils.rememberPreference
import timber.log.Timber
import com.jtech.zemer.utils.updater.AppInstaller
import com.jtech.zemer.utils.updater.InstallResult
import com.jtech.zemer.utils.updater.InstallerType
import com.jtech.zemer.utils.updater.rememberApkInstallController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdaterScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val (checkForUpdates, onCheckForUpdatesChange) = rememberPreference(CheckForUpdatesKey, false)
    val (installerTypeOrdinal, onInstallerTypeChange) = rememberPreference(InstallerTypeKey, InstallerType.NATIVE.ordinal)
    val installerType = InstallerType.fromOrdinal(installerTypeOrdinal)

    var isChecking by remember { mutableStateOf(false) }
    var showResultDialog by remember { mutableStateOf(false) }
    var updateResult by remember { mutableStateOf<UpdateChecker.UpdateResult?>(null) }
    var downloadState by remember { mutableStateOf<UpdateChecker.DownloadState>(UpdateChecker.DownloadState.Idle) }
    var installError by remember { mutableStateOf<String?>(null) }
    var installerSelectionError by remember { mutableStateOf<String?>(null) }

    val installController = rememberApkInstallController(installerType) { result ->
        when (result) {
            is InstallResult.Success -> downloadState = UpdateChecker.DownloadState.Idle
            is InstallResult.RequiresUserAction -> Unit // the system installer UI takes over
            is InstallResult.Error -> installError = result.message
        }
    }
    val isInstalling = installController.isInstalling

    fun installWithPermissionCheck(apkFile: File) {
        installError = null
        installController.install(apkFile)
    }

    val backFocus = remember { FocusRequester() }
    val firstFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        firstFocus.requestFocus()
    }

    // Persist the Shizuku choice only once its permission is actually granted
    DisposableEffect(Unit) {
        val listener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
            if (grantResult == PackageManager.PERMISSION_GRANTED) {
                installerSelectionError = null
                onInstallerTypeChange(InstallerType.SHIZUKU.ordinal)
            } else {
                installerSelectionError = context.getString(R.string.shizuku_permission_required)
            }
        }
        // Benign if Shizuku isn't installed/running (the binder is absent) — log, don't crash
        // or report (it's an expected state, not an error worth Crashlytics noise).
        runCatching { Shizuku.addRequestPermissionResultListener(listener) }
            .onFailure { Timber.d(it, "Shizuku permission listener not registered (Shizuku unavailable)") }
        onDispose {
            runCatching { Shizuku.removeRequestPermissionResultListener(listener) }
        }
    }

    fun selectInstaller(type: InstallerType) {
        installerSelectionError = null
        when (type) {
            InstallerType.NATIVE -> onInstallerTypeChange(type.ordinal)

            InstallerType.ROOT -> scope.launch {
                // Opens the root shell, which shows the Magisk/SuperSU grant prompt
                val hasRoot = withContext(Dispatchers.IO) { AppInstaller.hasRootAccess() }
                if (hasRoot) {
                    onInstallerTypeChange(type.ordinal)
                } else {
                    installerSelectionError = context.getString(R.string.installer_root_unavailable)
                }
            }

            // Shizuku's checks are binder IPC — run them off the main thread (the ROOT branch
            // above already does this for its shell check). Persist the selection BEFORE the
            // async permission grant so leaving the screen mid-grant can't lose it; the install
            // path re-validates the permission and reports clearly if it is still missing.
            InstallerType.SHIZUKU -> scope.launch {
                if (!withContext(Dispatchers.IO) { AppInstaller.hasShizukuOrSui(context) }) {
                    installerSelectionError = context.getString(R.string.installer_shizuku_not_installed)
                    return@launch
                }
                if (!withContext(Dispatchers.IO) { AppInstaller.isShizukuAlive() }) {
                    installerSelectionError = context.getString(R.string.shizuku_not_running)
                    return@launch
                }
                onInstallerTypeChange(type.ordinal)
                if (!withContext(Dispatchers.IO) { AppInstaller.hasShizukuPermission() }) {
                    runCatching { Shizuku.requestPermission(0) }.onFailure {
                        installerSelectionError = context.getString(R.string.shizuku_permission_required)
                    }
                }
            }
        }
    }

    // Auto-install when download completes
    LaunchedEffect(downloadState) {
        if (downloadState is UpdateChecker.DownloadState.Downloaded) {
            installWithPermissionCheck((downloadState as UpdateChecker.DownloadState.Downloaded).apkFile)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(4.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(8.dp)
        ) {
            SwitchPreference(
                title = { Text(stringResource(R.string.check_for_updates)) },
                icon = { Icon(painterResource(R.drawable.update), null) },
                checked = checkForUpdates,
                onCheckedChange = onCheckForUpdatesChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(firstFocus)
            )

            Spacer(Modifier.height(4.dp))

            ListPreference(
                title = { Text(stringResource(R.string.installer_method)) },
                icon = { Icon(painterResource(R.drawable.download), null) },
                selectedValue = installerType,
                values = InstallerType.entries.toList(),
                valueText = { stringResource(it.title) },
                onValueSelected = ::selectInstaller,
                modifier = Modifier.fillMaxWidth()
            )

            installerSelectionError?.let { error ->
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            Spacer(Modifier.height(4.dp))

            PreferenceEntry(
                title = { Text(stringResource(R.string.check_for_updates_now)) },
                icon = {
                    if (isChecking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(painterResource(R.drawable.sync), null)
                    }
                },
                onClick = {
                    if (!isChecking) {
                        isChecking = true
                        scope.launch {
                            updateResult = UpdateChecker.checkForUpdates()
                            isChecking = false
                            showResultDialog = true
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(Modifier.height(32.dp))
    }

    if (showResultDialog) {
        when (val result = updateResult) {
            is UpdateChecker.UpdateResult.UpdateAvailable -> {
                UpdateDownloadDialog(
                    currentVersion = result.currentVersion,
                    latestVersion = result.latestVersion,
                    notes = result.notes,
                    downloadState = downloadState,
                    isInstalling = isInstalling,
                    installError = installError,
                    installerType = installerType,
                    onDownload = {
                        downloadState = UpdateChecker.DownloadState.Downloading(0f)
                        installError = null
                        scope.launch {
                            UpdateChecker.downloadUpdate(context).collectLatest { state ->
                                downloadState = state
                            }
                        }
                    },
                    onInstall = { apk -> installWithPermissionCheck(apk) },
                    onDismiss = {
                        showResultDialog = false
                        downloadState = UpdateChecker.DownloadState.Idle
                        installError = null
                    },
                )
            }
            is UpdateChecker.UpdateResult.UpToDate -> {
                DefaultDialog(
                    onDismiss = { showResultDialog = false },
                    horizontalAlignment = Alignment.Start,
                    title = { Text(stringResource(R.string.up_to_date)) },
                    content = {
                        Text(stringResource(R.string.up_to_date_message, result.currentVersion))
                    },
                    buttons = {
                        TextButton(onClick = { showResultDialog = false }) {
                            Text(stringResource(android.R.string.ok))
                        }
                    }
                )
            }
            is UpdateChecker.UpdateResult.Error -> {
                DefaultDialog(
                    onDismiss = { showResultDialog = false },
                    horizontalAlignment = Alignment.Start,
                    title = { Text(stringResource(R.string.error)) },
                    content = {
                        Text(result.message)
                    },
                    buttons = {
                        TextButton(onClick = { showResultDialog = false }) {
                            Text(stringResource(android.R.string.ok))
                        }
                    }
                )
            }
            null -> { }
        }
    }

    TopAppBar(
        title = { Text(stringResource(R.string.updater)) },
        navigationIcon = {
            IconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain,
            ) {
                Icon(
                    painter = painterResource(R.drawable.arrow_back),
                    contentDescription = null,
                    modifier = Modifier
                        .focusRequester(backFocus)
                        .focusProperties { down = firstFocus }
                )
            }
        }
    )
}
