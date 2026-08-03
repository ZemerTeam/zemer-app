package com.jtech.zemer.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jtech.zemer.R
import com.jtech.zemer.extensions.openStatusLink
import com.jtech.zemer.statuses.linkifyStatusText

private const val COLLAPSED_CAPTION_LINES = 3

/**
 * The status caption panel, WhatsApp-style, SHARED by the live and saved viewers. Collapsed it shows up to
 * [COLLAPSED_CAPTION_LINES] lines and, when the text overflows, a "Read more" toggle; expanded it shows the
 * full caption in a bounded scroll area with a "Read less" toggle below. The panel is a dark scrim (darker
 * when expanded) so the text stays readable over the media, links are clickable (opened externally unless
 * they are one of the app's deep links), and a copy button sits in the caption's own right space.
 * [reserveEnd] keeps that content clear of anything floating over the caption's right (e.g. the save FAB);
 * expanding freezes the caller's auto-advance via [onExpandedChange].
 */
@Composable
fun ExpandableStatusCaption(
    caption: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier,
    reserveEnd: Dp = 0.dp,
) {
    val context = LocalContext.current
    val linkColor = MaterialTheme.colorScheme.primary
    var overflows by remember(caption) { mutableStateOf(false) }
    val background = if (expanded) Color.Black.copy(alpha = 0.94f) else Color.Black.copy(alpha = 0.85f)
    val linkified = remember(caption, linkColor) {
        linkifyStatusText(caption, linkColor) { context.openStatusLink(it) }
    }
    Row(
        modifier
            .fillMaxWidth()
            .background(background)
            .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 16.dp + reserveEnd),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = linkified,
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = if (expanded) Int.MAX_VALUE else COLLAPSED_CAPTION_LINES,
                overflow = if (expanded) TextOverflow.Clip else TextOverflow.Ellipsis,
                onTextLayout = { if (!expanded) overflows = it.hasVisualOverflow },
                modifier = if (expanded) {
                    Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())
                } else {
                    Modifier
                },
            )
            if (expanded || overflows) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(if (expanded) R.string.read_less else R.string.read_more),
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { onExpandedChange(!expanded) },
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        StatusCopyButton(onClick = onCopy, modifier = Modifier.size(36.dp))
    }
}
