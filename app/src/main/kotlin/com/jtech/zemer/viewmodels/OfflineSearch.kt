package com.jtech.zemer.viewmodels

import com.metrolist.innertube.YouTube.SearchFilter
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.YTItem
import com.metrolist.innertube.pages.SearchSummary

/**
 * Offline-mode search assembly (manual offline mode, #366): pure section/filter math over the
 * downloaded-scoped Room results, so the branch is JVM-tested. Sections are categorical — Songs are
 * the non-video matches, Videos the video matches — so one download never appears in two sections
 * regardless of the videos-in-music library preference.
 */
internal fun offlineSearchSummarySections(
    songs: List<SongItem>,
    artists: List<ArtistItem>,
    albums: List<AlbumItem>,
    songsTitle: String,
    videosTitle: String,
    artistsTitle: String,
    albumsTitle: String,
): List<SearchSummary> = buildList {
    val (videos, audio) = songs.partition { it.isVideo }
    if (audio.isNotEmpty()) add(SearchSummary(songsTitle, audio))
    if (videos.isNotEmpty()) add(SearchSummary(videosTitle, videos))
    if (artists.isNotEmpty()) add(SearchSummary(artistsTitle, artists))
    if (albums.isNotEmpty()) add(SearchSummary(albumsTitle, albums))
}

/** The offline result list for one search filter chip; playlist filters have no offline source. */
internal fun offlineFilteredItems(
    filter: SearchFilter,
    songs: List<SongItem>,
    artists: List<ArtistItem>,
    albums: List<AlbumItem>,
): List<YTItem> = when (filter) {
    SearchFilter.FILTER_SONG -> songs.filterNot { it.isVideo }
    SearchFilter.FILTER_VIDEO -> songs.filter { it.isVideo }
    SearchFilter.FILTER_ARTIST -> artists
    SearchFilter.FILTER_ALBUM -> albums
    else -> emptyList()
}
