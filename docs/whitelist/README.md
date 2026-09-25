# Artist whitelist documentation

## Scope

This document covers the artist whitelist as implemented in the app source: database storage, Firestore fetch, in-memory caches, filtering rules, sync consumers, and UI entry points.

## Storage model

### Room entity

`ArtistWhitelistEntity` is stored in table `artist_whitelist` with primary key `artistId` and the following fields:

| Field | Kotlin type / default visible in code | Meaning directly implied by field name and usage |
| --- | --- | --- |
| `artistId` | `String` | Artist identifier and primary key. |
| `artistName` | `String` | Stored display name for the whitelisted artist. |
| `addedAt` | `LocalDateTime = LocalDateTime.now()` | Insert timestamp default. |
| `source` | `String = "firestore"` | Source label default. |
| `lastSyncedAt` | `LocalDateTime = LocalDateTime.now()` | Sync timestamp default. |
| `isFemale` | `Boolean = false` | Used by content filters to block or allow female singers. |
| `isChasid` | `Boolean = false` | Captured from Firestore and stored (the chassidish-promotion feature that consumed it was removed; the filter no longer threads it). |
| `displayName` | `String? = null` | The curated clean single-script name — present ONLY on the ~50 split docs whose legacy `artistName` is a dual "English - עברית" dash form. AUTHORITATIVE for the artist row's name (see the display-name split section). |
| `altName` | `String? = null` | The same name in the other script — a search alias matched by library artist search, the Artists/KidZone browse pills, and `artistByName`. |
| `isGenZ` | `Boolean = false` | Captured from Firestore and stored. |
| `isKids` | `Boolean = false` | Captured from Firestore and stored. |
| `isKidZone` | `Boolean = false` | Used by Kid Zone / non-Kid-Zone DAO queries. |
| `thumbnailUrl` | `@Ignore var String? = null` (body property) | **Transient, NOT a column** (no Room migration): the artist's channel image carried in from the whitelist fetch (mirror/Firestore `thumbnail` field). Consumed by `syncArtistWhitelist` to populate `Artist.thumbnailUrl`; Room ignores it when reading rows back. |

### DAO methods

The DAO exposes these whitelist-specific operations:

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

The DAO also uses `artist_whitelist` in many library queries so local songs, albums, artists, related songs, and search previews are constrained to whitelisted artists.

## Fetch path (content mirror first, Firestore fallback)

Every `WhitelistFetcher` read is `mirrorFirst`: it asks the plain-JSON content mirror (`ZemerContentClient`,
`content.zemer.io`) first and falls back to the Firebase SDK (`FirebaseFirestore.getInstance()`) only when the
mirror call throws (network, non-2xx, empty/invalid body, parse error):

| Read | Mirror (`ZemerContentClient`) | Firestore fallback |
| --- | --- | --- |
| `fetchVersion()` | `version()` → `/whitelist/version` (`gate`) | `databasenumber/latest`: timestamp field `updatedAt` or field `update` as string/long, converted to `Long`. |
| `fetchWhitelist()` | `whitelist()` → `/whitelist` (throws on an empty list) | `artistsWhitelist`: all documents, each valid one mapped to `ArtistWhitelistEntity`. |
| `fetchBlockedIds()` | `blockedIds()` → `/blockedContentIds` | `blockedContentIds`: all documents; each is one id-level **override** (see "Conditional id overrides"). Read-only — the app never writes/deletes this collection. |

For each whitelist document (mirror or `artistsWhitelist`), the fetcher accepts artist ID from `id` or `artistId`, artist name from `name` or `artistName`, and boolean flags from `isFemale`, `isChasid`, `isGenZ`, `isKids`, and `isKidZone`. Missing boolean flags default to `false`. Documents missing ID or name are skipped by the `return@forEach` statements.

### Artist thumbnails (server-carried, fill-only)

Each whitelist doc also carries a **`thumbnail`** (a yt3/lh3 channel-image URL, resolved once server-side;
mirrored by `content.zemer.io/whitelist`). The pipeline, end to end:

