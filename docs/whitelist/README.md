# Artist whitelist documentation

The artist whitelist as implemented in the app: storage, fetch, caches, filtering rules, sync
consumers, and UI entry points.

## Storage model

### Room entity

`ArtistWhitelistEntity` is stored in table `artist_whitelist` with primary key `artistId` and the following fields:

| Field | Kotlin type / default | Meaning |
| --- | --- | --- |
| `artistId` | `String` | Artist identifier and primary key. |
| `artistName` | `String` | Stored display name for the whitelisted artist. |
| `addedAt` / `lastSyncedAt` | `LocalDateTime = LocalDateTime.now()` | Insert / sync timestamps. |
| `source` | `String = "firestore"` | Source label. |
| `isFemale` | `Boolean = false` | Used by content filters to block or allow female singers. |
| `isChasid` | `Boolean = false` | Stored only; the filter does not read it. |
| `displayName` | `String? = null` | The curated clean single-script name — present ONLY on split docs whose legacy `artistName` is a dual "English - עברית" dash form. AUTHORITATIVE for the artist row's name (see below). |
| `altName` | `String? = null` | The same name in the other script — a search alias matched by library artist search, the Artists/KidZone browse pills, and `artistByName`. |
| `isGenZ` | `Boolean = false` | Stored only. |
| `isKids` | `Boolean = false` | Kids-only artists are excluded from the telemetry-ranked rows (`RankedContentGate`). |
| `isKidZone` | `Boolean = false` | Used by Kid Zone / non-Kid-Zone DAO queries. |
| `thumbnailUrl` | `@Ignore var String? = null` (body property) | **Transient, NOT a column** (no Room migration): the artist's channel image carried in from the whitelist fetch (mirror/Firestore `thumbnail` field). Consumed by `syncArtistWhitelist` to populate `Artist.thumbnailUrl`; Room ignores it when reading rows back. |

### DAO methods

| Operation | Method |
| --- | --- |
| Upsert one row | `upsert(whitelist: ArtistWhitelistEntity)` |
| Replace-insert a list | `insertWhitelist(whitelistEntries: List<ArtistWhitelistEntity>)` |
| Flow of IDs | `getAllWhitelistedArtistIds()` |
| Suspended list of IDs | `getAllWhitelistedArtistIdsSync()` |
| Suspended lookup by ID | `getWhitelistEntry(artistId: String)` |
| Suspended list of rows | `getWhitelistEntriesSync()` |
| Boolean membership test | `isArtistWhitelisted(artistId: String)` |
| Missing thumbnail IDs | `getWhitelistedArtistIdsMissingThumb(limit: Int)` |
| Fill-only thumbnail write (sync) | `updateArtistThumbnailUrl(artistId, thumbnailUrl)` — only when the row has none |
| Overwriting thumbnail write (fallback resolver) | `replaceArtistThumbnailUrl(artistId, thumbnailUrl)` |
| Delete all whitelist rows | `clearWhitelist()` |
| Apply curated display names (set-based) | `applyWhitelistDisplayNames()` — renames artist rows to the split docs' `displayName`; idempotent, self-terminating, run after `insertWhitelist` on every full fetch |
| Curated display name for one artist (sync) | `whitelistDisplayNameSync(artistId)` — the stale-artist YTM refresh prefers it over the channel title |

### The display-name split (`displayName` / `altName`)

Contract: `handoff-docs/zemer-whitelist-display-names.md`. The legacy `artistName` is FROZEN (old
installs and zemer-search wire credits carry it); `displayName` exists only on split docs and is
authoritative for the artist row's rendered name; `altName` is the cross-script search alias. Three
rules that must not regress: (1) `artistByName` matches all three whitelist names so a legacy
dual-name wire credit still resolves to the whitelisted row after the rename (missing it mints a
generated duplicate — the infinite-album-skeleton class); (2) the rename is the set-based
`applyWhitelistDisplayNames`, never a one-shot legacy-name-guarded UPDATE (that froze installs on
stale names when the curator corrected a `displayName`); (3) `DisplayNamesBackfilledKey` is only set
by a full fetch whose payload actually carried split names (`whitelistCarriesDisplayNames`, pure +
tested), so a stale pre-split mirror snapshot cannot burn the one-time backfill.

Many library DAO queries also JOIN `artist_whitelist`, so local songs, albums, artists, related songs
and search previews are constrained to whitelisted artists.

## Fetch path (content mirror first, Firestore fallback)

