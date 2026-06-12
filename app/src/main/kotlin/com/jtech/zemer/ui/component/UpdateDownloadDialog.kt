package com.jtech.zemer.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jtech.zemer.R
import com.jtech.zemer.utils.UpdateChecker
import com.jtech.zemer.utils.updater.InstallerType
import java.io.File

/**
 * The "update available -> download -> install" dialog, shared by the Updater settings screen
 * and the startup update prompt so both behave identically. Purely presentational: the caller
 * owns the state (download/install) and the actions.
 *
 * Correctly reflects the install phase: while downloading or installing the action buttons and
 * dismissal are disabled (you can't re-trigger a download mid-install), and the per-method
 * heads-up + errors (download or install, including async Shizuku failures) are surfaced here
 * rather than only as a toast.
 */
@Composable
fun UpdateDownloadDialog(
    currentVersion: String,
    latestVersion: String,
    notes: String?,
    downloadState: UpdateChecker.DownloadState,
    isInstalling: Boolean,
    installError: String?,
    installerType: InstallerType,
    onDownload: () -> Unit,
    onInstall: (File) -> Unit,
    onDismiss: () -> Unit,
) {
    val isDownloading = downloadState is UpdateChecker.DownloadState.Downloading
    val downloadProgress = (downloadState as? UpdateChecker.DownloadState.Downloading)?.progress ?: 0f
    val downloadError = (downloadState as? UpdateChecker.DownloadState.Error)?.message
    val downloadedApk = (downloadState as? UpdateChecker.DownloadState.Downloaded)?.apkFile
    val busy = isDownloading || isInstalling

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.update_available)) },
        text = {
            Column {
                Text(stringResource(R.string.update_available_message, currentVersion, latestVersion))

                if (!notes.isNullOrBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.whats_new),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(text = notes, style = MaterialTheme.typography.bodySmall)
                }

                if (isDownloading) {
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.downloading_update), style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(8.dp))
                    if (downloadProgress >= 0) {
                        LinearProgressIndicator(progress = { downloadProgress }, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(4.dp))
                        Text("${(downloadProgress * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }

                if (isInstalling) {
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.installing), style = MaterialTheme.typography.labelMedium)
                    installerType.installingNote?.let { note ->
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                val errorText = installError?.let { stringResource(R.string.install_failed, it) } ?: downloadError
                if (errorText != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = errorText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            if (!busy) {
                if (downloadedApk != null) {
                    // Download finished — install (or retry a failed install) with the chosen method.
                    TextButton(onClick = { onInstall(downloadedApk) }) {
                        Text(stringResource(R.string.install))
                    }
                } else {
                    TextButton(onClick = onDownload) {
                        Text(stringResource(R.string.download_and_install))
                    }
                }
            }
        },
        dismissButton = {
            if (!busy) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.later)) }
            }
        },
    )
}
