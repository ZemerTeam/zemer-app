# Zemer Playlists — the hand-curated playlists section

The **Zemer Playlists** feature: a Home-tab shelf of playlists curated by the Zemer team, served
ready-to-render by the zemer-search server (`https://search.zemer.io`).

## TL;DR

The server owns everything editorial: which playlists exist, their order, tracks, covers, and all
content filtering. The app's job is fetch → render → play:

1. `GET /zemer-playlists` (list) and `GET /zemer-playlists?id=…` (detail) —
   `search/ZemerSearchClient.curatedPlaylists()` / `curatedPlaylist()`. **All three content flags
   are sent explicitly on every request** (`allowFemale`; `blockVideos=0`, pinned by `zemerSearchOptions` since blocked videos render as audio; `kidZone=0`) because the
   server is default-OPEN; the parameter list is the unit-tested `zemerCuratedPlaylistsParameters()`.
2. A Home shelf under Latest Releases (`HomeScreen.kt` — `zemer_playlists_*` items), a "See all"
   grid (`ui/screens/ZemerPlaylistsScreen.kt`, route `zemer_playlists`), and a dedicated detail
   screen (`ui/screens/playlist/ZemerCuratedPlaylistScreen.kt`, route `zemer_playlist/{playlistId}`).
3. Tracks are plain videoIds mapped to the same `SongItem`s the search path uses
   (`ZemerResultMapper` — `ZemerCuratedPlaylistResponse.toSongItems`), played through `ListQueue`.

## Non-obvious invariants

- **Playlist ids are server slugs (`"acapella"`), never YouTube playlist ids.** They must never be
  routed through any YouTube-playlist code path (`online_playlist/…`, save-to-library, playlist
  menus) — the detail screen is deliberately its own small screen.
- **Fail-closed flags.** The server treats an *omitted* flag as "don't filter", so the flags are
  always sent explicitly, with the same values on list and detail (`ZemerSearchOptions` ←
  `ContentFilterState`). `kidZone` is always `0`: the Home tab is never reachable from inside KidZone.
- **No client-side re-filtering, no caching.** Responses are post-filter (counts, covers, runtimes
  match what plays). `ZemerSearchRepository.curatedPlaylists/curatedPlaylist` deliberately do **not**
  cache: the freshness contract is a plain re-fetch on screen open, and no cache means a response
  fetched under one flag set can never be shown under another. Only the id-overrides (`dropBlocked`)
  run client-side. Exception: when `search.zemer.io` is UNREACHABLE, both calls fall back to the
  offline snapshot (`serverOrOffline` → `OfflineReadProvider`, see `docs/offline/README.md`), which
  receives the same flags and filters via `contentGatePasses` — only freshness is relaxed, only
  during an outage.
- **Covers are server-generated SVGs at a relative URL** (`/zemer-playlists/cover?id=…`), resolved
  against the API host by `resolveZemerUrl()` and decoded by Coil's `SvgDecoder` registered in
  `App.newImageLoader` (why `coil-svg` exists). Never substitute a member track's album art.
- **The card shows no text title, because the cover already carries it.**
  `ZemerCuratedPlaylistGridItem` (`ui/component/ZemerCuratedPlaylistCard.kt`) passes
  `showTitle = false`; the compact Home row also passes `showSubtitle = false` (cover-only), while the
  See-all grid keeps the "N songs • runtime" sub-label. Both flags live on the shared
  `YouTubeGridItem` (default true).
- **Empty list is a normal state** — the Home section and See-all grid simply don't render. A detail
  404 backs out via `UiState.NotFound` → `navigateUp()`; Home re-fetches on screen-open so the stale
  card disappears.
- **The section can never break Home.** Its own `ZemerCuratedPlaylistsViewModel` (the
  `LatestReleasesViewModel` isolation pattern): a failed fetch keeps the previous list and reports via
  `reportException`; it refreshes on every Home screen-open, on pull-to-refresh, and on every
  content-filter flag change (`reloadOnContentFlagChange`, which drops the initial emission so a
  screen open is exactly one fetch - there is no separate fetch on VM creation).

## The detail screen's All / Albums / Songs chips

Same chips as the Latest Releases screen (`LatestReleaseFilter`, same strings, `ChipsRow`). A
curated playlist is direct `videoIds` picks plus `albumIds` expanded to their tracks:

- **All** — the full flattened tracklist, curated order.
- **Albums** — the curated albums as browsable rows (the detail response's `albums`, decoded with the
  `/search` album model `ZemerAlbum`); tap opens the album through the server path (`zemerAlbumRoute`,
  `search/ZemerRoutes.kt`). Play/Shuffle here play the album-sourced tracks.
- **Songs** — the direct picks: tracks whose `fromAlbum` flag is false
  (`ZemerCuratedPlaylistPage.albumTrackIds` carries the album-sourced ids because `SongItem` has no
  such field).

The chip row shows ONLY when the playlist has albums (`curatedChipsVisible(albumCount)`, unit-tested):
without albums All == Songs and Albums is an empty dead end, so the plain track list renders and
`effectiveFilter` pins the filter to ALL.

The rows, **Play and Shuffle all read the same filtered list** (`filterCuratedTracks()`,
unit-tested), so shuffling under a chip plays exactly what is shown. Rows never pass `albumIndex` —
the shared row renders a number *instead of* artwork.

`fromAlbum` and `albums` are decoded leniently: an older server without them yields no albums (so the
chip row is hidden) and every track reads as a Song, so deploy order never matters.

## Server coordination

App↔server changes travel as request docs in `handoff-docs/` (outside this repo), never as
zemer-search edits:
`zemer-curated-playlists-endpoint.md` (the integration spec), `…-track-provenance-request.md`
(`fromAlbum`), `…-albums-list-request.md` (`albums`).

## Tests

Plain JVM, no network:

- `app/src/test/kotlin/com/jtech/zemer/search/ZemerCuratedPlaylistsTest.kt` — the send-always
  parameter contract, lenient wire decoding (nulls, unknown keys, empty list, missing
  `fromAlbum`/`albums`), curated→`SongItem` mapping, relative-cover resolution, and the runtime-label
  rule (null hides; under 120 min in minutes; 2 h or more in rounded hours).
- `app/src/test/kotlin/com/jtech/zemer/ui/screens/playlist/ZemerCuratedPlaylistFilterTest.kt` — the
  chip filter (ALL passthrough, ALBUMS/SONGS split, old-server empty set).
