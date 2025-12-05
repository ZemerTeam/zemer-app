package com.jtech.zemer.ui.screens.settings

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil3.annotation.ExperimentalCoilApi
import coil3.imageLoader
import com.jtech.zemer.LocalPlayerAwareWindowInsets
import com.jtech.zemer.LocalPlayerConnection
import com.jtech.zemer.R
import com.jtech.zemer.constants.CustomDownloadPathKey
import com.jtech.zemer.constants.MaxImageCacheSizeKey
import com.jtech.zemer.constants.MaxSongCacheSizeKey
import com.jtech.zemer.extensions.tryOrNull
import com.jtech.zemer.ui.component.ActionPromptDialog
import com.jtech.zemer.ui.component.IconButton
import com.jtech.zemer.ui.component.ListPreference
import com.jtech.zemer.ui.component.PreferenceEntry
import com.jtech.zemer.ui.component.PreferenceGroupTitle
import com.jtech.zemer.ui.utils.backToMain
import com.jtech.zemer.ui.utils.formatFileSize
import com.jtech.zemer.utils.EnvironmentPaths.DEFAULT_RELATIVE_DOWNLOAD_PATH
import com.jtech.zemer.utils.EnvironmentPaths.toUserFacingPath
import com.jtech.zemer.utils.rememberPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@SuppressLint("AutoboxingStateCreation")
@OptIn(ExperimentalCoilApi::class, ExperimentalMaterial3Api::class)
@Composable
fun StorageSettings(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val context = LocalContext.current
    val imageDiskCache = context.imageLoader.diskCache ?: return
    val playerCache = LocalPlayerConnection.current?.service?.playerCache ?: return
    val downloadCache = LocalPlayerConnection.current?.service?.downloadCache ?: return

    val coroutineScope = rememberCoroutineScope()
    val (maxImageCacheSize, onMaxImageCacheSizeChange) = rememberPreference(
        key = MaxImageCacheSizeKey,
        defaultValue = 512
    )
    val (maxSongCacheSize, onMaxSongCacheSizeChange) = rememberPreference(
        key = MaxSongCacheSizeKey,
        defaultValue = 1024
    )
    val (customDownloadPath, onCustomDownloadPathChange) = rememberPreference(
        key = CustomDownloadPathKey,
        defaultValue = ""
    )
    val resolvedDownloadPath = remember(customDownloadPath) {
        customDownloadPath.toUserFacingPath().ifBlank { DEFAULT_RELATIVE_DOWNLOAD_PATH }
    }
    val downloadPickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            uri?.let {
                val flags =
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(it, flags)
                onCustomDownloadPathChange(it.toString())
            }
        }
    val onResetDownloadPath = {
        customDownloadPath.takeIf { it.isNotBlank() }?.let { stored ->
            runCatching {
                context.contentResolver.releasePersistableUriPermission(
                    Uri.parse(stored),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
        }
        onCustomDownloadPathChange("")
    }
    var clearCacheDialog by remember { mutableStateOf(false) }
    var clearDownloads by remember { mutableStateOf(false) }
    var clearImageCacheDialog by remember { mutableStateOf(false) }

    var imageCacheSize by remember {
        mutableLongStateOf(imageDiskCache.size)
    }
    var playerCacheSize by remember {
        mutableLongStateOf(tryOrNull { playerCache.cacheSpace } ?: 0)
    }
    var downloadCacheSize by remember {
        mutableStateOf(tryOrNull { downloadCache.cacheSpace } ?: 0)
    }
    val imageCacheProgress by animateFloatAsState(
        targetValue = (imageCacheSize.toFloat() / imageDiskCache.maxSize).coerceIn(0f, 1f),
        label = "imageCacheProgress",
    )
    val playerCacheProgress by animateFloatAsState(
        targetValue = (playerCacheSize.toFloat() / (maxSongCacheSize * 1024 * 1024L)).coerceIn(
            0f,
            1f
        ),
        label = "playerCacheProgress",
    )

    LaunchedEffect(maxImageCacheSize) {
        if (maxImageCacheSize == 0) {
            coroutineScope.launch(Dispatchers.IO) {
                imageDiskCache.clear()
            }
        }
    }
    LaunchedEffect(maxSongCacheSize) {
        if (maxSongCacheSize == 0) {
            coroutineScope.launch(Dispatchers.IO) {
                playerCache.keys.forEach { key ->
                    playerCache.removeResource(key)
                }
            }
        }
    }

    LaunchedEffect(imageDiskCache) {
        while (isActive) {
            delay(500)
            imageCacheSize = imageDiskCache.size
        }
    }
    LaunchedEffect(playerCache) {
        while (isActive) {
            delay(500)
            playerCacheSize = tryOrNull { playerCache.cacheSpace } ?: 0
        }
    }
    LaunchedEffect(downloadCache) {
        while (isActive) {
            delay(500)
            downloadCacheSize = tryOrNull { downloadCache.cacheSpace } ?: 0
        }
    }

    val backFocus = remember { FocusRequester() }
    val firstFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        firstFocus.requestFocus()
    }

    Column(
        Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
            .verticalScroll(rememberScrollState()),
    ) {
        PreferenceGroupTitle(
            title = stringResource(R.string.downloaded_songs),
        )

        Text(
            text = stringResource(R.string.size_used, formatFileSize(downloadCacheSize)),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )

        PreferenceEntry(
            title = { Text(stringResource(R.string.custom_download_path)) },
            description = stringResource(
                R.string.custom_download_path_summary,
                resolvedDownloadPath
            ),
            onClick = { downloadPickerLauncher.launch(null) }
        )

        PreferenceEntry(
            title = { Text(stringResource(R.string.video_download_path)) },
            description = stringResource(R.string.video_download_path_summary),
            onClick = { /* Video path is fixed to Movies/Zemer */ }
        )

        if (customDownloadPath.isNotBlank()) {
            PreferenceEntry(
                title = { Text(stringResource(R.string.reset_download_path)) },
                description = stringResource(R.string.reset_download_path_summary),
                onClick = onResetDownloadPath
            )
        }

        PreferenceEntry(
            title = { Text(stringResource(R.string.clear_all_downloads)) },
            onClick = {clearDownloads = true
            },
            modifier = Modifier.focusRequester(firstFocus),
        )

        if (clearDownloads) {
            ActionPromptDialog(
                title = stringResource(R.string.clear_all_downloads),
                onDismiss = { clearDownloads = false },
                onConfirm = {
                    coroutineScope.launch(Dispatchers.IO) {
                        downloadCache.keys.forEach { key ->
                            downloadCache.removeResource(key)
                        }
                    }
                    clearDownloads = false
                },
                onCancel = { clearDownloads = false },
                content = {
                    Text(text = stringResource(R.string.clear_downloads_dialog))
                }
            )
        }

        PreferenceGroupTitle(
            title = stringResource(R.string.song_cache),
        )

        if (maxSongCacheSize != 0) {
            if (maxSongCacheSize == -1) {
                Text(
                    text = stringResource(R.string.size_used, formatFileSize(playerCacheSize)),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            } else {
                // Use M3 LinearProgressIndicator with theme colors
                LinearProgressIndicator(
                    progress = { playerCacheProgress },
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    color = MaterialTheme.colorScheme.primary, // Explicitly use theme color
                    trackColor = MaterialTheme.colorScheme.surfaceVariant, // Use appropriate track color
                    strokeCap = StrokeCap.Round // M3 default style
                )

                Text(
                    text =
                    stringResource(
                        R.string.size_used,
                        "${formatFileSize(playerCacheSize)} / ${
                            formatFileSize(
                                maxSongCacheSize * 1024 * 1024L,
                            )
                        }",
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
        }

        ListPreference(
            title = { Text(stringResource(R.string.max_cache_size)) },
            selectedValue = maxSongCacheSize,
            values = listOf(0, 128, 256, 512, 1024, 2048, 4096, 8192, -1),
            valueText = {
                when (it) {
                    0 -> stringResource(R.string.disable)
                    -1 -> stringResource(R.string.unlimited)
                    else -> formatFileSize(it * 1024 * 1024L)
                }
            },
            onValueSelected = onMaxSongCacheSizeChange,
        )

        PreferenceEntry(
            title = { Text(stringResource(R.string.clear_song_cache)) },
            onClick = { clearCacheDialog = true
            },
        )

        if (clearCacheDialog) {
            ActionPromptDialog(
                title = stringResource(R.string.clear_song_cache),
                onDismiss = { clearCacheDialog = false },
                onConfirm = {
                    coroutineScope.launch(Dispatchers.IO) {
                        playerCache.keys.forEach { key ->
                            playerCache.removeResource(key)
                        }
                    }
                    clearCacheDialog = false
                },
                onCancel = { clearCacheDialog = false },
                content = {
                    Text(text = stringResource(R.string.clear_song_cache_dialog))
                }
            )
        }

        PreferenceGroupTitle(
            title = stringResource(R.string.image_cache),
        )

        if (maxImageCacheSize > 0) {
            // Use M3 LinearProgressIndicator with theme colors
            LinearProgressIndicator(
                progress = { imageCacheProgress },
                modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                color = MaterialTheme.colorScheme.primary, // Explicitly use theme color
                trackColor = MaterialTheme.colorScheme.surfaceVariant, // Use appropriate track color
                strokeCap = StrokeCap.Round // M3 default style
            )

            Text(
                text = stringResource(
                    R.string.size_used,
                    "${formatFileSize(imageCacheSize)} / ${formatFileSize(imageDiskCache.maxSize)}"
                ),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }

        ListPreference(
            title = { Text(stringResource(R.string.max_cache_size)) },
            selectedValue = maxImageCacheSize,
            values = listOf(0, 128, 256, 512, 1024, 2048, 4096, 8192),
            valueText = {
                when (it) {
                    0 -> stringResource(R.string.disable)
                    else -> formatFileSize(it * 1024 * 1024L)
                }
            },
            onValueSelected = onMaxImageCacheSizeChange,
        )

        PreferenceEntry(
            title = { Text(stringResource(R.string.clear_image_cache)) },
            onClick = { clearImageCacheDialog = true
            },
        )

        if (clearImageCacheDialog) {
            ActionPromptDialog(
                title = stringResource(R.string.clear_image_cache),
                onDismiss = { clearImageCacheDialog = false },
                onConfirm = {
                    coroutineScope.launch(Dispatchers.IO) {
                        imageDiskCache.clear()
                    }
                    clearImageCacheDialog = false
                },
                onCancel = { clearImageCacheDialog = false },
                content = {
                    Text(text = stringResource(R.string.clear_image_cache_dialog))
                }
            )
        }
    }

    TopAppBar(
        title = { Text(stringResource(R.string.storage)) },
        navigationIcon = {
            IconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain,
            ) {
                Icon(
                    painterResource(R.drawable.arrow_back),
                    contentDescription = null,
                    modifier = Modifier
                        .focusRequester(backFocus)
                        .focusProperties { down = firstFocus }
                )
            }
        }
    )
}
