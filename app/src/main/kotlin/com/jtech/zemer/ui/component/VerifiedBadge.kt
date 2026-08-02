package com.jtech.zemer.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The verified check badge shown on a JewishStatus creator (an accent circle with a check). One
 * component so the story-circle and story-header copies can't drift.
 */
@Composable
fun VerifiedBadge(
    modifier: Modifier = Modifier,
    size: Dp = 16.dp,
) {
    val colorScheme = MaterialTheme.colorScheme
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(colorScheme.primary),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "✓",
            color = colorScheme.onPrimary,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
        )
    }
}
