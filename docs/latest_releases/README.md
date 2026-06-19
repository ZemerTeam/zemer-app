# Latest Releases — the kosher new-releases feed

Hand-authored docset for the **Latest Releases** feature: a Home-tab shelf of the newest
releases from whitelisted (kosher) artists, newest-first across artists, sourced from a small
precomputed JSON the app fetches from a server.

Everything in these pages is derived from the code in `zemer-app` on the `recent` branch. Every
claim cites the file and symbol that proves it. No assumptions: where a fact came from a live
probe rather than the source, it is marked as such and points at the harness script that proved
it.

## TL;DR

The global YouTube feeds (`FEmusic_new_releases`, charts) carry almost no kosher content, so a
"what's new from kosher artists" shelf cannot be built from them. Instead:

1. A **server job** walks every whitelisted artist's discography, finds releases inside a time
   window, and writes a small JSON (`recent-releases.json`, ~13-15 KB).
2. The app **fetches that JSON** (`LatestReleasesStore`), caches it on disk, and re-filters it
   through the same per-user whitelist filter every other surface uses
   (`LatestReleasesViewModel` -> `filterWhitelisted`).
3. The result renders as a **card shelf on Home** (above Featured Playlists) plus a **"See all"
   screen**, reusing the app's existing album card, album menu, and navigation. Cards show
   `Artist • <relative date>`; a **single** (`trackCount == 1`) shows a centred play button on its
   artwork and plays with autoplay radio on tap, while an **album** keeps the corner play button
   and opens its page.

The feed is treated as an **external dependency that must never break the rest of the UI**: it
has its own ViewModel isolated from `HomeViewModel`, every network/parse failure is swallowed in
favour of the last-good cache, a stale cache self-expires after 3 days, and a server outage
simply makes the shelf empty.

| Where | File |
|---|---|
| Feed URL | `https://api.flipphoneguy.duckdns.org/zemer/recent-releases.json` (`LatestReleasesStore.FEED_URL`) |
| Runtime store | `app/.../latestreleases/LatestReleasesStore.kt` |
| ViewModel | `app/.../viewmodels/LatestReleasesViewModel.kt` |
| Feed -> AlbumItem adapter | `app/.../latestreleases/LatestReleaseMapping.kt` |
| Relative-date label | `app/.../latestreleases/LatestReleaseDate.kt` |
| Tap (play single / open album) | `app/.../latestreleases/LatestReleasePlayback.kt` |
| Home shelf | `app/.../ui/screens/HomeScreen.kt` (`latest_releases_title` / `latest_releases_list`) |
| "See all" screen | `app/.../ui/screens/LatestReleasesScreen.kt`, route `latest_releases` |
| Card subtitle override | `app/.../ui/component/Items.kt` (`YouTubeGridItem`/`YouTubeListItem` `subtitleOverride`) |
| JVM resilience test | `app/src/test/.../latestreleases/LatestReleasesStoreTest.kt` |
| JVM tap-decision test | `app/src/test/.../latestreleases/LatestReleasePlaybackTest.kt` |
| Hard-data harness | `tests/recent-releases/` |

## The pages

1. **[01-architecture-and-data-flow.md](01-architecture-and-data-flow.md)** — the end-to-end
   path from server JSON to rendered card, the modules involved, and the "never break the UI"
   contract.
2. **[02-feed-format-and-server.md](02-feed-format-and-server.md)** — the JSON schema
   (`LatestReleasesFeed` / `LatestRelease`), the feed URL, where the server job lives and how
   often it runs.
3. **[03-runtime-store.md](03-runtime-store.md)** — `LatestReleasesStore`: the disk cache,
   conditional (ETag) fetch, retry-then-give-up policy, 3-day staleness cap, atomic writes, and
   every failure mode it defends against.
4. **[04-viewmodel-and-filtering.md](04-viewmodel-and-filtering.md)** —
   `LatestReleasesViewModel`, the cached-then-refresh sequence, the whitelist re-filter, the
   `toAlbumItem` adapter, and the relative-date label.
5. **[05-ui.md](05-ui.md)** — the Home shelf, the "See all" screen, the route wiring, the
   `Artist • <date>` subtitle, the `openOrPlay` single-vs-album tap, and the visibility/cap rules.
6. **[06-test-harness.md](06-test-harness.md)** — the JVM resilience test and the
   `tests/recent-releases/` hard-data harness (probes, builder twin, no-network parser tests).
7. **[07-runbook.md](07-runbook.md)** — operations: what to check when the shelf is empty or
   wrong, how the server job and the app interact, and how to validate end-to-end.

## One-paragraph mental model

The app does **not** compute the feed; it consumes a JSON a server precomputes. The store
(`LatestReleasesStore`) is an `object` with test seams that holds the last-good feed in memory
and on disk, refreshes it once per launch with a conditional GET, and never throws — the worst a
dead server can do is leave the shelf empty. The ViewModel
(`LatestReleasesViewModel`, deliberately separate from `HomeViewModel`) shows the cached copy
instantly, refreshes once, and re-runs both through `filterWhitelisted` so per-user content
preferences (female / KidZone / Israeli, by artist id) apply exactly as everywhere else. The UI
reuses the app's standard album card and menu, so the shelf looks and behaves like every other
browse shelf. The server algorithm itself was proven in `tests/recent-releases/` against live
YouTube before being deployed.
