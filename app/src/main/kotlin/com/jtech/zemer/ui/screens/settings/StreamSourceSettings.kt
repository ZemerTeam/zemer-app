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
import com.jtech.zemer.constants.StreamSabrKey
import com.jtech.zemer.ui.component.AppBarTitle
import com.jtech.zemer.ui.component.BackNavigationIcon
import com.jtech.zemer.ui.component.RequestInitialDpadFocus
import com.jtech.zemer.ui.component.SettingsCardGroup
import com.jtech.zemer.ui.component.SettingsScreenTopSpacing
import com.jtech.zemer.ui.component.SwitchPreference
import com.jtech.zemer.ui.component.zemerTopAppBarColors
import com.jtech.zemer.utils.StreamClientTable
import com.jtech.zemer.utils.StreamSourcePrefs
import com.jtech.zemer.utils.rememberEnumPreference
import com.jtech.zemer.utils.rememberPreference
import com.jtech.zemer.extensions.cookieHasSession
import com.zemer.cipher.StreamClientStore

/**
 * The hybrid string map (owner decision, 2026-08-16): the known families keep their bespoke
 * localized title + description; a remotely-added family renders its config title (English,
 * server-driven — the genres/home-rows precedent) with the generic description.
 */
private data class FamilyStrings(val titleRes: Int, val descRes: Int)

private val KNOWN_FAMILY_STRINGS = mapOf(
    "WEB_REMIX" to FamilyStrings(R.string.stream_source_web_remix, R.string.stream_source_web_remix_desc),
    "TVHTML5" to FamilyStrings(R.string.stream_source_tvhtml5, R.string.stream_source_tvhtml5_desc),
    "VISIONOS" to FamilyStrings(R.string.stream_source_visionos, R.string.stream_source_visionos_desc),
    "WEB_CREATOR" to FamilyStrings(R.string.stream_source_web_creator, R.string.stream_source_web_creator_desc),
)

/** SABR-specific descriptions for the known families; a remotely-added one gets the generic line. */
private val KNOWN_SABR_FAMILY_DESC = mapOf(
    "WEB_REMIX" to R.string.stream_source_sabr_web_remix_desc,
    "VISIONOS" to R.string.stream_source_sabr_visionos_desc,
    "TVHTML5" to R.string.stream_source_sabr_tvhtml5_desc,
)

private fun groupTitleRes(group: String): Int = when (group) {
    StreamSourceUiModel.GROUP_WEB -> R.string.stream_source_web_clients
    StreamSourceUiModel.GROUP_NATIVE -> R.string.stream_source_native_clients
    StreamSourceUiModel.GROUP_CREATOR -> R.string.stream_source_creator_clients
    else -> R.string.stream_source_other_clients
}

