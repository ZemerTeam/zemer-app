package com.jtech.zemer.utils

/**
 * Removes one item key from a comma-separated bottom-navigation set, preserving the order of the
 * survivors. Blank/whitespace entries are dropped. If removing [key] would leave the bar empty, the
 * [fallback] set is returned instead so the navigation bar can never render with zero destinations.
 *
 * Pure and Android-free so the min-one-item guard is unit-testable (see BottomNavItemsTest).
 */
internal fun removeBottomNavItem(
    csv: String,
    key: String,
    fallback: String,
): String {
    val survivors = csv.split(",")
        .map { it.trim() }
        .filter { it.isNotEmpty() && it != key }
    return if (survivors.isEmpty()) fallback else survivors.joinToString(",")
}
