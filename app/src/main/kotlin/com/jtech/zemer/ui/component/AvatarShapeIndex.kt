package com.jtech.zemer.ui.component

/**
 * Pure, deterministic index into the expressive avatar-shape palette (see [expressiveAvatarShape]):
 * a given [key] (artist id / handle) always maps to the same slot, so the same person keeps the same
 * scalloped silhouette on every screen. `floorMod` (not `%`) keeps the result in `0 until [count]`
 * even for the many strings whose `hashCode()` is negative; a null key falls to slot 0.
 *
 * Kept in its own Android-free file so it stays a plain JVM unit test (the sibling [expressiveAvatarShape]
 * pulls in Compose `MaterialShapes`, which can't load in a JVM test).
 */
internal fun avatarPolygonIndex(key: Any?, count: Int): Int =
    Math.floorMod(key?.hashCode() ?: 0, count)
