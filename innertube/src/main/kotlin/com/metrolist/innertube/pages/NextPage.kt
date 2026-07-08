package com.metrolist.innertube.pages

import com.metrolist.innertube.models.Album
import com.metrolist.innertube.models.Artist
import com.metrolist.innertube.models.BrowseEndpoint
import com.metrolist.innertube.models.PlaylistPanelRenderer
import com.metrolist.innertube.models.PlaylistPanelVideoRenderer
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.WatchEndpoint
import com.metrolist.innertube.models.WatchEndpoint.WatchEndpointMusicSupportedConfigs.WatchEndpointMusicConfig.Companion.MUSIC_VIDEO_TYPE_ATV
import com.metrolist.innertube.models.oddElements
import com.metrolist.innertube.models.splitBySeparator
import com.metrolist.innertube.utils.parseTime

data class NextResult(
    val title: String? = null,
    val items: List<SongItem>,
    val currentIndex: Int? = null,
    val lyricsEndpoint: BrowseEndpoint? = null,
    val relatedEndpoint: BrowseEndpoint? = null,
    val continuation: String?,
    val endpoint: WatchEndpoint, // current or continuation next endpoint
    // Authoritative song→video-counterpart map (song videoId → video-rendition videoId), collected from
    // any `playlistPanelVideoWrapperRenderer`s in the response. Empty for the common wrapper-less
    // response. Consumed by the video-mode counterpart cache; never affects `items`.
    val counterparts: Map<String, String> = emptyMap(),
)

object NextPage {
    /** A panel item's videoId (direct field, else the watch-endpoint fallback used by [fromPlaylistPanelVideoRenderer]). */
    fun videoIdOf(renderer: PlaylistPanelVideoRenderer): String? =
        renderer.videoId ?: renderer.navigationEndpoint.watchEndpoint?.videoId

    /** A panel item's music-video type (MUSIC_VIDEO_TYPE_ATV = audio song, _OMV/_UGC = a real video), or null. */
    fun musicVideoTypeOf(renderer: PlaylistPanelVideoRenderer): String? =
        renderer.navigationEndpoint.watchEndpoint
            ?.watchEndpointMusicSupportedConfigs
            ?.watchEndpointMusicConfig
            ?.musicVideoType

    /**
     * The [PlaylistPanelVideoRenderer] a panel content ultimately holds, whether delivered directly or
     * wrapped in a `playlistPanelVideoWrapperRenderer` — so wrapped rows feed the queue like any other
     * (fixes the silently-dropped wrapped row).
     */
    fun primaryRendererOf(content: PlaylistPanelRenderer.Content): PlaylistPanelVideoRenderer? =
        content.playlistPanelVideoRenderer
            ?: content.playlistPanelVideoWrapperRenderer?.primaryRenderer?.playlistPanelVideoRenderer

    /**
     * song videoId → video-rendition videoId, for every wrapper whose counterpart is a real video
     * (musicVideoType != ATV). Pure + order-preserving; empty when the response carries no wrappers.
     * A wrapper whose primary is itself the video (counterpart = the ATV song) contributes nothing — a
     * video queue item is its own SELF rendition, it needs no counterpart mapping.
     */
    fun counterpartsFrom(contents: List<PlaylistPanelRenderer.Content>): Map<String, String> {
        val map = LinkedHashMap<String, String>()
        for (content in contents) {
            val wrapper = content.playlistPanelVideoWrapperRenderer ?: continue
            val primary = wrapper.primaryRenderer?.playlistPanelVideoRenderer ?: continue
            val primaryId = videoIdOf(primary) ?: continue
            val counterpartId = wrapper.counterpart.orEmpty()
                .mapNotNull { it.counterpartRenderer?.playlistPanelVideoRenderer }
                .firstOrNull { musicVideoTypeOf(it) != MUSIC_VIDEO_TYPE_ATV }
                ?.let(::videoIdOf) ?: continue
            map[primaryId] = counterpartId
        }
        return map
    }

    fun fromPlaylistPanelVideoRenderer(renderer: PlaylistPanelVideoRenderer): SongItem? {
        val longByLineRuns = renderer.longBylineText?.runs?.splitBySeparator() ?: return null
        return SongItem(
            id = renderer.videoId
                ?: renderer.navigationEndpoint.watchEndpoint?.videoId
                ?: return null,
            title =
                renderer.title
                    ?.runs
                    ?.firstOrNull()
                    ?.text ?: return null,
            artists =
                longByLineRuns.firstOrNull()?.oddElements()?.map {
                    Artist(
                        name = it.text,
                        id = it.navigationEndpoint?.browseEndpoint?.browseId,
                    )
                } ?: return null,
            album =
                longByLineRuns
                    .getOrNull(1)
                    ?.firstOrNull()
                    ?.takeIf {
                        it.navigationEndpoint?.browseEndpoint != null
                    }?.let {
                        Album(
                            name = it.text,
                            id = it.navigationEndpoint?.browseEndpoint?.browseId!!,
                        )
                    },
            duration =
                renderer.lengthText
                    ?.runs
                    ?.firstOrNull()
                    ?.text
                    ?.parseTime() ?: return null,
            thumbnail =
                renderer.thumbnail.thumbnails
                    .lastOrNull()
                    ?.url ?: return null,
            explicit =
                renderer.badges?.find {
                    it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
                } != null,
            libraryAddToken = PageHelper.extractFeedbackToken(renderer.menu?.menuRenderer?.items?.find {
                it.toggleMenuServiceItemRenderer?.defaultIcon?.iconType?.startsWith("LIBRARY_") == true
            }?.toggleMenuServiceItemRenderer, "LIBRARY_ADD"),
            libraryRemoveToken = PageHelper.extractFeedbackToken(renderer.menu?.menuRenderer?.items?.find {
                it.toggleMenuServiceItemRenderer?.defaultIcon?.iconType?.startsWith("LIBRARY_") == true
            }?.toggleMenuServiceItemRenderer, "LIBRARY_SAVED")
        )
    }
}
