package com.jtech.zemer.ui.screens.search

import com.jtech.zemer.search.ZEMER_FILTER_EPISODE
import com.jtech.zemer.search.ZEMER_FILTER_PODCAST
import com.metrolist.innertube.YouTube.SearchFilter
import com.metrolist.innertube.models.EpisodeItem
import com.metrolist.innertube.models.PodcastItem
import com.metrolist.innertube.models.YTItem

/**
 * Whether a selected search chip may render under the current content flags. Blocking podcasts
 * hides the Podcasts/Episodes chips reactively, but a retained results screen keeps its selected
 * filter (and the ViewModel keeps the fetched page) — the screen resets a disallowed filter and
 * refuses to render its page, so blocked content can never survive a Settings round-trip.
 */
fun searchFilterAllowed(filter: SearchFilter?, blockPodcasts: Boolean): Boolean =
    !(blockPodcasts && (filter == ZEMER_FILTER_PODCAST || filter == ZEMER_FILTER_EPISODE))

/**
 * The Block Podcasts result filter: podcast shows and episodes are dropped from every search
 * result list when the flag is on. This client-side predicate (plus the chip/section gates) is the
 * ONLY enforcement of the podcast block on search — the flag does not ride the server request.
 */
fun dropBlockedPodcastItems(items: List<YTItem>, blockPodcasts: Boolean): List<YTItem> =
    if (!blockPodcasts) items
    else items.filterNot { it is PodcastItem || it is EpisodeItem }