- `fetchWhitelist` sets it on the transient `ArtistWhitelistEntity.thumbnailUrl` (both mirror and
  Firestore paths).
- `syncArtistWhitelist` writes it onto `Artist.thumbnailUrl` inside the sync transaction — new artist
  rows get it in their insert; existing rows via `updateArtistThumbnailUrl`, whose SQL is **fill-only**
  (`AND (thumbnailUrl IS NULL OR thumbnailUrl = '')`): a null/blank server value never wipes anything,
  a device-resolved image is never overwritten, and steady-state syncs touch zero rows (no Room
  invalidation churn). The which-rows logic is the pure, tested `artistThumbnailUpdates(...)`.
- If many whitelisted artists lack a thumbnail (>= `MISSING_THUMB_BOOTSTRAP_THRESHOLD`, e.g. an app
  update while the whitelist version is unchanged), the version gate is bypassed once so a full fetch
  can bootstrap them.
- The UI (`WhitelistedArtistListItem`/`GridItem`) requests a small crop via `resize()`
  (`ARTIST_AVATAR_PX`) and, on a load **error** (missing or rotted URL), falls back to the shared
  **`ArtistThumbResolver`** — the ONE on-device resolver (app-wide `Semaphore(4)` bound; definitive
  answers never retried; transient failures retried after a cooldown; column-targeted
  `replaceArtistThumbnailUrl` write). Do not reintroduce per-ViewModel resolvers or a bulk post-sync
  backfill — both patterns were removed for storming InnerTube and racing the sync transaction.

## Runtime caches

| Cache | File | Behavior |
| --- | --- | --- |
| `WhitelistCache` | `app/src/main/kotlin/com/jtech/zemer/utils/WhitelistCache.kt` | Process-wide `@Volatile Map<String, ArtistWhitelistEntity>` swapped WHOLE by `updateAll` (never mutated in place — the old clear-then-refill let a concurrent reader, notably the offline subset's live-whitelist overlay, see an empty/partial whitelist mid-refresh and briefly serve de-whitelisted content), with `get`, `snapshot` (immutable point-in-time view), and `allowedEntries`. `upsert` is gone. |
| `WhitelistEntryCache` | `app/src/main/kotlin/com/jtech/zemer/utils/WhitelistFilter.kt` | Private `ConcurrentHashMap` used by filtering to memoize per-artist DAO lookups. |
| Per-call `artistCache` | `filterWhitelisted` local mutable map | Deduplicates lookup work inside one list filtering call. |
| `BlockedIdsCache` | `app/src/main/kotlin/com/jtech/zemer/utils/BlockedIdsCache.kt` | Process-wide atomic `@Volatile Map<String, String>` (id → reason) of id-level overrides, with `updateAll`, `isBlocked(id, config)`, and pure `serialize`/`parse`. See "Conditional id overrides". |

`WhitelistCache.allowedEntries(config)` filters cached entries through `WhitelistCache.isAllowed`. The only current exclusion in `isAllowed` is: when `config.filtersEnabled` is true and `config.allowFemaleSingers` is false, entries with `isFemale == true` are excluded.

## Content filter configuration

`ContentFilterConfig` contains:

| Property | Default | Code-visible use |
| --- | --- | --- |
| `filtersEnabled` | `true` | If false, `artistMatchesFilters` allows every artist without whitelist membership. |
| `allowFemaleSingers` | `false` | If false while filters are enabled, female singers are excluded. |
| `blockVideos` | `false` | Part of the config state; used outside whitelist membership in content filtering flows. |
| `blockPodcasts` | `false` | The podcast CATEGORY gate: when true (with filters on), `podcastPasses` drops all podcast/episode items. |

`ContentFilterState` keeps the current config in a `MutableStateFlow`, exposes `state` and `current`, and provides `updateConfig`, `updateContentFilters`, and the `hasActiveFilters` property.

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
   - Otherwise allow. (The decision is a plain Boolean; the old `ArtistFilterDecision.isChasidish`
     threading was removed with the chassidish-promotion feature.)

## Conditional id overrides

The artist whitelist is **artist-grained**: blocking a female singer hides all her items, but a *mixed*
channel/artist that the whitelist allows can still leak individual items the server's filters miss (e.g.
a male-primary track *featuring* a woman). Id overrides patch exactly those, item by id, without blocking
the whole channel — and **conditionally**, so they only hide for the users they should.

