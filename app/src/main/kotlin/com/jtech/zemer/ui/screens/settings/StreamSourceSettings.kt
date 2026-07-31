package com.jtech.zemer.ui.screens.settings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
import com.jtech.zemer.constants.StreamSourceVisionOSKey
import com.jtech.zemer.constants.StreamSourceTVHTML5Key
import com.jtech.zemer.constants.StreamSourceWebCreatorKey
import com.jtech.zemer.constants.StreamSourceWebRemixKey
import com.jtech.zemer.ui.component.AppBarTitle
import com.jtech.zemer.ui.component.BackNavigationIcon
import com.jtech.zemer.ui.component.PreferenceGroupTitle
import com.jtech.zemer.ui.component.SwitchPreference
import com.jtech.zemer.ui.component.zemerTopAppBarColors
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
    val (iosEnabled, onIOSChange)               = rememberPreference(StreamSourceIOSKey,        defaultValue = false)
    val (ipadosEnabled, onIPadOSChange)         = rememberPreference(StreamSourceIPadOSKey,     defaultValue = false)
    val (visionosEnabled, onVisionOSChange)     = rememberPreference(StreamSourceVisionOSKey,   defaultValue = true)
    val (webCreatorEnabled, onWebCreatorChange) = rememberPreference(StreamSourceWebCreatorKey, defaultValue = true)
    val (androidCreatorEnabled, onAndroidCreatorChange) = rememberPreference(StreamSourceAndroidCreatorKey, defaultValue = false)

    // Effective stream order shown to the user: WEB_REMIX is the primary client; the rest mirror
    // YTPlayerUtils.ALL_FALLBACK_CLIENTS (ANDROID_VR variants deduped). Only enabled toggles appear.
    val streamOrder = listOf(
        "WEB_REMIX" to webRemixEnabled,
        "visionOS" to visionosEnabled,
        "WEB_CREATOR" to webCreatorEnabled,
        "Android VR" to androidVREnabled,
        "TVHTML5" to tvhtml5Enabled,
        "iOS" to iosEnabled,
        "iPadOS" to ipadosEnabled,
        "ANDROID_CREATOR" to androidCreatorEnabled,
    ).filter { it.second }.map { it.first }

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
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 12.dp),
        ) {
            Text(
                text = stringResource(R.string.stream_source_order),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                streamOrder.forEach { name ->
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }

        PreferenceGroupTitle(
            title = stringResource(R.string.stream_source_web_clients)
        )

        SwitchPreference(
            title = { Text(stringResource(R.string.stream_source_web_remix)) },
            description = stringResource(R.string.stream_source_web_remix_desc),
            icon = { Icon(painterResource(R.drawable.play), null) },
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
            title = { Text(stringResource(R.string.stream_source_visionos)) },
            description = stringResource(R.string.stream_source_visionos_desc),
            icon = { Icon(painterResource(R.drawable.play), null) },
            checked = visionosEnabled,
            onCheckedChange = onVisionOSChange,
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
        title = { AppBarTitle(stringResource(R.string.stream_sources)) },
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
