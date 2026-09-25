# Genres — the song-level genre browsing layer

The **Genres** feature: a Home chip strip, a browsable catalog, per-genre detail pages, and genre
radio, served by the zemer-search server (`https://search.zemer.io`). The server-side contract
(endpoints, slug vocabulary, kinds) is `handoff-docs/zemer-app-genres.md`; this doc is the app side.

## TL;DR

Genre describes style at the **song** level (via its release), independent of the artist flags
(`isChasid`/`isFemale`/…). The app's job is fetch → render → play, keyed off the stable **slug**
(`"nigunim"`), never the display **title**:

1. `GET /genres` (catalog), `GET /genres?id=<slug>` (page: header + top-k album/single shelves + a
   paged songs/videos tracklist), `GET /genres?id=<slug>&facet=<facet>` (one facet's FULL list, paged
   — the see-all screens), and `GET /radio?kind=genre&seed=<slug>` (genre radio). All in
   `search/ZemerSearchClient.kt`; wire models in `search/ZemerGenresModels.kt`.
2. **All content flags sent on every call** (`allowFemale`, `blockVideos`, `kidZone=0`) — the server
   is default-OPEN; the lists are the unit-tested `zemerGenresParameters()` /
   `zemerGenreFacetParameters()` (`ZemerSearchOptions` ← `ContentFilterState`).
3. Surfaces: a Home chip strip (`ui/screens/HomeGenresRow.kt`, VM `ZemerGenresViewModel`, toggled by
   `ShowHomeGenresKey` in Appearance settings), the catalog (`ui/screens/GenresScreen.kt`, route
   `genres`, VM `ZemerGenreCatalogViewModel`), the detail page (`ui/screens/GenreScreen.kt`, route
   `genre/{genreId}`, VM `ZemerGenreViewModel`), and the Albums/Singles see-all
   (`ui/screens/GenreSectionScreen.kt`, route `genre_section/{genreId}?section=`, VM
   `ZemerGenreSectionViewModel`). Route builders: `search/ZemerRoutes.kt`
   (`zemerGenresRoute`/`zemerGenreRoute`/`zemerGenreSectionRoute`). All VMs refetch on a flag change
   via the shared `reloadOnContentFlagChange` (`viewmodels/ZemerFlagRefetch.kt`).
4. **Play a genre = genre radio** (`ZemerRadioQueue.genre(slug)` → `/radio?kind=genre`), never the
   browse tracklist.

## Non-obvious invariants

- **Key off the slug, render the title.** `id` is the stable contract; `title` is a display string
  the server changes freely. Routes carry the raw slug (`[\w-]`, URL-safe, so `ZemerRoutes.kt` does no
  encoding — kept pure for the JVM tests).
- **`kind` grouping is fail-closed.** `GenreKind.fromSlug` maps `style`/`occasion`/`non-music`;
  `musicGenres()` drops `non-music` **and any unknown/new kind** so spoken-word never renders beside
  songs. `genresByKind()` buckets Styles then Occasions.
- **Editorially hidden slugs.** `HIDDEN_GENRE_SLUGS` (`lullaby`, `carlebach`, `workout`, `kids`) are
  dropped from browse app-side (owner decision); their songs stay reachable elsewhere. `acapella` is
  pinned LAST via `pinLast()`.
- **Live-only; one memo.** All genre endpoints are **live-only** — no offline snapshot (offline
  `/radio` explicitly excludes `kind=genre`). The catalog has a `GENRES_CACHE_TTL_MS` (60 s)
  flag-keyed memo in `ZemerSearchRepository.genres()` to collapse the Home→see-all→back burst;
  detail/facet calls are uncached.
- **The detail Play button is genre RADIO, not the tracklist.** It seeds no song, so its plays report
  `radio`; per-genre `PlaySource.genre` / `TrackingSurface.genre` attribution rides only the
  tracklist row taps (seed-first song radio).
