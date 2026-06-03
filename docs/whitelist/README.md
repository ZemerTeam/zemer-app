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
| `isChasid` | `Boolean = false` | Returned in filter decisions and available for sorting/promotion code paths. |
| `isGenZ` | `Boolean = false` | Captured from Firestore and stored. |
| `isKids` | `Boolean = false` | Captured from Firestore and stored. |
| `isKidZone` | `Boolean = false` | Used by Kid Zone / non-Kid-Zone DAO queries. |

### DAO methods

The DAO exposes these whitelist-specific operations:

| Operation | Method |
| --- | --- |
| Upsert one row | `upsert(whitelist: ArtistWhitelistEntity)` |
| Replace-insert a list | `insertWhitelist(whitelistEntries: List<ArtistWhitelistEntity>)` |
| Flow of IDs | `getAllWhitelistedArtistIds()` |
| Suspended list of IDs | `getAllWhitelistedArtistIdsSync()` |
| Flow of rows | `getAllWhitelistedArtists()` |
| Suspended lookup by ID | `getWhitelistEntry(artistId: String)` |
| Suspended list of rows | `getWhitelistEntriesSync()` |
| Boolean membership test | `isArtistWhitelisted(artistId: String)` |
| Random IDs | `getRandomWhitelistedArtistIds(limit: Int)` |
| Missing thumbnail IDs | `getWhitelistedArtistIdsMissingThumb(limit: Int)` |
| Delete all whitelist rows | `clearWhitelist()` |
| Delete one whitelist row | `removeFromWhitelist(artistId: String)` |

The DAO also uses `artist_whitelist` in many library queries so local songs, albums, artists, related songs, and search previews are constrained to whitelisted artists.

## Firestore fetch path

`WhitelistFetcher` uses `FirebaseFirestore.getInstance()` and reads:

| Firestore path | Code-visible purpose |
| --- | --- |
| `databasenumber/latest` | `fetchVersion()` reads timestamp field `updatedAt` or field `update` as string/long and converts to `Long`. |
| `artistsWhitelist` | `fetchWhitelist()` reads all documents and maps each valid document to `ArtistWhitelistEntity`. |

For each `artistsWhitelist` document, the fetcher accepts artist ID from `id` or `artistId`, artist name from `name` or `artistName`, and boolean flags from `isFemale`, `isChasid`, `isGenZ`, `isKids`, and `isKidZone`. Missing boolean flags default to `false`. Documents missing ID or name are skipped by the `return@forEach` statements.

## Runtime caches

| Cache | File | Behavior |
| --- | --- | --- |
| `WhitelistCache` | `app/src/main/kotlin/com/jtech/zemer/utils/WhitelistCache.kt` | Process-wide `ConcurrentHashMap<String, ArtistWhitelistEntity>` with `updateAll`, `upsert`, `get`, `snapshot`, and `allowedEntries`. |
| `WhitelistEntryCache` | `app/src/main/kotlin/com/jtech/zemer/utils/WhitelistFilter.kt` | Private `ConcurrentHashMap` used by filtering to memoize per-artist DAO lookups. |
| Per-call `artistCache` | `filterWhitelisted` local mutable map | Deduplicates lookup work inside one list filtering call. |

`WhitelistCache.allowedEntries(config)` filters cached entries through `WhitelistCache.isAllowed`. The only current exclusion in `isAllowed` is: when `config.filtersEnabled` is true and `config.allowFemaleSingers` is false, entries with `isFemale == true` are excluded.

## Content filter configuration

`ContentFilterConfig` contains:

| Property | Default | Code-visible use |
| --- | --- | --- |
| `filtersEnabled` | `true` | If false, `artistMatchesFilters` allows every artist without whitelist membership. |
| `allowFemaleSingers` | `false` | If false while filters are enabled, female singers are excluded. |
| `blockVideos` | `true` | Part of the config state; used outside whitelist membership in content filtering flows. |

`ContentFilterState` keeps the current config in a `MutableStateFlow`, exposes `current`, and provides `update`, `updateFromPreferences`, `updateFromServer`, and `reset` methods.

## Filtering algorithm from `filterWhitelisted`

`List<YTItem>.filterWhitelisted(...)` accepts a `MusicDatabase`, a `ContentFilterConfig`, `requireAllArtists`, and `fallbackArtistId`.

