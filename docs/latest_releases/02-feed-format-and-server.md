# 02 — Feed format and the server

## The JSON schema

The app deserializes the feed with kotlinx.serialization into two data classes
(`LatestReleasesStore.kt:27-48`). The parser is lenient:
`Json { ignoreUnknownKeys = true }` (`LatestReleasesStore.kt:80`), so the server can add fields
without breaking older apps.

### Top level: `LatestReleasesFeed` (`LatestReleasesStore.kt:28-34`)

| Field | Type | Default | Notes |
|---|---|---|---|
| `generatedAt` | `String?` | `null` | ISO-8601 timestamp the feed was built. |
| `whitelistVersion` | `String?` | `null` | The whitelist version the build used; logged on apply (`LatestReleasesStore.kt:167`). |
| `windowDays` | `Int` | `0` | The recency window the server used; logged (`:167`). |
| `count` | `Int` | `0` | Release count; informational. |
| `releases` | `List<LatestRelease>` | `emptyList()` | The releases, **newest-first as the server produced them** (the app preserves this order — doc 04). |

All top-level fields are optional/defaulted, so a `{}` body parses to an empty feed rather than
failing.

### Each release: `LatestRelease` (`LatestReleasesStore.kt:37-48`)

| Field | Type | Required | Used for |
|---|---|---|---|
| `artistId` | `String` | yes | Whitelist filtering (`Artist.id`), the artist whose release this is. |
| `artistName` | `String` | yes | Card subtitle artist name (shown as `Artist • <relative date>` — doc 05). |
| `title` | `String` | yes | Card title (`AlbumItem.title`). |
| `browseId` | `String` | yes | Album browse id (`MPRE…`); the list key and `album/<id>` navigation target. |
| `playlistId` | `String` | yes | Album playlist id (`OLAK…`); carried into `AlbumItem`. |
| `thumbnail` | `String` | yes | Album art URL. |
| `year` | `Int?` | no | Catalog year (`AlbumItem.year`). Distinct from `uploadDate` (see below). |
| `uploadDate` | `String` | yes | ISO-8601 upload timestamp; the recency sort key and the card's relative-date label. |
| `trackCount` | `Int?` | no | Number of tracks on the release. `trackCount == 1` marks a **single**, which the app plays on tap instead of opening (doc 05). Older feeds omit it → the app falls back to opening the album. |
| `sampleVideoId` | `String?` | no | The track whose `/player` response yielded `uploadDate`; the builder's thumbnail fallback, and the videoId the app plays when the release is a single. |

A release missing any **required** (non-defaulted) field fails deserialization of the whole feed
— which the store catches and treats as "keep the previous releases" (doc 03,
`applyFetched`).

### Worked example (from the JVM test fixture, `LatestReleasesStoreTest.kt:21-27`)

```json
{
  "generatedAt": "2026-06-18T00:00:00Z",
  "whitelistVersion": "9",
  "windowDays": 14,
  "count": 2,
  "releases": [
    {"artistId":"UC1","artistName":"A","title":"T1","browseId":"MPRE1","playlistId":"OLAK1",
     "thumbnail":"u1","year":2026,"uploadDate":"2026-06-17T00:00:00-07:00","sampleVideoId":"v1"},
    {"artistId":"UC2","artistName":"B","title":"T2","browseId":"MPRE2","playlistId":"OLAK2",
     "thumbnail":"u2","uploadDate":"2026-06-16T00:00:00-07:00"}
  ]
}
```

The second entry omits the optional `year` and `sampleVideoId`, and neither entry carries the
optional `trackCount` — all still parse (this fixture predates the field; an older cached feed
looks the same, and such releases default to opening the album rather than playing as a single).

## `year` vs `uploadDate` (a proven distinction, not an assumption)

Per the harness findings (`tests/recent-releases/README.md`, "Findings (2026-06-18)"):
`year` (catalog year) is **not** `uploadDate` (when the video hit YouTube) — "they diverge for
re-uploads / auto-generated art tracks. The feed sorts/windows by `uploadDate` and stores `year`
for later use." The app honours this: it sorts/displays recency by `uploadDate`
(`relativeDateLabel`, doc 04) and only passes `year` through to `AlbumItem` for the standard card
subtitle.

## Where the feed comes from

- **URL:** `https://api.flipphoneguy.duckdns.org/zemer/recent-releases.json`
  (`LatestReleasesStore.FEED_URL`, `LatestReleasesStore.kt:69`), served with an ETag (the store
  sends `If-None-Match` and honours `304` — doc 03).
- **Builder location:** the deployed job is **not** in this repo. Per
  `tests/recent-releases/README.md` ("Where it ships"), it is "a self-contained copy in the
  **vps repo** (`~/github/private/vps` -> `flask_app/apps/api/zemer/`), run by a systemd timer
  4x/day." The copy under `tests/recent-releases/` is the **validated twin** of that job (doc
  06) — the algorithm was proven here against live YouTube first, then deployed.
- **Data sources the builder uses (proven by the harness):**
  - The precise date is `/player` -> `microformat.microformatDataRenderer.uploadDate`, full
    ISO-8601, returned with **visitorData only (no cookie)**
    (`tests/recent-releases/README.md`; `lib.mjs:86-91`).
  - The artist whitelist is the `artistsWhitelist` Firestore collection, "**world-readable**
    with the client API key (~1600 artists), so the server needs no service-account key"
    (`README.md`; `whitelist.mjs`).
  - The artist **discography grid** (Albums/Singles "more" endpoint) is recency-sorted, so the
    job reads only the top entries; the artist **landing-page carousel is NOT sorted** — order
    must not be read off it (`README.md`; `lib.mjs:138-163`).

> Keep the deployed builder (vps repo) and the harness twin (`tests/recent-releases/`) in sync —
> the README states this explicitly. The app cares only about the JSON's shape, but the shape and
> ordering are the builder's contract.
