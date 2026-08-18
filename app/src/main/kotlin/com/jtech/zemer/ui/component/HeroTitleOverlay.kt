@file:OptIn(ExperimentalFoundationApi::class)

package com.jtech.zemer.ui.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp

/**
 * The bottom title / subtitle overlay shared by the full-bleed carousel HEROES (Latest Releases +
 * Trending Videos): a bottom-anchored transparent->black gradient carrying the white [title] and a
 * 75%-white [subtitle], both single-line marquee, plus an optional [extraContent] column slot (Latest
 * Releases puts its library badges there). Extracted so the two heroes' overlays can't drift. Place it
 * inside the hero's artwork [BoxScope]; it self-aligns to the bottom.
 */
@Composable
fun BoxScope.HeroTitleOverlay(
    title: String,
    subtitle: String?,
    titleStyle: TextStyle = MaterialTheme.typography.labelLarge,
    subtitleStyle: TextStyle = MaterialTheme.typography.bodySmall,
    extraContent: @Composable ColumnScope.() -> Unit = {},
) {
    Column(
        modifier = Modifier
            .align(Alignment.BottomStart)
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f)))
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            text = title,
            color = Color.White,
            style = titleStyle,
            maxLines = 1,
            modifier = Modifier.basicMarquee(),
        )
        if (!subtitle.isNullOrEmpty()) {
            Text(
                text = subtitle,
                color = Color.White.copy(alpha = 0.75f),
                style = subtitleStyle,
                maxLines = 1,
                modifier = Modifier.basicMarquee(),
            )
        }
        extraContent()
    }
}
