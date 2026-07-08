package com.metrolist.innertube.models

import kotlinx.serialization.Serializable

/**
 * YouTube Music's carrier for a song↔video counterpart pair inside a `next()`/watch-queue response. A
 * wrapper holds the item that would otherwise appear as a bare [PlaylistPanelVideoRenderer]
 * ([primaryRenderer]) plus its alternate rendition(s) ([counterpart]) — e.g. an audio-only song
 * (MUSIC_VIDEO_TYPE_ATV) whose counterpart is the official music video (MUSIC_VIDEO_TYPE_OMV).
 *
 * It appears only in authenticated contexts whose account has the "show video" preference; anonymous
 * requests return bare renderers. Before this model existed a wrapped row parsed as an all-null
 * [PlaylistPanelRenderer.Content] and was silently dropped from the queue — so parsing the primary here
 * also fixes that latent dropped-row bug, independent of counterpart discovery.
 */
@Serializable
data class PlaylistPanelVideoWrapperRenderer(
    val primaryRenderer: WrappedRenderer? = null,
    val counterpart: List<Counterpart>? = null,
) {
    @Serializable
    data class Counterpart(
        val counterpartRenderer: WrappedRenderer? = null,
    )

    /** Both `primaryRenderer` and `counterpartRenderer` wrap a single [PlaylistPanelVideoRenderer]. */
    @Serializable
    data class WrappedRenderer(
        val playlistPanelVideoRenderer: PlaylistPanelVideoRenderer? = null,
    )
}
