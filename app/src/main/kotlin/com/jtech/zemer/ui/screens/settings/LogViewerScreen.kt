package com.jtech.zemer.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.jtech.zemer.LocalPlayerAwareWindowInsets
import com.jtech.zemer.R
import com.jtech.zemer.constants.DebugLoggingEnabledKey
import com.jtech.zemer.ui.component.IconButton
import com.jtech.zemer.ui.component.PreferenceEntry
import com.jtech.zemer.ui.component.PreferenceGroupTitle
import com.jtech.zemer.ui.component.SwitchPreference
import com.jtech.zemer.ui.utils.backToMain
import com.jtech.zemer.utils.LogBufferTree
import com.jtech.zemer.utils.rememberPreference
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogViewerScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val backFocus = remember { FocusRequester() }
    val firstFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        firstFocus.requestFocus()
    }

    val (debugLogging, onDebugLoggingChange) = rememberPreference(DebugLoggingEnabledKey, true)
    val entries = remember { LogBufferTree.entries }
    var refreshTick by remember { mutableStateOf(0) }

    LaunchedEffect(debugLogging) {
        while (isActive) {
            delay(1000)
            refreshTick++
        }
    }

    Column(
        Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
            .fillMaxSize(),
    ) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            PreferenceGroupTitle(title = stringResource(R.string.log_viewer))

            SwitchPreference(
                title = { Text(stringResource(R.string.enable_debug_logging)) },
                description = stringResource(R.string.enable_debug_logging_desc),
                icon = { Icon(painterResource(R.drawable.info), null) },
                checked = debugLogging,
                onCheckedChange = onDebugLoggingChange,
            )

            PreferenceEntry(
                title = { Text(stringResource(R.string.clear_logs)) },
                onClick = {
                    LogBufferTree.clear()
                },
                modifier = Modifier.focusRequester(firstFocus),
            )

            HorizontalDivider()

            if (entries.isEmpty()) {
                Text(
                    text = stringResource(R.string.no_logs),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            } else {
                entries.takeLast(200).forEach { entry ->
                    val color = when (entry.priority) {
                        android.util.Log.ERROR -> MaterialTheme.colorScheme.error
                        android.util.Log.WARN -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                    Text(
                        text = "${LogBufferTree.priorityName(entry.priority)}/${entry.tag ?: "Zemer"}: ${entry.message}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = color,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 1.dp),
                    )
                }
            }
        }
    }

    TopAppBar(
        title = { Text(stringResource(R.string.log_viewer)) },
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
                        .focusProperties { down = firstFocus }
                )
            }
        }
    )
}
