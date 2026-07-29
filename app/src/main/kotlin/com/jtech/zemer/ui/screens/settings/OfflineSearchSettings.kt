package com.jtech.zemer.ui.screens.settings

import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.jtech.zemer.LocalPlayerAwareWindowInsets
import com.jtech.zemer.R
import com.jtech.zemer.constants.OfflineSubsetEnabledKey
import com.jtech.zemer.constants.OfflineSubsetWifiOnlyKey
import com.jtech.zemer.ui.component.IconButton
import com.jtech.zemer.ui.component.PreferenceEntry
import com.jtech.zemer.ui.component.PreferenceGroupTitle
import com.jtech.zemer.ui.component.SwitchPreference
import com.jtech.zemer.ui.utils.backToMain
import com.jtech.zemer.utils.rememberPreference
import com.jtech.zemer.viewmodels.OfflineSearchSettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflineSearchSettings(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: OfflineSearchSettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val status by viewModel.status.collectAsStateWithLifecycle()

    // Read-only here: the ViewModel is the single writer of both keys (writing from both places
    // double-committed every toggle).
    val (enabled, _) = rememberPreference(OfflineSubsetEnabledKey, defaultValue = false)
    val (wifiOnly, _) = rememberPreference(OfflineSubsetWifiOnlyKey, defaultValue = true)

    val backFocus = remember { FocusRequester() }
    val firstFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        firstFocus.requestFocus()
    }

    val statusDescription = remember(status) {
        buildString {
            append(context.getString(R.string.offline_search_size, Formatter.formatShortFileSize(context, status.sizeOnDisk)))
            append(" · ")
            val lastUpdated = if (status.lastSyncedAt <= 0L) {
                context.getString(R.string.offline_search_never)
            } else {
                DateUtils.getRelativeTimeSpanString(
                    status.lastSyncedAt,
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS,
                ).toString()
            }
            append(context.getString(R.string.offline_search_last_updated, lastUpdated))
            if (status.waitingForWifi) {
                append("\n")
                append(context.getString(R.string.offline_search_waiting_wifi))
            }
            status.lastError?.let {
                append("\n")
                append(context.getString(R.string.offline_search_last_error, it))
            }
        }
    }

    Column(
        Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
            .verticalScroll(rememberScrollState()),
    ) {
        PreferenceGroupTitle(
            title = stringResource(R.string.offline_search),
        )

        SwitchPreference(
            title = { Text(stringResource(R.string.offline_search_enable)) },
            description = stringResource(R.string.offline_search_enable_desc),
            icon = { Icon(painterResource(R.drawable.offline), null) },
            checked = enabled,
            onCheckedChange = viewModel::setEnabled,
            modifier = Modifier.focusRequester(firstFocus),
        )

        if (enabled) {
            SwitchPreference(
                title = { Text(stringResource(R.string.offline_search_wifi_only)) },
                description = stringResource(R.string.offline_search_wifi_only_desc),
                icon = { Icon(painterResource(R.drawable.wifi_proxy), null) },
                checked = wifiOnly,
                onCheckedChange = viewModel::setWifiOnly,
            )

            PreferenceEntry(
                title = {
                    Text(
                        if (status.running) {
                            stringResource(R.string.offline_search_updating)
                        } else {
                            stringResource(R.string.offline_search_download_now)
                        }
                    )
                },
                description = statusDescription,
                icon = { Icon(painterResource(R.drawable.download), null) },
                onClick = { viewModel.downloadNow() },
                isEnabled = !status.running,
            )
        }
    }

    TopAppBar(
        title = { Text(stringResource(R.string.offline_search)) },
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
        },
        scrollBehavior = scrollBehavior,
    )
}
