# Offline search backup (`app/src/main/kotlin/com/jtech/zemer/offline/`)

A **fallback for a Zemer-server outage** — not a general offline mode. Every request goes to
`search.zemer.io` first; only when the server is **unreachable** does the app serve a downloaded,
incrementally-synced snapshot of the corpus, so search and browse keep working until the server is
back. Playback is unaffected either way (streaming stays InnerTube + the cipher). The server-side
contract lives in the handoff doc `~/zemer-fix/handoff-docs/zemer-app-ondevice-fallback-subset.md`.

## What the backup serves (and what it never does)

| Endpoint | Offline | Notes |
| --- | --- | --- |
| `/search` | ✅ | Full Hebrew-aware matcher port (`SubsetNormalize`, `SubsetSynonyms`, `SubsetSearch`, `SubsetCategories`). |
| `/artist` | ✅ | `offlineArtist` — gate-as-404, top-songs by play count, year-desc album/single split. |
| `/album` | ✅ | `offlineAlbum` — members in stored order, per-track filter, header carries the real OP `playlistId`. |
| `/home-rows` | ✅ | `offlineHomeRows` — `home_rank` shard order + the live topCommunity computation. |
| `/zemer-playlists` | ✅ | List + detail; `auto-*` raw-order ranks reproduced; chart-movement badges are live-only. |
| `/podcast-genres` | ✅ | `offlinePodcastGenres`/`offlinePodcastGenre` — titles/kinds from the `genrecatalog` shard when present, else the naive slug-uppercase fallback. |
| `/radio` | ⚠️ partial | `offlineRadio` (2026-09-11, the `radio-<n>` shards) — see the "Offline radio" section; `kind == "genre"` stays live-only. |
| `/playlist` | ❌ live-only | Live YouTube expansion — not in the snapshot. |

Fallback responses use the **same wire models** the app decodes from the live server
(`ZemerSearchResponse`, `ZemerArtistResponse`, …), so the routing layer, mapper, and screens consume
them identically — no offline-specific UI path exists.

## Additive shards, 2026-09-11 (offline radio, real-video parity, server-owned genre titles)

