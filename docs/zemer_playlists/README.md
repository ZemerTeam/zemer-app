# Zemer Playlists — the hand-curated playlists section

Hand-authored docset for the **Zemer Playlists** feature: a Home-tab shelf of playlists curated by
the Zemer team ("Shabbos", "Acapella", …), served ready-to-render by the zemer-search server
(`https://search.zemer.io`). Every claim cites the file and symbol that proves it.

## TL;DR

The server owns everything editorial: which playlists exist, their order, their tracks, their
covers, and all content filtering. The app's job is fetch → render → play:

1. `GET /zemer-playlists` (list) and `GET /zemer-playlists?id=…` (detail) —
   `search/ZemerSearchClient.curatedPlaylists()` / `curatedPlaylist()`. **All three content flags
   are sent explicitly on every request** (`allowFemale`, `blockVideos`, `kidZone=0`) because the
   server is default-OPEN; the exact parameter list is the unit-tested
   `zemerCuratedPlaylistsParameters()`.
2. A Home shelf right under Latest Releases (`HomeScreen.kt` — `zemer_playlists_*` items), a
   "See all" grid (`ui/screens/ZemerPlaylistsScreen.kt`, route `zemer_playlists`), and a dedicated
   detail screen (`ui/screens/playlist/ZemerCuratedPlaylistScreen.kt`, route
   `zemer_playlist/{playlistId}`).
3. Tracks are plain videoIds mapped to the same `SongItem`s the search path uses
   (`ZemerResultMapper` — `ZemerCuratedPlaylistResponse.toSongItems`), played through `ListQueue`.

## Non-obvious invariants (the things that will bite)

- **Playlist ids are server slugs (`"acapella"`), never YouTube playlist ids.** They must never be
  routed through any YouTube-playlist code path (`online_playlist/…`, save-to-library, playlist
  menus) — the detail screen is deliberately its own small screen.
- **Fail-closed flags.** The server treats an *omitted* flag as "don't filter", so a restricted
  user's flags are always sent explicitly, with the same values on the list and the detail request
  (`ZemerSearchOptions` ← `ContentFilterState`). `kidZone` is always `0` for the same reason the
  `/album` call pins it: the Home tab is never reachable from inside the KidZone tab.
- **No client-side re-filtering, no caching.** Responses are post-filter (counts, covers, runtimes
  match what plays). The repository (`ZemerSearchRepository.curatedPlaylists/curatedPlaylist`)
  deliberately does **not** cache: the doc'd freshness contract is a plain re-fetch on screen open
  (in-memory server reads, single-digit ms), and no cache means a response fetched under one flag
  set can never be shown under another. Only the surgical id-overrides (`dropBlocked`) and
  `hideExplicit` run client-side, like every Zemer surface.
  One deliberate exception: when `search.zemer.io` is UNREACHABLE, both curated calls fall back to
  the on-device offline snapshot (`serverOrOffline` → `OfflineReadProvider`, see
  `docs/offline/README.md`) — up to 14 days old, filtered client-side by the offline read layer's
  shared `contentGatePasses` gate for the same flags. Flag-consistency still holds (the flags are
  passed into the offline read); only the freshness contract is relaxed, and only during an outage.
- **Covers are server-generated SVGs at a relative URL.** The list/detail `thumbnail` is
  `/zemer-playlists/cover?id=…` — resolved against the API host by `resolveZemerUrl()`
  (`ZemerSearchClient.kt`), decoded by Coil's `SvgDecoder` registered in `App.newImageLoader`
  (the `coil-svg` artifact exists for exactly this). Curated playlists are editorial categories, so
  they get a branded title card, never a member track's album art.
