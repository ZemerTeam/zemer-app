package com.jtech.zemer.search

import android.content.Context
import com.jtech.zemer.constants.HideExplicitKey
import com.jtech.zemer.utils.ContentFilterState
import com.jtech.zemer.utils.dataStore
import com.jtech.zemer.utils.getSuspend

/**
 * The content-filter inputs a Zemer query needs, mapped from the app's existing filter state. The
 * server applies [allowFemale]/[blockVideos] (it has no chasid param — that flag isn't part of the
 * runtime [ContentFilterState]); [hideExplicit] is applied client-side to the mapped song/video lists.
 */
data class ZemerSearchOptions(
    val allowFemale: Boolean,
    val blockVideos: Boolean,
    val hideExplicit: Boolean,
)

/** Builds the options from the live content-filter state + the Hide-Explicit preference. */
suspend fun zemerSearchOptions(context: Context): ZemerSearchOptions {
    val filters = ContentFilterState.current
    return ZemerSearchOptions(
        allowFemale = filters.allowFemaleSingers,
        blockVideos = filters.blockVideos,
        hideExplicit = context.dataStore.getSuspend(HideExplicitKey, false),
    )
}
