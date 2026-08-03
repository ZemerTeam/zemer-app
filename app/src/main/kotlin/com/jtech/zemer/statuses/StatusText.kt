package com.jtech.zemer.statuses

import android.util.Patterns
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle

private val SCHEME_PREFIX = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://")

/**
 * Build an [AnnotatedString] from a status caption/body with every web URL turned into a clickable link
 * (accent-colored + underlined). Clicks are routed through [onLinkClick] - the caller passes the
 * deep-link-aware external opener (`Context.openStatusLink`), NOT the default UriHandler - so a link can
 * never open in an in-app webview. A URL written without a scheme (e.g. `example.com`) gets `https://`
 * when opened, while the visible text stays exactly as typed. With no URLs the whole string is appended
 * verbatim, so this is safe to use for every caption/body.
 */
fun linkifyStatusText(
    text: String,
    linkColor: Color,
    onLinkClick: (String) -> Unit,
): AnnotatedString = buildAnnotatedString {
    val matcher = Patterns.WEB_URL.matcher(text)
    var last = 0
    while (matcher.find()) {
        val start = matcher.start()
        val end = matcher.end()
        if (start > last) append(text.substring(last, start))
        val raw = text.substring(start, end)
        val href = if (SCHEME_PREFIX.containsMatchIn(raw)) raw else "https://$raw"
        val link = LinkAnnotation.Clickable(href) { onLinkClick(href) }
        // Style the link range manually (matches the app's existing withLink usage) so we don't depend on
        // a specific TextLinkStyles overload.
        withStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)) {
            withLink(link) { append(raw) }
        }
        last = end
    }
    if (last < text.length) append(text.substring(last))
}
