package com.jtech.zemer.ui.component

import android.text.format.Formatter
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jtech.zemer.R
import com.jtech.zemer.utils.UpdateChecker
import com.jtech.zemer.utils.updater.InstallerType
import com.jtech.zemer.utils.updater.UpdateDownloadFailure
import java.io.File

/** The primary action the update dialog offers for a given download / install state. */
enum class UpdateDialogAction { DOWNLOAD, RETRY, INSTALL, CANCEL, NONE }

/**
 * Pure: which primary action the dialog shows. Downloading offers only Cancel; installing offers
 * nothing (an install cannot be interrupted); a finished download offers Install (also the retry
 * for a failed install); a failed download offers Retry; otherwise Download.
 */
fun updateDialogPrimaryAction(state: UpdateChecker.DownloadState, isInstalling: Boolean): UpdateDialogAction = when {
    isInstalling -> UpdateDialogAction.NONE
    state is UpdateChecker.DownloadState.Downloading -> UpdateDialogAction.CANCEL
    state is UpdateChecker.DownloadState.Downloaded -> UpdateDialogAction.INSTALL
    state is UpdateChecker.DownloadState.Error -> UpdateDialogAction.RETRY
    else -> UpdateDialogAction.DOWNLOAD
}

/**
 * Pure: the value shown under the "Nightly build" label. `NightlyUpdates.versionLabel` reads
 * "nightly #1215 (0f8e9f8)"; with the channel already named by the label the prefix is redundant
 * and pushed the value onto two lines, so it is dropped here.
 */
fun updateVersionValue(latestVersion: String, isNightly: Boolean): String =
    if (isNightly) latestVersion.removePrefix("nightly").trimStart() else latestVersion

/**
 * Pure: the label over the new-version column. A nightly names its channel; the forced
 * return-to-stable path names the channel too (the version may equal the installed one, so
 * "New version 38 -> 38" would read as a no-op); a plain stable update says "New version".
 */
fun updateNewVersionLabelRes(isNightly: Boolean, isReturnToStable: Boolean): Int = when {
    isNightly -> R.string.update_nightly_build
    isReturnToStable -> R.string.update_stable_release
    else -> R.string.update_new_version
}

/**
 * The "update available -> download -> install" dialog, shared by the Updater settings screen
 * and the startup update prompt so both behave identically. Purely presentational: the caller
 * owns the state (download/install) and the actions.
 *
 * Shows the installed -> new version transition, the release notes rendered as Markdown (the
 * stable changelog is Markdown; a nightly's commit message is formatted into it), byte-level
 * download progress with a Cancel, and a classified, human-readable failure instead of the raw
 * exception text. Dismissal is disabled while downloading or installing.
 */
@Composable
fun UpdateDownloadDialog(
    currentVersion: String,
    latestVersion: String,
    isNightly: Boolean,
    isReturnToStable: Boolean = false,
    notes: String?,
    downloadState: UpdateChecker.DownloadState,
    isInstalling: Boolean,
    installError: String?,
    installerType: InstallerType,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onInstall: (File) -> Unit,
    onDismiss: () -> Unit,
) {
    val downloading = downloadState as? UpdateChecker.DownloadState.Downloading
    val downloadFailure = (downloadState as? UpdateChecker.DownloadState.Error)?.failure
    val downloadedApk = (downloadState as? UpdateChecker.DownloadState.Downloaded)?.apkFile
    val busy = downloading != null || isInstalling
    val action = updateDialogPrimaryAction(downloadState, isInstalling)

    DefaultDialog(
        onDismiss = { if (!busy) onDismiss() },
        horizontalAlignment = Alignment.Start,
        // Centered over the symmetric version card (DefaultDialog starts a title only when an
        // icon is present).
        title = {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.update_available), textAlign = TextAlign.Center)
            }
        },
        content = {
            VersionTransitionCard(currentVersion, latestVersion, isNightly, isReturnToStable)

            if (!notes.isNullOrBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.whats_new),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(4.dp))
                // The notes take whatever height the dialog has left (the body column is weighted
                // by DefaultDialog), capped so a long changelog scrolls; on a small screen the
                // panel shrinks instead of pushing the actions off-screen.
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .heightIn(max = 360.dp),
                ) {
                    Column(
                        Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                    ) {
                        MarkdownText(notes)
                    }
                }
            }

            if (downloading != null) {
                Spacer(Modifier.height(14.dp))
                val context = LocalContext.current
                val downloaded = Formatter.formatShortFileSize(context, downloading.downloadedBytes)
                LabeledWavyProgress(
                    label = stringResource(R.string.downloading_update),
                    progress = downloading.progress.takeIf { it >= 0f },
                    detail = if (downloading.totalBytes > 0) {
                        stringResource(
                            R.string.update_download_progress,
                            downloaded,
                            Formatter.formatShortFileSize(context, downloading.totalBytes),
                        )
                    } else {
                        stringResource(R.string.update_download_progress_unsized, downloaded)
                    },
                )
            }

            if (isInstalling) {
                Spacer(Modifier.height(14.dp))
                LabeledWavyProgress(
                    label = stringResource(R.string.installing),
                    progress = null,
                    detail = installerType.installingNote?.let { stringResource(it) },
                )
            }

            val errorText = installError?.let { stringResource(R.string.install_failed, it) }
                ?: downloadFailure?.let { stringResource(it.messageRes()) }
            if (errorText != null) {
                Spacer(Modifier.height(12.dp))
                InlineErrorPanel(errorText)
            }
        },
        buttons = when (action) {
            UpdateDialogAction.NONE -> null
            UpdateDialogAction.CANCEL -> {
                { TextButton(onClick = onCancelDownload) { Text(stringResource(R.string.cancel)) } }
            }
            else -> {
                {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.later)) }
                    Spacer(Modifier.width(8.dp))
                    when (action) {
                        UpdateDialogAction.INSTALL -> Button(onClick = { onInstall(downloadedApk!!) }) {
                            Text(stringResource(R.string.install))
                        }
                        UpdateDialogAction.RETRY -> Button(onClick = onDownload) {
                            Text(stringResource(R.string.retry))
                        }
                        else -> Button(onClick = onDownload) {
                            Text(stringResource(R.string.download_and_install))
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun VersionTransitionCard(currentVersion: String, latestVersion: String, isNightly: Boolean, isReturnToStable: Boolean) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            VersionColumn(
                label = stringResource(R.string.update_installed_version),
                value = currentVersion,
                emphasized = false,
                modifier = Modifier.weight(1f),
            )
            Icon(
                painter = painterResource(R.drawable.arrow_forward),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 10.dp),
            )
            VersionColumn(
                label = stringResource(updateNewVersionLabelRes(isNightly, isReturnToStable)),
                value = updateVersionValue(latestVersion, isNightly),
                emphasized = true,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun VersionColumn(label: String, value: String, emphasized: Boolean, modifier: Modifier = Modifier) {
    // Equal-width, centered columns around the arrow: a symmetric "from -> to", not a left-heavy row.
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            color = if (emphasized) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun UpdateDownloadFailure.messageRes(): Int = when (this) {
    UpdateDownloadFailure.TIMEOUT -> R.string.update_error_timeout
    UpdateDownloadFailure.NETWORK -> R.string.update_error_network
    UpdateDownloadFailure.STORAGE -> R.string.update_error_storage
    UpdateDownloadFailure.CORRUPT_ARTIFACT -> R.string.update_error_corrupt
    UpdateDownloadFailure.UNKNOWN -> R.string.update_error_unknown
}
