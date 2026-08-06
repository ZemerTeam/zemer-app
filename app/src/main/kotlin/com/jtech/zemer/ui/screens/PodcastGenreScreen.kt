package com.jtech.zemer.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.jtech.zemer.R
import com.jtech.zemer.ui.component.AppBarTitle
import com.jtech.zemer.ui.component.BackTopAppBar
import com.jtech.zemer.ui.component.EmptyPlaceholder
import com.jtech.zemer.ui.component.shimmer.GridItemPlaceHolder
import com.jtech.zemer.ui.component.shimmer.ShimmerHost
import com.jtech.zemer.viewmodels.PodcastGenreViewModel
import com.jtech.zemer.viewmodels.PodcastGenreViewModel.UiState

/**
 * One podcast genre's detail: a FLAT grid of its member shows. Reuses the shared [YtItemGrid] (which
 * renders + routes [com.metrolist.innertube.models.PodcastItem] through the standard podcast show card)
 * and [BackTopAppBar] — the same shape as [GenreSectionScreen], since a podcast genre has no
 * facets/tracklist/radio like a music genre. 404 backs out; empty/error render inline.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PodcastGenreScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: PodcastGenreViewModel = hiltViewModel(),
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
            if (uiState.shows.isEmpty()) {
                EmptyPlaceholder(
                    icon = R.drawable.podcast,
                    text = stringResource(R.string.home_see_all_empty),
                )
            } else {
                YtItemGrid(items = uiState.shows, navController = navController)
            }

        UiState.NotFound -> Unit

        UiState.Error -> Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.error_unknown),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = viewModel::load) {
                Text(stringResource(R.string.retry))
            }
        }
    }

    BackTopAppBar(
        title = {
            AppBarTitle((state as? UiState.Loaded)?.title ?: stringResource(R.string.genres))
        },
        navController = navController,
        scrollBehavior = scrollBehavior,
    )
}
