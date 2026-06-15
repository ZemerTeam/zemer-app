package com.jtech.zemer.widget

/**
 * Pure widget layout decisions, kept separate from the Glance/Android [MusicWidget] class so they
 * can be unit tested without an Android runtime.
 */
internal object WidgetLayout {

    /** Below this height the seek row + its time labels don't fit alongside the control row. */
    const val COMPACT_SEEK_THRESHOLD_DP = 72f

    /** Whether the seek row fits at the given widget [heightDp]. */
    fun showSeekRow(heightDp: Float): Boolean = heightDp >= COMPACT_SEEK_THRESHOLD_DP
}
