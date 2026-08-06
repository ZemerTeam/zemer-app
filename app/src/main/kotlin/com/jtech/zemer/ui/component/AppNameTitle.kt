package com.jtech.zemer.ui.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.jtech.zemer.R

/**
 * The app-name brand title (splash, onboarding, login gate, About). ONE component so its color is
 * defined once: NEUTRAL (`onSurface`), matching every other screen title + the top bar chrome — the
 * accent is reserved for real content, never headings. Callers vary only the [style]/[fontWeight]/
 * [textAlign] for their context (a large splash display vs a headline). Reach for this instead of a
 * hand-rolled `Text(stringResource(R.string.app_name), …)`.
 */
@Composable
fun AppNameTitle(
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.headlineMedium,
    fontWeight: FontWeight = FontWeight.Bold,
    textAlign: TextAlign? = null,
) {
    Text(
        text = stringResource(R.string.app_name),
        style = style,
        fontWeight = fontWeight,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = textAlign,
        modifier = modifier,
    )
}
