# 01 — Architecture and data flow

## Why a server feed at all

The feature shows "newest releases from kosher artists." YouTube's own new-releases surfaces
cannot supply this: per `tests/recent-releases/README.md` (and the `LatestReleasesStore`
KDoc, `LatestReleasesStore.kt:21-26`), "the global YouTube feeds (`FEmusic_new_releases`,
charts) carry almost no kosher content," so the feed is **precomputed server-side and served as
a small JSON the app fetches cheaply.**

The app is therefore a pure **consumer** of a JSON document. It does not crawl YouTube to build
the list — that work happens off-device (doc 02). This keeps the on-device cost to a single
small HTTP GET per launch.

## The modules

All app-side code lives under one package, `com.jtech.zemer.latestreleases` (including the shared
card composable), plus a ViewModel and two UI screens:

| File | Responsibility |
|---|---|
| `latestreleases/LatestReleasesStore.kt` | Network + disk cache. The `LatestReleasesFeed`/`LatestRelease` data models, the ETag fetch, the retry/give-up/staleness policy. A singleton `object` with test seams. |
| `latestreleases/LatestReleaseMapping.kt` | `LatestRelease.toAlbumItem()` — adapts a feed row to the InnerTube `AlbumItem` the rest of the app already renders, filters, and navigates. |
| `latestreleases/LatestReleaseDate.kt` | `LatestRelease.relativeDateLabel()` — formats `uploadDate` as a localized relative span ("2 days ago"). |
| `latestreleases/LatestReleasePlayback.kt` | `LatestRelease.playableSingle()` / `openOrPlay()` — the shared single-vs-album tap decision (play a 1-track single with radio, else open the album); `isNowPlaying()`, the now-playing match (single by videoId, album by browseId); and `sampleTracks()` / `shufflePlay()` backing the See-all shuffle FAB. |
| `latestreleases/LatestReleaseCard.kt` | `LatestReleaseCard` — the one shared card composable both surfaces render each release through (`asGrid` picks grid vs list); centralizes the album mapping, subtitle, centred play button, now-playing state, tap and long-press menu. |
| `viewmodels/LatestReleasesViewModel.kt` | Orchestration + whitelist re-filter. Owns the `StateFlow<List<LatestRelease>>` the UI observes. Hilt-injected. |
| `ui/screens/HomeScreen.kt` | The Home shelf (`latest_releases_title` / `latest_releases_list` items), rendering each release via `LatestReleaseCard(asGrid = true)`. |
| `ui/screens/LatestReleasesScreen.kt` | The "See all" full-list screen, route `latest_releases`, rendering each release via `LatestReleaseCard(asGrid = false)`. |
| `ui/component/Items.kt` | `subtitleOverride` + `centeredPlayButton` params on `YouTubeGridItem` / `YouTubeListItem`, so a card can show `Artist • <relative date>` and a single can show the centred play button on its artwork. |

## End-to-end flow

```
         (off-device, doc 02)
  server job  --writes-->  recent-releases.json  --served w/ ETag-->  FEED_URL
                                                                         |
  ============================ app (per launch) ==========================|====
                                                                         v
  LatestReleasesViewModel.init                              LatestReleasesStore
    |  initialize(context)                                    cachedReleases()  --> disk cache (instant, no net)
    |  cachedReleases() -----------------------------------------^  |
    |     -> filterReleases() -> _releases.value (instant)          |
    |  refresh() ---------------------------------------------------^  conditional GET (ETag), retry x3
    |     -> filterReleases() -> _releases.value (after net)
    v
  releases: StateFlow<List<LatestRelease>>
    |
    +--> HomeScreen        (take(12) -> LazyRow of YouTubeGridItem cards)
    +--> LatestReleasesScreen ("See all": full list -> LazyColumn of YouTubeListItem rows)
```

The two render paths use **separate** `LatestReleasesViewModel` instances (each `hiltViewModel()`
call resolves in its own nav scope), but the store underneath is a process-wide `object`, so the
cached feed and the refresh are shared regardless — opening "See all" reuses the cache (a
conditional refresh, not a fresh fetch).

## The "never break the rest of the UI" contract

This is the central design constraint, stated in three KDocs and enforced by code:

1. **Isolated ViewModel.** `LatestReleasesViewModel` is "deliberately separate from
   [HomeViewModel] so a failure fetching the external feed can never affect the rest of Home"
   (`LatestReleasesViewModel.kt:22-30`). `HomeViewModel.kt` has no reference to the feed; the
   Home screen obtains the feed ViewModel independently (`HomeScreen.kt:140-141`).

2. **No throwing across the boundary.** Both store entry points "return an empty list rather
   than throwing, so callers gate on `isNotEmpty()`" (`LatestReleasesStore.kt:63`). Every
   network/parse/disk error is caught and logged via Timber, never propagated (doc 03).

3. **Empty is a valid state, never forced content.** The Home shelf only renders when the
   filtered list is non-empty (`HomeScreen.kt:529`, `latestReleasesCapped.takeIf {
   it.isNotEmpty() }`), and the "See all" screen "when the feed is empty/unavailable the list is
   simply empty — nothing is forced" (`LatestReleasesScreen.kt:41-43`).

The net effect: a server outage, a malformed push, or an offline device degrades the feature to
"the shelf isn't there," and nothing else on Home is touched.

## Reuse, not reinvention

The feed carries enough per release (`browseId`, `playlistId`, `title`, `artist`, `thumbnail`,
`year`) to build a standard `AlbumItem` (`LatestReleaseMapping.kt:11-19`). Once mapped, the
release flows through the app's existing machinery unchanged:

- **Filtering:** `filterWhitelisted(database)` — the same function used elsewhere
  (`utils/WhitelistFilter.kt:149`).
- **Rendering:** `YouTubeGridItem` / `YouTubeListItem` — the same album cards/rows.
- **Menu:** `YouTubeAlbumMenu` on long-press.
- **Tap:** `openOrPlay` — a single (`trackCount == 1`) plays via `YouTubeQueue.radio`, anything
  else navigates to `album/<id>`. Both reuse existing queue/navigation; the single's metadata is
  built from the feed (`playableSingle`).

So the only genuinely new code is the store, the ViewModel orchestration, the small adapters
(`toAlbumItem`, `relativeDateLabel`, `playableSingle`/`openOrPlay`), and the screen scaffolding.
