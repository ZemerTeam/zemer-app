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
    // KidZone navigation context: true only for requests made from inside the KidZone tab (the kid
    // podcasts grid and every drill-in from it), restricting the server response to kid-flagged
    // content. Never derived from the content-filter state - it is a per-screen navigation fact.
    val kidZone: Boolean = false,
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
