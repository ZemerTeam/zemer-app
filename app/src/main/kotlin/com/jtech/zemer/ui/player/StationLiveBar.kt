package com.jtech.zemer.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import com.jtech.zemer.R

/**
 * The station replacement for the seek slider (handoff §5: no scrubbing — a broadcast has no
 * transport). A LIVE badge plus a read-only progress line through the current slot; progress is
 * informational only and never accepts input.
 */
@Composable
fun StationLiveBar(
    position: Long,
    duration: Long,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 14.dp),
    ) {
        StationLiveBadge(accentColor = accentColor)
        LinearProgressIndicator(
            progress = {
                if (duration == C.TIME_UNSET || duration <= 0L) 0f
                else (position.toFloat() / duration).coerceIn(0f, 1f)
            },
            color = accentColor,
            trackColor = accentColor.copy(alpha = 0.24f),
            drawStopIndicator = {},
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp),
        )
    }
}

/**
 * The shared LIVE chip — the full player's [StationLiveBar] and the mini players all render this
 * one badge, so the broadcast treatment can never drift between surfaces.
 */
@Composable
fun StationLiveBadge(
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    Text(
        text = stringResource(R.string.station_live_badge),
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
        color = accentColor,
        modifier = modifier
            .background(accentColor.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}
