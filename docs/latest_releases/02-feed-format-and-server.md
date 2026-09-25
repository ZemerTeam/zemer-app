# 02 - Feed format & the server

## The JSON schema (`LatestReleasesStore.kt`)

Parsed with `Json { ignoreUnknownKeys = true }`, so the server can add fields without breaking older
apps.

**`LatestReleasesFeed`** - every field defaulted, so `{}` parses to an empty feed:

| Field | Type | Default | Notes |
|---|---|---|---|
| `generatedAt` | `String?` | `null` | ISO-8601 build time. |
| `whitelistVersion` | `String?` | `null` | Logged on apply. |
| `windowDays` | `Int` | `0` | Logged on apply. |
| `count` | `Int` | `0` | Informational. |
| `releases` | `List<LatestRelease>` | empty | **Newest-first**; the app preserves this order. |

**`LatestRelease`**:

| Field | Type | Required | Used for |
|---|---|---|---|
| `artistId` | `String` | yes | Whitelist filtering (the album's artist id). |
| `artistName` | `String` | yes | Subtitle `Artist • <relative date>`. |
| `title` | `String` | yes | Card title. |
| `browseId` | `String` | yes | `MPRE…`; list key and `album/<id>` target. |
| `playlistId` | `String` | yes | `OLAK…`; carried into `AlbumItem`. |
| `thumbnail` | `String` | yes | Artwork URL. |
| `year` | `Int?` | no | Catalog year → `AlbumItem.year` only. |
| `uploadDate` | `String` | yes | ISO-8601; recency sort key and the relative-date label. |
| `trackCount` | `Int?` | no | `1` marks a **single** (plays on tap); absent → opens the album. |
| `sampleVideoId` | `String?` | no | The track that yielded `uploadDate`; what a single plays and what the See-all shuffle uses. |

A release missing a **required** field fails the whole parse; the store then keeps the previous
releases ([03](03-runtime-store.md)). `year` ≠ `uploadDate` (they diverge for re-uploads /
auto-generated art tracks), so recency always uses `uploadDate`.

### Changing the feed

- **Adding** a field is safe. To use it, add it to `LatestRelease` **with a default** so older feeds
  still parse.
- **Renaming/removing a required field** breaks existing apps (they silently keep their last-good
  cache until it expires) - coordinate with an app release.
- `FEED_URL` is a compile-time constant: changing it needs an APK (unlike the cipher config, it is
  not remotely overridable).

## The server

- **URL:** `https://flipphoneguy.duckdns.org/?page=zemer_releases` (`LatestReleasesStore.FEED_URL`),
  served with an ETag.
- The deployed builder is **not in this repo** - it lives in the private VPS repo and runs hourly.
  `tests/recent-releases/build-feed.mjs` is its **validated twin** and the on-repo source of truth
  for the algorithm; keep the two in sync. The app cares only about the JSON's shape and order.

### Builder algorithm (`build-feed.mjs`)

1. Fetch the whitelist (`fetchWhitelist`); keep channel ids starting with `UC`.
2. **Incremental**: prior-feed entries still inside the window are carried forward; a known
   `browseId` is skipped.
3. Per artist (`artistCandidates`): follow the Albums/Singles "more" endpoints to the
   **recency-sorted** discography grids and take the top `TOP` of each (the artist landing-page
   carousel is **not** sorted - never read order off it).
4. Per candidate (`releaseDate`): album browse → track list (`albumTracks`, whose length is
   `trackCount`) → first track's `/player` → `microformat.microformatDataRenderer.uploadDate`
   (available with visitorData only, no cookie). Drop anything older than the window.
5. Sort newest-first by `uploadDate` and write
   `{ generatedAt, whitelistVersion, windowDays, count, releases }`.

### Harness (`tests/recent-releases/`)

Scripts, env vars (`WINDOW`, `TOP`, `CONCURRENCY`, `LIMIT`, `OUT`; output `feed*.json` is gitignored)
and the measured findings are in [`tests/recent-releases/README.md`](../../tests/recent-releases/README.md).
Quick use:

```bash
node --test tests/recent-releases/self-test.mjs        # parsers, no network
WINDOW=14 node tests/recent-releases/build-feed.mjs    # live build
```