Every `WhitelistFetcher` read is `mirrorFirst`: it asks the plain-JSON content mirror (`ZemerContentClient`,
`content.zemer.io`) first and falls back to the Firebase SDK (`FirebaseFirestore.getInstance()`) only when the
mirror call throws (network, non-2xx, empty/invalid body, parse error):

| Read | Mirror (`ZemerContentClient`) | Firestore fallback |
| --- | --- | --- |
| `fetchVersion()` | `version()` → `/whitelist/version` (`gate`) | `databasenumber/latest`: timestamp field `updatedAt` or field `update` as string/long, converted to `Long`. |
| `fetchWhitelist()` | `whitelist()` → `/whitelist` (throws on an empty list) | `artistsWhitelist`: all documents, each valid one mapped to `ArtistWhitelistEntity`. |
| `fetchBlockedIds()` | `blockedIds()` → `/blockedContentIds` | `blockedContentIds`: all documents; each is one id-level **override** (see "Conditional id overrides"). Read-only — the app never writes/deletes this collection. |

For each whitelist document, the fetcher accepts the id from `id` or `artistId`, the name from
`name` or `artistName`, and the boolean flags `isFemale`/`isChasid`/`isGenZ`/`isKids`/`isKidZone`
(missing = `false`). Documents missing an id or name are skipped.

### Artist thumbnails (server-carried, fill-only)

Each whitelist doc also carries a **`thumbnail`** (a channel-image URL resolved server-side):

- `fetchWhitelist` sets it on the transient `ArtistWhitelistEntity.thumbnailUrl` (both mirror and
  Firestore paths).
- `syncArtistWhitelist` writes it onto `Artist.thumbnailUrl` inside the sync transaction — new artist
  rows get it in their insert; existing rows via `updateArtistThumbnailUrl`, whose SQL is **fill-only**
  (`AND (thumbnailUrl IS NULL OR thumbnailUrl = '')`): a null/blank server value never wipes anything,
  a device-resolved image is never overwritten, and steady-state syncs touch zero rows (no Room
  invalidation churn). The which-rows logic is the pure, tested `artistThumbnailUpdates(...)`.
- If many whitelisted artists lack a thumbnail (>= `MISSING_THUMB_BOOTSTRAP_THRESHOLD` = 25, e.g. an
  app update while the whitelist version is unchanged), the version gate is bypassed so a full fetch
  can bootstrap them.
- The UI (`WhitelistedArtistListItem`/`GridItem`) requests a small crop via `resize()`
  (`ARTIST_AVATAR_PX`) and, on a load **error** (missing or rotted URL), falls back to the shared
  **`ArtistThumbResolver`** — the ONE on-device resolver (app-wide `MAX_CONCURRENT` = 4 bound;
  definitive answers never retried; transient failures retried after a cooldown; column-targeted
  `replaceArtistThumbnailUrl` write). Do not reintroduce per-ViewModel resolvers or a bulk post-sync
  backfill — they storm InnerTube and race the sync transaction.

## Runtime caches

| Cache | File | Behavior |
| --- | --- | --- |
| `WhitelistCache` | `utils/WhitelistCache.kt` | Process-wide immutable map held in a `MutableStateFlow` (exposed as the `entries` `StateFlow`) and swapped WHOLE by `updateAll` — never mutated in place, so a concurrent reader (notably the offline live-whitelist overlay) can never see an empty/partial whitelist mid-refresh and serve de-whitelisted content. Also `get`, `snapshot` (point-in-time view), `allowedEntries(config)` and `allowedEntries(database, config)` (refills from the DAO when empty). |
| `WhitelistEntryCache` | `utils/WhitelistFilter.kt` | Private `ConcurrentHashMap` memoizing per-artist DAO lookups. |
| Per-call `artistCache` | `filterWhitelisted` local map | Deduplicates lookups inside one filtering call. |
| `BlockedIdsCache` | `utils/BlockedIdsCache.kt` | `@Volatile Map<String, String>` (id → reason) of id-level overrides, with `updateAll`, `isBlocked(id, config)`, and pure `serialize`/`parse`. See "Conditional id overrides". |

`WhitelistCache.isAllowed` excludes exactly one thing: female entries when filters are enabled and
`allowFemaleSingers` is false.

## Content filter configuration

`ContentFilterConfig` contains:

| Property | Default | Code-visible use |
| --- | --- | --- |
| `filtersEnabled` | `true` | If false, `artistMatchesFilters` allows every artist without whitelist membership. |
| `allowFemaleSingers` | `false` | If false while filters are enabled, female singers are excluded. |
| `blockVideos` | `false` | Not a whitelist-membership input; read by the video gates. |
| `blockPodcasts` | `false` | The podcast CATEGORY gate: when true (with filters on), `podcastPasses` drops all podcast/episode items. |

