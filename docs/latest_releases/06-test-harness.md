# 06 — Tests and the hard-data harness

Three test layers cover this feature: a JVM unit test pinning the store's resilience, a JVM unit
test pinning the single-vs-album tap decision, and the `tests/recent-releases/` harness that
proved the **server algorithm** against live YouTube before it was deployed.

## JVM unit test — store resilience (`app/src/test/.../latestreleases/LatestReleasesStoreTest.kt`)

Pure JVM (no Android runtime, no server). Drives the store through its test seams
(`cacheDirForTest`, `nowProvider`, `fetcher`, `retryDelayMs` — doc 03) and asserts the contract
"that must never break the UI" (`:12-17`). The six cases:

| Test (`:49-115`) | Pins |
|---|---|
| `refresh parses, returns newest-first, and caches to disk` | A 200 parses, returns the feed order, and is readable back from disk after clearing memory. |
| `repeated failure gives up after MAX_ATTEMPTS and stops refetching until next launch` | Exactly 3 fetch calls, then `gaveUp` suppresses further network. |
| `a failed refresh keeps the last-good cache instead of clearing it` | After a good fetch, a later failure still returns the cached 2 releases. |
| `cache older than 3 days is dropped` | With the clock advanced +4 days, `cachedReleases()` is empty. |
| `not-modified keeps the cached releases` | A 304 returns the cached releases unchanged. |
| `an unparseable body keeps the previous releases` | A 200 with `"not json"` keeps the prior 2 releases. |

`setUp`/`tearDown` (`:29-47`) point the cache dir at a temp dir, pin the clock to a constant
`NOW`, set `retryDelayMs = 0L` (no real sleeping), and reset in-memory state between cases.

Run it with the app's unit tests:

```bash
./gradlew :app:testDebugUnitTest --tests "*LatestReleasesStoreTest"
```

> There is no instrumented/Compose UI test for the shelf — the rendering is standard reuse of
> existing, already-covered card components, so the new logic worth testing is the store
> (covered above), the tap decision (below), and the server algorithm (covered below).

## JVM unit test — the tap decision (`app/src/test/.../latestreleases/LatestReleasePlaybackTest.kt`)

Pure JVM (no player, no Android runtime). Pins `LatestRelease.playableSingle()` (the heart of
`openOrPlay`), `isPlayableSingle()` (which drives both the tap and the centred play icon),
`isNowPlaying()` (the card's active state), and the shuffle-FAB track selection
(`sampleMediaMetadata()` / `sampleTracks()`) — doc 05. Ten cases:

| Test | Pins |
|---|---|
| `a one-track release is a playable single carrying its track metadata` | `trackCount == 1` -> `MediaMetadata` with the `sampleVideoId`, title, and artist. |
| `a multi-track release is not a single (opens the album)` | `trackCount > 1` -> `null` (so `openOrPlay` navigates to the album). |
| `a single with no videoId cannot be played (opens the album)` | `trackCount == 1` but null/blank `sampleVideoId` -> `null`. |
| `an older feed entry with no track count opens the album` | `trackCount == null` -> `null`. |
| `isPlayableSingle drives the centred play icon and agrees with what plays on tap` | `isPlayableSingle()` is true only for a playable single, and equals `playableSingle() != null` — so the centred icon never promises playback the tap won't deliver. |
| `a single is active when its videoId is the current track (not via album id)` | a single matches on `mediaMetadata.id == sampleVideoId` (it carries no album), so a plain album-id check would never light. |
| `an album release is active when a track from that album (browseId) is playing` | an album matches on `mediaMetadata.album.id == browseId`. |
| `the metadata a single actually plays makes its own card active` | feeding `playableSingle()` straight back into `isNowPlaying()` returns true — the regression guard for the bug fixed in this iteration. |
| `sampleMediaMetadata builds a track for any release with a videoId (single or album)` | a sample track is built whenever `sampleVideoId` exists — even for a multi-track album — and is null otherwise. |
| `sampleTracks keeps the sample of every release that has a videoId, preserving order` | the shuffle FAB's source list is every release's sample (albums included), in feed order, dropping only those with no playable sample. |

```bash
./gradlew :app:testDebugUnitTest --tests "*LatestReleasePlaybackTest"
```

## Hard-data harness — `tests/recent-releases/`

Node >= 20. Reuses the parent harness's `../clients.mjs` + `../cred.mjs` (needs
`innertube_cookie.txt` at the repo root, but only its `visitorData` — the feed path is anonymous;
`tests/recent-releases/README.md`). These scripts reproduce the app's exact InnerTube path and
measure against live YouTube, the same philosophy as the parent `tests/` harness: never reason
from convention.

