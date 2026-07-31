package com.jtech.zemer.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.jtech.zemer.LocalPlayerAwareWindowInsets
import com.jtech.zemer.LocalPlayerConnection
import com.jtech.zemer.R
import com.jtech.zemer.constants.GridThumbnailHeight
import com.jtech.zemer.playback.queues.StationQueue
import com.jtech.zemer.ui.component.BackNavigationIcon
import com.jtech.zemer.ui.component.ZemerStationCard
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.jtech.zemer.viewmodels.STATION_ROW_REFRESH_MS
import com.jtech.zemer.viewmodels.ZemerStationsViewModel
import kotlinx.coroutines.delay

/**
 * The "See all" screen for the Home "Zemer Radio" section: every live station as a grid (the same
 * card the row uses — cover, title, nowPlaying line), fresh-fetched on open like every Zemer
 * see-all. Tapping tunes in at the live broadcast position ([StationQueue]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZemerStationsScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: ZemerStationsViewModel = hiltViewModel(),
) {
    val playerConnection = LocalPlayerConnection.current
    val stations by viewModel.stations.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(Unit) {
        // Same lifecycle-scoped now-playing ticker as the home row: suspended outside RESUMED, so
        // nothing polls while the app is backgrounded.
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.refresh()
            while (true) {
                delay(STATION_ROW_REFRESH_MS)
                viewModel.refresh()
            }
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = GridThumbnailHeight + 24.dp),
        contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
    ) {
        items(
            items = stations,
            key = { it.id },
        ) { station ->
            ZemerStationCard(
                station = station,
                fillMaxWidth = true,
                modifier = Modifier.clickable {
                    // Tune in at the live broadcast position.
                    playerConnection?.let { pc ->
                        pc.playQueue(StationQueue(station.id, pc.service))
                    }
                },
            )
        }
    }

    TopAppBar(
        title = { Text(stringResource(R.string.zemer_radio)) },
        navigationIcon = { BackNavigationIcon(navController) },
        scrollBehavior = scrollBehavior,
    )
}
