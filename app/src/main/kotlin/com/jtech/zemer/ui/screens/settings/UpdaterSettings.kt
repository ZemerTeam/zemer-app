package com.jtech.zemer.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.jtech.zemer.LocalPlayerAwareWindowInsets
import com.jtech.zemer.R
import com.jtech.zemer.constants.CheckForUpdatesKey
import com.jtech.zemer.constants.UpdateNotificationsEnabledKey
import com.jtech.zemer.ui.component.IconButton
import com.jtech.zemer.ui.component.SwitchPreference
import com.jtech.zemer.ui.utils.backToMain
import com.jtech.zemer.utils.rememberPreference

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdaterScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val (checkForUpdates, onCheckForUpdatesChange) = rememberPreference(CheckForUpdatesKey, false)
    val (updateNotifications, onUpdateNotificationsChange) = rememberPreference(UpdateNotificationsEnabledKey, false)

    val backFocus = remember { FocusRequester() }
    val firstFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        firstFocus.requestFocus()
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

            if (checkForUpdates) {
                Spacer(Modifier.height(4.dp))

                SwitchPreference(
                    title = { Text(stringResource(R.string.update_notifications)) },
                    icon = { Icon(painterResource(R.drawable.notification), null) },
                    checked = updateNotifications,
                    onCheckedChange = onUpdateNotificationsChange,
                    isEnabled = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(Modifier.height(32.dp))
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
