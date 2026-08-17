@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.jtech.zemer.ui.component

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Shape

/**
 * Gentle scalloped "cookie" morph shapes (Material 3 Expressive [MaterialShapes]) used to clip avatars.
 * Only the cookie family is used - its shallow scallops never crop a face the way the deep-concave shapes
 * (clover / sunny) would.
 */
private val AvatarPolygons = listOf(
    MaterialShapes.Cookie9Sided,
    MaterialShapes.Cookie12Sided,
    MaterialShapes.Cookie7Sided,
    MaterialShapes.Cookie6Sided,
)

/**
 * A stable expressive avatar shape derived from [key], so a given artist/person keeps the same scalloped
 * silhouette on every screen it appears on (the hash is deterministic). A null key falls to the first shape.
 */
@Composable
fun expressiveAvatarShape(key: Any?): Shape =
    AvatarPolygons[Math.floorMod(key?.hashCode() ?: 0, AvatarPolygons.size)].toShape()
