package com.jtech.zemer.search

import kotlinx.serialization.Serializable

/**
 * Wire models for the podcast genre family (`GET /podcast-genres`), deliberately shaped to MIRROR the
 * music `/genres` catalog so the same catalog UI serves both. Differences from music, per the server
 * contract (`handoff-docs/zemer-app-podcasts-request.md`, the 2026-08-06 genres note): the count field
 * is `showCount` (not `trackCount`), there is no `kind` (podcast genres are a flat list), and the detail
 * is just a flat list of SHOWS — no artists/albums/songs/videos facets, no tracklist paging, no radio.
 * Slugs are the stable contract; `title` is display text.
 */

/** One catalog row: a stable server slug + display title + post-filter show count. */
@Serializable
data class ZemerPodcastGenreSummary(
    val id: String = "",
    val title: String = "",
    val showCount: Int = 0,
)

/** `GET /podcast-genres` — the flat genre catalog. */
@Serializable
data class ZemerPodcastGenresResponse(
    val count: Int = 0,
    val genres: List<ZemerPodcastGenreSummary> = emptyList(),
)

/**
 * `GET /podcast-genres?id=<slug>` — one genre's page: its header + the flat list of member shows
 * (the same [ZemerPodcastShow] rows the browse grid / channel shelf use, so they render + route through
 * the existing podcast show card). A 404 means the slug is unknown or everything is filtered out.
 */
@Serializable
data class ZemerPodcastGenrePageResponse(
    val genre: ZemerPodcastGenreSummary = ZemerPodcastGenreSummary(),
    val shows: List<ZemerPodcastShow> = emptyList(),
)
