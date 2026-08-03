package com.jtech.zemer.ui.utils

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.abs

/**
 * The shared 3D "cube" rotation for a status viewer's HorizontalPager face, used by BOTH the live and the
 * saved viewers so the swipe-between-creators animation is identical. [pageOffset] is this page's signed
 * distance from the settled page (`(currentPage - page) + currentPageOffsetFraction`); the face pivots on
 * its leading edge and rotates up to 90 degrees so adjacent pages read as faces of a cube.
 */
fun Modifier.cubeFace(pageOffset: Float): Modifier = graphicsLayer {
    val off = pageOffset.coerceIn(-1f, 1f)
    cameraDistance = 20f * density
    transformOrigin = TransformOrigin(if (off < 0f) 0f else 1f, 0.5f)
    rotationY = (if (off < 0f) 90f else -90f) * abs(off)
}
