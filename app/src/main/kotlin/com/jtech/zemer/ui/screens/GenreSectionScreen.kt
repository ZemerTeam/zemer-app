package com.jtech.zemer.ui.screens

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.jtech.zemer.R
import com.jtech.zemer.ui.component.AppBarTitle
import com.jtech.zemer.ui.component.BackTopAppBar
import com.jtech.zemer.ui.component.EmptyPlaceholder
import com.jtech.zemer.ui.component.ErrorRetryState
import com.jtech.zemer.ui.component.shimmer.ShimmerHost
import com.jtech.zemer.ui.component.shimmer.GridItemPlaceHolder
import com.jtech.zemer.viewmodels.ZemerGenreSectionViewModel
import com.jtech.zemer.viewmodels.ZemerGenreSectionViewModel.UiState

/**
 * A genre's per-section see-all: the full Albums or Singles grid (the detail screen shows only the
 * top-20 shelf). Reuses the shared [YtItemGrid] (2-column, opens albums via the server route) and
 * [BackTopAppBar]. 404 backs out; an empty/error state renders inline. The title reflects the
 * section, which the ViewModel derives from the route's `section` argument.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenreSectionScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: ZemerGenreSectionViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state) {
        if (state is UiState.NotFound) navController.navigateUp()
    }

    when (val uiState = state) {
        // A 2-column placeholder grid, matching the YtItemGrid it precedes.
        UiState.Loading -> ShimmerHost {
            repeat(4) {
                Row {
                    GridItemPlaceHolder(modifier = Modifier.weight(1f), fillMaxWidth = true)
                    GridItemPlaceHolder(modifier = Modifier.weight(1f), fillMaxWidth = true)
                }
            }
        }

        is UiState.Loaded ->
            if (uiState.albums.isEmpty()) {
                EmptyPlaceholder(
                    icon = R.drawable.music_note,
                    text = stringResource(R.string.home_see_all_empty),
                )
            } else {
                YtItemGrid(items = uiState.albums, navController = navController, zemerAlbums = true)
            }

        UiState.NotFound -> Unit

        UiState.Error -> ErrorRetryState(onRetry = viewModel::load)
    }

    BackTopAppBar(
        title = {
            AppBarTitle(stringResource(if (viewModel.isSingles) R.string.singles else R.string.albums))
        },
        navController = navController,
        scrollBehavior = scrollBehavior,
    )
}
