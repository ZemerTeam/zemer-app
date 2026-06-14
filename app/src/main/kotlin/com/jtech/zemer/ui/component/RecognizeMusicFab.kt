package com.jtech.zemer.ui.component

import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.jtech.zemer.R

/**
 * Floating action button that opens the "Recognize music" screen. Shown above the bottom navigation
 * bar on main screens when enabled (the `recognizeMusicFab` preference, default on). Kept as its own
 * component so `MainActivity` only wires placement/visibility.
 */
@Composable
fun RecognizeMusicFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Icon(
            painter = painterResource(R.drawable.mic),
            contentDescription = stringResource(R.string.recognize_music),
        )
    }
}