`ContentFilterState` holds the current config (`state` flow + `current`), updated via `updateConfig` /
`updateContentFilters`.

## Filtering algorithm from `filterWhitelisted`

`List<YTItem>.filterWhitelisted(...)` accepts a `MusicDatabase`, a `ContentFilterConfig`, `requireAllArtists`, and `fallbackArtistId`.

1. It obtains allowed entries from `WhitelistCache.allowedEntries(config)`.
2. If that cache result is empty, it reads `database.getWhitelistEntriesSync()` and refreshes the cache.
3. It builds `allowedIds` from allowed entries if the allowed list is not empty.
4. For each `YTItem`, it first drops the item when `BlockedIdsCache.isBlocked(item.id, config)` is true — the conditional id override (see below), checked before the membership decision. Otherwise it evaluates by concrete type:
   - `SongItem`: checks song artists; empty artist list can fall back to `fallbackArtistId`. An episode
     `SongItem` (`isEpisode`) is instead gated by `podcastPasses` against the podcast channel whitelist.
   - `AlbumItem`: checks album artists; empty artist list can fall back to `fallbackArtistId`.
   - `ArtistItem`: checks the artist ID directly.
   - `PlaylistItem`: checks `author.id`; missing author ID is rejected.
   - `PodcastItem` / `EpisodeItem`: gated by `podcastPasses` (show id + host channel id) against the podcast
     channel whitelist, never the artist one; it drops everything when `blockPodcasts` is on.
5. `requireAllArtists = false` means a song/album is allowed when any listed artist passes. `requireAllArtists = true` requires all listed artists that have IDs to pass and at least one allowed artist to exist.
6. `artistMatchesFilters` implements the membership decision:
   - If filters are disabled, return allowed.
   - If non-empty `allowedIds` exists, return whether `artistId` is in the set.
   - Load `IsraeliArtistRegistry`; if the artist is in that registry, reject.
   - Try per-call cache, private process cache, then DAO `getWhitelistEntry`.
   - If no entry exists, reject.
   - If filters are enabled and female singers are disallowed and the entry is female, reject.
   - Otherwise allow.

## Conditional id overrides

The artist whitelist is **artist-grained**: blocking a female singer hides all her items, but a *mixed*
channel/artist that the whitelist allows can still leak individual items the server's filters miss (e.g.
a male-primary track *featuring* a woman). Id overrides patch exactly those, item by id, without blocking
the whole channel — and **conditionally**, so they only hide for the users they should.

### Data model

`WhitelistFetcher.fetchBlockedIds()` reads the mirror's `/blockedContentIds` (pre-bucketed by reason,
disabled entries dropped server-side) with the Firestore `blockedContentIds` collection as fallback —
one document per overridden id:

| Field | Meaning |
| --- | --- |
| `id` (or the document id) | The id to hide — matched against `YTItem.id`, i.e. the videoId (song/video), playlistId, or browseId/channelId. One flat table covers every item type. |
| `reason` (or `category`) | Which content-filter setting the block is gated on. `female` → hidden only when `!allowFemaleSingers`; `global` → hidden for everyone. Absent/unknown reasons default to `global` (over-block, never leak). |
| `disabled: true` (optional) | Soft-delete / template: kept in Firestore but never applied, so an override can be turned off without deleting the document. |

The result is a `Map<String, String>` (id → reason). The app only ever READS this collection (it is
managed by the separate zemer-admin app).

### Decision (`BlockedIdsCache.isBlocked(id, config)`)

All overrides are inert when `!config.filtersEnabled`. Otherwise: `female` → `!config.allowFemaleSingers`;
`global` / any other reason → always. The snapshot is a single `@Volatile Map`, replaced atomically so a
concurrent reader never sees a half-applied update.

### Sync, caching, and coverage

- **Sync (no user interaction):** `SyncUtils.refreshBlockedIds()` runs inside the automatic
  `syncArtistWhitelist` — on both the synced path and the version-unchanged early-return — so the table
  refreshes whenever the whitelist does. It is best-effort: a failed fetch leaves the previous table
  intact (never silently unblocks).
- **Cached like the whitelist:** persisted to DataStore (`BlockedContentIdsKey`, one `id\treason` per
  line via `BlockedIdsCache.serialize`/`parse`) and loaded into the in-memory table at startup in
  `App.kt`, so the blocklist is active offline / before the first sync of the session.
