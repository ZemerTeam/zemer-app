# tests/recent-releases — kosher latest-releases feed harness

Hard-data tooling for the **Latest Releases** feed: it reproduces the feed builder's exact InnerTube
path against live YouTube and validates the builder. `build-feed.mjs` is the twin of the deployed job
in the private vps repo (`flask_app/apps/api/zemer/`): keep its `build.mjs` / `lib.mjs` /
`whitelist.mjs` in sync with the algorithm here. The feed contract, the builder algorithm and the app
side are in [`docs/latest_releases/`](../../docs/latest_releases/README.md) (algorithm:
[`02-feed-format-and-server.md`](../../docs/latest_releases/02-feed-format-and-server.md)).

Node ≥ 20; reuses `../clients.mjs` + `../cred.mjs` (needs `innertube_cookie.txt` at the repo root —
only its `visitorData` is used; the feed path is anonymous).

## Scripts

| script | what it does |
| --- | --- |
| `lib.mjs` | InnerTube layer (WEB_REMIX `browse`/`player`/`next`) + parsers (`artistReleases`, `artistItemsGrid`, `albumTracks`, `albumFirstTrack`, `findDateFields`, `biggestThumbnail`). |
| `whitelist.mjs` | Reads the `artistsWhitelist` Firestore collection over plain HTTPS with the client API key (repo-root `google-services.json` or `FIREBASE_API_KEY`) — no service-account key. |
| `probe-dates.mjs` | For real kosher artists, dumps every date-bearing field from the artist/album/`next`/`player` responses. |
| `probe-order.mjs` | Proves (a) the discography grid is newest-first by `uploadDate` and (b) `/player` returns the date without a cookie. |
| `build-feed.mjs` | The full feed builder against live YouTube (the deployed job's twin). |
| `self-test.mjs` | No-network unit tests for the `lib.mjs` parsers/helpers — the bits that break when YouTube changes a renderer shape. |

```bash
node tests/recent-releases/probe-dates.mjs                 # date-source probe (live)
node tests/recent-releases/probe-order.mjs                 # ordering + no-cookie probe (live)
WINDOW=14 node tests/recent-releases/build-feed.mjs        # build the feed (live; writes feed.json)
node --test tests/recent-releases/self-test.mjs            # parsers/helpers, no network
```

`build-feed.mjs` env: `WINDOW` (days, default 7), `TOP` (per grid, default 2), `CONCURRENCY`
(default 6), `LIMIT` (cap artists, for a quick sample), `OUT` (output path).

## Findings the builder relies on

- The per-release date is `/player` → `microformat.microformatDataRenderer.uploadDate` (full
  ISO-8601), returned with **visitorData only** (no cookie); the YouTube Data API is not needed.
- The `artistsWhitelist` Firestore collection is readable with the client API key, so the server
  needs no service-account key.
- The artist **discography grid** (the Albums/Singles "more" endpoint) is recency-sorted, so the job
  reads only the top `TOP` per grid; the artist **landing-page carousel** is NOT sorted.
- `year` (catalog year) ≠ `uploadDate` (re-uploads / auto-generated art tracks): sort and window by
  `uploadDate`.
- The album browse lists every track, so `trackCount` comes free (`albumTracks`); the app treats
  `trackCount == 1` as a playable single.
