package com.jtech.zemer.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.jtech.zemer.R
import com.jtech.zemer.ui.component.AppBarTitle
import com.jtech.zemer.ui.component.BackNavigationIcon
import com.jtech.zemer.ui.component.EmptyPlaceholder
import com.jtech.zemer.ui.component.zemerTopAppBarColors
import com.jtech.zemer.ui.component.GenreDetailHeader
import com.jtech.zemer.ui.component.podcastGenreIcon
import com.jtech.zemer.ui.component.shimmer.BoxPlaceholder
import com.jtech.zemer.ui.component.shimmer.GridItemPlaceHolder
import com.jtech.zemer.ui.component.shimmer.ShimmerHost
import com.jtech.zemer.viewmodels.PodcastGenreViewModel
import com.jtech.zemer.viewmodels.PodcastGenreViewModel.UiState

/**
 * One podcast genre's detail — PARITY with the music genre page ([GenreScreen]): the same shared
 * [GenreDetailHeader] (show-art mosaic + weave + big title) over the genre's member SHOWS in the shared
 * [YtItemGrid] (which renders + routes each [com.metrolist.innertube.models.PodcastItem] through the
 * standard show card). The one honest difference is the header has no Play pill — podcasts have no genre
 * radio (a genre is a set of show series, not a playable track queue). 404 backs out; empty/error inline.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PodcastGenreScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: PodcastGenreViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    // Own the grid's scroll state so the top bar can reveal the title once the header scrolls off
    // (item 0 is the GenreDetailHeader) — parity with the music genre page.
    val gridState = rememberLazyGridState()
    val showTopBarTitle by remember {
        derivedStateOf { gridState.firstVisibleItemIndex > 0 }
    }

    LaunchedEffect(state) {
        if (state is UiState.NotFound) navController.navigateUp()
    }

    when (val uiState = state) {
        UiState.Loading -> ShimmerHost {
            // A header slab (the mosaic) then a 2-column card grid — the shape that loads.
            BoxPlaceholder(Modifier.fillMaxWidth().height(220.dp))
            repeat(3) {
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
                // The header mosaic is the genre's OWN color source — its shows' art. Min 3 unique for
                // a full strip, else the header falls back to just the weave + title (no broken mosaic).
                val covers = remember(uiState.shows) {
                    uiState.shows.mapNotNull { it.thumbnail?.takeIf { url -> url.isNotBlank() } }
                        .distinct()
                        .let { if (it.size >= 3) it.take(5) else emptyList() }
                }
                YtItemGrid(
                    items = uiState.shows,
                    navController = navController,
                    gridState = gridState,
                    columns = 3,
                    header = {
                        GenreDetailHeader(
                            title = uiState.title,
                            coverUrls = covers,
                            motifRes = podcastGenreIcon(viewModel.genreId),
                        )
                    },
                )
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

    // Scroll-reveal title, exactly like the music genre page: the big header carries the title at rest,
    // and the bar shows it only once the header has scrolled off.
    TopAppBar(
        title = {
            if (showTopBarTitle) {
                AppBarTitle((state as? UiState.Loaded)?.title.orEmpty())
            }
        },
        navigationIcon = { BackNavigationIcon(navController) },
        scrollBehavior = scrollBehavior,
        colors = zemerTopAppBarColors(),
    )
}
