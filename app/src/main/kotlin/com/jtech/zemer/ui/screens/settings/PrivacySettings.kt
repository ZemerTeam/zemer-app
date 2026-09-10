package com.jtech.zemer.ui.screens.settings

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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import com.jtech.zemer.ui.component.RequestInitialDpadFocus
import com.jtech.zemer.LocalDatabase
import com.jtech.zemer.LocalPlayerAwareWindowInsets
import com.jtech.zemer.R
import com.jtech.zemer.constants.PauseListenHistoryKey
import com.jtech.zemer.constants.PauseSearchHistoryKey
import com.jtech.zemer.ui.component.AppBarTitle
import com.jtech.zemer.ui.component.BackNavigationIcon
import com.jtech.zemer.ui.component.PreferenceEntry
import com.jtech.zemer.ui.component.SettingsCardGroup
import com.jtech.zemer.ui.component.SettingsScreenTopSpacing
import com.jtech.zemer.ui.component.SwitchPreference
import com.jtech.zemer.ui.component.zemerTopAppBarColors
import com.jtech.zemer.utils.rememberPreference
import com.jtech.zemer.ui.component.ConfirmDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacySettings(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val database = LocalDatabase.current
    val (pauseListenHistory, onPauseListenHistoryChange) = rememberPreference(
        key = PauseListenHistoryKey,
        defaultValue = false
    )
    val (pauseSearchHistory, onPauseSearchHistoryChange) = rememberPreference(
        key = PauseSearchHistoryKey,
        defaultValue = false
    )
    var showClearListenHistoryDialog by remember {
        mutableStateOf(false)
    }

    if (showClearListenHistoryDialog) {
        ConfirmDialog(
            text = stringResource(R.string.clear_listen_history_confirm),
            onDismiss = { showClearListenHistoryDialog = false },
            onConfirm = {
                showClearListenHistoryDialog = false
                database.query {
                    clearListenHistory()
                }
            },
        )
    }

    var showClearSearchHistoryDialog by remember {
        mutableStateOf(false)
    }

    val backFocus = remember { FocusRequester() }
    val firstFocus = remember { FocusRequester() }

    RequestInitialDpadFocus(firstFocus)

    if (showClearSearchHistoryDialog) {
        ConfirmDialog(
            text = stringResource(R.string.clear_search_history_confirm),
            onDismiss = { showClearSearchHistoryDialog = false },
            onConfirm = {
                showClearSearchHistoryDialog = false
                database.query {
                    clearSearchHistory()
                }
            },
        )
    }

    Column(
        Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(SettingsScreenTopSpacing))
        SettingsCardGroup(
            title = stringResource(R.string.listen_history),
            rows = listOf(
                {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.pause_listen_history)) },
                        icon = { Icon(painterResource(R.drawable.history), null) },
                        checked = pauseListenHistory,
                        onCheckedChange = onPauseListenHistoryChange,
                        modifier = Modifier.focusRequester(firstFocus),
                    )
                },
                {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.clear_listen_history)) },
                        icon = { Icon(painterResource(R.drawable.delete_history), null) },
                        onClick = { showClearListenHistoryDialog = true },
                    )
                },
            ),
        )

        SettingsCardGroup(
            title = stringResource(R.string.search_history),
            rows = listOf(
                {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.pause_search_history)) },
                        icon = { Icon(painterResource(R.drawable.search_off), null) },
                        checked = pauseSearchHistory,
                        onCheckedChange = onPauseSearchHistoryChange,
                    )
                },
                {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.clear_search_history)) },
                        icon = { Icon(painterResource(R.drawable.clear_all), null) },
                        onClick = { showClearSearchHistoryDialog = true },
                    )
                },
            ),
        )
    }

    TopAppBar(
        title = { AppBarTitle(stringResource(R.string.privacy)) },
        navigationIcon = {
            BackNavigationIcon(
                navController,
                modifier = Modifier
                    .focusRequester(backFocus)
                    .focusProperties { down = firstFocus }
            )
        },
        colors = zemerTopAppBarColors(),
    )
}
