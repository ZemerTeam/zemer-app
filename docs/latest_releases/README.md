# Latest Releases - the kosher new-releases feed

Hand-authored docset for the **Latest Releases** feature: a Home shelf of the newest releases from
whitelisted artists, newest-first across artists, sourced from a small JSON a server precomputes.

The global YouTube feeds (`FEmusic_new_releases`, charts) carry almost no kosher content, so:

1. A **server job** walks every whitelisted artist's discography, keeps releases inside a time
   window, and writes `recent-releases.json` ([02](02-feed-format-and-server.md)).
2. The app **fetches it** (`LatestReleasesStore`), caches it on disk, and **re-filters** it through
   the same `filterWhitelisted` every other surface uses (`LatestReleasesViewModel`).
3. It renders as a **carousel shelf on Home** (Music tab, after Music Status, directly above Zemer
   Playlists) plus a **"See all" screen**. A **single** (`trackCount == 1` with a `sampleVideoId`) plays
   with radio on tap; anything else (an **album**) opens its page.

## The "never break the rest of the UI" contract

The feed is an external dependency. Rules that must not regress:

- **`LatestReleasesViewModel` stays separate from `HomeViewModel`** - a feed failure can never
  affect the rest of Home. Don't fold it in.
- **The store never throws**: every network/parse/disk error is logged (Timber tag
  `Zemer_LatestReleases`) and answered with the last-good copy or an empty list. That is the
  contract, not missing error handling.
- **Empty is a valid state**: the Home shelf is emitted only when the filtered list is non-empty; See
  all simply shows an empty list.
- **The 3-day staleness cap is deliberate**: a dead server makes the shelf disappear rather than show
  ancient "latest" releases forever.

## Pages

1. [Architecture, data flow & filtering](01-architecture-and-data-flow.md) - modules, the
   ViewModel's cached-then-refresh sequence, the reactive whitelist re-filter, the adapters.
2. [Feed format & the server](02-feed-format-and-server.md) - the JSON schema, the builder, the
   `tests/recent-releases/` harness.
3. [The runtime store](03-runtime-store.md) - disk cache, ETag fetch, retry/give-up, staleness,
   failure modes.
4. [The UI](04-ui.md) - the Home shelf, See all, the single-vs-album tap.

## Troubleshooting

| Symptom | Check |
|---|---|
| Shelf missing on Home | 1. `curl -s "https://flipphoneguy.duckdns.org/?page=zemer_releases" \| head -c 400` - an error or empty `releases` is the **server job**. 2. `adb logcat -s Zemer_LatestReleases`: `Fetched N releases …`, `Feed unchanged (304)`, `Feed fetch HTTP <code>`, `Gave up after 3 attempts …`. 3. `Showing X releases (of Y before whitelist filter)` with `X == 0 < Y` means the user's content preferences excluded everything - correct behaviour. 4. Server down > 3 days → cache self-dropped by design. |
| Wrong order / old releases | Order is the server's, preserved by the app; re-run the builder twin (`WINDOW=14 node tests/recent-releases/build-feed.mjs`). Cached releases persist until the next launch's refresh. |
| Subtitle shows only the artist | `relativeDateLabel` returned null: the feed's `uploadDate` isn't valid ISO-8601. |
| A single opens the album | The served/cached feed lacks `"trackCount": 1` or `sampleVideoId` (older builder or cache) - relaunch after the feed is fixed; a wrong count is the builder's `albumTracks`. |

## Tests

`./gradlew :app:testDebugUnitTest --tests "*LatestRelease*"` runs the four JVM tests in
`app/src/test/kotlin/com/jtech/zemer/latestreleases/`: `LatestReleasesStoreTest` (store resilience,
[03](03-runtime-store.md)), `LatestReleasesVisibilityTest` (reactive re-filter,
[01](01-architecture-and-data-flow.md)), `LatestReleasePlaybackTest` and `LatestReleaseFilterTest`
(tap decision, now-playing match, shuffle source, See-all filter, [04](04-ui.md)). The UI rendering
reuses existing card components and has no Compose test. The server algorithm is covered by
`tests/recent-releases/` ([02](02-feed-format-and-server.md)).
