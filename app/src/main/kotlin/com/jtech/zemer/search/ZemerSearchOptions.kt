package com.jtech.zemer.search

import android.content.Context
import com.jtech.zemer.utils.ContentFilterState

/**
 * The content-filter inputs a Zemer query needs, mapped from the app's existing filter state. The
 * server applies [allowFemale]/[blockVideos] (it has no chasid param — that flag isn't part of the
 * runtime [ContentFilterState]).
 */
data class ZemerSearchOptions(
    val allowFemale: Boolean,
    val blockVideos: Boolean,
)

/** Builds the options from the live content-filter state. */
suspend fun zemerSearchOptions(context: Context): ZemerSearchOptions {
    val filters = ContentFilterState.current
    return ZemerSearchOptions(
        allowFemale = filters.allowFemaleSingers,
        // Always fetch videos from the server. "Block videos" no longer hides them — blocked videos are
        // shown as audio-only "video song" rows (every video plays audio-first; the Song/Video toggle is
        // the only watch path and is gated on BlockVideosKey). Sending blockVideos=1 here would drop the
        // videos category server-side and there would be nothing to render as audio.
        blockVideos = false,
    )
}
