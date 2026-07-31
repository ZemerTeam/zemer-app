package com.jtech.zemer.ui.component.shimmer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * The base shimmer box (owner ask: skeletons are "just boxes ... imported, never redrawn"): a
 * single rounded `onSurface` slab whose size is the caller's ([modifier]) and whose corner is
 * [shape]. [ButtonPlaceholder]/[GridItemPlaceHolder] are specializations of this; screen skeletons
 * that used to hand-roll `Spacer().clip().background(onSurface)` call this instead, so the shimmer
 * fill lives in ONE place. Always render inside a [ShimmerHost] (it supplies the sweep).
 */
@Composable
fun BoxPlaceholder(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(8.dp),
) {
    Spacer(
        modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.onSurface),
    )
}
