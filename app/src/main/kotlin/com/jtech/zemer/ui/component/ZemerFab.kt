package com.jtech.zemer.ui.component

import androidx.annotation.DrawableRes
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource

/**
 * The app's single floating-action-button look: a themed [FloatingActionButton] holding one drawable.
 * Colors default to the primary-container pair so every FAB matches; callers pass a different icon
 * (and, when needed, alternate theme colors) rather than re-rolling a `FloatingActionButton`.
 * [RecognizeMusicFab] and the story viewer's save FAB both go through here.
 */
@Composable
fun ZemerFab(
    @DrawableRes icon: Int,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
) {
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        containerColor = containerColor,
        contentColor = contentColor,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = contentDescription,
        )
    }
}
