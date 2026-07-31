package com.jtech.zemer.ui.screens

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.snapping.SnapLayoutInfoProvider
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.snapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.jtech.zemer.search.ZemerGenreSummary
import com.jtech.zemer.search.homeGenreChips
import com.jtech.zemer.search.zemerGenreRoute
import com.jtech.zemer.ui.component.GenreChip

/**
 * The Home genre-chips strip (the title above it is the standard NavigationTitle emitted by
 * HomeScreen, arrow → the catalog): ONE row of hollow accent-outlined pills — a single row cannot
 * fall out of alignment (the two-row column pairing left ragged gaps and was reviewed out) — with a
 * MAGNETIC fling: inertial decay settling into a soft spring snap on the nearest chip, so a swipe
 * feels weighted instead of a hard stop. Kept out of HomeScreen so the giant gains ~nothing;
 * visibility (the ShowHomeGenres preference + empty-hides) is the caller's. No impression tracking:
 * impressions are per-videoId by contract (the server drops anything that isn't an 11-char
 * videoId), and a genre chip has no videoId to report.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeGenresRow(
    genres: List<ZemerGenreSummary>,
    navController: NavController,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val chips = remember(genres) { homeGenreChips(genres) }
    // Weighted snap: splines carry the fling's momentum, then a low-stiffness spring eases the
    // strip onto the nearest chip start — the "magnetic" settle.
    val decay = rememberSplineBasedDecay<Float>()
    val flingBehavior = remember(listState, decay) {
        snapFlingBehavior(
            snapLayoutInfoProvider = SnapLayoutInfoProvider(listState, SnapPosition.Start),
            decayAnimationSpec = decay,
            snapAnimationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessLow),
        )
    }
    LazyRow(
        state = listState,
        flingBehavior = flingBehavior,
        // Insets PLUS the standard 12dp start/end, so the first chip lines up with the section
        // title and the cards below (the grid rows get that inset from their items' own padding;
        // a bare chip has none).
        contentPadding = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal)
            .add(WindowInsets(left = 12.dp, right = 12.dp))
            .asPaddingValues(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        // Breathing room above and below the strip so it doesn't crowd the section titles.
        modifier = modifier.padding(vertical = 8.dp),
    ) {
        items(
            items = chips,
            key = { it.id },
        ) { genre ->
            GenreChip(
                title = genre.title,
                slug = genre.id,
                onClick = { navController.navigate(zemerGenreRoute(genre.id)) },
            )
        }
    }
}