- **The card shows no text title, because the cover already carries it.** The generated cover SVG
  bakes the playlist name into the artwork, so `ZemerCuratedPlaylistGridItem`
  (`ui/component/ZemerCuratedPlaylistCard.kt`) passes `showTitle = false` on both surfaces, and the
  compact Home row also passes `showSubtitle = false` so it is cover-only (no count/runtime label);
  the wider See-all grid keeps the "N songs" (and runtime) sub-label. Both flags live on the shared
  `YouTubeGridItem` (default true, so no other caller is affected).
- **Empty list is a normal state.** `count: 0` → the Home section and See-all grid simply don't
  render. A detail 404 (curation changed between list and open) backs out via `UiState.NotFound`
  → `navigateUp()`; the Home section re-fetches on screen-open so the stale card disappears.
- **The section can never break Home.** Its own `ZemerCuratedPlaylistsViewModel` (the
  `LatestReleasesViewModel` isolation pattern): fetch failures keep the previous list and report
  via `reportException`; refreshes run on VM creation, on every content-filter flag change, on
  every Home screen-open (`LaunchedEffect`), and on pull-to-refresh.

## The detail screen's All / Albums / Songs chips

Same chips as the Latest Releases screen (same `LatestReleaseFilter` enum, same strings, same
`ChipsRow`). The split comes from the curator's authoring model — a playlist is direct `videoIds`
picks plus `albumIds` expanded to their tracks:

- **All** — the full flattened tracklist, curated order.
- **Albums** — the curated albums themselves as browsable rows (`albums` array in the detail
  response, decoded with the `/search` album model `ZemerAlbum`); tap opens the normal album screen
  through the server path (`SearchProvider.ZEMER.onlineAlbumRoute`). Play/Shuffle under this chip
  play the album-sourced tracks.
- **Songs** — the direct picks: tracks whose server `fromAlbum` flag is false
  (`ZemerCuratedPlaylistPage.albumTrackIds` carries the album-sourced videoIds because `SongItem`
  has no such field).

The chip row, the visible rows, **Play and Shuffle all read the same filtered list**
(`filterCuratedTracks()`, pure + unit-tested), so shuffling under a chip plays exactly what is
shown. Rows never pass `albumIndex` — the shared row renders a number *instead of* artwork, an
album-screen convention that is wrong here where every row has its own art.

Both server fields (`fromAlbum`, `albums`) were requested via handoff docs and are decoded
leniently: an older server without them just means an empty Albums chip / everything reads as a
Song — deploy order never matters.

## Server coordination

App↔server changes travel as request docs in `~/zemer-fix/handoff-docs/` (not in either repo):
`zemer-curated-playlists-endpoint.md` (the original integration spec),
`…-track-provenance-request.md` (`fromAlbum`), `…-albums-list-request.md` (`albums`). The server
side lives in the zemer-search repo (`/zemer-playlists` in `server/api.mjs`,
`zemerPlaylistList/Detail` in `corpus/store.mjs`, authored in `data/zemer-playlists.json`,
applied by `harvester/zemer-playlists.mjs`).

## Tests

Plain JVM, no network:

- `app/src/test/kotlin/com/jtech/zemer/search/ZemerCuratedPlaylistsTest.kt` — the
  send-always/fail-closed parameter contract, lenient wire decoding (nulls, unknown keys, empty
  list, old-server absence of `fromAlbum`/`albums`), curated→`SongItem` mapping (order, durations,
  sparse-row drop), relative-cover resolution, and the runtime-label rule (null hides; <120 min in
  minutes; 2 h or more in rounded hours). See the card-rendering note above for the See-all vs Home
  card label difference (`ui/component/ZemerCuratedPlaylistCard.kt`).
- `app/src/test/kotlin/com/jtech/zemer/ui/screens/playlist/ZemerCuratedPlaylistFilterTest.kt` —
  the chip filter (ALL passthrough, ALBUMS/SONGS split, old-server empty set).

Ground truth for the live endpoint is `curl` against `search.zemer.io` (see "Quick test" in the
handoff doc); the `tests/` streaming harness is unrelated to this feature.
