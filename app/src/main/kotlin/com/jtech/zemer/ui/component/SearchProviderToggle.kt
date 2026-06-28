package com.jtech.zemer.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.jtech.zemer.R
import com.jtech.zemer.search.SearchProvider

/**
 * The two-segment "pill" that switches the online search engine, shown on the right of the search bar.
 * The selected side is filled with the accent color; the other is an outline. Each segment is a single
 * focus target (the `clickable`), so it stays 100% D-pad navigable — a focused-but-unselected segment
 * shows the standard outline highlight (docs/ui/standards.md). Material 3 *standard*.
 */
@Composable
fun SearchProviderToggle(
    provider: SearchProvider,
    onProviderChange: (SearchProvider) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(percent = 50)
    Row(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProviderSegment(
            iconRes = R.drawable.ic_launcher_monochrome,
            label = stringResource(R.string.search_provider_zemer),
            selected = provider == SearchProvider.ZEMER,
            onClick = { onProviderChange(SearchProvider.ZEMER) },
        )
        ProviderSegment(
            iconRes = R.drawable.play,
            label = stringResource(R.string.search_provider_youtube_short),
            selected = provider == SearchProvider.YOUTUBE,
            onClick = { onProviderChange(SearchProvider.YOUTUBE) },
        )
    }
}

@Composable
private fun ProviderSegment(
    iconRes: Int,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(percent = 50)
    val background = when {
        selected -> MaterialTheme.colorScheme.primary
        focused -> MaterialTheme.colorScheme.surfaceVariant
        else -> Color.Transparent
    }
    val content =
        if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = Modifier
            .clip(shape)
            .background(background)
            .then(
                // Focus ring on BOTH states so the already-selected segment still shows D-pad focus;
                // contrast against the primary fill when selected.
                if (focused) {
                    Modifier.border(
                        1.5.dp,
                        if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.outline,
                        shape,
                    )
                } else {
                    Modifier
                },
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(15.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = label,
            color = content,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
        )
    }
}
