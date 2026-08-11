package com.jtech.zemer.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import com.jtech.zemer.search.GenreKind
import com.jtech.zemer.search.zemerGenreRoute
import com.jtech.zemer.ui.component.AppBarTitle
import com.jtech.zemer.ui.component.BackNavigationIcon
import com.jtech.zemer.ui.component.EmptyPlaceholder
import com.jtech.zemer.ui.component.GenreCardGrid
import com.jtech.zemer.ui.component.GenreCatalogTopSpacing
import com.jtech.zemer.ui.component.zemerTopAppBarColors
import com.jtech.zemer.ui.component.shimmer.BoxPlaceholder
import com.jtech.zemer.ui.component.shimmer.ShimmerHost
import com.jtech.zemer.ui.component.shimmer.TextPlaceholder
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
    }

    TopAppBar(
        title = { AppBarTitle(stringResource(R.string.genres)) },
        navigationIcon = { BackNavigationIcon(navController) },
        scrollBehavior = scrollBehavior,
        colors = zemerTopAppBarColors(),
    )
}

/**
 * The catalog's OWN loading skeleton, shaped like what actually loads: two sections, each a title
 * bar over a two-column grid of 96dp card slabs — never generic list rows (a skeleton that doesn't
 * match its content reads as a bait-and-switch).
 */
@Composable
private fun GenreCatalogShimmer(modifier: Modifier = Modifier) {
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

