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
import com.jtech.zemer.search.HOME_GENRE_CHIPS_MAX
import com.jtech.zemer.search.ZemerGenreSummary
import com.jtech.zemer.search.ZemerPodcastGenreSummary
import com.jtech.zemer.search.homeGenreChips
import com.jtech.zemer.search.zemerGenreRoute
import com.jtech.zemer.search.zemerPodcastGenreRoute
import com.jtech.zemer.ui.component.GenreChip
import com.jtech.zemer.ui.component.podcastGenreIcon

/**
 * The reusable Home genre-chips strip: ONE row of hollow accent-outlined [GenreChip] pills — a single
 * row cannot fall out of alignment (the two-row pairing left ragged gaps and was reviewed out) — with a
 * MAGNETIC fling: inertial decay settling into a soft spring snap on the nearest chip, so a swipe feels
 * weighted instead of a hard stop. Decoupled from any genre SOURCE — [chips] is a `(slug, title)` list
 * and [onChipClick] gets the slug — so the music and podcast Home strips share ONE strip. Kept out of
 * HomeScreen so the giant gains ~nothing; visibility (empty-hides, the ShowHomeGenres pref for music) is
 * the caller's. No impression tracking: impressions are per-videoId by contract, and a chip has none.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeGenreChipsStrip(
    chips: List<Pair<String, String>>,
    onChipClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    // Resolves a slug → motif drawable; null = the music default ([GenreChip]'s slug lookup).
    iconOverride: ((String) -> Int)? = null,
) {
    val listState = rememberLazyListState()
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
        modifier = modifier.padding(vertical = 12.dp),
    ) {
        items(items = chips, key = { it.first }) { (slug, title) ->
            GenreChip(
                title = title,
                slug = slug,
                onClick = { onChipClick(slug) },
                iconOverride = iconOverride?.invoke(slug),
            )
        }
    }
}

/** The MUSIC Home genre strip (arrow → the music catalog). Trims the catalog to the top chips. */
@Composable
fun HomeGenresRow(
    genres: List<ZemerGenreSummary>,
    navController: NavController,
    modifier: Modifier = Modifier,
) {
    val chips = remember(genres) { homeGenreChips(genres).map { it.id to it.title } }
    HomeGenreChipsStrip(
        chips = chips,
        onChipClick = { navController.navigate(zemerGenreRoute(it)) },
        modifier = modifier,
    )
}

/** The PODCAST Home genre strip — the same strip, with the podcast catalog + routes. */
@Composable
fun HomePodcastGenresRow(
    genres: List<ZemerPodcastGenreSummary>,
    navController: NavController,
    modifier: Modifier = Modifier,
) {
    // The catalog arrives most-populated-first, so the top N are the chips (no kind-aware trim like music).
    val chips = remember(genres) { genres.take(HOME_GENRE_CHIPS_MAX).map { it.id to it.title } }
    HomeGenreChipsStrip(
        chips = chips,
        onChipClick = { navController.navigate(zemerPodcastGenreRoute(it)) },
        modifier = modifier,
        iconOverride = { podcastGenreIcon(it) },
    )
}
