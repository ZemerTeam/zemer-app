# Offline search backup (`app/src/main/kotlin/com/jtech/zemer/offline/`)

A **fallback for a Zemer-server outage** — not a general offline mode. Every request goes to
`search.zemer.io` first; only when the server is **unreachable** does the app serve a downloaded,
incrementally-synced snapshot of the corpus. Playback is unaffected (streaming stays InnerTube + the
cipher). Server-side contract: `handoff-docs/zemer-app-ondevice-fallback-subset.md`.

## What the backup serves

| Endpoint | Offline | Notes |
| --- | --- | --- |
| `/search` | ✅ | Hebrew-aware matcher port (`SubsetNormalize`, `SubsetSynonyms`, `SubsetSearch`, `SubsetCategories`). |
| `/artist` | ✅ | `offlineArtist` — gate-as-404, top songs by play count, year-desc album/single split. |
| `/album` | ✅ | `offlineAlbum` — members in stored order, per-track filter, header carries the album's own `playlistId` (never the MPRE browseId). |
| `/home-rows` | ✅ | `offlineHomeRows` — `home_rank` shard order; topCommunity computed at read time. |
| `/zemer-playlists` | ✅ | List + detail; `auto-*` raw-order ranks reproduced; chart-movement badges are live-only. |
| `/podcast-genres` | ✅ | `offlinePodcastGenres`/`offlinePodcastGenre` — titles/kinds from the `genrecatalog` shard, else a slug-uppercase fallback. |
| `/podcast`, `/podcast-channel`, `/podcasts`, `/podcasts/new-episodes` | ✅ | `OfflineReadProvider` podcast reads. |
| `/radio` | ⚠️ partial | `offlineRadio` — see "Offline radio"; `kind == "genre"` stays live-only. |
| `/playlist`, `/podcast-home-rows`, `/video-home-rows`, `/genres`, `/stations` | ❌ | Live-only. |

Fallback responses use the **same wire models** as the live server (`ZemerSearchResponse`,
`ZemerArtistResponse`, …), so the mapper and screens consume them identically — there is no
offline-specific UI path.

## Additive shards

`synonyms`, `genrecatalog`, `lyricsflags`, `realvideos` and the hash-bucketed `radio-<n>` shards are
additive to the manifest (wire layouts: `SubsetDecoder`). An older app ignores them (the decoder's
unknown-shard branch); every `SubsetCorpus` field they populate defaults empty/null, so a snapshot
missing one just serves less through that path, never crashes.

- **`synonyms`** — `SubsetSynonyms.compile(corpus.synonymGroups)` runs once per corpus
  (`BuiltCategories.build`), so the table tracks the server's; falls back to `SubsetSynonyms.DEFAULT`
  when absent.
- **`genrecatalog`** — real podcast-genre titles + `kinds` sections. The music entries are decoded but
  have no offline consumer: `/genres` stays live-only because per-track genre membership is not decoded
  from the `tracks-*` shard.
- **`lyricsflags`** — an affordance hint (`SubsetCorpus.hasLikelyLyrics`):
  `MediaStoreDownloadManager.prefetchLyricsIfLikely` warms the lyrics cache (`LyricsStore.ensure`,
  fire-and-forget) for a just-downloaded flagged song. It only decides whether to prefetch; it never
  gates any lyrics provider or the download.
- **`realvideos`** — `offlineHomeRows` sets `ZemerTrack.realVideo`, so the offline Featured Videos hero
  shows only real filmed videos (live-field parity); absent = false.

### Offline radio (`SubsetRadio.kt`)

A faithful, SCOPED port of the server's `index/radio.mjs` ranking blend over the shipped popularity
(`pop`) and up-to-20-neighbour session/library co-occurrence lists: the SESS/LIB weights, the
same-artist tier, the era + content-class popularity backfill, the `h01` tie/shuffle jitter (fnv-1a) and
the `MAX_RUN = 2` artist-diversity pass. `song` / `artist` / `album` / `playlist` / `shuffle` all work;
an unresolvable seed degrades to a plain popularity station rather than failing.

**Disclosed gaps — never faked:** the related-artist tier, the skip-dock and acapella exclusion (no
shard carries their data). `kind == "genre"` (or an unknown kind) returns null, so `serverOrOffline`
rethrows the original network error instead of serving a genre-blind station under that name.

**Continuation tokens are self-describing and offline-only** (`OfflineRadioToken` in
`SubsetReadLayer.kt`): the token encodes kind/seed/flags/offset and the deterministic station is
recomputed, so paging is a pure prefix slice. `ZemerSearchRepository.radioContinuation` routes by
`OfflineRadioToken.PREFIX`; an offline chain stays offline and never hands off to the live server
mid-station. A malformed token (`parse` → null, incl. a negative offset) ends the station, never crashes.

## Architecture (in dependency order)

1. **Sync engine** — `SubsetManifest` / `SubsetSyncClient` / `SubsetStore` / `OfflineSubsetSyncer`. The
   manifest lists content-addressed shards; `subsetSyncPlan` diffs it against the committed local
   manifest; changed shards download to `.staged` files, are hash-verified, and are **promoted only
   when ALL verified** — then stale shards delete and the manifest commits last (temp + rename). A
   failure anywhere leaves the previous snapshot intact; `SubsetDecoder.loadCorpus` **re-verifies every
   shard hash at read time**, and a manifest with an unknown `schema` generation is rejected wholesale
   (shard rows are positional and would mis-decode silently).
