package com.jtech.zemer.ui.screens

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.jtech.zemer.LocalPlayerAwareWindowInsets
import com.jtech.zemer.R
import com.jtech.zemer.search.zemerPodcastGenreRoute
import com.jtech.zemer.ui.component.AppBarTitle
import com.jtech.zemer.ui.component.BackTopAppBar
import com.jtech.zemer.ui.component.EmptyPlaceholder
import com.jtech.zemer.ui.component.ErrorRetryState
import com.jtech.zemer.ui.component.GenreCardGrid
import com.jtech.zemer.ui.component.GenreCatalogShimmer
import com.jtech.zemer.ui.component.GenreCatalogTopSpacing
import com.jtech.zemer.ui.component.podcastGenreIcon
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
                GenreCatalogShimmer()
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
                ErrorRetryState(onRetry = viewModel::load)
            }
        }

        // Mirror of the top spacer: the last card row otherwise sits flush against the bottom
        // edge, unlike the matching breathing room under the top bar.
        item(key = "bottom_spacer") { Spacer(Modifier.height(GenreCatalogTopSpacing)) }
    }

    BackTopAppBar(
        title = { AppBarTitle(stringResource(R.string.genres)) },
        navController = navController,
        scrollBehavior = scrollBehavior,
    )
}
