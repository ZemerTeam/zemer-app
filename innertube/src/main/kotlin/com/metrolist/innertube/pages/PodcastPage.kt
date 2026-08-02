package com.metrolist.innertube.pages

import com.metrolist.innertube.models.EpisodeItem
import com.metrolist.innertube.models.PodcastItem

/**
 * A podcast SHOW page: the show header, its episodes, and a paging cursor. Populated by the Zemer
 * server mapper (`ZemerResultMapper.toPodcastPage`) — the old InnerTube renderer parsers were removed
 * when podcast discovery moved to search.zemer.io (playback stays InnerTube, by videoId).
 */
data class PodcastPage(
    val podcast: PodcastItem,
    val episodes: List<EpisodeItem>,
    val continuation: String?,
)
