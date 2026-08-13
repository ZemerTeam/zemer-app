package com.jtech.zemer.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.jtech.zemer.LocalPlayerAwareWindowInsets
import com.jtech.zemer.R
import com.jtech.zemer.search.zemerPodcastGenreRoute
import com.jtech.zemer.ui.component.AppBarTitle
import com.jtech.zemer.ui.component.BackNavigationIcon
import com.jtech.zemer.ui.component.EmptyPlaceholder
import com.jtech.zemer.ui.component.GenreCardGrid
import com.jtech.zemer.ui.component.GenreCatalogTopSpacing
import com.jtech.zemer.ui.component.podcastGenreIcon
import com.jtech.zemer.ui.component.zemerTopAppBarColors
import com.jtech.zemer.ui.component.shimmer.BoxPlaceholder
import com.jtech.zemer.ui.component.shimmer.ShimmerHost
import com.jtech.zemer.ui.component.shimmer.TextPlaceholder
import com.jtech.zemer.viewmodels.PodcastGenreCatalogViewModel
import com.jtech.zemer.viewmodels.PodcastGenreCatalogViewModel.UiState

/**
 * The podcast-genre catalog: big genre cards in the shared [GenreCardGrid], grouped under the
 * SERVER-OWNED kind sections (`kinds` on `/podcast-genres` — titles come from the wire, unlike
 * music's app-side Styles/Occasions strings, so a new kind ships titled with no app release). No
 * `kinds` (older server / offline snapshot) = one headerless flat grid, exactly the pre-kinds render.
 * Tapping a card opens the genre's show list ([PodcastGenreScreen]). Count-free, like music.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PodcastGenresScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: PodcastGenreCatalogViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LazyColumn(
        contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
    ) {
        // Breathing room between the top bar and the first section (owner ask; matches music).
        item(key = "top_spacer") { Spacer(Modifier.height(GenreCatalogTopSpacing)) }

        when (val uiState = state) {
            UiState.Loading -> item(key = "loading_shimmer") {
                PodcastGenreCatalogShimmer()
            }

            is UiState.Loaded -> {
                if (uiState.sections.isEmpty()) {
                    item(key = "empty") {
                        EmptyPlaceholder(
                            icon = R.drawable.podcast,
                            text = stringResource(R.string.home_see_all_empty),
                        )
                    }
                } else {
                    uiState.sections.forEachIndexed { index, section ->
                        item(key = "section_${section.title ?: "ungrouped_$index"}") {
                            GenreCardGrid(
                                title = section.title,
                                genres = section.genres.map { it.id to it.title },
                                onGenreClick = { navController.navigate(zemerPodcastGenreRoute(it)) },
                                iconOverride = ::podcastGenreIcon,
                                firstInList = index == 0,
                            )
                        }
                    }
                }
            }

            UiState.Error -> item(key = "error_state") {
                Column(
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
        }

        // Mirror of the top spacer: the last card row otherwise sits flush against the bottom
        // edge, unlike the matching breathing room under the top bar.
        item(key = "bottom_spacer") { Spacer(Modifier.height(GenreCatalogTopSpacing)) }
    }

    TopAppBar(
        title = { AppBarTitle(stringResource(R.string.genres)) },
        navigationIcon = { BackNavigationIcon(navController) },
        scrollBehavior = scrollBehavior,
        colors = zemerTopAppBarColors(),
    )
}

/**
 * The catalog's own loading skeleton, shaped like what loads: kind sections, each a title bar over a
 * two-column grid of 96dp card slabs (matches the music catalog's shimmer now that both group).
 */
@Composable
private fun PodcastGenreCatalogShimmer(modifier: Modifier = Modifier) {
    ShimmerHost(modifier) {
        repeat(2) { section ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                if (section > 0) Spacer(Modifier.height(12.dp))
                TextPlaceholder()
                Spacer(Modifier.height(12.dp))
                repeat(3) { row ->
                    if (row > 0) Spacer(Modifier.height(10.dp))
                    Row {
                        repeat(2) { col ->
                            if (col > 0) Spacer(Modifier.width(10.dp))
                            BoxPlaceholder(
                                Modifier.weight(1f).height(96.dp),
                                shape = RoundedCornerShape(20.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