1. It obtains allowed entries from `WhitelistCache.allowedEntries(config)`.
2. If that cache result is empty, it reads `database.getWhitelistEntriesSync()` and refreshes the cache.
3. It builds `allowedIds` from allowed entries if the allowed list is not empty.
4. It evaluates each `YTItem` by concrete type:
   - `SongItem`: checks song artists; empty artist list can fall back to `fallbackArtistId`.
   - `AlbumItem`: checks album artists; empty artist list can fall back to `fallbackArtistId`.
   - `ArtistItem`: checks the artist ID directly.
   - `PlaylistItem`: checks `author.id`; missing author ID is rejected.
5. `requireAllArtists = false` means a song/album is allowed when any listed artist passes. `requireAllArtists = true` requires all listed artists that have IDs to pass and at least one allowed artist to exist.
6. `artistMatchesFilters` implements the membership decision:
   - If filters are disabled, return allowed.
   - If non-empty `allowedIds` exists, return whether `artistId` is in the set.
   - Load `IsraeliArtistRegistry`; if the artist is in that registry, reject.
   - Try per-call cache, private process cache, then DAO `getWhitelistEntry`.
   - If no entry exists, reject.
   - If filters are enabled and female singers are disallowed and the entry is female, reject.
   - Otherwise allow and carry the `isChasid` flag in the decision.

## Sync integration points

The whitelist appears in these synchronization paths:

| Source file | Whitelist-related behavior |
| --- | --- |
| `app/src/main/kotlin/com/jtech/zemer/utils/SyncUtils.kt` | Owns `isSyncingWhitelist`, `whitelistSyncProgress`, `syncArtistWhitelist`, and calls `filterWhitelisted` while syncing liked/library/uploaded songs, uploaded albums, artist subscriptions, playlists, and playlist contents. |
| `app/src/main/kotlin/com/jtech/zemer/App.kt` | Imports `WhitelistFetcher` and initializes content filter state from preferences. |
| `app/src/main/kotlin/com/jtech/zemer/MainActivity.kt` | Launches `syncUtils.syncArtistWhitelist()` from multiple startup / state paths and includes `kid_zone` navigation handling. |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/LibraryViewModels.kt` | Calls `syncUtils.syncArtistWhitelist()` from library flows. |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/HomeViewModel.kt` | Uses `WhitelistCache`, `ContentFilterState`, `IsraeliArtistRegistry`, and database whitelist methods in home feed filtering. |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/WhitelistedArtistsViewModel.kt` | Drives the whitelisted artists screen. |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/KidZoneViewModel.kt` | Drives Kid Zone data. |

## UI entry points

| UI route / screen | File | Data role visible from names/imports |
| --- | --- | --- |
| `artists` / `WhitelistedArtistsScreen` | `app/src/main/kotlin/com/jtech/zemer/ui/screens/WhitelistedArtistsScreen.kt` | Main artists tab is wired to whitelisted artists. |
| `kid_zone` / `KidZoneScreen` | `app/src/main/kotlin/com/jtech/zemer/ui/screens/KidZoneScreen.kt` | Kid-zone artist presentation. |
| Content settings | `app/src/main/kotlin/com/jtech/zemer/ui/screens/settings/ContentSettings.kt` | UI for enable content filters and allow female singers preferences. |
| Onboarding | `app/src/main/kotlin/com/jtech/zemer/ui/screens/OnboardingScreen.kt` | Presents content filter setup and runs `syncArtistWhitelist(forceSync = true)`. |
| Contribute | `app/src/main/kotlin/com/jtech/zemer/ui/screens/settings/ContributeScreen.kt` | UI has contribution toggles for `isFemale`, `isChasid`, and `isGenZ`. |

## Whitelist-related Kotlin files