| Script | What it does |
|---|---|
| `lib.mjs` | The request layer (WEB_REMIX `browse`/`next`/`player`, faithful to `InnerTube.kt`: `browse` is `setLogin=false` -> visitorData only; `next`/`player` can run authed) plus the parsers (`artistReleases`, `artistItemsGrid`, `albumTracks` + `albumFirstTrack`, `findDateFields`, `biggestThumbnail`). |
| `whitelist.mjs` | Reads the `artistsWhitelist` Firestore collection over plain HTTPS with the client API key — no service-account key. |
| `probe-dates.mjs` | The make-or-break probe: dumps every date-bearing field from artist/album/`next`/`player` responses for real kosher artists. |
| `probe-order.mjs` | Proves (a) the discography grid is newest-first by `uploadDate` and (b) `/player` returns the date with no cookie. |
| `build-feed.mjs` | The full feed builder — the **validated twin** of the deployed VPS job. |
| `self-test.mjs` | No-network unit tests for the `lib.mjs` parsers/helpers (the bits that break when YouTube changes a renderer shape). |

### Run

```bash
node tests/recent-releases/probe-dates.mjs             # date-source probe (live)
node tests/recent-releases/probe-order.mjs             # ordering + no-cookie probe (live)
WINDOW=14 node tests/recent-releases/build-feed.mjs     # build the feed (live; writes feed.json)
node --test tests/recent-releases/self-test.mjs         # parsers/helpers, no network
```

`build-feed.mjs` env (`build-feed.mjs:24-30`): `WINDOW` (days, default 7), `TOP` (per grid,
default 2), `CONCURRENCY` (default 6), `LIMIT` (cap artists, for a quick sample), `OUT` (output
path; defaults to `feed.json`, which is gitignored — `.gitignore:114-115`).

### The builder algorithm (`build-feed.mjs`)

This documents the *server's* algorithm — the app does not run it, but the feed's shape and order
are this job's contract:

1. Fetch the whitelist (`fetchWhitelist`), keep channels whose id starts with `UC` (`:90`).
2. **Incremental:** load the prior feed, keep entries still inside the window as a baseline
   (`:94-100`); a release already known is skipped, and because the discography grid is
   recency-sorted, hitting a known/old entry stops scanning that grid (`:1-8` header).
3. Per artist (`artistCandidates`, `:50-84`): read the artist page, follow the distinct
   Albums/Singles "more" endpoints to the recency-sorted grids, take the **top `TOP`** of each,
   de-duped by `browseId`. If there is no "see all" grid, use the inline artist-page releases.
4. Per candidate (`releaseDate`, `:37-47`): album browse -> track list (`albumTracks`) -> first
   track -> `/player` -> `uploadDate` from `microformat`. The track-list length is stored as
   `trackCount` (free — the browse already lists every track). Skip if older than the window.
5. Sort newest-first by `uploadDate`, write
   `{ generatedAt, whitelistVersion, windowDays, count, releases }` (`:132-137`) — the exact shape
   the app deserializes (doc 02).

### Findings the harness established (`tests/recent-releases/README.md`, "Findings (2026-06-18)")

These are *measured*, not assumed:

- A precise per-release date exists at `/player` ->
  `microformat.microformatDataRenderer.uploadDate`, full ISO-8601, **returned with visitorData
  only (no cookie)**. The YouTube Data API v3 is blocked on the project and isn't needed.
- The `artistsWhitelist` Firestore collection is **world-readable** with the client API key
  (~1600 artists), so the server needs no service-account key.
- The artist **discography grid** is recency-sorted (job reads top 2 per grid); the artist
  **landing-page carousel is NOT sorted** — don't read order off it.
- `year` != `uploadDate`; the feed sorts/windows by `uploadDate`, stores `year`.
- A full run over ~1600 artists is ~3 min with 0 rate-limit blocks; the JSON is ~13-15 KB.

### Keeping the twin in sync

The deployed job is a self-contained copy in the **vps repo** (`flask_app/apps/api/zemer/`), run
by a systemd timer 4x/day, served with ETag at `FEED_URL`
(`tests/recent-releases/README.md`, "Where it ships"). The README instructs keeping
`build.mjs`/`lib.mjs`/`whitelist.mjs` there in sync with the algorithm here.
