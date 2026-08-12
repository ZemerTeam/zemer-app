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
    color = Color.Black.copy(alpha = 0.9f),
    offset = Offset(0f, 1f),
    blurRadius = 10f,
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
    // A single LINEAR fade from dark at the top to transparent at the bottom. Linear = one constant
    // slope = no slope change, which is what caused the visible "split line" (Mach banding appears
    // where a gradient's slope changes). Because it never kinks, the tail can be short, so the scrim
    // stays contained around the header instead of reaching far down the screen. Dark enough at the top
    // (behind the status bar + segment bars + name) with the name/date drop shadow ([statusTextShadow])
    // carrying the rest. Background BEFORE the insets padding so the scrim paints through the status-bar
    // strip too — keep that order.
    val scrim = Brush.verticalGradient(
        listOf(
            colorScheme.scrim.copy(alpha = 0.8f),
            Color.Transparent,
        ),
    )
    val context = LocalContext.current

    Column(
        modifier
            .fillMaxWidth()
            .background(scrim)
            .windowInsetsPadding(WindowInsets.statusBars)
            // Small tail: the linear fade needs no room to hide a slope change, so the scrim ends just
            // below the date instead of reaching far down the screen.
            .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 12.dp),
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
                            // Solid white (not dimmed) so the date reads over bright media too — the
                            // shadow, not a dark scrim, carries legibility (the WhatsApp approach). The
                            // smaller labelSmall size keeps it visually secondary to the name.
                            text = subtitle,
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall.copy(shadow = statusTextShadow),
                        )
                    }
                }
            }
        }
    }
}