@Composable
private fun familyTitle(family: StreamSourceUiModel.Family): String =
    KNOWN_FAMILY_STRINGS[family.id]?.let { stringResource(it.titleRes) }
        ?: family.configTitle
        ?: family.id

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreamSourceSettings(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    // Both toggle lists (DIRECT chain, SABR roster) derive from the CURRENT client table
    // (remote/bundled via the store, compiled floor otherwise), so the screen always matches what
    // the resolvers run. Snapshot once per screen-open — a remote refresh shows on the next open.
    val (families, sabrFamilies) = remember {
        val table = StreamClientTable.current()
        val meta = StreamClientStore.config()?.families.orEmpty()
        StreamSourceUiModel.families(table, meta) to StreamSourceUiModel.sabrFamilies(table, meta)
    }

    // When the client table last synced with the deploy channel (200 applied or 304 unchanged);
    // 0 = never synced this install (offline / file not published yet) — the line is hidden then.
    val lastSyncedText = remember {
        StreamClientStore.lastSyncedMs.takeIf { it > 0L }?.let {
            java.text.DateFormat.getDateTimeInstance(
                java.text.DateFormat.SHORT, java.text.DateFormat.SHORT,
            ).format(java.util.Date(it))
        }
    }

    // One dynamic boolean preference per family (absent = enabled). The map is keyed in chain
    // order; families is stable for the composition, so the loop order is too.
    val familyStates = families.associate { family ->
        val (enabled, setEnabled) = rememberPreference(
            StreamSourcePrefs.familyKey(family.id),
            defaultValue = true,
        )
        family.id to Pair(enabled, setEnabled)
    }
    val disabled = familyStates.filterValues { !it.first }.keys

    val (sabrEnabled, onSabrChange) = rememberPreference(StreamSabrKey, defaultValue = false)
    // One dynamic SABR preference per SABR-roster family (absent = enabled), like the DIRECT rows.
    val sabrFamilyStates = sabrFamilies.associate { family ->
        val (enabled, setEnabled) = rememberPreference(
            StreamSourcePrefs.sabrFamilyKey(family.id),
            defaultValue = true,
        )
        family.id to Pair(enabled, setEnabled)
    }

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

    val backFocus = remember { FocusRequester() }
    val firstFocus = remember { FocusRequester() }

    // firstFocus is attached to whichever first row is visible (the relay toggle for a login-less session,
    // the first family toggle otherwise). Keyed on the visibility inputs so it re-requests once a row is
    // actually composed; guarded in case neither is composed yet.
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
            lastSyncedText?.let { synced ->
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.stream_sources_updated, synced),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                // Effective stream order: the chain order with disabled families dropped — derived
                // from the same table as the toggles, so the two can never drift.
                StreamSourceUiModel.enabledOrder(families, disabled).forEach { family ->
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Text(
                            text = familyTitle(family),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }

        StreamSourceUiModel.grouped(families).forEachIndexed { groupIndex, (group, groupFamilies) ->
            SettingsCardGroup(
                title = stringResource(groupTitleRes(group)),
                rows = groupFamilies.mapIndexed { familyIndex, family ->
                    {
                        val (enabled, setEnabled) = familyStates.getValue(family.id)
                        SwitchPreference(
                            title = { Text(familyTitle(family)) },
                            description = KNOWN_FAMILY_STRINGS[family.id]?.let { stringResource(it.descRes) }
                                ?: stringResource(R.string.stream_source_generic_desc),
                            icon = { Icon(painterResource(R.drawable.play), null) },
                            checked = enabled,
                            onCheckedChange = setEnabled,
                            // When the relay group is hidden (a normal login), the first family row
                            // takes the initial D-pad focus.
                            modifier = if (loggedInNormally && groupIndex == 0 && familyIndex == 0) {
                                Modifier.focusRequester(firstFocus)
                            } else {
                                Modifier
                            },
                        )
                    }
                },
            )
        }

        SettingsCardGroup(
            title = stringResource(R.string.stream_source_experimental),
            rows = listOf(
                {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.stream_source_sabr)) },
                        description = stringResource(R.string.stream_source_sabr_desc),
                        icon = { Icon(painterResource(R.drawable.play), null) },
                        checked = sabrEnabled,
                        onCheckedChange = onSabrChange,
                    )
                },
            ),
        )

        // The SABR client list — shown when SABR streaming is on. Mirrors the DIRECT client list above:
        // one toggle per SABR-roster family, in TABLE order (the order the resolvers try them).
        if (sabrEnabled && sabrFamilies.isNotEmpty()) {
            SettingsCardGroup(
                title = stringResource(R.string.stream_source_sabr_clients),
                rows = sabrFamilies.map { family ->
                    {
                        val (enabled, setEnabled) = sabrFamilyStates.getValue(family.id)
                        SwitchPreference(
                            title = { Text(familyTitle(family)) },
                            description = KNOWN_SABR_FAMILY_DESC[family.id]?.let { stringResource(it) }
                                ?: stringResource(R.string.stream_source_generic_desc),
                            icon = { Icon(painterResource(R.drawable.play), null) },
                            checked = enabled,
                            onCheckedChange = setEnabled,
                        )
                    }
                },
            )
        }
        } // end if (!relayEnabled): DIRECT-only client list

        // Breathing room below the last card group (and clearance for the mini-player insets).
        Spacer(Modifier.height(SettingsScreenTopSpacing))
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
