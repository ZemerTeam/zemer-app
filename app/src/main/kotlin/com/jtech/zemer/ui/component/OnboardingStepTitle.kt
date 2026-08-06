package com.jtech.zemer.ui.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign

/**
 * The title of an onboarding step / dialog. ONE component so every onboarding heading matches the rest
 * of the app: bold, NEUTRAL (`onSurface`) — never the pink accent (accent is reserved for content and
 * selected/interactive state, not headings). Callers vary only the [style] (a step gets `headlineSmall`,
 * a dialog a smaller `title*`) and [textAlign] (centered step vs a left dialog title).
 */
@Composable
fun OnboardingStepTitle(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.headlineSmall,
    textAlign: TextAlign? = TextAlign.Center,
) {
    Text(
        text = text,
        style = style,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = textAlign,
        modifier = modifier,
    )
}
