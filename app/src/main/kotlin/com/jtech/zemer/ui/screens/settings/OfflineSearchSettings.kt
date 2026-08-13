package com.jtech.zemer.ui.screens.settings

import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
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
import com.jtech.zemer.ui.component.RequestInitialDpadFocus
import com.jtech.zemer.LocalPlayerAwareWindowInsets
import com.jtech.zemer.R
import com.jtech.zemer.constants.OfflineSubsetEnabledKey
import com.jtech.zemer.ui.component.AppBarTitle
import com.jtech.zemer.ui.component.BackNavigationIcon
import com.jtech.zemer.ui.component.PreferenceEntry
import com.jtech.zemer.ui.component.SettingsCardGroup
import com.jtech.zemer.ui.component.SettingsScreenTopSpacing
import com.jtech.zemer.ui.component.PreferenceGroupTitle
import com.jtech.zemer.ui.component.SwitchPreference
import com.jtech.zemer.ui.component.zemerTopAppBarColors
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

    // Read-only here: the ViewModel is the single writer (writing from both places double-committed
    // every toggle).
    val (enabled, _) = rememberPreference(OfflineSubsetEnabledKey, defaultValue = false)

    val backFocus = remember { FocusRequester() }
    val firstFocus = remember { FocusRequester() }

    RequestInitialDpadFocus(firstFocus)

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
        Spacer(Modifier.height(SettingsScreenTopSpacing))
        SettingsCardGroup(
            title = stringResource(R.string.offline_search),
            rows = buildList {
                add {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.offline_search_enable)) },
                        description = stringResource(R.string.offline_search_enable_desc),
                        icon = { Icon(painterResource(R.drawable.offline), null) },
                        checked = enabled,
                        onCheckedChange = viewModel::setEnabled,
                        modifier = Modifier.focusRequester(firstFocus),
                    )
                }
                if (enabled) {
                    add {
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
            },
        )
    }

    TopAppBar(
        title = { AppBarTitle(stringResource(R.string.offline_search)) },
        navigationIcon = {
            BackNavigationIcon(
                navController,
                modifier = Modifier
                    .focusRequester(backFocus)
                    .focusProperties { down = firstFocus }
            )
        },
        scrollBehavior = scrollBehavior,
        colors = zemerTopAppBarColors(),
    )
}
