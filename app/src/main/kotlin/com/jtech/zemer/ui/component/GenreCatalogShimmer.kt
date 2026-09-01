package com.jtech.zemer.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jtech.zemer.ui.component.shimmer.BoxPlaceholder
import com.jtech.zemer.ui.component.shimmer.ShimmerHost
import com.jtech.zemer.ui.component.shimmer.TextPlaceholder

/**
 * The genre-catalog loading skeleton, shaped like what actually loads: two sections, each a title bar
 * over a two-column grid of 96dp card slabs — never generic list rows (a skeleton that doesn't match
 * its content reads as a bait-and-switch). Shared by the music ([GenresScreen]) and podcast
 * ([PodcastGenresScreen]) catalogs, which grid the same way.
 */
@Composable
fun GenreCatalogShimmer(modifier: Modifier = Modifier) {
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
