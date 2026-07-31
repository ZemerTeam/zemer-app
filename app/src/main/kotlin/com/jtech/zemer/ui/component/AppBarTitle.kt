package com.jtech.zemer.ui.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow

/**
 * The shared screen-title style for top app bars: `titleLarge`, bold, single line with ellipsis — the
 * same treatment the Home top bar uses. Put every screen-level `TopAppBar` / [BackTopAppBar] title
 * through this so titles never drift back to the default (thinner) weight. Dynamic collection names
 * (album / artist / playlist) that need scrolling can pass `Modifier.basicMarquee()` via [modifier];
 * [maxLines] is overridable for the rare title that must wrap.
 */
@Composable
fun AppBarTitle(
    text: String,
    modifier: Modifier = Modifier,
    maxLines: Int = 1,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}
