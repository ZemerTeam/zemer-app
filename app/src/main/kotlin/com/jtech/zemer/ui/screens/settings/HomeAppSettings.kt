package com.jtech.zemer.ui.screens.settings

import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.jtech.zemer.R
import com.jtech.zemer.ui.component.SettingsCardGroup
import com.jtech.zemer.ui.component.SwitchPreference
import com.jtech.zemer.utils.HomeAppAlias

/** The "Use Zemer as home app" group (Appearance). Locked on when the ROM ships the launcher marker. */
@Composable
fun HomeAppSettingsGroup() {
    val context = LocalContext.current
    val managed = remember { HomeAppAlias.isManaged() }
    var enabled by remember { mutableStateOf(managed || HomeAppAlias.isEnabled(context)) }

    SettingsCardGroup(
        title = stringResource(R.string.home_app),
        rows = listOf(
            {
                SwitchPreference(
                    title = { Text(stringResource(R.string.home_app_toggle)) },
                    description = stringResource(if (managed) R.string.home_app_toggle_managed else R.string.home_app_toggle_desc),
                    icon = { Icon(painterResource(R.drawable.home_outlined), null) },
                    checked = enabled,
                    onCheckedChange = { on ->
                        HomeAppAlias.setEnabled(context, on)
                        enabled = on
                        if (on) HomeAppAlias.openHomePicker(context)
                    },
                    isEnabled = !managed,
                )
            },
        ),
    )
}
