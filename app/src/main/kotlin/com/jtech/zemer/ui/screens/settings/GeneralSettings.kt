package com.jtech.zemer.ui.screens.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.jtech.zemer.LocalPlayerAwareWindowInsets
import com.jtech.zemer.R
import com.jtech.zemer.constants.DefaultLinkHandlerKey
import com.jtech.zemer.ui.component.PreferenceGroupTitle
import com.jtech.zemer.ui.component.SwitchPreference
import com.jtech.zemer.utils.rememberPreference

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneralSettings(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val (defaultLinkHandler, onDefaultLinkHandlerChange) = rememberPreference(
        DefaultLinkHandlerKey,
        defaultValue = false
    )

    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(8.dp))

        PreferenceGroupTitle(title = stringResource(R.string.links))

        SwitchPreference(
            title = { Text(stringResource(R.string.default_link_handler)) },
            icon = { Icon(painterResource(R.drawable.link), null) },
            checked = defaultLinkHandler,
            onCheckedChange = {
                onDefaultLinkHandlerChange(it)

                // If enabled, prompt user to set as default browser
                if (it && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val intent = Intent(Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(intent)
                }
            }
        )

        Spacer(Modifier.height(8.dp))
    }
}