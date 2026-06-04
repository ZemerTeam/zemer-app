package com.jtech.zemer.ui.screens.settings

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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import com.jtech.zemer.LocalPlayerAwareWindowInsets
import com.jtech.zemer.R
import com.jtech.zemer.constants.StreamSourceAndroidCreatorKey
import com.jtech.zemer.constants.StreamSourceAndroidVRKey
import com.jtech.zemer.constants.StreamSourceIOSKey
import com.jtech.zemer.constants.StreamSourceIPadOSKey
import com.jtech.zemer.constants.StreamSourceTVHTML5Key
import com.jtech.zemer.constants.StreamSourceWebCreatorKey
import com.jtech.zemer.constants.StreamSourceWebRemixKey
import com.jtech.zemer.ui.component.IconButton
import com.jtech.zemer.ui.component.PreferenceGroupTitle
import com.jtech.zemer.ui.component.SwitchPreference
import com.jtech.zemer.ui.utils.backToMain
import com.jtech.zemer.utils.rememberPreference

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreamSourceSettings(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val (webRemixEnabled, onWebRemixChange)     = rememberPreference(StreamSourceWebRemixKey,   defaultValue = true)
    val (tvhtml5Enabled, onTVHTML5Change)       = rememberPreference(StreamSourceTVHTML5Key,    defaultValue = true)
    val (androidVREnabled, onAndroidVRChange)   = rememberPreference(StreamSourceAndroidVRKey,  defaultValue = true)
    val (iosEnabled, onIOSChange)               = rememberPreference(StreamSourceIOSKey,        defaultValue = true)
    val (ipadosEnabled, onIPadOSChange)         = rememberPreference(StreamSourceIPadOSKey,     defaultValue = true)
    val (webCreatorEnabled, onWebCreatorChange) = rememberPreference(StreamSourceWebCreatorKey, defaultValue = true)
    val (androidCreatorEnabled, onAndroidCreatorChange) = rememberPreference(StreamSourceAndroidCreatorKey, defaultValue = true)

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
            title = stringResource(R.string.stream_source_web_clients)
        )

        SwitchPreference(
            title = { Text(stringResource(R.string.stream_source_web_remix)) },
            description = stringResource(R.string.stream_source_web_remix_desc),
            icon = { Icon(painterResource(R.drawable.music_note), null) },
            checked = webRemixEnabled,
            onCheckedChange = onWebRemixChange,
            modifier = Modifier.focusRequester(firstFocus),
        )

        SwitchPreference(
            title = { Text(stringResource(R.string.stream_source_tvhtml5)) },
            description = stringResource(R.string.stream_source_tvhtml5_desc),
            icon = { Icon(painterResource(R.drawable.play), null) },
            checked = tvhtml5Enabled,
            onCheckedChange = onTVHTML5Change,
        )

        PreferenceGroupTitle(
            title = stringResource(R.string.stream_source_native_clients)
        )

        SwitchPreference(
            title = { Text(stringResource(R.string.stream_source_android_vr)) },
            description = stringResource(R.string.stream_source_android_vr_desc),
            icon = { Icon(painterResource(R.drawable.play), null) },
            checked = androidVREnabled,
            onCheckedChange = onAndroidVRChange,
        )

        SwitchPreference(
            title = { Text(stringResource(R.string.stream_source_ios)) },
            description = stringResource(R.string.stream_source_ios_desc),
            icon = { Icon(painterResource(R.drawable.play), null) },
            checked = iosEnabled,
            onCheckedChange = onIOSChange,
        )

        SwitchPreference(
            title = { Text(stringResource(R.string.stream_source_ipad_os)) },
            description = stringResource(R.string.stream_source_ipad_os_desc),
            icon = { Icon(painterResource(R.drawable.play), null) },
            checked = ipadosEnabled,
            onCheckedChange = onIPadOSChange,
        )

        PreferenceGroupTitle(
            title = stringResource(R.string.stream_source_creator_clients)
        )

        SwitchPreference(
            title = { Text(stringResource(R.string.stream_source_web_creator)) },
            description = stringResource(R.string.stream_source_web_creator_desc),
            icon = { Icon(painterResource(R.drawable.play), null) },
            checked = webCreatorEnabled,
            onCheckedChange = onWebCreatorChange,
        )

        SwitchPreference(
            title = { Text(stringResource(R.string.stream_source_android_creator)) },
            description = stringResource(R.string.stream_source_android_creator_desc),
            icon = { Icon(painterResource(R.drawable.play), null) },
            checked = androidCreatorEnabled,
            onCheckedChange = onAndroidCreatorChange,
        )
    }

    TopAppBar(
        title = { Text(stringResource(R.string.stream_sources)) },
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
                        .focusProperties { down = firstFocus },
                )
            }
        },
        scrollBehavior = scrollBehavior,
    )
}
