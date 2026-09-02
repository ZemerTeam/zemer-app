package com.jtech.zemer.ui.component.lyrics

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.jtech.zemer.R
import com.jtech.zemer.models.MediaMetadata

/** "Lyrics from <provider> · synced" — provenance is part of the UI, not hidden. */
@Composable
fun LyricsSourceHeader(provider: String?, synced: Boolean, color: Color, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.lyrics_from, provider ?: stringResource(R.string.unknown)) + " · " +
            stringResource(if (synced) R.string.lyrics_synced else R.string.lyrics_plain),
        style = MaterialTheme.typography.labelMedium,
        color = color.copy(alpha = 0.6f),
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
    )
}

/** Art, title, artist and a trailing tonal "more" pill — the compact identity row under the lyrics. */
@Composable
fun LyricsNowPlayingBar(mediaMetadata: MediaMetadata, textColor: Color, onMoreClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(model = mediaMetadata.thumbnailUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(mediaMetadata.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = textColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(mediaMetadata.artists.joinToString { it.name }, style = MaterialTheme.typography.bodyMedium, color = textColor.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(12.dp))
        FilledTonalIconButton(onClick = onMoreClick, shape = RoundedCornerShape(16.dp), colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = textColor, contentColor = MaterialTheme.colorScheme.surface)) {
            Icon(painterResource(R.drawable.more_horiz), contentDescription = stringResource(R.string.more_options))
        }
    }
}
