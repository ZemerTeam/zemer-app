package com.jtech.zemer.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.jtech.zemer.LocalPlayerAwareWindowInsets
import com.jtech.zemer.crawler.LyricsCrawlerService
import com.jtech.zemer.ui.component.AppBarTitle
import com.jtech.zemer.ui.component.BackNavigationIcon
import com.jtech.zemer.ui.component.SettingsScreenTopSpacing
import com.jtech.zemer.ui.component.zemerTopAppBarColors

/**
 * Debug-only screen driving [LyricsCrawlerService]: one button that starts/stops a screen-off crawl of the whole
 * catalog, fetching every enabled provider's lyrics for each song and feeding them to the Zemer server.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsCrawlerScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val context = LocalContext.current
    val progress by LyricsCrawlerService.progress.collectAsState()

    Column(
        Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(SettingsScreenTopSpacing))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                if (progress.running) "Crawling…" else "Idle",
                style = MaterialTheme.typography.titleLarge,
            )
            Text("Songs fetched this session: ${progress.done}")
            if (progress.current.isNotBlank()) Text("Now: ${progress.current}")
            Button(
                onClick = {
                    if (progress.running) LyricsCrawlerService.stop(context) else LyricsCrawlerService.start(context)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (progress.running) "Stop crawling" else "Start crawling")
            }
            Text(
                "Walks the whole catalog (most-played first) and fetches every enabled lyrics provider for each " +
                    "song, sending the results to the Zemer server. Keeps running with the screen off. It does not " +
                    "play audio, so it never affects play-count rankings. The server hands each device a different " +
                    "slice, so multiple devices never duplicate work.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    TopAppBar(
        title = { AppBarTitle("Lyrics crawler") },
        navigationIcon = { BackNavigationIcon(navController) },
        colors = zemerTopAppBarColors(),
        scrollBehavior = scrollBehavior,
    )
}
