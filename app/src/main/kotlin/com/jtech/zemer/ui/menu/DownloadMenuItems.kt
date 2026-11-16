package com.jtech.zemer.ui.menu

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jtech.zemer.R
import com.jtech.zemer.playback.DownloadRowKind
import com.jtech.zemer.ui.component.Material3MenuItemData
import com.jtech.zemer.utils.PermissionHelper

/**
 * THE download row for every Material3 item/collection menu. One builder means identical icon, label,
 * progress display and — crucially — identical behaviour: tapping a download row NEVER dismisses the
 * menu, so the row animates straight from "Download" → live progress ring + "%" → "Remove download"
 * while the sheet stays open. Pass the [DownloadRowKind] decided by
 * [com.jtech.zemer.playback.DownloadMenuLogic]; [progress] is the 0..1 fraction for the DOWNLOADING
 * ring. Returns null for [DownloadRowKind.HIDDEN] so callers can `?.let(::add)`. Download and Retry run
 * through [storageGate] ([rememberStoragePermissionGate]).
 */
fun downloadMenuItem(
    kind: DownloadRowKind,
    storageGate: (() -> Unit) -> Unit,
    progress: Float = 0f,
    error: String? = null,
    onDownload: () -> Unit = {},
    onCancel: () -> Unit = {},
    onRetry: () -> Unit = {},
    onRemove: () -> Unit = {},
): Material3MenuItemData? {
    val pct = progress.coerceIn(0f, 1f)
    return when (kind) {
        DownloadRowKind.HIDDEN -> null
        DownloadRowKind.REMOVE -> Material3MenuItemData(
            icon = { Icon(painterResource(R.drawable.offline), null, Modifier.size(24.dp)) },
            title = { Text(stringResource(R.string.remove_download), color = MaterialTheme.colorScheme.error) },
            onClick = onRemove,
        )
        DownloadRowKind.DOWNLOADING -> Material3MenuItemData(
            icon = {
                CircularProgressIndicator(
                    progress = { pct },
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                )
            },
            title = { Text(stringResource(R.string.downloading_to_device)) },
            description = { Text("${(pct * 100).toInt()}%") },
            onClick = onCancel,
        )
        DownloadRowKind.FAILED -> Material3MenuItemData(
            icon = { Icon(painterResource(R.drawable.info), null, Modifier.size(24.dp)) },
            title = { Text(stringResource(R.string.download_failed), color = MaterialTheme.colorScheme.error) },
            description = { Text(error ?: stringResource(R.string.retry_download)) },
            onClick = { storageGate(onRetry) },
        )
        DownloadRowKind.DOWNLOAD_VIDEO -> Material3MenuItemData(
            icon = { Icon(painterResource(R.drawable.download), null, Modifier.size(24.dp)) },
            title = { Text(stringResource(R.string.download_video_to_device)) },
            onClick = { storageGate(onDownload) },
        )
        DownloadRowKind.DOWNLOAD -> Material3MenuItemData(
            icon = { Icon(painterResource(R.drawable.download), null, Modifier.size(24.dp)) },
            title = { Text(stringResource(R.string.download_to_device)) },
            onClick = { storageGate(onDownload) },
        )
    }
}

/**
 * Runs a download action, first asking for the storage permissions ([PermissionHelper]) when they are missing.
 * The action runs whatever the answer: the download manager decides whether the save can work without them
 * (before Android 10 it cannot, and the row then says so), so a failed row asks again on Retry.
 */
@Composable
fun rememberStoragePermissionGate(): (() -> Unit) -> Unit {
    val context = LocalContext.current
    val pending = remember { arrayOfNulls<() -> Unit>(1) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        pending[0]?.invoke()
        pending[0] = null
    }
    return remember(context, launcher) {
        { action ->
            if (PermissionHelper.hasMediaStoreWritePermission(context)) {
                action()
            } else {
                pending[0] = action
                launcher.launch(PermissionHelper.getRequiredWritePermissions())
            }
        }
    }
}
