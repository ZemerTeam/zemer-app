package com.metrolist.innertube.models

import kotlinx.serialization.Serializable

@Serializable
data class Tabs(
    val tabs: List<Tab>,
) {
    @Serializable
    data class Tab(
        val tabRenderer: TabRenderer,
    ) {
        @Serializable
        data class TabRenderer(
            val title: String?,
            val content: Content?,
            val endpoint: NavigationEndpoint?,
        ) {
            @Serializable
            data class Content(
                val sectionListRenderer: SectionListRenderer?,
                val musicQueueRenderer: MusicQueueRenderer?,
            )
        }
    }
}

/**
 * The first tab's browse endpoint matching [predicate]. Watch-next tabs are resolved by page type
 * (MUSIC_PAGE_TYPE_TRACK_LYRICS / MUSIC_PAGE_TYPE_TRACK_RELATED) rather than a fixed index, so an
 * inserted tab such as Comments can no longer shift Related off its slot.
 */
internal fun List<Tabs.Tab>.browseEndpointMatching(predicate: (BrowseEndpoint) -> Boolean): BrowseEndpoint? =
    firstNotNullOfOrNull { it.tabRenderer.endpoint?.browseEndpoint?.takeIf(predicate) }
