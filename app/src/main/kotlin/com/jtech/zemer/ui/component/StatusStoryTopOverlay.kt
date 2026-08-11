package com.jtech.zemer.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.jtech.zemer.ui.theme.HeaderFontFamily

/**
 * The shared WhatsApp/Stories top overlay: segment progress bars at the very top, then a compact creator
 * header (back button, avatar, name, timestamp), all forced white over a fade-to-transparent gradient —
 * the WhatsApp look (#394): the media runs full-bleed to the top with the header floating over it, no
 * opaque band with a hard bottom edge. Used by BOTH the live story viewer and the saved-status viewer
 * so they present identically. There is deliberately NO
 * "Music Status" app-bar title row - it only stole vertical space from the (now full-bleed) media; the
 * creator avatar/name identifies the content the stories way. [currentSegment] is the active segment
 * (0-based) and [progress] fills it; earlier segments are full, later ones empty.
 */
// The legibility belt over bright media where the fade has already thinned: a soft dark drop shadow
// under the white name/date text (what WhatsApp does), so the header reads without an opaque band.
private val statusTextShadow = Shadow(
    color = Color.Black.copy(alpha = 0.65f),
    offset = Offset(0f, 1f),
    blurRadius = 6f,
)

@Composable
fun StatusStoryTopOverlay(
    navController: NavController,
    avatarUrl: String?,
    creatorName: String,
    subtitle: String?,
    segmentCount: Int,
    currentSegment: Int,
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    // A top-down fade, not a flat band: dark enough at the very top for the segment bars + white text,
    // dissolving into the media below (the extra bottom padding is the visible fade tail). The gradient
    // paints through the status-bar strip too (background BEFORE the insets padding — keep that order).
    // The gradient alone is not enough over bright media, so the name/date also carry a drop shadow
    // (the WhatsApp trick) — see [statusTextShadow].
    val scrim = Brush.verticalGradient(
        colors = listOf(
            colorScheme.scrim.copy(alpha = 0.8f),
            colorScheme.scrim.copy(alpha = 0.5f),
            Color.Transparent,
        ),
    )
    val context = LocalContext.current

    Column(
        modifier
            .fillMaxWidth()
            .background(scrim)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 28.dp),
    ) {
        // Segment progress bars at the very top (stories convention), the active one filling with `progress`.
        Row(Modifier.fillMaxWidth()) {
            for (i in 0 until segmentCount.coerceAtLeast(1)) {
                val fill = when {
                    i < currentSegment -> 1f
                    i == currentSegment -> progress.coerceIn(0f, 1f)
                    else -> 0f
                }
                Box(
                    Modifier
                        .weight(1f)
                        .height(2.dp)
                        .padding(horizontal = 1.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(Color.White.copy(alpha = 0.35f)),
                ) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(fill).background(Color.White))
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // Compact header row: back + avatar + name/timestamp, forced white for legibility over the media.
        CompositionLocalProvider(LocalContentColor provides Color.White) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                BackNavigationIcon(navController = navController)

                Spacer(Modifier.width(4.dp))

                AsyncImage(
                    model = ImageRequest.Builder(context).data(avatarUrl).crossfade(true).build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(colorScheme.surfaceVariant),
                )

                Spacer(Modifier.width(10.dp))

                Column(Modifier.weight(1f)) {
                    Text(
                        text = creatorName,
                        color = Color.White,
                        style = MaterialTheme.typography.titleSmall.copy(shadow = statusTextShadow),
                        fontFamily = HeaderFontFamily,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (!subtitle.isNullOrEmpty()) {
                        Text(
                            text = subtitle,
                            color = Color.White.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.labelSmall.copy(shadow = statusTextShadow),
                        )
                    }
                }
            }
        }
    }
}
