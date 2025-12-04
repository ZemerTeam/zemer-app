package com.jtech.zemer.ui.screens.settings

import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.google.firebase.auth.FirebaseAuth
import com.jtech.zemer.LocalPlayerAwareWindowInsets
import com.jtech.zemer.R
import com.jtech.zemer.ui.component.Material3SettingsGroup
import com.jtech.zemer.ui.component.Material3SettingsItem

data class SettingItem(
    val id: String,
    val title: String,
    val description: String,
    val icon: Int,
    val section: String,
    val route: String?
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    latestVersionName: String,
) {
    LocalUriHandler.current
    val context = LocalContext.current
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val firebaseAuth = remember { FirebaseAuth.getInstance() }
    var isLoggedIn by remember { mutableStateOf(firebaseAuth.currentUser != null) }

    DisposableEffect(firebaseAuth) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            isLoggedIn = auth.currentUser != null
        }
        firebaseAuth.addAuthStateListener(listener)
        onDispose { firebaseAuth.removeAuthStateListener(listener) }
    }

    // Define all settings items without stringResource (use string literals)
    val baseSettings = listOf(
        SettingItem(
            id = "appearance",
            title = stringResource(R.string.appearance),
            description = "Theme, colors, density",
            icon = R.drawable.palette,
            section = "Interface",
            route = "settings/appearance"
        ),
        SettingItem(
            id = "player",
            title = stringResource(R.string.player_and_audio),
            description = "Audio quality, playback",
            icon = R.drawable.play,
            section = "Player & Content",
            route = "settings/player"
        ),
        SettingItem(
            id = "content",
            title = stringResource(R.string.content),
            description = "Language, content settings",
            icon = R.drawable.language,
            section = "Player & Content",
            route = "settings/content"
        ),
        SettingItem(
            id = "dpad",
            title = stringResource(R.string.settings_button_setup),
            description = "D-pad configuration",
            icon = R.drawable.swipe,
            section = "Player & Content",
            route = "settings/dpad"
        ),
        SettingItem(
            id = "privacy",
            title = stringResource(R.string.privacy),
            description = "History, privacy settings",
            icon = R.drawable.security,
            section = "Privacy & Security",
            route = "settings/privacy"
        ),
        SettingItem(
            id = "storage",
            title = stringResource(R.string.storage),
            description = "Cache management",
            icon = R.drawable.storage,
            section = "Storage & Data",
            route = "settings/storage"
        ),
        SettingItem(
            id = "backup",
            title = stringResource(R.string.backup_restore),
            description = "Backup your data",
            icon = R.drawable.restore,
            section = "Storage & Data",
            route = "settings/backup_restore"
        ),
        SettingItem(
            id = "about",
            title = stringResource(R.string.about),
            description = "About Zemer",
            icon = R.drawable.info,
            section = "System & About",
            route = "settings/about"
        )
    )
    val allSettings = baseSettings + if (isLoggedIn) {
        listOf(
            SettingItem(
                id = "logout",
                title = stringResource(R.string.action_logout),
                description = stringResource(R.string.sign_out_description),
                icon = R.drawable.person,
                section = "System & About",
                route = null
            )
        )
    } else {
        emptyList()
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        TopAppBar(
            title = { Text(stringResource(R.string.settings)) },
            navigationIcon = {
                IconButton(onClick = { navController.navigateUp() }) {
                    Icon(
                        painter = painterResource(R.drawable.arrow_back),
                        contentDescription = null
                    )
                }
            },
            scrollBehavior = scrollBehavior
        )
        // Content with scroll
        val paddingValues = LocalPlayerAwareWindowInsets.current.asPaddingValues()
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = paddingValues.calculateBottomPadding())
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            val sections = allSettings.groupBy { it.section }
            sections.forEach { (sectionTitle, items) ->
                Material3SettingsGroup(
                    title = sectionTitle,
                    items = items.map { setting ->
                        Material3SettingsItem(
                            icon = painterResource(setting.icon),
                            title = { Text(setting.title) },
                            description = { Text(setting.description) },
                            onClick = {
                                if (setting.route != null) {
                                    navController.navigate(setting.route)
                                } else if (setting.id == "logout") {
                                    FirebaseAuth.getInstance().signOut()
                                    Toast.makeText(context, R.string.action_logout, Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
