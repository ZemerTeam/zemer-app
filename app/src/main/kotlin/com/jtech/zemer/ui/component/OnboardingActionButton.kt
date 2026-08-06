package com.jtech.zemer.ui.component

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The onboarding button family — THREE shared M3 buttons by emphasis, so every onboarding button looks
 * the same and matches the app's design language. Crucially NONE override the shape: they all take the
 * standard M3 button shape, which is what was inconsistent before (hand-rolled `RoundedCornerShape`s of
 * 8/9/10/12dp mixed with the tonal button's stadium). M3 gives shape, ripple, and disabled state free.
 *
 * - [OnboardingPrimaryButton] — the high-emphasis action (Continue / Get started): a filled [Button].
 * - [OnboardingActionButton] — a medium-emphasis action (Grant / Open settings / Create account / Skip):
 *   a neutral [FilledTonalButton] on `surfaceContainerHighest` — a tone above the cards it sits inside
 *   (`surfaceContainer`), so the pill never blends into its card yet still reads as neutral, not accent.
 * - [OnboardingTextButton] — the low-emphasis action (Back): a neutral [TextButton].
 */
@Composable
fun OnboardingActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Text(text = text)
    }
}

@Composable
fun OnboardingPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    Button(onClick = onClick, enabled = enabled && !loading, modifier = modifier) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        } else {
            Text(text = text)
        }
    }
}

@Composable
fun OnboardingTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
    ) {
        Text(text = text)
    }
}
