package com.jtech.zemer.search

import kotlinx.serialization.Serializable

/**
 * Wire models for `GET /genres` (search.zemer.io) — the song-level genre layer (contract:
 * `~/zemer-fix/handoff-docs/zemer-app-genres.md`). Two hard rules from that contract:
 *
 * - **Key off the slug ([ZemerGenreSummary.id]), never the label** — `title` is a display string the
 *   server changes freely.
 * - **Absent `genres` on a track = UNKNOWN, never "none"** — ~84% of songs are unclassified, so the
 *   app never derives "not in genre X" from a missing tag. (The per-track `genres` field is not even
 *   modeled here; the lenient reader ignores it.)
 *
 * Counts are post-filter: the server computes them against the content flags sent, so every count is
 * reachable by that viewer. All fields are defaulted for the same reason as [ZemerSearchModels]:
 * kotlinx throws MissingFieldException for the WHOLE response if one element omits a required field.
 */
@Serializable
data class ZemerGenresResponse(
    val count: Int = 0,
    val genres: List<ZemerGenreSummary> = emptyList(),
)

/** One catalog row: a stable server slug + display title + post-filter song count + [GenreKind] slug. */
@Serializable
data class ZemerGenreSummary(
    val id: String = "",
    val title: String = "",
    val trackCount: Int = 0,
    val kind: String = "",
)

/**
 * Wire model for `GET /genres?id=<slug>` — one genre's page, shaped like [ZemerArtistResponse] (the
 * same flat category arrays, reusing [ZemerArtist]/[ZemerAlbum]/[ZemerTrack]) plus the header counts
 * and the `limit`/`offset` paging of the songs/videos tracklist. The capped `artists`/`albums`/
 * `singles` arrays belong to page 0 only; `songs`/`videos` accumulate across pages. Extra per-row
 * fields the endpoint sends (artist `trackCount`, release `releaseDate`, track `genres`/`isVideo`)
 * are ignored by the lenient reader. A 404 means the slug is unknown OR everything is filtered out
 * for this viewer.
 */
@Serializable
data class ZemerGenrePageResponse(
    val genre: ZemerGenreHeader = ZemerGenreHeader(),
    val artists: List<ZemerArtist> = emptyList(),
    val albums: List<ZemerAlbum> = emptyList(),
    val singles: List<ZemerAlbum> = emptyList(),
    val songs: List<ZemerTrack> = emptyList(),
    val videos: List<ZemerTrack> = emptyList(),
    val offset: Int = 0,
    /** Next page's `offset` to request; null = the tracklist is complete. */
    val nextOffset: Int? = null,
)

/** The genre-page header: identity plus the true post-filter totals (the arrays are capped top-k). */
@Serializable
data class ZemerGenreHeader(
    val id: String = "",
    val title: String = "",
    val kind: String = "",
    val trackCount: Int = 0,
    val artistCount: Int = 0,
    val albumCount: Int = 0,
    val singleCount: Int = 0,
    // Added 2026-07-31 (handoff addendum); absent on older servers, so defaulted.
    val songCount: Int = 0,
    val videoCount: Int = 0,
)

/**
 * Wire model for `GET /genres?id=<slug>&facet=<facet>` — one facet's FULL list, paged (the summary
 * page only returns the top-k). Used by the see-all screens. `items` is the same row shape the facet
 * uses on the summary page; for the `albums`/`singles` facets that is [ZemerAlbum]. Page with
 * `limit`+`offset` until [nextOffset] is null. A bad facet is a 400, an unknown/empty genre a 404.
 */
@Serializable
data class ZemerGenreFacetResponse(
    val genre: ZemerGenreHeader = ZemerGenreHeader(),
    val facet: String = "",
    val items: List<ZemerAlbum> = emptyList(),
    val offset: Int = 0,
    val nextOffset: Int? = null,
)

/**
 * The catalog's `kind` vocabulary. `NON_MUSIC` slugs (shiur/parsha/story/comedy/podcast) exist so
 * spoken-word content can be EXCLUDED — they are never featured beside songs (handoff §2), which is
 * why [musicGenres] drops them (and any future unknown kind, fail-closed) from every music surface.
 */
enum class GenreKind {
    STYLE, OCCASION, NON_MUSIC;

    companion object {
        fun fromSlug(kind: String): GenreKind? = when (kind) {
            "style" -> STYLE
            "occasion" -> OCCASION
            "non-music" -> NON_MUSIC
            else -> null
        }
    }
}

/**
 * Genres hidden app-side by editorial decision (owner request, 2026-07-30): Bedtime, Carlebach,
 * On the Move and Kids don't surface in the app's catalog/home row even though the server serves them. Their
 * songs stay reachable everywhere else (search, artist pages, radio) — this hides the browse entry
 * only.
 */
internal val HIDDEN_GENRE_SLUGS = setOf("lullaby", "carlebach", "workout", "kids")

/**
 * Genres pinned to the END of their surface (owner request, 2026-07-30): Acapella is served
 * second-most-popular but should close the row/section, not lead it. Applied AFTER any popularity
 * cap, so pinning never evicts the genre from a capped surface (the home strip).
 */
internal val GENRES_PINNED_LAST = setOf("acapella")

/** Stable reorder: pinned slugs move to the end, everything else keeps its server order. */
internal fun pinLast(genres: List<ZemerGenreSummary>): List<ZemerGenreSummary> =
    genres.sortedBy { if (it.id in GENRES_PINNED_LAST) 1 else 0 }

/**
 * The genres to show on a music surface: sparse rows dropped (blank slug can't key a lazy list),
 * de-duped, NON_MUSIC + unknown kinds removed (fail-closed — a new server kind stays hidden until
 * the app knows what it is), and the editorial [HIDDEN_GENRE_SLUGS] dropped. Server
 * (most-populated-first) order is preserved.
 */
internal fun musicGenres(genres: List<ZemerGenreSummary>): List<ZemerGenreSummary> =
    genres.filter { it.id.isNotBlank() && it.id !in HIDDEN_GENRE_SLUGS && GenreKind.fromSlug(it.kind).let { k -> k != null && k != GenreKind.NON_MUSIC } }
        .distinctBy { it.id }

/**
 * The catalog screen's grouping: music genres bucketed by kind, each bucket keeping the server's
 * popularity order except the [GENRES_PINNED_LAST] tail. Only STYLE/OCCASION can appear (the input
 * is [musicGenres]-cleaned here).
 */
internal fun genresByKind(genres: List<ZemerGenreSummary>): Map<GenreKind, List<ZemerGenreSummary>> =
    pinLast(musicGenres(genres)).groupBy { GenreKind.fromSlug(it.kind)!! }

/**
 * The home chips row's trim: the most-populated music genres, capped so the row stays a row, with
 * the [GENRES_PINNED_LAST] tail applied AFTER the cap (selection stays popularity-based; only the
 * display order moves).
 */
internal fun homeGenreChips(genres: List<ZemerGenreSummary>, max: Int = HOME_GENRE_CHIPS_MAX): List<ZemerGenreSummary> =
    pinLast(musicGenres(genres).take(max))

internal const val HOME_GENRE_CHIPS_MAX = 12
