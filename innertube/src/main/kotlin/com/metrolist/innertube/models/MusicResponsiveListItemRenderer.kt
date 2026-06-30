@file:OptIn(ExperimentalSerializationApi::class)

package com.metrolist.innertube.models

import com.metrolist.innertube.models.BrowseEndpoint.BrowseEndpointContextSupportedConfigs.BrowseEndpointContextMusicConfig.Companion.MUSIC_PAGE_TYPE_ALBUM
import com.metrolist.innertube.models.BrowseEndpoint.BrowseEndpointContextSupportedConfigs.BrowseEndpointContextMusicConfig.Companion.MUSIC_PAGE_TYPE_ARTIST
import com.metrolist.innertube.models.BrowseEndpoint.BrowseEndpointContextSupportedConfigs.BrowseEndpointContextMusicConfig.Companion.MUSIC_PAGE_TYPE_AUDIOBOOK
import com.metrolist.innertube.models.BrowseEndpoint.BrowseEndpointContextSupportedConfigs.BrowseEndpointContextMusicConfig.Companion.MUSIC_PAGE_TYPE_LIBRARY_ARTIST
import com.metrolist.innertube.models.BrowseEndpoint.BrowseEndpointContextSupportedConfigs.BrowseEndpointContextMusicConfig.Companion.MUSIC_PAGE_TYPE_PLAYLIST
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

/**
 * Typical list item
 * Used in [MusicCarouselShelfRenderer], [MusicShelfRenderer]
 * Appears in quick picks, search results, table items, etc.
 */
@Serializable
data class MusicResponsiveListItemRenderer(
    val badges: List<Badges>?,
    val fixedColumns: List<FlexColumn>?,
    val flexColumns: List<FlexColumn>,
    val thumbnail: ThumbnailRenderer?,
    val menu: Menu?,
    val playlistItemData: PlaylistItemData?,
    val overlay: Overlay?,
    val navigationEndpoint: NavigationEndpoint?,
) {
    val isSong: Boolean
        get() = navigationEndpoint == null
            || navigationEndpoint.watchEndpoint != null
            || navigationEndpoint.watchPlaylistEndpoint != null
            || overlay?.musicItemThumbnailOverlayRenderer
                ?.content?.musicPlayButtonRenderer
                ?.playNavigationEndpoint?.watchEndpoint != null
    val isPlaylist: Boolean
        get() = navigationEndpoint?.browseEndpoint?.browseEndpointContextSupportedConfigs?.browseEndpointContextMusicConfig?.pageType == MUSIC_PAGE_TYPE_PLAYLIST
    val isAlbum: Boolean
        get() = navigationEndpoint?.browseEndpoint?.browseEndpointContextSupportedConfigs?.browseEndpointContextMusicConfig?.pageType == MUSIC_PAGE_TYPE_ALBUM ||
                navigationEndpoint?.browseEndpoint?.browseEndpointContextSupportedConfigs?.browseEndpointContextMusicConfig?.pageType == MUSIC_PAGE_TYPE_AUDIOBOOK
    val isArtist: Boolean
        get() = navigationEndpoint?.browseEndpoint?.browseEndpointContextSupportedConfigs?.browseEndpointContextMusicConfig?.pageType == MUSIC_PAGE_TYPE_ARTIST
                || navigationEndpoint?.browseEndpoint?.browseEndpointContextSupportedConfigs?.browseEndpointContextMusicConfig?.pageType == MUSIC_PAGE_TYPE_LIBRARY_ARTIST

    val videoId: String?
        get() = playlistItemData?.videoId
            ?: flexColumns.firstOrNull()
                ?.musicResponsiveListItemFlexColumnRenderer
                ?.text?.runs?.firstOrNull()
                ?.navigationEndpoint?.watchEndpoint?.videoId
            ?: overlay?.musicItemThumbnailOverlayRenderer
                ?.content?.musicPlayButtonRenderer
                ?.playNavigationEndpoint?.watchEndpoint?.videoId

    val playlistSetVideoId: String?
        get() = playlistItemData?.playlistSetVideoId
            ?: overlay?.musicItemThumbnailOverlayRenderer
                ?.content?.musicPlayButtonRenderer
                ?.playNavigationEndpoint?.watchEndpoint?.playlistSetVideoId

    /** YouTube's song-vs-video signal for this item, read from whichever watch endpoint is present. */
    val musicVideoType: String?
        get() = overlay?.musicItemThumbnailOverlayRenderer
            ?.content?.musicPlayButtonRenderer
            ?.playNavigationEndpoint?.watchEndpoint
            ?.watchEndpointMusicSupportedConfigs?.watchEndpointMusicConfig?.musicVideoType
            ?: flexColumns.firstOrNull()
                ?.musicResponsiveListItemFlexColumnRenderer
                ?.text?.runs?.firstOrNull()
                ?.navigationEndpoint?.watchEndpoint
                ?.watchEndpointMusicSupportedConfigs?.watchEndpointMusicConfig?.musicVideoType

    /**
     * True when YouTube classifies this item as a video (any type other than the audio track
     * MUSIC_VIDEO_TYPE_ATV). An absent/null type is treated as a song (audio), never a video — so the
     * flag only ever turns ON for an item YouTube positively marks as a video.
     */
    val isVideo: Boolean
        get() = musicVideoType?.let { it != "MUSIC_VIDEO_TYPE_ATV" } ?: false

    @Serializable
    data class FlexColumn(
        @JsonNames("musicResponsiveListItemFixedColumnRenderer")
        val musicResponsiveListItemFlexColumnRenderer: MusicResponsiveListItemFlexColumnRenderer,
    ) {
        @Serializable
        data class MusicResponsiveListItemFlexColumnRenderer(
            val text: Runs?,
        )
    }

    @Serializable
    data class PlaylistItemData(
        val playlistSetVideoId: String?,
        val videoId: String,
    )

    @Serializable
    data class Overlay(
        val musicItemThumbnailOverlayRenderer: MusicItemThumbnailOverlayRenderer,
    ) {
        @Serializable
        data class MusicItemThumbnailOverlayRenderer(
            val content: Content,
        ) {
            @Serializable
            data class Content(
                val musicPlayButtonRenderer: MusicPlayButtonRenderer,
            ) {
                @Serializable
                data class MusicPlayButtonRenderer(
                    val playNavigationEndpoint: NavigationEndpoint?,
                )
            }
        }
    }
}
