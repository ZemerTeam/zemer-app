package com.jtech.zemer.ui.screens.statuses

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.jtech.zemer.LocalPlayerAwareWindowInsets
import com.jtech.zemer.R
import com.jtech.zemer.statuses.sortedByUnseenFirst
import com.jtech.zemer.ui.component.AppBarTitle
import com.jtech.zemer.ui.component.BackNavigationIcon
import com.jtech.zemer.ui.component.StatusCreatorCircle
import com.jtech.zemer.ui.component.zemerTopAppBarColors
import com.jtech.zemer.ui.utils.storyRoute
import com.jtech.zemer.viewmodels.ZemerStatusesViewModel

/**
 * The "See all" screen for the Home "Music Status" row: every JewishStatus creator as a grid of
 * story-circles. Reuses the shared [ZemerStatusesViewModel] state (same creators + seen the Home row
 * shows), so it is instant when opened from Home and self-populates (cache-backed refresh) if entered
 * cold. Fully-viewed creators sink to the end, matching the row. An empty/unreachable feed just shows
 * an empty grid.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusesScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: ZemerStatusesViewModel = hiltViewModel(),
) {
    val creators by viewModel.creators.collectAsState()
    val seenPostIds by viewModel.seenPostIds.collectAsState()
    val ordered = remember(creators, seenPostIds) { creators.sortedByUnseenFirst(seenPostIds) }

    LaunchedEffect(Unit) { viewModel.refresh() }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 88.dp),
            contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(
                items = ordered,
                key = { creator -> creator.id },
            ) { creator ->
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                    StatusCreatorCircle(
                        creator = creator,
                        seenPostIds = seenPostIds,
                        onClick = { navController.navigate(storyRoute(creator.id)) },
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
            }
        }
    }

    TopAppBar(
        title = { AppBarTitle(stringResource(R.string.statuses)) },
        navigationIcon = { BackNavigationIcon(navController) },
        scrollBehavior = scrollBehavior,
        colors = zemerTopAppBarColors(),
    )
}
