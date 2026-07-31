# Genres — the song-level genre browsing layer

Hand-authored docset for the **Genres** feature: a Home chip strip, a browsable catalog, per-genre
detail pages, and genre radio, served by the zemer-search server (`https://search.zemer.io`). The
server-side contract lives in `~/zemer-fix/handoff-docs/zemer-app-genres.md` (endpoints, slug
vocabulary, kinds); this doc is the app side. Every claim cites the file that proves it.

## TL;DR

Genre describes style at the **song** level (via its release), independent of the artist flags
(`isChasid`/`isFemale`/…). The app's job is fetch → render → play, keyed off the stable **slug**
(`"nigunim"`), never the display **title** (`"Nigunim"`):

1. `GET /genres` (catalog), `GET /genres?id=<slug>` (page: header + top-k artist/album/single
   shelves + a paged songs/videos tracklist), `GET /genres?id=<slug>&facet=<facet>` (one facet's
   FULL list, paged — the see-all screens), and `GET /radio?kind=genre&seed=<slug>` (genre radio).
   All in `search/ZemerSearchClient.kt`; wire models in `search/ZemerGenresModels.kt`.
2. **All content flags sent on every call** (`allowFemale`, `blockVideos`, `kidZone=0`) — the server
   is default-OPEN; the exact lists are the unit-tested `zemerGenresParameters()` /
   `zemerGenreFacetParameters()`.
3. Surfaces: a Home chip strip (`ui/screens/HomeGenresRow.kt`, VM `viewmodels/ZemerGenresViewModel`),
   the catalog (`ui/screens/GenresScreen.kt`, route `genres`, VM `ZemerGenreCatalogViewModel`), the
   detail page (`ui/screens/GenreScreen.kt`, route `genre/{genreId}`, VM `ZemerGenreViewModel`), and
   the Albums/Singles see-all (`ui/screens/GenreSectionScreen.kt`, route
   `genre_section/{genreId}?section=`, VM `ZemerGenreSectionViewModel`). Route builders in
   `search/ZemerRoutes.kt` (`zemerGenresRoute`/`zemerGenreRoute`/`zemerGenreSectionRoute`).
4. **Play a genre = genre radio** (`playback/queues/ZemerRadioQueue.genre(slug)` → `/radio?kind=genre`),
   never the browse tracklist.

## Non-obvious invariants (the things that will bite)

- **Key off the slug, render the title.** `id` is the stable contract; `title` is a display string
  the server changes freely (`ZemerGenresModels.kt`). Routes carry the slug (`[\w-]`, URL-safe, so
  `ZemerRoutes.kt` does no encoding — kept pure for the JVM tests).
- **`kind` grouping + fail-closed non-music.** `GenreKind.fromSlug` maps `style`/`occasion`/
  `non-music`; `musicGenres()` drops `non-music` **and any unknown/new kind** (fail-closed) so
  spoken-word (shiur/parsha/…) never renders beside songs. `genresByKind()` buckets Styles then
  Occasions; the whole pipeline is JVM-tested in `ZemerGenresTest`.
- **Editorially hidden slugs.** `HIDDEN_GENRE_SLUGS` (`lullaby`, `carlebach`, `workout`, `kids`) are
  dropped from browse app-side (owner decision); their songs stay reachable everywhere else.
  `acapella` is pinned LAST via `pinLast()` (served second-most-popular, should close the row).
- **Fail-closed flags, no caching (except one).** Every genre call sends all three flags explicitly
  (`ZemerSearchOptions` ← `ContentFilterState`). The catalog has a short (60 s) flag-keyed TTL memo
  in `ZemerSearchRepository.genres()` to collapse the Home→see-all→back→chip burst; the detail/facet
  calls are uncached (single user-initiated opens). All genre endpoints are **live-only** — no
  offline snapshot (like `/playlist`, `/radio`, `/stations`).
- **The detail Play button is genre RADIO, not the tracklist.** `ZemerRadioQueue.genre(slug)` seeds
  no song (endless station), so its plays correctly report `radio`; per-genre `PlaySource.genre` /
  `TrackingSurface.genre` attribution rides only the tracklist row taps (seed-first song radio).
- **No Artists shelf on a genre page.** An artist card opens the artist's FULL catalog, mostly
  unrelated to the genre — deliberately omitted (`GenreScreen.kt`). Albums/Singles shelves stay.
- **Tracklist paging + cross-list dedup.** The songs/videos list pages via `offset`/`nextOffset`
  with a near-edge prefetch (`shouldPrefetchNearEnd`, ~10 rows before the end, off-composition
  `snapshotFlow` — no per-frame recomposition; end shimmer only as the in-flight fallback). A track
  the server returns in BOTH the song and video arrays (the corpus reclassifies `isVideo`) is
  de-duped across the two lists in `ZemerGenreViewModel` (page 0 AND `loadMore`), and the rows use
  disjoint `song_`/`video_` keys so the keyed `LazyColumn` can't collide.