| File | Lines | Key declarations |
| --- | ---: | --- |
| `app/src/main/kotlin/com/jtech/zemer/App.kt` | 412 | class App, val settings, fun sanitizeCookie, val trimmed, val httpClient, val responseText, val json, val visitorData, val clientVersion, val timestamp |
| `app/src/main/kotlin/com/jtech/zemer/MainActivity.kt` | 2140 | class MainActivity, var pendingIntent, var latestVersionName, var playerConnection, val serviceConnection, var dpadKeyMap, val hatTracker, var pendingServiceStart, fun requestStoragePermissionsIfNeeded, val permissions |
| `app/src/main/kotlin/com/jtech/zemer/constants/PreferenceKeys.kt` | 554 | val DynamicThemeKey, val DarkModeKey, val PureBlackKey, val DensityScaleKey, val CustomDensityScaleKey, val DefaultOpenTabKey, val BottomNavigationBarEnabledKey, val SlimNavBarKey, val BottomNavigationItemsKey, val GridItemsSizeKey |
| `app/src/main/kotlin/com/jtech/zemer/db/DatabaseDao.kt` | 1678 | interface DatabaseDao, fun songsByRowIdAsc, fun songsByCreateDateAsc, fun songsByNameAsc, fun songsByPlayTimeAsc, fun songs, val collator, val collator, fun likedSongsByRowIdAsc, fun likedSongsByCreateDateAsc |
| `app/src/main/kotlin/com/jtech/zemer/db/MusicDatabase.kt` | 590 | class MusicDatabase, val delegate, val openHelper, fun query, fun transaction, fun close, class InternalDatabase, val dao, fun newInstance, val startTime |
| `app/src/main/kotlin/com/jtech/zemer/db/entities/ArtistWhitelistEntity.kt` | 21 | class ArtistWhitelistEntity, val artistName, val addedAt, val source, val lastSyncedAt, val isFemale, val isChasid, val isGenZ, val isKids, val isKidZone |
| `app/src/main/kotlin/com/jtech/zemer/di/SyncModule.kt` | 121 | val Context, object SyncModule, fun provideSyncDataStore, fun provideFirebaseFirestore, fun provideUserAuthManager, fun provideDeviceIdGenerator, fun provideMainDataStore, fun provideUserPreferencesRepository, fun provideContentFilterSyncService |
| `app/src/main/kotlin/com/jtech/zemer/playback/MediaLibrarySessionCallback.kt` | 655 | class MediaLibrarySessionCallback, val databaseLazy, val downloadUtil, val database, val scope, var toggleLike, var toggleStartRadio, var toggleLibrary, val connectionResult, val whitelistedArtistIds |
| `app/src/main/kotlin/com/jtech/zemer/playback/MusicService.kt` | 1590 | class MusicService, val database, var audioFocusRequest, var lastAudioFocusState, var wasPlayingBeforeAudioFocusLoss, var hasAudioFocus, val scope, val binder, val waitingForNetworkConnection, val isNetworkConnected |
| `app/src/main/kotlin/com/jtech/zemer/playback/queues/LocalAlbumRadio.kt` | 65 | class LocalAlbumRadio, val albumWithSongs, val startIndex, val database, val endpoint, var continuation, var firstTimeLoaded, val nextResult, val filteredItems, val nextResult |
| `app/src/main/kotlin/com/jtech/zemer/playback/queues/YouTubeAlbumRadio.kt` | 60 | class YouTubeAlbumRadio, var playlistId, val database, val endpoint, var albumSongCount, var continuation, var firstTimeLoaded, val albumSongs, val filteredSongs, val nextResult |
| `app/src/main/kotlin/com/jtech/zemer/playback/queues/YouTubeQueue.kt` | 58 | class YouTubeQueue, var endpoint, val database, var continuation, val nextResult, val filteredItems, val nextResult, val filteredItems, fun radio |
| `app/src/main/kotlin/com/jtech/zemer/sync/ContentFilterSyncService.kt` | 446 | class ContentFilterSyncService, val userPreferencesRepository, val authManager, val serviceScope, val _syncState, val syncState, val _lastSyncResult, val lastSyncResult, var _isApplyingServerPreferences, fun initialize |
| `app/src/main/kotlin/com/jtech/zemer/sync/UserPreferencesRepository.kt` | 760 | fun ContentFilterConfig, fun com, class UserPreferencesRepository, val firestore, val authManager, val deviceIdGenerator, fun getDocumentId, fun classifyFirebaseError, val lastSyncTimeKey, val deviceIdKey |
| `app/src/main/kotlin/com/jtech/zemer/sync/models/DevicePreferencesEntity.kt` | 126 | class DeviceContentFilters, val enableContentFilters, val allowFemaleSingers, val blockVideos, val femalePasscodeHash, fun fromConfig, fun toConfig, class DeviceMetadata, val deviceName, val manufacturer |
| `app/src/main/kotlin/com/jtech/zemer/sync/models/UserPreferencesEntity.kt` | 92 | class UserPreferencesEntity, val userEmail, val userId, val contentFilters, val currentDevice, val allDevices, val isLocked, val createdAt, val updatedAt, fun fromConfig |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/Library.kt` | 410 | fun LibraryArtistListItem, fun WhitelistedArtistListItem, fun LibraryArtistGridItem, fun WhitelistedArtistGridItem, fun LibraryAlbumListItem, fun LibraryAlbumGridItem, fun LibraryPlaylistListItem, fun LibraryPlaylistGridItem |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/KidZoneScreen.kt` | 336 | fun KidZoneScreen, val menuState, var viewType, val firstFocus, val searchFocus, val firstArtistFocus, val artists, val searchQuery, val syncProgress, val isSyncing |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/NavigationBuilder.kt` | 338 | fun NavGraphBuilder, val videoId, val title, val artist |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/OnboardingScreen.kt` | 2077 | class OnboardingStep, class LegalKind, fun NetworkStatusBanner, val context, var isConnected, var isChecking, val newConnectionState, fun OnboardingFlow, val context, val viewModel |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/Screens.kt` | 53 | class Screens, val route, object Home, object Artists, object KidZone, object Search, object Library, val MainScreens |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/SplashScreen.kt` | 165 | fun SplashScreen, var hasTappedSkip, val composition, val lottieColors, val loopingState |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/WhitelistedArtistsScreen.kt` | 404 | fun WhitelistedArtistsScreen, val menuState, var viewType, val firstFocus, val searchFocus, val firstArtistFocus, val artists, val searchQuery, val syncProgress, val isSyncing |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/playlist/LocalPlaylistScreen.kt` | 1503 | fun LocalPlaylistScreen, val context, val menuState, val database, val haptic, val playerConnection, val isPlaying, val mediaMetadata, val playlist, val songs |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/settings/ContentSettings.kt` | 681 | class ContentSettingsViewModel, val authManager, val webAuthManager, val syncService, val userPreferencesRepository, val authState, val syncState, val syncStatus, fun formatLastSyncTime, val sdf |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/settings/ContributeScreen.kt` | 388 | fun ContributeScreen, val uiState, val context, val credentialManager, val scope, val scrollState, fun launchGoogleSignIn, val googleIdOption, val request, val response |
| `app/src/main/kotlin/com/jtech/zemer/utils/ContentFilterConfig.kt` | 108 | class ContentFilterConfig, val filtersEnabled, val allowFemaleSingers, val blockVideos, val femalePasscodeHash, val lastSyncTime, val isSynced, object ContentFilterState, val _state, val state |
| `app/src/main/kotlin/com/jtech/zemer/utils/IsraeliArtistRegistry.kt` | 51 | object IsraeliArtistRegistry, var cachedIds, val mutex, fun isIsraeli, val snapshot, val ids |
| `app/src/main/kotlin/com/jtech/zemer/utils/SyncUtils.kt` | 724 | class WhitelistSyncProgress, val current, val total, val currentArtistName, val isComplete, class SyncUtils, val databaseLazy, val database, val syncScope, val isSyncingLikedSongs |
| `app/src/main/kotlin/com/jtech/zemer/utils/UrlValidator.kt` | 109 | object UrlValidator, fun validateAndParseUrl, val trimmedUrl, val urlWithScheme, val httpUrl, fun isValidUrl, fun isUrlFromTrustedHost, val httpUrl, fun getQueryParameter, val httpUrl |
| `app/src/main/kotlin/com/jtech/zemer/utils/WhitelistCache.kt` | 40 | object WhitelistCache, val memory, fun updateAll, fun upsert, fun get, fun snapshot, var entries, fun allowedEntries, fun isAllowed |
| `app/src/main/kotlin/com/jtech/zemer/utils/WhitelistFetcher.kt` | 72 | object WhitelistFetcher, val firestore, var lastFetchTime, val doc, val updatedAt, val update, val value, val now, val whitelistEntities, val snapshot |
| `app/src/main/kotlin/com/jtech/zemer/utils/WhitelistFilter.kt` | 262 | class ArtistFilterDecision, val allowed, val isChasidish, object WhitelistEntryCache, val memory, fun get, fun put, var anyAllowed, var allAllowed, var isChasidish |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/AccountViewModel.kt` | 81 | class AccountContentType, class AccountViewModel, val database, val playlists, val albums, val artists, val selectedContentType, val likedPlaylists, val likedAlbums, val libraryArtists |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/ArtistItemsViewModel.kt` | 98 | class ArtistItemsViewModel, val database, val artistId, val browseId, val params, val title, val itemsPage, val hideExplicit, fun loadMore, val oldItemsPage |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/ArtistViewModel.kt` | 134 | class ArtistViewModel, val database, val artistId, var artistPage, var isLoading, val libraryArtist, val librarySongs, val libraryAlbums, fun fetchArtistsFromYTM, val hideExplicit |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/BrowseViewModel.kt` | 55 | class BrowseViewModel, val database, val browseId, val items, val title, val allItems |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/ChartsViewModel.kt` | 87 | class ChartsViewModel, val database, val _chartsPage, val chartsPage, val _isLoading, val isLoading, val _error, val error, fun loadCharts, val hideExplicit |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/ContributeViewModel.kt` | 296 | class ContributeArtist, val docId, val artistId, val artistName, val imageUrl, val isFemale, val isChasid, val isGenZ, class ContributeUiState, val isLoading |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/ExploreViewModel.kt` | 78 | class ExploreViewModel, val database, val explorePage, val artists, val favouriteArtists, var favIndex, val artistIds, val firstArtistKey |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/HistoryViewModel.kt` | 108 | class HistoryViewModel, val database, var historySource, val today, val thisMonday, val lastMonday, val historyPage, val events, val date, val daysAgo |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/HomeViewModel.kt` | 1537 | class HomeViewModel, val database, val syncUtils, class HomeArtistProfile, val id, val name, val isAmerican, val isIsraeli, val isFemale, val isFamous |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/KidZoneViewModel.kt` | 73 | class KidZoneViewModel, val database, val syncUtils, val searchQuery, val syncProgress, val isSyncing, fun sync, val allArtists, val filteredByQuery, val thumbRequests |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/LibraryVideosViewModel.kt` | 47 | class LibraryVideosViewModel, val database, val videos, val hideExplicit, val filters, val allowed, val artistIds, fun refresh |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/LibraryViewModels.kt` | 462 | class LibrarySongsViewModel, val syncUtils, val allSongs, fun syncLikedSongs, fun syncLibrarySongs, fun syncUploadedSongs, class LibraryArtistsViewModel, val database, val syncUtils, val allArtists |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/NewReleaseViewModel.kt` | 110 | class NewReleaseViewModel, val database, val _newReleaseAlbums, val newReleaseAlbums, val _newReleaseSongs, val newReleaseSongs, val isLoading, val error, val hideExplicit, val filtered |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/OnboardingViewModel.kt` | 205 | class OnboardingViewModel, val userPreferencesRepository, val authManager, val webAuthManager, val syncService, class UiState, val isCheckingAutoRestore, val hasServerPreferences, val restoredConfig, val contentFiltersAlreadySet |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/OnlinePlaylistViewModel.kt` | 167 | class OnlinePlaylistViewModel, val database, val playlistId, val playlist, val playlistSongs, val _isLoading, val isLoading, val _error, val error, val _isLoadingMore |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/OnlineSearchSuggestionViewModel.kt` | 129 | class OnlineSearchSuggestionViewModel, val database, val query, val _viewState, val viewState, val filters, val whitelist, val matchingArtists, val result, val hideExplicit |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/OnlineSearchViewModel.kt` | 286 | class OnlineSearchViewModel, val database, val query, val initialFilter, val filter, var summaryPage, val viewStateMap, val isSummaryLoading, val summaryError, val filterLoading |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/WhitelistedArtistsViewModel.kt` | 82 | class WhitelistedArtistsViewModel, val database, val syncUtils, val searchQuery, val syncProgress, val isSyncing, val allArtists, val filteredByToggle, val entry, val filteredByQuery |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/YouTubeBrowseViewModel.kt` | 52 | class YouTubeBrowseViewModel, val database, val browseId, val params, val result, val explicitFiltered |
