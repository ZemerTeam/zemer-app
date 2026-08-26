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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.jtech.zemer.constants.InnerTubeCookieKey
import com.jtech.zemer.constants.PlaybackMode
import com.jtech.zemer.constants.PlaybackModeKey
import com.jtech.zemer.constants.StreamSourceVisionOSKey
import com.jtech.zemer.constants.StreamSourceMWEBKey
import com.jtech.zemer.constants.StreamSourceTVHTML5Key
import com.jtech.zemer.constants.StreamSourceWebCreatorKey
import com.jtech.zemer.constants.StreamSourceWebRemixKey
import com.jtech.zemer.ui.component.AppBarTitle
import com.jtech.zemer.ui.component.BackNavigationIcon
import com.jtech.zemer.ui.component.RequestInitialDpadFocus
import com.jtech.zemer.ui.component.SettingsCardGroup
import com.jtech.zemer.ui.component.SettingsScreenTopSpacing
import com.jtech.zemer.ui.component.SwitchPreference
import com.jtech.zemer.ui.component.zemerTopAppBarColors
import com.jtech.zemer.ui.utils.backToMain
import com.jtech.zemer.utils.rememberEnumPreference
import com.jtech.zemer.utils.rememberPreference
import com.jtech.zemer.extensions.cookieHasSession

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreamSourceSettings(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val (webRemixEnabled, onWebRemixChange)     = rememberPreference(StreamSourceWebRemixKey,   defaultValue = true)
    val (tvhtml5Enabled, onTVHTML5Change)       = rememberPreference(StreamSourceTVHTML5Key,    defaultValue = true)
    val (visionosEnabled, onVisionOSChange)     = rememberPreference(StreamSourceVisionOSKey,   defaultValue = true)
    val (webCreatorEnabled, onWebCreatorChange) = rememberPreference(StreamSourceWebCreatorKey, defaultValue = true)
    val (mwebEnabled, onMWEBChange)             = rememberPreference(StreamSourceMWEBKey,       defaultValue = true)

    // RELAY playback mode: stream audio through the Zemer relay instead of resolving YouTube on-device.
    // Off (DIRECT) for every normal user. When ON, the per-client fallback list below is bypassed entirely.
    var playbackMode by rememberEnumPreference(PlaybackModeKey, defaultValue = PlaybackMode.DIRECT)
    val relayEnabled = playbackMode == PlaybackMode.RELAY

    // The relay toggle is ONLY for the login-less "filtered device" session. A normal Google or Anonymous
    // login (both carry a SAPISID cookie) has working direct playback, so it must not see or flip this
    // switch — the whole "Filtered devices" group is hidden for them, leaving just the client list.
    val (loginCookie) = rememberPreference(InnerTubeCookieKey, defaultValue = "")
    val loggedInNormally = remember(loginCookie) { loginCookie.cookieHasSession() }
    // The "a normal login forces DIRECT" reset lives globally in App.kt (so it fires from ANY login entry
    // point), not here — a screen-local reset stranded users who logged in elsewhere and also flashed an
    // empty settings screen with no focused row.

    // Effective stream order shown to the user: WEB_REMIX is the primary client; the rest mirror
    // YTPlayerUtils.ALL_FALLBACK_CLIENTS. Only enabled toggles appear.
    val streamOrder = listOf(
        "WEB_REMIX" to webRemixEnabled,
        "visionOS" to visionosEnabled,
        "WEB_CREATOR" to webCreatorEnabled,
        "TVHTML5" to tvhtml5Enabled,
        "MWEB" to mwebEnabled,
    ).filter { it.second }.map { it.first }

    val backFocus = remember { FocusRequester() }
    val firstFocus = remember { FocusRequester() }

    // firstFocus is attached to whichever first row is visible (the relay toggle for a login-less session,
    // the web-remix toggle otherwise). Keyed on the visibility inputs so it re-requests once a row is
    // actually composed (e.g. after the global relay reset makes the client list appear); guarded in case
    // neither is composed yet.
    RequestInitialDpadFocus(firstFocus, keys = arrayOf(loggedInNormally, relayEnabled))

    Column(
        Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(SettingsScreenTopSpacing))
        // The RELAY toggle leads: it is the one switch that changes WHERE audio comes from. When on, the
        // on-device client fallback list below no longer applies (playback goes through the Zemer relay).
        // Shown ONLY for a login-less session — a normal Google/Anonymous login never sees it.
        if (!loggedInNormally) {
        SettingsCardGroup(
            title = stringResource(R.string.stream_relay_group),
            rows = listOf(
                {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.stream_relay_title)) },
                        description = stringResource(R.string.stream_relay_desc),
                        icon = { Icon(painterResource(R.drawable.security), null) },
                        checked = relayEnabled,
                        onCheckedChange = { on -> playbackMode = if (on) PlaybackMode.RELAY else PlaybackMode.DIRECT },
                        // When shown (login-less), the relay toggle carries the initial D-pad focus.
                        modifier = Modifier.focusRequester(firstFocus),
                    )
                },
            ),
        )
        } // end if (!loggedInNormally)

        // The per-client fallback list only governs DIRECT playback; in RELAY mode the relay resolves the
        // stream server-side, so these toggles do nothing. Hide the whole section so it is not available.
        if (!relayEnabled) {

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

        SettingsCardGroup(
            title = stringResource(R.string.stream_source_web_clients),
            rows = listOf(
                {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.stream_source_web_remix)) },
                        description = stringResource(R.string.stream_source_web_remix_desc),
                        icon = { Icon(painterResource(R.drawable.play), null) },
                        checked = webRemixEnabled,
                        onCheckedChange = onWebRemixChange,
                        // When the relay group is hidden (a normal login), this is the first row, so it takes focus.
                        modifier = if (loggedInNormally) Modifier.focusRequester(firstFocus) else Modifier,
                    )
                },
                {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.stream_source_tvhtml5)) },
                        description = stringResource(R.string.stream_source_tvhtml5_desc),
                        icon = { Icon(painterResource(R.drawable.play), null) },
                        checked = tvhtml5Enabled,
                        onCheckedChange = onTVHTML5Change,
                    )
                },
                {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.stream_source_mweb)) },
                        description = stringResource(R.string.stream_source_mweb_desc),
                        icon = { Icon(painterResource(R.drawable.play), null) },
                        checked = mwebEnabled,
                        onCheckedChange = onMWEBChange,
                    )
                },
            ),
        )

        SettingsCardGroup(
            title = stringResource(R.string.stream_source_native_clients),
            rows = listOf(
                {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.stream_source_visionos)) },
                        description = stringResource(R.string.stream_source_visionos_desc),
                        icon = { Icon(painterResource(R.drawable.play), null) },
                        checked = visionosEnabled,
                        onCheckedChange = onVisionOSChange,
                    )
                },
            ),
        )

        SettingsCardGroup(
            title = stringResource(R.string.stream_source_creator_clients),
            rows = listOf(
                {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.stream_source_web_creator)) },
                        description = stringResource(R.string.stream_source_web_creator_desc),
                        icon = { Icon(painterResource(R.drawable.play), null) },
                        checked = webCreatorEnabled,
                        onCheckedChange = onWebCreatorChange,
                    )
                },
            ),
        )
        } // end if (!relayEnabled): DIRECT-only client list
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
