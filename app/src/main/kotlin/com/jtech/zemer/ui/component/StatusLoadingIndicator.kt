package com.jtech.zemer.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade

/**
 * The status viewers' shared loading state, used by BOTH the live and saved viewers so it looks identical
 * everywhere. When [avatarUrl] is given it shows the creator's profile picture with a Material 3 progress
 * ring around it (rounded cap + subtle track), so a loading creator is recognisable; otherwise (creator
 * not yet known) it falls back to a bare centered spinner. Forced white for legibility over the media -
 * pass a background/size via [modifier] (e.g. `Modifier.fillMaxSize().background(Color.Black)`).
 */
@Composable
fun StatusLoadingIndicator(
    modifier: Modifier = Modifier,
    avatarUrl: String? = null,
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        if (avatarUrl != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(avatarUrl).crossfade(true).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
            CircularProgressIndicator(
                modifier = Modifier.size(88.dp),
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.25f),
                strokeCap = StrokeCap.Round,
            )
        } else {
            CircularProgressIndicator(
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.25f),
                strokeCap = StrokeCap.Round,
            )
        }
    }
}
