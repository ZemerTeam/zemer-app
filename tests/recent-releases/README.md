# tests/recent-releases — kosher latest-releases feed harness

Hard-data tooling for the **Latest Releases** feature: the recent releases (window 7 days by default,
served as 14) from whitelisted (kosher) artists, newest-first across artists. The global YouTube feeds
(`FEmusic_new_releases`, charts) carry almost no kosher content, so the feed is precomputed
**server-side** and served as a small JSON the app fetches cheaply. These scripts proved that approach
against the live CDN and validate the builder (same philosophy as the parent `tests/` harness:
reproduce the app's exact InnerTube path, measure against live YouTube, never reason from convention).

Node ≥ 20; reuses `../clients.mjs` + `../cred.mjs` (needs `innertube_cookie.txt` at the repo root —
only its `visitorData` is used; the feed path is anonymous, no login).

## What it reproduces

| script | what it does |
| --- | --- |
| `lib.mjs` | InnerTube layer (WEB_REMIX `browse`/`player`/`next`) + parsers (`artistReleases`, `artistItemsGrid`, `albumFirstTrack`, `findDateFields`, `biggestThumbnail`). |
| `whitelist.mjs` | Reads the `artistsWhitelist` Firestore collection over plain HTTPS with the client API key (repo-root `google-services.json` or `FIREBASE_API_KEY`) — no admin/service-account key. |
| `probe-dates.mjs` | The make-or-break probe: for real kosher artists, dumps every date-bearing field from the artist/album/`next`/`player` responses. |
| `probe-order.mjs` | Proves (a) the discography grid is newest-first by `uploadDate` and (b) `/player` returns the date without a cookie. |
| `build-feed.mjs` | The full feed builder against live YouTube (validated twin of the deployed VPS job): per artist, top-2 of each grid → date via `/player` → window/sort/dedup → small JSON. |
| `self-test.mjs` | No-network unit tests for the `lib.mjs` parsers/helpers — the bits that break when YouTube changes a renderer shape. |

## Run

```bash
node tests/recent-releases/probe-dates.mjs                 # date-source probe (live)
node tests/recent-releases/probe-order.mjs                 # ordering + no-cookie probe (live)
WINDOW=14 node tests/recent-releases/build-feed.mjs        # build the feed (live; writes feed.json)
node --test tests/recent-releases/self-test.mjs            # parsers/helpers, no network
```

`build-feed.mjs` env: `WINDOW` (days, default 7), `TOP` (per grid, default 2), `CONCURRENCY`,
`LIMIT` (cap artists, for a quick sample), `OUT` (output path).

## Findings (2026-06-18)

- A precise per-release date **exists**: `/player` → `microformat.microformatDataRenderer.uploadDate`,
  full ISO-8601, returned with **visitorData only** (no cookie). The YouTube Data API v3 is blocked on
  the project, and isn't needed.
- The `artistsWhitelist` Firestore collection is **world-readable** with the client API key (~1600
  artists), so the server needs no service-account key.
- The artist **discography grid** (the Albums/Singles "more" endpoint) is recency-sorted, so the job
  only reads the top 2 per grid; the artist **landing-page carousel** is NOT sorted (don't read order
  off it).
- `year` (catalog year) ≠ `uploadDate` (when the video hit YouTube) — they diverge for re-uploads /
  auto-generated art tracks. The feed sorts/windows by `uploadDate` and stores `year` for later use.
- The album browse already lists every track, so the feed stores `trackCount` for free (`albumTracks`).
  The app uses it to tell a **single** (`trackCount == 1`) from an album: tapping a single plays it
  immediately (with autoplay radio), tapping an album opens its page. A live sample was ~89% singles
  (1 track) and ~11% albums (8–12 tracks), with no missing counts.
- A full run over ~1600 artists is ~3 min with 0 rate-limit blocks; the JSON is ~13–15 KB.

## Where it ships

The deployed job is a self-contained copy in the **vps repo** (`~/github/private/vps` →
`flask_app/apps/api/zemer/`), run by a systemd timer 4×/day and served (with ETag) at
`https://api.flipphoneguy.duckdns.org/zemer/recent-releases.json`. Keep `build.mjs` / `lib.mjs` /
`whitelist.mjs` there in sync with the algorithm here. The app fetches that JSON via
`com.jtech.zemer.latestreleases.LatestReleasesStore` (+ `LatestReleasesViewModel` and the Home
"Latest Releases" section); the store's resilience logic is unit-tested in
`app/src/test/.../latestreleases/LatestReleasesStoreTest.kt`.
