package com.jtech.zemer.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Box
import com.jtech.zemer.R

/**
 * The shared "copy text" icon button used across the status viewers (the live caption, the live pill row,
 * the saved viewer): an icon-only glyph on a themed circle. Icon-only because the copy glyph is
 * self-explanatory; the label stays as the accessibility description. The caller sizes it via [modifier]
 * (e.g. `Modifier.size(36.dp)` or `Modifier.height(h).aspectRatio(1f)`) so one definition covers every
 * placement.
 */
@Composable
fun StatusCopyButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.secondaryContainer,
    onContainer: Color = MaterialTheme.colorScheme.onSecondaryContainer,
) {
    Box(
        modifier
            .clip(CircleShape)
            .background(container.copy(alpha = 0.9f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.content_copy),
            contentDescription = stringResource(R.string.copy_text),
            tint = onContainer,
            modifier = Modifier.size(18.dp),
        )
    }
}
