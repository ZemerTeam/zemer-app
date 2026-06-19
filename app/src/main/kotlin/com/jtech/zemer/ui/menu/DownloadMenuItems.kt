package com.jtech.zemer.ui.menu

import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jtech.zemer.R
import com.jtech.zemer.playback.DownloadRowKind
import com.jtech.zemer.ui.component.Material3MenuItemData

/**
 * THE download row for every Material3 item/collection menu. One builder means identical icon, label,
 * progress display and — crucially — identical behaviour: tapping a download row NEVER dismisses the
 * menu, so the row animates straight from "Download" → live progress ring + "%" → "Remove download"
 * while the sheet stays open. (This is the fix for the old inconsistency where some menus dismissed on
 * download and some didn't.) Pass the [DownloadRowKind] decided by
 * [com.jtech.zemer.playback.DownloadMenuLogic]; [progress] is the 0..1 fraction for the DOWNLOADING
 * ring. Returns null for [DownloadRowKind.HIDDEN] so callers can `?.let(::add)`.
 */
fun downloadMenuItem(
    kind: DownloadRowKind,
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
            onClick = onRetry,
        )
        DownloadRowKind.DOWNLOAD_VIDEO -> Material3MenuItemData(
            icon = { Icon(painterResource(R.drawable.download), null, Modifier.size(24.dp)) },
            title = { Text(stringResource(R.string.download_video_to_device)) },
            onClick = onDownload,
        )
        DownloadRowKind.DOWNLOAD -> Material3MenuItemData(
            icon = { Icon(painterResource(R.drawable.download), null, Modifier.size(24.dp)) },
            title = { Text(stringResource(R.string.download_to_device)) },
            onClick = onDownload,
        )
    }
}
