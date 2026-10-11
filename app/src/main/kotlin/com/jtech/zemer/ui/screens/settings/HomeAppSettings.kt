package com.jtech.zemer.ui.screens.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.jtech.zemer.R
import com.jtech.zemer.launcher.HomeAppAlias
import com.jtech.zemer.ui.component.SettingsCardGroup
import com.jtech.zemer.extensions.toast
import com.jtech.zemer.ui.component.SwitchPreference
import com.jtech.zemer.utils.reportException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class HomeAppState(val managed: Boolean, val enabled: Boolean)

/** The "Use Zemer as home app" group (Appearance). Locked on once a launcher marker has been seen. */
@Composable
fun HomeAppSettingsGroup() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // File stats + a PackageManager call: read off the main thread; the group appears once known.
    var state by remember { mutableStateOf<HomeAppState?>(null) }
    val roleRequest = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {}
    LaunchedEffect(Unit) {
        state = withContext(Dispatchers.IO) {
            val managed = HomeAppAlias.isManaged(context)
            HomeAppState(managed, managed || HomeAppAlias.isEnabled(context))
        }
    }
    val current = state ?: return

    SettingsCardGroup(
        title = stringResource(R.string.home_app),
        rows = listOf(
            {
                SwitchPreference(
                    title = { Text(stringResource(R.string.home_app_toggle)) },
                    description = stringResource(
                        if (current.managed) R.string.home_app_toggle_managed else R.string.home_app_toggle_desc,
                    ),
                    icon = { Icon(painterResource(R.drawable.home_outlined), null) },
                    checked = current.enabled,
                    onCheckedChange = { on ->
                        scope.launch {
                            runCatching { withContext(Dispatchers.IO) { HomeAppAlias.setEnabled(context, on) } }
                                .onSuccess {
                                    state = current.copy(enabled = on)
                                    if (on && !HomeAppAlias.openHomePicker(context)) {
                                        // No picker on this ROM: ask for the home role, else say where to set it.
                                        val opened = HomeAppAlias.homeRoleRequestIntent(context)
                                            ?.let { runCatching { roleRequest.launch(it) }.isSuccess } == true
                                        if (!opened) context.toast(R.string.home_app_picker_unavailable, long = true)
                                    }
                                }
                                .onFailure { reportException(it) }
                        }
                    },
                    isEnabled = !current.managed,
                )
            },
        ),
    )
}
