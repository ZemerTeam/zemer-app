package com.jtech.zemer.ui.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight

/**
 * The shared screen-title style for top app bars: `titleLarge`, bold — the same treatment the Home
 * top bar uses. Put every screen-level `TopAppBar` / [BackTopAppBar] title through this so titles
 * never drift back to the default (thinner) weight.
 */
@Composable
fun AppBarTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        modifier = modifier,
    )
}
