package com.jtech.zemer.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.jtech.zemer.utils.markdown.LiteMarkdown
import com.jtech.zemer.utils.markdown.MarkdownBlock
import com.jtech.zemer.utils.markdown.MarkdownInline

/**
 * Renders [LiteMarkdown] (release notes, commit messages) as themed text: headings, bullet and
 * numbered lists, bold / italic / inline code, and tappable links. Body text inherits
 * [LocalTextStyle], so a dialog's bodyMedium applies without a style argument.
 */
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    onLinkClick: ((String) -> Unit)? = null,
) {
    val uriHandler = LocalUriHandler.current
    val openLink: (String) -> Unit = onLinkClick ?: { url -> runCatching { uriHandler.openUri(url) }.getOrNull() ?: Unit }
    val linkColor = MaterialTheme.colorScheme.primary
    val codeBackground = MaterialTheme.colorScheme.surfaceContainerHighest
    val blocks = LiteMarkdown.parse(markdown)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Heading -> Text(
                    text = block.inlines.toAnnotated(linkColor, codeBackground, openLink),
                    style = if (block.level <= 2) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                is MarkdownBlock.Paragraph -> Text(block.inlines.toAnnotated(linkColor, codeBackground, openLink))
                is MarkdownBlock.ListItem -> Row {
                    Text(
                        text = block.ordinal?.let { "$it." } ?: "•",
                        modifier = Modifier.width(20.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(block.inlines.toAnnotated(linkColor, codeBackground, openLink))
                }
            }
        }
    }
}

private fun List<MarkdownInline>.toAnnotated(
    linkColor: Color,
    codeBackground: Color,
    onLinkClick: (String) -> Unit,
): AnnotatedString = buildAnnotatedString {
    forEach { inline ->
        when (inline) {
            is MarkdownInline.Text -> append(inline.text)
            is MarkdownInline.Bold -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(inline.text) }
            is MarkdownInline.Italic -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(inline.text) }
            is MarkdownInline.Code -> withStyle(
                SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground),
            ) { append(inline.text) }
            is MarkdownInline.Link -> withStyle(
                SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline),
            ) {
                withLink(LinkAnnotation.Clickable(inline.href) { onLinkClick(inline.href) }) { append(inline.text) }
            }
        }
    }
}