- **See-all uses the facet endpoint, not `k`.** `ZemerGenreSectionViewModel` pages
  `/genres?id=&facet=albums|singles` (limit 200) until `nextOffset` is null, so the FULL list is
  browsable (e.g. acapella's 115 albums), not a 60 cap. Reuses the shared `YtItemGrid` (2-col,
  server-route album opens) + `BackTopAppBar`. The see-all arrow shows on any non-empty shelf (like
  the artist page) — the server's header counts were unreliable for gating (they once reported the
  sliced length; now fixed to true totals, but always-show is simpler).

## The visual layer (all monochrome + one gold accent)

The design rule is one cohesive theme — background/surface/text plus a single accent — with color
coming only from album art, never applied decoratively. Details in `docs/ui/README.md`; genre
specifics:

- **Per-genre motif icons** (`ui/component/GenreIcons.kt`): a slug-keyed `@DrawableRes` map to
  `res/drawable/genre_*.xml` (Material Symbols outlined, plus hand-drawn `genre_menorah` for
  Chanukah, `genre_alef` for Yiddish, `genre_sukkah` for Succos), tinted with the theme accent.
  Unknown slugs fall back to the music note. NOT `material-icons-extended` — that dependency was
  reviewed out for the vector-drawable convention.
- **The drifting weave** (`ui/component/GenreCard.kt` `GenreWeaveLayer`): a faint tiled motif
  background shared by the catalog cards and the detail header (one continuous fabric). The motion
  is a **GPU-composited layer translation** (`graphicsLayer { translationX }`), NOT a per-frame
  redraw — the tile grid is drawn once and only re-composited each frame; the grid period is one
  cell so the loop is seamless. This is why ~20 cards can animate at once without jank.
- **Album-art mosaic header** (`GenreScreen.kt` genre_header): the genre's own top covers fill the
  detail header behind a scrim (the ONE color source). Selection is `ZemerResultMapper.headerCovers`
  — de-duped, sized to the header band via `mosaicVariant` (a mosaic-only URL rewrite, isolated from
  the shared `thumbnailFor`), and **all-or-nothing with a minimum of 3 unique covers** (songs reuse
  album art, so many genres have only a handful; below 3 a lone/stretched cover breaks the flow, so
  the weave-only header carries them). Failed/loading columns show a neutral `ColorPainter`, never a
  transparent gap. The VM preloads the same URLs the moment the page lands so they download in
  parallel with the first frame.
- **Fonts** (`ui/theme/Type.kt`): a `HeaderFontFamily` (Heebo heavy, full Hebrew) is used ONLY on
  genre titles, the Play pill, and the catalog card titles — NOT app-wide (the rest of
  `AppTypography` stays platform default).

## Stations (zero app code)

Two genres also back synchronized **stations** (`nigunim` = Nigunim Radio, `calm` = Chill Radio) — a
different product from genre radio. They flow through the existing `/stations` + `StationQueue`
integration (see `docs/stations/README.md`) with no genre-specific code; nothing switches on station
id. Don't assume a genre has a station — read `/stations` for the live set.

## Tests

Plain-JVM: `search/ZemerGenresTest` (params incl. facet, kind filtering, hidden/pinned slugs,
grouping, wire decoding, `toGenrePage`/`toAlbumFacetPage` mappers, `mosaicVariant`, `headerCovers`),
`search/ZemerRoutesTest` (route builders), `tracking/TrackingEventsTest` (genre slugs match the
server alphabet), `ui/screens/GenreScreenTest` (prefetch threshold, header counts, cover selection).

## Files

Data: `search/ZemerGenresModels.kt`, `search/ZemerSearchClient.kt` (genre/genreFacet + params),
`search/ZemerSearchRepository.kt`, `search/ZemerResultMapper.kt` (toGenrePage/toAlbumFacetPage/
mosaicVariant/headerCovers), `search/ZemerRoutes.kt`, `playback/queues/ZemerRadioQueue.kt`
(`genre` factory), `tracking/PlaySource.kt` + `tracking/TrackingEvents.kt` (`genre` slugs).
UI: `ui/screens/{HomeGenresRow,GenresScreen,GenreScreen,GenreSectionScreen}.kt`,
`ui/component/{GenreCard,GenreChip,GenreIcons}.kt`, `res/drawable/genre_*.xml`, `ui/theme/Type.kt`.
VMs: `viewmodels/{ZemerGenresViewModel,ZemerGenreCatalogViewModel,ZemerGenreViewModel,ZemerGenreSectionViewModel}.kt`
+ the shared `reloadOnContentFlagChange` (`viewmodels/ZemerFlagRefetch.kt`).
Prefs: `constants/PreferenceKeys.kt` (`ShowHomeGenresKey`), toggled in `settings/AppearanceSettings.kt`.
