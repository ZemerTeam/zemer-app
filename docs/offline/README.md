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
| `/playlist` | ❌ live-only | Live YouTube expansion — not in the snapshot. |
| `/radio` | ❌ live-only | Needs the co-occurrence graph — not shipped. |

Fallback responses use the **same wire models** the app decodes from the live server
(`ZemerSearchResponse`, `ZemerArtistResponse`, …), so the routing layer, mapper, and screens consume
them identically — no offline-specific UI path exists.

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
wire layout), `SubsetNormalizeTest` / `SubsetFemaleSynonymsTest` (ports pinned to real JS output),
`SubsetSearchTest` (scoring/filter laws), `SubsetReadLayerTest` (endpoint assembly rules incl.
`offlineArtist`), `SubsetLiveWhitelistTest` (overlay cascade + freshness), and
`ZemerSearchRoutingTest` (the `serverOrOffline` policy incl. `UnresolvedAddressException` and
cancellation). End-to-end parity vs the live server was verified during the port over a full corpus
build (see the handoff doc).