- **No Artists shelf on a genre page** — an artist card opens the artist's FULL, mostly-unrelated
  catalog, so it is deliberately omitted. Albums/Singles shelves stay.
- **Tracklist paging + cross-list dedup.** The songs/videos list pages via `offset`/`nextOffset` with
  a near-edge prefetch (the pure `shouldPrefetchNearEnd`, `TRACKLIST_PREFETCH_ROWS` = 10, driven by an
  off-composition `snapshotFlow`). A track the server returns in BOTH the song and video arrays is
  de-duped across the two lists in `ZemerGenreViewModel` (page 0 AND `loadMore`), and rows use
  disjoint `song_`/`video_` keys so the keyed `LazyColumn` can't collide.
- **See-all uses the facet endpoint, not `k`.** `ZemerGenreSectionViewModel` pages
  `/genres?id=&facet=albums|singles` (limit 200) until `nextOffset` is null, so the FULL list is
  browsable. Reuses the shared `YtItemGrid` + `BackTopAppBar`. The see-all arrow shows on any
  non-empty shelf (like the artist page) — never gate it on the header counts.

## The visual layer (monochrome + the one theme accent)

Color comes only from album art, never applied decoratively:

- **Per-genre motif icons** (`ui/component/GenreIcons.kt`): a slug-keyed `@DrawableRes` map to
  `res/drawable/genre_*.xml` (Material Symbols outlined, plus hand-drawn `genre_menorah` (chanukah),
  `genre_alef` (yiddish), `genre_sukkah` (succos)), tinted with the theme accent; unknown slugs fall
  back to the music note. Do NOT add `material-icons-extended` (the vector-drawable convention).
- **The drifting weave** (`GenreWeaveLayer` in `ui/component/GenreCard.kt`), shared by the catalog
  cards and the detail header: the vector motif is rasterized **once** into a tiny cached tile
  (`drawWithCache`); each frame only blits that tile across the brick grid at a phase offset (period
  one cell, so the loop is seamless). Never re-rasterize the vector per frame (catalog jank), and
  don't go back to a cached `graphicsLayer` translation (with ~20 weaves the compositor could evict a
  card's layer, leaving it blank with nothing to redraw it — the tile blit self-heals).
- **Album-art mosaic header** (`GenreScreen.kt`): the genre's own covers fill the detail header
  behind a scrim (the ONE color source). Selection is `ZemerResultMapper.headerCovers` — de-duped,
  sized via the mosaic-only `mosaicVariant` (isolated from the shared `thumbnailFor`), and
  **all-or-nothing with a minimum of 3 unique covers** (below that a lone/stretched cover breaks the
  flow, so the weave-only header is used). Failed/loading columns show a neutral `ColorPainter`,
  never a transparent gap. The VM preloads the same URLs as soon as the page lands.
- **Fonts** (`ui/theme/Type.kt`): `HeaderFontFamily` (Heebo heavy, full Hebrew) is used only on the
  genre titles, Play pill and catalog card titles (and the Music Status creator names) — NOT
  app-wide; `AppTypography` stays platform default.

## Stations

Some genres also back synchronized **stations** — a different product from genre radio, served by
`/stations` + `StationQueue` (see `docs/stations/README.md`) with no genre-specific code. Don't assume
a genre has a station.

## Tests

Plain-JVM: `search/ZemerGenresTest` (params incl. facet, kind filtering, hidden/pinned slugs,
grouping, wire decoding, `toGenrePage`/`toAlbumFacetPage`),
`search/ZemerRoutesTest` (route builders), `tracking/TrackingEventsTest` (genre slugs match the
server alphabet), `ui/screens/GenreScreenTest` (prefetch threshold, header-cover selection, `mosaicVariant`).

Other genre UI pieces: `ui/component/{GenreCardGrid,GenreChip,GenreDetailHeader,GenreCatalogShimmer}.kt`.
