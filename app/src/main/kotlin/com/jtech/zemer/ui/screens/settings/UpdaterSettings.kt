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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import com.jtech.zemer.constants.CheckForUpdatesKey
import com.jtech.zemer.constants.InstallerTypeKey
import com.jtech.zemer.ui.component.IconButton
import com.jtech.zemer.ui.component.ListPreference
import com.jtech.zemer.ui.component.PreferenceEntry
import com.jtech.zemer.ui.component.SwitchPreference
import com.jtech.zemer.ui.utils.backToMain
import com.jtech.zemer.utils.UpdateChecker
import com.jtech.zemer.utils.rememberPreference
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
        runCatching { Shizuku.addRequestPermissionResultListener(listener) }
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

            InstallerType.SHIZUKU -> when {
                !AppInstaller.hasShizukuOrSui(context) ->
                    installerSelectionError = context.getString(R.string.installer_shizuku_not_installed)

                !AppInstaller.isShizukuAlive() ->
                    installerSelectionError = context.getString(R.string.shizuku_not_running)

                AppInstaller.hasShizukuPermission() -> onInstallerTypeChange(type.ordinal)

                else -> runCatching { Shizuku.requestPermission(0) }.onFailure {
                    installerSelectionError = context.getString(R.string.shizuku_permission_required)
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
                val isDownloading = downloadState is UpdateChecker.DownloadState.Downloading
                val downloadProgress = (downloadState as? UpdateChecker.DownloadState.Downloading)?.progress ?: 0f
                val downloadError = (downloadState as? UpdateChecker.DownloadState.Error)?.message

                AlertDialog(
                    onDismissRequest = {
                        if (!isDownloading && !isInstalling) {
                            showResultDialog = false
                            downloadState = UpdateChecker.DownloadState.Idle
                            installError = null
                        }
                    },
                    title = { Text(stringResource(R.string.update_available)) },
                    text = {
                        Column {
                            Text(stringResource(R.string.update_available_message, result.currentVersion, result.latestVersion))
                            if (!result.notes.isNullOrBlank()) {
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    text = stringResource(R.string.whats_new),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = result.notes,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            if (isDownloading) {
                                Spacer(Modifier.height(16.dp))
                                Text(
                                    text = stringResource(R.string.downloading_update),
                                    style = MaterialTheme.typography.labelMedium
                                )
                                Spacer(Modifier.height(8.dp))
                                if (downloadProgress >= 0) {
                                    LinearProgressIndicator(
                                        progress = { downloadProgress },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = "${(downloadProgress * 100).toInt()}%",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                } else {
                                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                }
                            }
                            if (isInstalling) {
                                Spacer(Modifier.height(16.dp))
                                Text(
                                    text = stringResource(R.string.installing),
                                    style = MaterialTheme.typography.labelMedium
                                )
                                // Heads-up: a silent install closes/restarts the app
                                val installNote = when (installerType) {
                                    InstallerType.ROOT -> R.string.installing_note_restart
                                    InstallerType.SHIZUKU -> R.string.installing_note_reopen
                                    InstallerType.NATIVE -> null
                                }
                                installNote?.let {
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = stringResource(it),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            val errorText = installError?.let { stringResource(R.string.install_failed, it) } ?: downloadError
                            if (errorText != null) {
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    text = errorText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    },
                    confirmButton = {
                        if (!isDownloading && !isInstalling) {
                            val downloadedApk = (downloadState as? UpdateChecker.DownloadState.Downloaded)?.apkFile
                            if (downloadedApk != null) {
                                // Download finished but install failed or needs retry
                                TextButton(onClick = { installWithPermissionCheck(downloadedApk) }) {
                                    Text(stringResource(R.string.install))
                                }
                            } else {
                                TextButton(onClick = {
                                    downloadState = UpdateChecker.DownloadState.Downloading(0f)
                                    installError = null
                                    scope.launch {
                                        UpdateChecker.downloadUpdate(context).collectLatest { state ->
                                            downloadState = state
                                        }
                                    }
                                }) {
                                    Text(stringResource(R.string.download_and_install))
                                }
                            }
                        }
                    },
                    dismissButton = {
                        if (!isDownloading && !isInstalling) {
                            TextButton(onClick = {
                                showResultDialog = false
                                downloadState = UpdateChecker.DownloadState.Idle
                                installError = null
                            }) {
                                Text(stringResource(R.string.later))
                            }
                        }
                    }
                )
            }
            is UpdateChecker.UpdateResult.UpToDate -> {
                AlertDialog(
                    onDismissRequest = { showResultDialog = false },
                    title = { Text(stringResource(R.string.up_to_date)) },
                    text = {
                        Text(stringResource(R.string.up_to_date_message, result.currentVersion))
                    },
                    confirmButton = {
                        TextButton(onClick = { showResultDialog = false }) {
                            Text(stringResource(android.R.string.ok))
                        }
                    }
                )
            }
            is UpdateChecker.UpdateResult.Error -> {
                AlertDialog(
                    onDismissRequest = { showResultDialog = false },
                    title = { Text(stringResource(R.string.error)) },
                    text = {
                        Text(result.message)
                    },
                    confirmButton = {
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
