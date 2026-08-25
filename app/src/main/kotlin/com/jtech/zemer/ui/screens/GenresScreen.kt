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
import com.jtech.zemer.search.GenreKind
import com.jtech.zemer.search.zemerGenreRoute
import com.jtech.zemer.ui.component.AppBarTitle
import com.jtech.zemer.ui.component.BackTopAppBar
import com.jtech.zemer.ui.component.EmptyPlaceholder
import com.jtech.zemer.ui.component.ErrorRetryState
import com.jtech.zemer.ui.component.GenreCardGrid
import com.jtech.zemer.ui.component.GenreCatalogShimmer
import com.jtech.zemer.ui.component.GenreCatalogTopSpacing
import com.jtech.zemer.viewmodels.ZemerGenreCatalogViewModel
import com.jtech.zemer.viewmodels.ZemerGenreCatalogViewModel.UiState

/**
 * The genre catalog (the home chips row's "See all"): every music genre as a BIG card in a
 * two-column grid, grouped Styles → Occasions, each in the server's most-populated-first order.
 * Non-music genres are dropped upstream ([com.jtech.zemer.search.genresByKind]) and never render
 * here. Tapping a card opens the genre detail. Deliberately count-free (a concrete
 * number reads as small; the catalog should read as complete). Sections render via the shared
 * [GenreCardGrid].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenresScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: ZemerGenreCatalogViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LazyColumn(
        contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
    ) {
        // Breathing room between the top bar and the first section title (owner ask; matches the
        // podcast catalog).
        item(key = "top_spacer") { Spacer(Modifier.height(GenreCatalogTopSpacing)) }

        when (val uiState = state) {
            UiState.Loading -> item(key = "loading_shimmer") {
                GenreCatalogShimmer()
            }

            is UiState.Loaded -> {
                if (uiState.groups.isEmpty()) {
                    item(key = "empty") {
                        EmptyPlaceholder(
                            icon = R.drawable.music_note,
                            text = stringResource(R.string.home_see_all_empty),
                        )
                    }
                } else {
                    // Fixed kind order (Styles, then Occasions) regardless of map iteration.
                    listOf(
                        GenreKind.STYLE to R.string.genre_kind_style,
                        GenreKind.OCCASION to R.string.genre_kind_occasion,
                    ).filter { (kind, _) -> uiState.groups[kind].orEmpty().isNotEmpty() }
                        .forEachIndexed { index, (kind, titleRes) ->
                            val genres = uiState.groups[kind].orEmpty()
                            item(key = "section_$kind") {
                                GenreCardGrid(
                                    title = stringResource(titleRes),
                                    genres = genres.map { it.id to it.title },
                                    onGenreClick = { navController.navigate(zemerGenreRoute(it)) },
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
