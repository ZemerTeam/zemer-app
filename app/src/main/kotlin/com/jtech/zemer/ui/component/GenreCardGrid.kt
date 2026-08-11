package com.jtech.zemer.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * One genre-catalog section: an optional bold title over a two-column [GenreCard] grid. Shared by the
 * music catalog (kind titles from app strings) and the podcast catalog (server-owned kind titles; null
 * title = the ungrouped/flat section) so the two can't drift.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GenreCardGrid(
    genres: List<Pair<String, String>>, // slug to display title
    onGenreClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    iconOverride: ((String) -> Int)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            Spacer(Modifier.height(12.dp))
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            maxItemsInEachRow = 2,
        ) {
            genres.forEach { (slug, genreTitle) ->
                // Stable per-genre key so a list update keeps each genre's card (cached motif + running
                // weave) instead of reusing a slot for a different genre and re-inflating.
                key(slug) {
                    GenreCard(
                        title = genreTitle,
                        slug = slug,
                        onClick = { onGenreClick(slug) },
                        modifier = Modifier.weight(1f),
                        iconOverride = iconOverride?.invoke(slug),
                    )
                }
            }
            // An odd count leaves the last card alone on its row; without a weighted partner it
            // would stretch to the full width. The invisible spacer keeps it at exact cell width.
            if (genres.size % 2 == 1) {
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** The catalogs' shared gap between the top bar and the first section title (owner ask: roomy). */
val GenreCatalogTopSpacing = 24.dp