2. **Decoders + corpus** — `SubsetDecoder` gunzips each shard into the in-memory `SubsetCorpus`
   (mirror of the server's SQLite tables); derived maps are lazy. The decode guard catches `Throwable`
   (cancellation rethrown): an `OutOfMemoryError` on a low-RAM device must degrade to "no snapshot".
3. **Read layer** — `SubsetReadLayer` + `SubsetCategories` + `SubsetSearch` + `SubsetFemale` +
   `SubsetNormalize` + `SubsetSynonyms`: ports of the server's `store.mjs` / `categories.mjs` /
   `search.mjs` / `credits.mjs` / `normalize.mjs`, with stable Kotlin sorts. **Parity with the live
   server (id-set + order) is the correctness bar.** Derived thumbnails deliberately use the server's
   `mqdefault` variant (the shared `ytThumb`) — do NOT "fix" them to `ZemerResultMapper.thumbnailFor`'s
   `hqdefault`, a different contract that would break the parity diff.
4. **Provider + routing** — `OfflineReadProvider` caches the decoded corpus behind a `SoftReference`,
   keyed on (manifest version, live artist + podcast-channel whitelist fingerprint) and overlaid with
   `withLiveWhitelist` + `withLivePodcastWhitelist`; `serverOrOffline` (top-level in
   `ZemerSearchRepository.kt`) does the server-first routing.

## Invariants (enforced by unit tests)

- **Fallback triggers ONLY on server-unreachable** — `isZemerServerUnreachable()`: `IOException` **or**
  `java.nio.channels.UnresolvedAddressException` (Ktor CIO's no-network/dead-DNS signal, an
  `IllegalArgumentException` — without it airplane mode would never fall back). A 404-null is returned
  as-is; a non-network exception is never masked; cancellation propagates.
- **Only SERVER responses are memoized** in the search LRU — the access-ordered LRU (`CACHE_SIZE` 12) has
  no TTL and refreshes an entry on every hit, and `invalidate()` only runs from the error-state
  Retry, so a cached offline result would outlive the outage.
- **Kosher defenses** (the offline read layer is the THIRD enforcement site of the filtering contract —
  `docs/whitelist/README.md`):
  - `subsetSnapshotIsFresh` — a snapshot older than `SUBSET_MAX_SNAPSHOT_AGE_MS` (14 days) refuses to
    serve.
  - `SubsetCorpus.withLiveWhitelist` — the live synced whitelist overlays the shard flags at corpus
    load: de-whitelisted artists are DROPPED with every referencing row, and `isFemale` comes from the
    live flag. An empty live map (not yet synced) is a no-op.
  - `contentGatePasses` + `idDropped` — ONE shared female/KidZone/video gate + blocked-id check across
    every offline surface; never hand-inline the predicate per site.
- **Enabled = daily auto-update on ANY connection** (no metered gate — a product decision): `App.kt`
  runs `OfflineSubsetSyncer.maybeSync()` shortly after start, gated on `AUTO_UPDATE_INTERVAL_MS` (24 h).
  UI-triggered downloads go through `requestSync`, on the syncer's OWN scope, so leaving a screen never
  cancels a download. `WhitelistCache` is a `@Volatile` whole-map swap so a concurrent refresh never
  exposes an empty/partial map to the overlay.

## Surfaces

- **Settings → Search backup** (`OfflineSearchSettings` + `OfflineSearchSettingsViewModel`): opt-in
  toggle, size / last-updated / last-error status, Download now.
- **Onboarding step** (`ui/screens/onboarding/OnboardingSearchBackupScreen`): enable (pre-selected,
  downloads immediately) or "Not now" — declining also silences the promo.
- **One-time promo** (`ui/component/OfflineBackupPromo.kt`): a self-gating dismissible card above
  Zemer search results for existing installs; hides once enabled or dismissed.

## Testing

JVM unit tests, no Android runtime (`app/src/test/.../offline/` unless noted): `SubsetSyncTest`
(diff/hash/path-traversal), `SubsetStoreStagingTest` (staging + read-time hash refusal),
`SubsetDecoderTest` (per-shard wire layout), `SubsetNormalizeTest` / `SubsetFemaleSynonymsTest` (pinned
to real JS output), `SubsetSearchTest` (scoring/filter laws, corpus `synonyms` expansion),
`SubsetRadioTest` (ranking order, cold-seed fallback, `MAX_RUN`, deterministic paging, genre/unknown →
null), `SubsetReadLayerTest` (endpoint assembly, `realvideos`, `offlineRadio`'s gate, token round-trip),
`SubsetPodcastReadTest` (incl. `genrecatalog` titles/kinds), `SubsetLiveWhitelistTest` (overlay cascade +
freshness), and `search/ZemerSearchRoutingTest` (the `serverOrOffline` policy incl.
`UnresolvedAddressException` and cancellation).