### Data model

The read-only Firestore collection `blockedContentIds` holds one document per overridden id:

| Field | Meaning |
| --- | --- |
| `id` (or the document id) | The id to hide — matched against `YTItem.id`, i.e. the videoId (song/video), playlistId, or browseId/channelId. One flat table covers every item type. |
| `reason` (or `category`) | Which content-filter setting the block is gated on. `female` → hidden only when `!allowFemaleSingers`; `global` → hidden for everyone. Absent/unknown reasons default to `global` (over-block, never leak). |
| `disabled: true` (optional) | Soft-delete / template: kept in Firestore but never applied, so an override can be turned off without deleting the document. |

`WhitelistFetcher.fetchBlockedIds()` returns `Map<String, String>` (id → reason), skipping `disabled`
documents. The app only ever READS this collection (writes are admin-only by the Firestore rules).

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
  above) and surgically in `ZemerResultMapper.dropBlocked()` for the raw Zemer engine. The id drop is
  safe on Zemer results because it is a specific-id drop, **not** the artist-membership whitelist (which
  the app deliberately never runs over Zemer results, as it would clip legitimate Hebrew/community hits).
- **No-op when empty:** an empty table changes nothing, so the override layer is a pure addition.
- **Covers follow the filtered tracks, not the curator image:** a community/online playlist's raw
  `playlist.thumbnail` (YouTube's curator art) bypasses the filter, so
  `ui/screens/playlist/filteredPlaylistCover(songs)` derives the opened-playlist header cover and the
  saved-to-Library cover from the first surviving track (`songs` is already `filterWhitelisted`-filtered)
  — never the curator image. Otherwise a mostly-female playlist shows a female cover even with female
  blocked.

## Sync integration points

The whitelist appears in these synchronization paths:

| Source file | Whitelist-related behavior |
| --- | --- |
| `app/src/main/kotlin/com/jtech/zemer/utils/SyncUtils.kt` | Owns `isSyncingWhitelist`, `whitelistSyncProgress`, `syncArtistWhitelist`, and calls `filterWhitelisted` while syncing liked/library/uploaded songs, uploaded albums, artist subscriptions, playlists, and playlist contents. Also runs `refreshBlockedIds()` (the id-override table) on both sync paths. |
| `app/src/main/kotlin/com/jtech/zemer/App.kt` | Imports `WhitelistFetcher` and initializes content filter state from preferences. Loads the persisted id-override table (`BlockedIdsCache`) at startup. |
| `app/src/main/kotlin/com/jtech/zemer/MainActivity.kt` | Launches `syncUtils.syncArtistWhitelist()` from multiple startup / state paths and includes `kid_zone` navigation handling. |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/LibraryViewModels.kt` | Calls `syncUtils.syncArtistWhitelist()` from library flows. |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/HomeViewModel.kt` | Uses `WhitelistCache`, `ContentFilterState`, `IsraeliArtistRegistry`, and database whitelist methods in home feed filtering. |
| `app/src/main/kotlin/com/jtech/zemer/offline/SubsetLiveWhitelist.kt` | The offline snapshot's live-whitelist overlay: `SubsetCorpus.withLiveWhitelist(WhitelistCache.snapshot())` runs at corpus load, DROPPING de-whitelisted artists (with every referencing row) and overriding `isFemale` from the live flag — so an admin change reaches offline results on the next app whitelist sync, not the next snapshot download. Paired with a 14-day staleness cap (`subsetSnapshotIsFresh`) and the shared `contentGatePasses`/`idDropped` gates in `offline/SubsetReadLayer.kt` — the THIRD enforcement site of the filtering contract, alongside `filterWhitelisted` and `ZemerResultMapper.dropBlocked`. |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/WhitelistedArtistsViewModel.kt` | Drives the whitelisted artists screen. |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/KidZoneViewModel.kt` | Drives Kid Zone data. |

## UI entry points

| UI route / screen | File | Data role visible from names/imports |
| --- | --- | --- |
| `artists` / `WhitelistedArtistsScreen` | `app/src/main/kotlin/com/jtech/zemer/ui/screens/WhitelistedArtistsScreen.kt` | Main artists tab is wired to whitelisted artists. |
| `kid_zone` / `KidZoneScreen` | `app/src/main/kotlin/com/jtech/zemer/ui/screens/KidZoneScreen.kt` | Kid-zone artist presentation. |
| Content settings | `app/src/main/kotlin/com/jtech/zemer/ui/screens/settings/ContentSettings.kt` | UI for the content filter preferences (allow female content, block videos, block podcasts). |
| Onboarding | `app/src/main/kotlin/com/jtech/zemer/ui/screens/OnboardingScreen.kt` (`OnboardingFlow`) + per-step files under `ui/screens/onboarding/` | Presents content filter setup. |
| Loading | `app/src/main/kotlin/com/jtech/zemer/ui/screens/LoadingScreen.kt` | Runs the forced `syncArtistWhitelist(forceSync = true)`. |

## Whitelist-related Kotlin files

Line counts and declarations regenerated from the tree (first ten `class`/`object`/`interface`/`fun` declarations at top level or one indent).

| File | Lines | Key declarations |
| --- | ---: | --- |
| `app/src/main/kotlin/com/jtech/zemer/App.kt` | 409 | class App, fun onCreate, fun checkForUpdatesOnStartup, fun initializeSettings, fun observeSettingsChanges, fun newImageLoader |
| `app/src/main/kotlin/com/jtech/zemer/MainActivity.kt` | 2498 | class MainActivity, fun requestStoragePermissionsIfNeeded, fun onStart, fun onResume, fun onConfigurationChanged, fun startActivity, fun startActivityForResult, fun launchingOwnActivity, fun onUserLeaveHint, fun onStop |
| `app/src/main/kotlin/com/jtech/zemer/constants/PreferenceKeys.kt` | 659 | class DensityScale, class SliderStyle, class RefreshRateMode, class DownloadAudioFormat, class AudioQuality, class LibraryViewType, fun toggle, class SongFilter, class ArtistFilter, class AlbumFilter |
| `app/src/main/kotlin/com/jtech/zemer/db/DatabaseDao.kt` | 1832 | interface DatabaseDao, fun songsByRowIdAsc, fun songsByCreateDateAsc, fun songsByNameAsc, fun songsByPlayTimeAsc, fun songs, fun likedSongsByRowIdAsc, fun likedSongsByCreateDateAsc, fun likedSongsByNameAsc, fun likedSongsByPlayTimeAsc |
| `app/src/main/kotlin/com/jtech/zemer/db/MusicDatabase.kt` | 654 | class MusicDatabase, fun query, fun transaction, fun close, class InternalDatabase, class Migration5To6, fun onPostMigrate, class Migration6To7, class Migration7To8, class Migration9To10 |
| `app/src/main/kotlin/com/jtech/zemer/db/entities/ArtistWhitelistEntity.kt` | 34 | class ArtistWhitelistEntity |
| `app/src/main/kotlin/com/jtech/zemer/di/SyncModule.kt` | 121 | object SyncModule, fun provideSyncDataStore, fun provideFirebaseFirestore, fun provideUserAuthManager, fun provideDeviceIdGenerator, fun provideMainDataStore, fun provideUserPreferencesRepository, fun provideContentFilterSyncService |
| `app/src/main/kotlin/com/jtech/zemer/playback/MediaLibrarySessionCallback.kt` | 810 | class MediaLibrarySessionCallback, fun onConnect, fun onCustomCommand, fun onPlaybackResumption, fun onGetLibraryRoot, fun onGetChildren, fun onGetItem, fun onSearch, fun onGetSearchResult, fun onSetMediaItems |
| `app/src/main/kotlin/com/jtech/zemer/playback/MusicService.kt` | 3284 | class MusicService, fun beaconStatus, fun streamContentType, fun relayedStreamUrl, fun stopCastRelay, fun startDiscovery, fun downloadCastLib, fun onCreate, fun setupAudioFocusRequest, fun handleAudioFocusChange |
| `app/src/main/kotlin/com/jtech/zemer/playback/queues/LocalAlbumRadio.kt` | 62 | class LocalAlbumRadio, fun getInitialStatus, fun hasNextPage, fun nextPage |
| `app/src/main/kotlin/com/jtech/zemer/playback/queues/YouTubeQueue.kt` | 75 | class YouTubeQueue, fun getInitialStatus, fun hasNextPage, fun nextPage |
| `app/src/main/kotlin/com/jtech/zemer/sync/ContentFilterSyncService.kt` | 336 | class ContentFilterSyncService, fun initialize, fun performManualSync, fun syncToServer, fun setSyncEnabled, fun isSyncEnabled, fun getSyncStatusFlow, fun getUserDevices, fun handleAuthStateChange, fun handlePreferenceChange |
| `app/src/main/kotlin/com/jtech/zemer/sync/UserPreferencesRepository.kt` | 692 | fun toDeviceContentFilters, fun toDeviceMetadata, class UserPreferencesRepository, fun getDocumentId, fun classifyFirebaseError, fun fetchDevicePreferences, fun fetchDevicePreferencesByDeviceId, fun uploadDevicePreferences, fun updateDevicePreferences, fun getUserDevices |
| `app/src/main/kotlin/com/jtech/zemer/sync/models/DevicePreferencesEntity.kt` | 100 | class DeviceContentFilters, fun fromConfig, fun toConfig, class DeviceMetadata, class UserDeviceData, class DevicePreferencesEntity |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/Library.kt` | 541 | fun artistAvatarRequest, fun LibraryArtistListItem, fun WhitelistedArtistListItem, fun LibraryArtistGridItem, fun WhitelistedArtistGridItem, fun LibraryAlbumListItem, fun LibraryAlbumGridItem, fun LibraryPlaylistListItem, fun LibraryPlaylistGridItem, fun WhitelistedPodcastListItem |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/KidZoneScreen.kt` | 164 | fun KidZoneScreen, fun KidZonePodcastsContent, fun openShow |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/NavigationBuilder.kt` | 492 | fun podcastsBlockedRedirect, fun navigationBuilder |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/OnboardingScreen.kt` | 89 | fun OnboardingFlow |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/Screens.kt` | 67 | class Screens, object Home, object Artists, object Podcasts, object KidZone, object Search, object Library |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/SplashScreen.kt` | 166 | fun SplashScreen |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/WhitelistedArtistsScreen.kt` | 112 | fun WhitelistedArtistsScreen, fun ArtistBrowseScreenContent |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/playlist/LocalPlaylistScreen.kt` | 1260 | fun LocalPlaylistScreen, fun LocalPlaylistHeader, fun uriToByteArray |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/settings/ContentSettings.kt` | 664 | class ContentSettingsViewModel, fun signInWithGoogle, fun signInAnonymously, fun signOut, fun performManualSync, fun setSyncEnabled, fun getUserDevices, fun isAutoRestored, fun getRestoredEmail, fun isLocked |
| `app/src/main/kotlin/com/jtech/zemer/utils/ContentFilterConfig.kt` | 87 | class ContentFilterConfig, fun allowsFemale, object ContentFilterState, fun updateConfig, fun updateContentFilters |
| `app/src/main/kotlin/com/jtech/zemer/utils/IsraeliArtistRegistry.kt` | 71 | object IsraeliArtistRegistry, fun isIsraeli, fun ensureLoaded, fun fetchIds, fun resetForTest |
| `app/src/main/kotlin/com/jtech/zemer/utils/SyncUtils.kt` | 1162 | class WhitelistSyncProgress, class SyncUtils, fun syncPodcastSubscriptions, fun syncEpisodesForLater, fun likeSong, fun toggleSavedForPlayer, fun toggleSaveEpisode, fun saveEpisodeLocal, fun syncLikedSongs, fun syncLibrarySongs |
| `app/src/main/kotlin/com/jtech/zemer/utils/UrlValidator.kt` | 82 | object UrlValidator, fun validateAndParseUrl, fun isValidUrl, fun getQueryParameter |
| `app/src/main/kotlin/com/jtech/zemer/utils/WhitelistCache.kt` | 52 | object WhitelistCache, fun updateAll, fun get, fun snapshot, fun allowedEntries, fun isAllowed |
| `app/src/main/kotlin/com/jtech/zemer/utils/BlockedIdsCache.kt` | 70 | object BlockedIdsCache, fun updateAll, fun isBlocked, fun serialize, fun parse |
| `app/src/main/kotlin/com/jtech/zemer/utils/WhitelistFetcher.kt` | 230 | object WhitelistFetcher, fun fetchVersion, fun fetchWhitelist, fun fetchBlockedIds, fun fetchPodcastVersion, fun fetchPodcastWhitelist |
| `app/src/main/kotlin/com/jtech/zemer/utils/WhitelistFilter.kt` | 331 | object WhitelistEntryCache, fun get, fun put, fun isWhitelisted, fun filterWhitelisted, fun shouldKeepPlaylistSong, fun filterWhitelistedWithLocalArtists, fun podcastPasses, fun artistMatchesFilters |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/ArtistViewModel.kt` | 216 | class ArtistViewModel, fun fetchArtistsFromYTM, fun loadMoreEpisodes, class EpisodePageFetch, fun fetchNextEpisodePage, fun drainEpisodeHistoryForSearch, fun radioQueue, fun appendChannelEpisodes |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/HistoryViewModel.kt` | 108 | class HistoryViewModel, fun fetchRemoteHistory, class DateAgo, object Today, object Yesterday, object ThisWeek, object LastWeek, class Other |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/HomeViewModel.kt` | 1008 | class HomeViewModel, class HomeArtistProfile, class HomeUiState, fun loadCachedLocalData, fun saveCachedLocalData, fun hasWhitelist, fun artistBasedQuickPicks, fun loadQuickPicks, fun loadKeepListening, fun loadHomeArtistProfiles |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/KidZoneViewModel.kt` | 99 | class KidZoneViewModel, fun fetchKidPodcasts, fun sync, fun requestThumb |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/LibraryVideosViewModel.kt` | 41 | class LibraryVideosViewModel, fun refresh |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/LibraryViewModels.kt` | 539 | class LibrarySongsViewModel, fun syncLikedSongs, fun syncLibrarySongs, class LibraryArtistsViewModel, fun sync, class LibraryAlbumsViewModel, class LibraryPlaylistsViewModel, class LibraryMixViewModel, class LibraryAutoPlaylistViewModel, class AutoPlaylistsState |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/OnboardingViewModel.kt` | 152 | class OnboardingViewModel, class UiState, fun signInWithGoogle, fun signInAnonymously, fun checkInitialState, fun attemptAutoRestore, fun getRestoredEmail |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/OnlinePlaylistViewModel.kt` | 193 | class OnlinePlaylistViewModel, fun fetchInitialPlaylistData, fun startProactiveBackgroundLoading, fun loadMoreSongs, fun retry, fun onCleared |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/OnlineSearchSuggestionViewModel.kt` | 100 | class OnlineSearchSuggestionViewModel, class SearchSuggestionViewState |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/OnlineSearchViewModel.kt` | 262 | class OnlineSearchViewModel, fun trackSearchOnce, fun loadSummary, fun loadFiltered, fun refresh |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/WhitelistedArtistsViewModel.kt` | 70 | class WhitelistedArtistsViewModel, fun sync, fun requestThumb |
