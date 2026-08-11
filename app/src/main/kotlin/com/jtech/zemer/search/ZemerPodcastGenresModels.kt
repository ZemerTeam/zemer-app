package com.jtech.zemer.search

import kotlinx.serialization.Serializable

/**
 * Wire models for the podcast genre family (`GET /podcast-genres`), deliberately shaped to MIRROR the
 * music `/genres` catalog so the same catalog UI serves both. Differences from music, per the server
 * contract (`handoff-docs/zemer-app-podcasts-request.md` + `zemer-app-podcast-genre-kinds-request.md`):
 * the count field is `showCount` (not `trackCount`), the kind VOCABULARY AND TITLES are server-owned
 * (the `kinds` catalog — music's Styles/Occasions header titles are app-side strings), and the detail
 * is just a flat list of SHOWS — no artists/albums/songs/videos facets, no tracklist paging, no radio.
 * Slugs are the stable contract; `title` is display text.
 */

/** One catalog row: a stable server slug + display title + kind slug + post-filter show count. */
@Serializable
data class ZemerPodcastGenreSummary(
    val id: String = "",
    val title: String = "",
    // The section this genre groups under (live 2026-08-11). Absent/unknown = ungrouped, never hidden —
    // unlike music's fail-closed kind drop, everything here is already whitelisted podcast content.
    val kind: String? = null,
    val showCount: Int = 0,
)

/** One server-owned section: slug + display title, emitted in display order. */
@Serializable
data class ZemerPodcastGenreKind(
    val id: String = "",
    val title: String = "",
)

/** `GET /podcast-genres` — the genre catalog. `kinds` absent (older server) = flat list. */
@Serializable
data class ZemerPodcastGenresResponse(
    val count: Int = 0,
    val kinds: List<ZemerPodcastGenreKind> = emptyList(),
    val genres: List<ZemerPodcastGenreSummary> = emptyList(),
)

/** The repository's cleaned catalog: the server's section vocabulary + the de-duped genre rows. */
data class PodcastGenreCatalog(
    val kinds: List<ZemerPodcastGenreKind> = emptyList(),
    val genres: List<ZemerPodcastGenreSummary> = emptyList(),
)

/** One rendered catalog section; a null [title] renders headerless (the ungrouped/flat fallback). */
data class PodcastGenreSection(
    val title: String?,
    val genres: List<ZemerPodcastGenreSummary>,
)

/**
 * The catalog screen's grouping (pure, unit-tested): sections follow the server's `kinds` order with
 * server-owned titles; members keep the server's within-kind order; a genre whose kind is blank or not
 * in the vocabulary falls to a trailing headerless section (never dropped); no `kinds` at all (older
 * server / offline snapshot) = one headerless section, today's flat grid.
 */
fun podcastGenreSections(catalog: PodcastGenreCatalog): List<PodcastGenreSection> {
    if (catalog.kinds.isEmpty()) {
        return if (catalog.genres.isEmpty()) emptyList() else listOf(PodcastGenreSection(null, catalog.genres))
    }
    val known = catalog.kinds.mapTo(HashSet()) { it.id }
    val byKind = catalog.genres.groupBy { it.kind }
    val grouped = catalog.kinds.mapNotNull { kind ->
        byKind[kind.id]?.let { PodcastGenreSection(kind.title.takeIf(String::isNotBlank), it) }
    }
    val ungrouped = catalog.genres.filter { it.kind.isNullOrBlank() || it.kind !in known }
    return grouped + if (ungrouped.isEmpty()) emptyList() else listOf(PodcastGenreSection(null, ungrouped))
}

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