Five shards the server always ships now, alongside the ones above (see `SubsetDecoder`'s doc for the
exact wire layouts): `synonyms`, `genrecatalog`, `lyricsflags`, `realvideos`, `radio-<n>` (×4, hash-
bucketed). All are additive to the manifest, so an app predating this addendum downloads and ignores
them (`SubsetDecoder`'s unknown-shard branch); every field on [SubsetCorpus] they populate defaults
empty/null, so a snapshot missing one shard just serves less through that one path, never crashes.

- **`synonyms`** — the app's synonym table now tracks the server's `data/synonyms.json` file instead of
  a hand-copied constant: `SubsetSynonyms.compile(corpus.synonymGroups)` runs once per corpus
  (`BuiltCategories.build`), falling back to `SubsetSynonyms.DEFAULT` when the shard is absent.
- **`genrecatalog`** — resolves the podcast genre catalog's real titles + `kinds` sections
  (`offlinePodcastGenres`/`offlinePodcastGenre`) instead of the naive slug-uppercase guess. The
  shard's MUSIC genre entries (`genreCatalog.genres`/`.kinds`) are decoded too but have no offline
  consumer yet — `/genres` (song-level browse) stays entirely live-only (see
  `ZemerSearchRepository.genres`'s doc): the per-track genre membership that a real offline `/genres`
  would need isn't decoded from the `tracks-*` shard (a separate, earlier addendum).
- **`lyricsflags`** — an affordance hint (`SubsetCorpus.hasLikelyLyrics`): `MediaStoreDownloadManager`
  warms the lyrics cache for a just-downloaded song the hint flags as having a verified text
  (`LyricsStore.ensure`, fire-and-forget, never affects the download), so it is already resolved
  before the user goes offline. The hint is scoped to the Zemer resolver only — it never gates the
  OTHER lyrics providers, which still run their normal chain regardless.
- **`realvideos`** — `offlineHomeRows` now sets `ZemerTrack.realVideo` from the shard, so the offline
  Featured Videos hero shows only real filmed videos (parity with the live `/home-rows` field);
  absent = conservative false, the prior behaviour.
- **`radio-<n>`** — see "Offline radio" below.

### Offline radio (`SubsetRadio.kt`)

A faithful, SCOPED port of `zemer-search/index/radio.mjs`'s ranking blend: `radio(idx, kind, seed,
seedTracks, pass, offset, limit)` builds a deterministic station from the shipped popularity (`pop`)
and up-to-20-neighbour session/library co-occurrence lists, matching the SESS/LIB weights, the
same-artist tier, the era+content-class-leaning popularity backfill, the `h01` tie/shuffle jitter (a
direct fnv-1a port) and the `MAX_RUN=2` artist-diversity pass. `kind == "song" / "artist" / "album" /
"playlist" / "shuffle"` all work; an unresolvable seed degrades gracefully to a plain popularity
station (matching the live server's own behaviour for an obscure/removed seed) rather than failing.

**Disclosed gaps — data the subset genuinely doesn't ship, never faked:** the related-artist tier (no
artist-level cooc graph shard), the skip-dock (no per-track skip-rate shard), and acapella exclusion
(no per-track acapella-membership shard). `kind == "genre"` returns null for the same reason as the
music genre catalog above (no per-track genre membership decoded) — `ZemerSearchRepository.radio`'s
`serverOrOffline` then rethrows the original network error rather than serving a genre-blind station
under that name.

**Continuation tokens are plain and offline-only** (`OfflineRadioToken`, in `SubsetReadLayer.kt`): the
live server's opaque token carries a server-side session (a random shuffle seed); an offline
continuation has none to carry, so the token just encodes the kind/seed/flags/offset back and the
station is recomputed identically — deterministic, so paging is still a pure prefix slice.
`ZemerSearchRepository.radioContinuation` tells an offline token apart from a live one by its
`OfflineRadioToken.PREFIX` before deciding which path to call; an offline page's WHOLE continuation
chain stays offline, it never hands off to the live server mid-station. A malformed/expired offline
token degrades to an empty page (ends the queue), never a crash.

## Architecture (the layers, in dependency order)

1. **Sync engine** — `SubsetManifest` / `SubsetSyncClient` / `SubsetStore` / `OfflineSubsetSyncer`.
   The server manifest lists content-addressed shards (`sha256(gz)[:16]`); `subsetSyncPlan` diffs it
   against the committed local manifest; changed shards download to `.staged` files, are
   hash-verified, and are **promoted only when ALL verified** — then stale shards delete and the
   manifest commits last (temp + rename). A failure at any point leaves the previous snapshot fully
   intact; `SubsetDecoder.loadCorpus` **re-verifies every shard hash at read time** as
   belt-and-suspenders, and a manifest with an unknown `schema` generation is rejected wholesale
   (cipher `schemaVersion` precedent — shard rows are positional and would mis-decode silently).
2. **Decoders + corpus** — `SubsetDecoder` gunzips each shard and builds the in-memory
   `SubsetCorpus` (the Kotlin mirror of the zemer-search SQLite tables); derived maps are lazy. The
   decode guard catches `Throwable` (cancellation rethrown): `OutOfMemoryError` on a low-RAM device
   must degrade to "no snapshot", never crash the path built to degrade gracefully.
3. **Read layer** — `SubsetReadLayer` + `SubsetCategories` + `SubsetSearch` + `SubsetFemale` +
   `SubsetNormalize` + `SubsetSynonyms`: faithful ports of the server's `store.mjs` /
   `categories.mjs` / `search.mjs` / `credits.mjs` / `normalize.mjs`, pinned to the JS with stable
   Kotlin sorts. **Parity with the live server is the correctness bar** — the port was verified
   against captured live responses (id-set + order), and derived thumbnails deliberately match the
   server's `mqdefault` variant (the shared `ytThumb`; do NOT "fix" it to
   `ZemerResultMapper.thumbnailFor`'s `hqdefault` — that helper covers fields the server sends no
   thumbnail for, a different contract).
4. **Provider + routing** — `OfflineReadProvider` caches the decoded corpus behind a
   `SoftReference`, keyed on (manifest version, live-whitelist fingerprint);
   `ZemerSearchRepository.serverOrOffline` does the server-first routing.

## Invariants (regression-prone; enforced by unit tests)

- **Fallback triggers ONLY on server-unreachable** — `isZemerServerUnreachable()`: `IOException`
  **or** `java.nio.channels.UnresolvedAddressException` (Ktor CIO signals no-network/dead-DNS with
  the latter, which is an `IllegalArgumentException`, NOT an `IOException` — the airplane-mode case
  would otherwise never fall back). A 404-null is returned as-is (never a fallback trigger); a
  non-network exception is never masked; cancellation propagates.
- **Only SERVER responses are memoized** in the search LRU — a cached offline response would keep
  serving the reduced snapshot result for the whole process after the server recovers (the
  access-ordered LRU refreshes on every hit, and `invalidate()` only runs from the error-state
  Retry path, which a "successful" cached result never shows).
- **Kosher defenses** (the offline read layer is the THIRD enforcement site of the filtering
  contract — see `docs/whitelist/README.md`):
  - `subsetSnapshotIsFresh` — a snapshot older than **14 days** refuses to serve (an unsyncable
    device must not serve an ever-aging copy).
  - `SubsetCorpus.withLiveWhitelist` — the minutes-fresh Firestore-synced whitelist overlays the
    shard flags at corpus load: de-whitelisted artists are DROPPED with every referencing row, and
    `isFemale` comes from the live flag. An empty live map (whitelist not yet synced) is a no-op.
  - `contentGatePasses` + `idDropped` — ONE shared female/KidZone/video gate + blocked-id check
    across every offline surface; never hand-inline the predicate per site.
- **Sync freshness is mandatory when enabled**: daily auto-update at app start on ANY connection
  (no metered gate — a product decision; incremental diffs are small), launched via
  `OfflineSubsetSyncer.requestSync` on the syncer's OWN scope so leaving a screen never cancels a
  first download. `WhitelistCache` is a `@Volatile` whole-map swap so a concurrent whitelist
  refresh can never expose an empty/partial map to the overlay.

## Surfaces

- **Settings → "Search backup"** (`OfflineSearchSettings` + `OfflineSearchSettingsViewModel`):
  opt-in toggle, size / last-updated / last-error status, Download now.
- **Onboarding step** (`ui/screens/onboarding/OnboardingSearchBackupScreen`): new users choose
  enable (pre-selected, downloads immediately) or "Not now" — declining also silences the promo.
- **One-time promo** (`ui/component/OfflineBackupPromo.kt`): a self-gating dismissible card above
  Zemer search results for existing installs; hides once enabled or dismissed.

## Testing

JVM unit tests, no Android runtime: `SubsetSyncTest` (diff/hash/path-traversal),
`SubsetStoreStagingTest` (staging contract + read-time hash refusal), `SubsetDecoderTest` (per-shard
wire layout, incl. the 2026-09-11 additive shards), `SubsetNormalizeTest` / `SubsetFemaleSynonymsTest`
(ports pinned to real JS output), `SubsetSearchTest` (scoring/filter laws, incl. a corpus-provided
`synonyms` shard expanding a group the built-in table doesn't know), `SubsetRadioTest` (the radio
ranking port: SESS/LIB/same-artist ordering, cold-seed fallback, diversify's MAX_RUN cap, deterministic
paging, `kind == "genre"`/unknown returning null), `SubsetReadLayerTest` (endpoint assembly rules incl.
`offlineArtist`, the `realvideos` wiring, `offlineRadio`'s content gate, `OfflineRadioToken` round-trip),
`SubsetPodcastReadTest` (incl. `genrecatalog`-sourced podcast titles/kinds), `SubsetLiveWhitelistTest`
(overlay cascade + freshness), and `ZemerSearchRoutingTest` (the `serverOrOffline` policy incl.
`UnresolvedAddressException` and cancellation). End-to-end parity vs the live server was verified
during the port over a full corpus build (see the handoff doc).
