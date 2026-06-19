# 07 — Runbook

Operational guide for the Latest Releases feature. The app is a consumer of a server-built JSON,
so most "it's broken" cases are either the server feed or the per-user whitelist, not app logic.

## How the pieces relate

```
vps repo job (4x/day, systemd)  --writes-->  recent-releases.json  --ETag-->  FEED_URL
                                                                                  |
app: LatestReleasesStore (fetch + cache)  ->  LatestReleasesViewModel (filter)  -> UI shelf + See-all
```

- **Feed URL:** `https://api.flipphoneguy.duckdns.org/zemer/recent-releases.json`
  (`LatestReleasesStore.FEED_URL`).
- **App refresh cadence:** once per launch, up to 3 attempts, then give up until next launch
  (doc 03). There is no in-session re-poll.
- **Disk cache lifetime:** up to 3 days since last successful fetch; older is dropped (doc 03,
  `MAX_STALE_MS`).

## Symptom -> where to look

### The shelf is missing entirely on Home

The header/list are only emitted when the **filtered** list is non-empty
(`HomeScreen.kt:529`). Work outward:

1. **Is the feed reachable and non-empty?**
   ```bash
   curl -s https://api.flipphoneguy.duckdns.org/zemer/recent-releases.json | head -c 400
   ```
   Expect a JSON with a non-empty `releases`. If it errors or `count` is 0, it's the **server
   job** (vps repo), not the app.
2. **Did the device fetch it?** Check logcat tag `Zemer_LatestReleases`:
   ```bash
   adb logcat -s Zemer_LatestReleases
   ```
   Look for `Fetched N releases (window …, v…)` (success), `Feed unchanged (304)`,
   `Feed fetch HTTP <code>` (server error), or `Gave up after 3 attempts` (network down).
3. **Did the whitelist filter empty it?** The same tag logs
   `After refresh: X releases shown (of Y before whitelist filter)`. If `Y > 0` but `X == 0`, the
   user's content preferences (female / KidZone / Israeli) excluded every release — that's
   correct behaviour, not a bug. Cross-check with `WhitelistFilter` logs (tag from
   `utils/WhitelistFilter.kt`).
4. **Stale-cache expiry.** If the server has been down > 3 days, the cache self-drops and the
   shelf disappears by design (doc 03). Restore the server.

### The shelf shows old releases / wrong order

- Order is **the server's** newest-first order, preserved by the app
  (`LatestReleasesViewModel.filterReleases` keeps feed order; doc 04). If the order looks wrong,
  it's the builder. Re-run the harness twin to check:
  ```bash
  WINDOW=14 node tests/recent-releases/build-feed.mjs
  ```
- "Old" releases within 3 days of the last fetch are expected from cache until the next launch's
  refresh replaces them.

### A card's subtitle shows only the artist (no date)

The subtitle is `joinByBullet(artistName, relativeDateLabel)`, and `joinByBullet` drops null/empty
parts. `relativeDateLabel` returns null on an unparseable `uploadDate`, so the line degrades to
just the artist — see doc 04/05. Check the feed's `uploadDate` is valid ISO-8601.

### A single opens the album instead of playing (or vice-versa)

Tap behaviour is `openOrPlay` -> `playableSingle()`: it plays only when `trackCount == 1` and a
`sampleVideoId` is present (doc 05). If a known single still opens the album:

1. **Is `trackCount` in the served feed?** `curl … | head` and check an entry has
   `"trackCount": 1`. An older cached feed (or a builder not yet redeployed) omits it -> every
   release opens the album by design.
2. **Is the device on a fresh feed?** The cache holds the last-good copy up to 3 days; relaunch to
   force the once-per-launch refresh, and watch `Zemer_LatestReleases` for the refreshed count.
3. If `trackCount` is wrong in the feed, it's the **builder** (`albumTracks`, vps repo) — re-run
   the harness twin to check.

## Validating end-to-end

1. **Server feed shape** — must match `LatestReleasesFeed`/`LatestRelease` (doc 02). The app's
   parser is lenient on unknown keys but strict on the required (non-defaulted) fields; a missing
   required field fails the whole parse (the store then keeps the previous releases — silently).
2. **Store resilience** — `./gradlew :app:testDebugUnitTest --tests "*LatestReleasesStoreTest"`.
3. **Server algorithm** — `node --test tests/recent-releases/self-test.mjs` (no network) and the
   live probes/builder (doc 06).
4. **On device** — install, open Home, watch `adb logcat -s Zemer_LatestReleases`.

## Changing the feed

- **Adding fields to the feed JSON** is safe: `ignoreUnknownKeys = true` means older apps ignore
  them (`LatestReleasesStore.kt:79`). To *use* a new field in the app, add it to `LatestRelease`
  (with a default so older feeds still parse) and thread it through `toAlbumItem` / the card.
- **Renaming or removing a required field** is a breaking change — it fails deserialization on
  existing apps, which silently keep their last-good cache until it expires. Coordinate any such
  change with an app release.
- **Changing the feed URL** means editing `LatestReleasesStore.FEED_URL` and shipping an APK —
  unlike the cipher config, this URL is a compile-time constant, not remotely overridable.
- **Server job changes** live in the vps repo; keep them in sync with the harness twin
  (`tests/recent-releases/`), which is the on-repo source of truth for the algorithm.

## Things that are intentional (don't "fix" them)

- The ViewModel is separate from `HomeViewModel` on purpose (failure isolation) — don't fold it
  in.
- The store swallows all errors and returns empty/last-good — that is the contract, not missing
  error handling. Errors are reported via Timber (tag `Zemer_LatestReleases`), not thrown.
- `explicit = false` in `toAlbumItem` is deliberate — the whitelist is the content gate, and the
  feed carries no explicit flag.
- The 3-day staleness cap is deliberate — it makes a dead server fail "off" rather than showing
  ancient "latest" releases forever.