- **Applied everywhere:** centrally in `filterWhitelisted` (all YouTube/browse/playback surfaces, step 4
  above), surgically in `ZemerResultMapper.dropBlocked()` for Zemer results, and in the offline read
  layer (`offline/SubsetReadLayer.kt` `idDropped`) — a filtering-contract change must land in all three.
  The id drop is
  safe on Zemer results because it is a specific-id drop, **not** the artist-membership whitelist (which
  the app deliberately never runs over Zemer results, as it would clip legitimate Hebrew/community hits).
- **No-op when empty:** an empty table changes nothing, so the override layer is a pure addition.
- **Covers follow the filtered tracks, not the curator image:** a community/online playlist's raw
  `playlist.thumbnail` (YouTube's curator art) bypasses the filter, so `filteredPlaylistCover(songs,
  thumbnailOf)` (`ui/screens/playlist/PlaylistHeaderCover.kt`) derives the opened-playlist header cover
  and the saved-to-Library cover from the first surviving track (`songs` is already
  `filterWhitelisted`-filtered) — never the curator image, or a mostly-female playlist shows a female
  cover even with female blocked.

## Sync integration points

| Source file | Whitelist-related behavior |
| --- | --- |
| `utils/SyncUtils.kt` | Owns `syncArtistWhitelist` (+ `isWhitelistSyncing` / `whitelistSyncProgress`) and runs `refreshBlockedIds()` on every sync path. The account syncs (liked/library songs, liked albums, artist and podcast subscriptions) pass remote lists through `filterWhitelisted`; saved playlists reconcile through `filterWhitelistedWithLocalArtists`. |
| `App.kt` | Initializes content-filter state from preferences and loads the persisted id-override table (`BlockedIdsCache`) at startup. |
| `MainActivity.kt` | Launches `syncUtils.syncArtistWhitelist()` from several startup / state paths. |
| `viewmodels/LibraryViewModels.kt`, `WhitelistedArtistsViewModel.kt`, `KidZoneViewModel.kt` | Call `syncUtils.syncArtistWhitelist()` (pull-to-refresh / screen flows). |
| `viewmodels/HomeViewModel.kt` | Uses `WhitelistCache`, `ContentFilterState`, `IsraeliArtistRegistry`, and the whitelist DAO in home filtering. |
| `offline/SubsetLiveWhitelist.kt` | The offline snapshot's live-whitelist overlay: `SubsetCorpus.withLiveWhitelist(WhitelistCache.snapshot())` runs at corpus load, DROPPING de-whitelisted artists (with every referencing row) and overriding `isFemale` from the live flag — so an admin change reaches offline results on the next app whitelist sync, not the next snapshot download. Paired with a 14-day staleness cap (`subsetSnapshotIsFresh`) and the shared `contentGatePasses`/`idDropped` gates in `offline/SubsetReadLayer.kt` — the THIRD enforcement site of the filtering contract, alongside `filterWhitelisted` and `ZemerResultMapper.dropBlocked`. |

## UI entry points

| UI route / screen | File | Role |
| --- | --- | --- |
| `artists` / `WhitelistedArtistsScreen` | `ui/screens/WhitelistedArtistsScreen.kt` | The Artists tab (whitelisted artists browse). |
| `kid_zone` / `KidZoneScreen` | `ui/screens/KidZoneScreen.kt` | Kid Zone browse. |
| Content settings | `ui/screens/settings/ContentSettings.kt` | Content filter preferences (allow female, block videos, block podcasts). |
| Onboarding | `ui/screens/OnboardingScreen.kt` (`OnboardingFlow`) + `ui/screens/onboarding/` | Content filter setup. |
| Loading | `ui/screens/LoadingScreen.kt` | Runs the forced `syncArtistWhitelist(forceSync = true)`. |

## Core files

`db/entities/ArtistWhitelistEntity.kt`, `db/DatabaseDao.kt` (whitelist DAO methods above),
`utils/WhitelistFetcher.kt` + `utils/ZemerContentClient.kt` (fetch), `utils/WhitelistCache.kt`,
`utils/WhitelistFilter.kt` (`filterWhitelisted`, `artistMatchesFilters`, `podcastPasses`,
`filterWhitelistedWithLocalArtists`), `utils/BlockedIdsCache.kt`, `utils/IsraeliArtistRegistry.kt`,
`utils/ContentFilterConfig.kt`, `utils/SyncUtils.kt` (`syncArtistWhitelist`, `refreshBlockedIds`,
`artistThumbnailUpdates`, `whitelistCarriesDisplayNames`), `utils/ArtistThumbResolver.kt`.
