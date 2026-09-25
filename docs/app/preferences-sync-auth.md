# Preferences, sync, and auth documentation

## Preference key inventory

Preference keys extracted from `PreferenceKeys.kt`: `163`.

| Kotlin val | Key type | Stored name |
| --- | --- | --- |
| `PlaybackModeKey` | `stringPreferencesKey` | `playbackMode` |
| `RelayDeviceIdKey` | `stringPreferencesKey` | `relayDeviceId` |
| `DynamicThemeKey` | `booleanPreferencesKey` | `dynamicTheme` |
| `EnableHighRefreshRateKey` | `booleanPreferencesKey` | `enableHighRefreshRate` |
| `RefreshRateModeKey` | `stringPreferencesKey` | `refreshRateMode` |
| `SelectedThemeColorKey` | `intPreferencesKey` | `selectedThemeColor` |
| `DarkModeKey` | `stringPreferencesKey` | `darkMode` |
| `PureBlackKey` | `booleanPreferencesKey` | `pureBlack` |
| `DensityScaleKey` | `floatPreferencesKey` | `density_scale_factor` |
| `CustomDensityScaleKey` | `floatPreferencesKey` | `custom_density_scale_value` |
| `DefaultOpenTabKey` | `stringPreferencesKey` | `defaultOpenTab` |
| `HomeContentTabKey` | `stringPreferencesKey` | `homeContentTab` |
| `BottomNavigationBarEnabledKey` | `booleanPreferencesKey` | `bottomNavigationBarEnabled` |
| `SlimNavBarKey` | `booleanPreferencesKey` | `slimNavBar` |
| `BottomNavigationItemsKey` | `stringPreferencesKey` | `bottomNavigationItems` |
| `BottomNavArtistsRemovedKey` | `booleanPreferencesKey` | `bottom_nav_artists_removed` |
| `RecognizeMusicFabKey` | `booleanPreferencesKey` | `recognizeMusicFab` |
| `GridItemsSizeKey` | `stringPreferencesKey` | `gridItemSize` |
| `SliderStyleKey` | `stringPreferencesKey` | `sliderStyle` |
| `SwipeToSongKey` | `booleanPreferencesKey` | `SwipeToSong` |
| `SwipeToRemoveSongKey` | `booleanPreferencesKey` | `SwipeToRemoveSong` |
| `FloatingMiniPlayerKey` | `booleanPreferencesKey` | `floatingMiniPlayerEnabled` |
| `CastEnabledKey` | `booleanPreferencesKey` | `castEnabled` |
| `HidePlayerThumbnailKey` | `booleanPreferencesKey` | `hidePlayerThumbnail` |
| `CropAlbumArtKey` | `booleanPreferencesKey` | `cropAlbumArt` |
| `SeekExtraSeconds` | `booleanPreferencesKey` | `seekExtraSeconds` |
| `ButtonDpadRightKey` | `intPreferencesKey` | `buttonDpadRight` |
| `ButtonDpadLeftKey` | `intPreferencesKey` | `buttonDpadLeft` |
| `ButtonDpadUpKey` | `intPreferencesKey` | `buttonDpadUp` |
| `ButtonDpadDownKey` | `intPreferencesKey` | `buttonDpadDown` |
| `ButtonDpadCenterKey` | `intPreferencesKey` | `buttonDpadCenter` |
| `AppLanguageKey` | `stringPreferencesKey` | `appLanguage` |
| `EnableZemerLyricsKey` | `booleanPreferencesKey` | `enableZemerLyrics` |
| `EnableSimpMusicKey` | `booleanPreferencesKey` | `enableSimpMusic` |
| `EnableLrcLibKey` | `booleanPreferencesKey` | `enableLrclib` |
| `EnableYouTubeLyricsKey` | `booleanPreferencesKey` | `enableYouTubeLyrics` |
| `LyricsProviderOrderKey` | `stringPreferencesKey` | `lyricsProviderOrder` |
| `YtmSyncKey` | `booleanPreferencesKey` | `ytmSync` |
| `BlockedContentIdsKey` | `stringPreferencesKey` | `blockedContentIds` |
| `StatusSourcesConfigKey` | `stringPreferencesKey` | `statusSourcesConfig` |
| `StatusSourcesVersionKey` | `longPreferencesKey` | `statusSourcesVersion` |
| `CheckForUpdatesKey` | `booleanPreferencesKey` | `checkForUpdates` |
| `NightlyUpdatesKey` | `booleanPreferencesKey` | `nightlyUpdates` |
| `LastNightlyAnnouncedKey` | `stringPreferencesKey` | `lastNightlyAnnounced` |
| `UpdateNotificationsEnabledKey` | `booleanPreferencesKey` | `updateNotifications` |
| `InstallerTypeKey` | `intPreferencesKey` | `installerType` |
| `LastWhitelistVersionKey` | `longPreferencesKey` | `lastWhitelistVersion` |
| `DisplayNamesBackfilledKey` | `booleanPreferencesKey` | `displayNamesBackfilled` |
| `LastPodcastWhitelistSyncTimeKey` | `longPreferencesKey` | `lastPodcastWhitelistSyncTime` |
| `LastPodcastWhitelistVersionKey` | `longPreferencesKey` | `lastPodcastWhitelistVersion` |
| `AudioQualityKey` | `stringPreferencesKey` | `audioQuality` |
| `DownloadAudioFormatKey` | `stringPreferencesKey` | `downloadAudioFormat` |
| `VideoQualityKey` | `stringPreferencesKey` | `videoQuality` |
| `StreamSourceWebRemixKey` | `booleanPreferencesKey` | `streamSourceWebRemix` |
| `StreamSourceTVHTML5Key` | `booleanPreferencesKey` | `streamSourceTVHTML5` |
| `StreamSourceWebCreatorKey` | `booleanPreferencesKey` | `streamSourceWebCreator` |
| `StreamSourceVisionOSKey` | `booleanPreferencesKey` | `streamSourceVisionOS` |
| `StreamSabrKey` | `booleanPreferencesKey` | `streamSabr` |
| `StreamSabrWebRemixKey` | `booleanPreferencesKey` | `streamSabrWebRemix` |
| `StreamSabrVisionOSKey` | `booleanPreferencesKey` | `streamSabrVisionOS` |
| `StreamSabrTVHTML5Key` | `booleanPreferencesKey` | `streamSabrTVHTML5` |
| `AudioOffload` | `booleanPreferencesKey` | `enableOffload` |
| `PersistentQueueKey` | `booleanPreferencesKey` | `persistentQueue` |
| `SkipSilenceKey` | `booleanPreferencesKey` | `skipSilence` |
| `AudioNormalizationKey` | `booleanPreferencesKey` | `audioNormalization` |
| `AutoLoadMoreKey` | `booleanPreferencesKey` | `autoLoadMore` |
| `DisableLoadMoreWhenRepeatAllKey` | `booleanPreferencesKey` | `disableLoadMoreWhenRepeatAll` |
| `AutoDownloadOnLikeKey` | `booleanPreferencesKey` | `autoDownloadOnLike` |
| `AutoSkipNextOnErrorKey` | `booleanPreferencesKey` | `autoSkipNextOnError` |
| `StopMusicOnTaskClearKey` | `booleanPreferencesKey` | `stopMusicOnTaskClear` |
| `CustomDownloadPathKey` | `stringPreferencesKey` | `customDownloadPath` |
| `MaxImageCacheSizeKey` | `intPreferencesKey` | `maxImageCacheSize` |
| `MaxSongCacheSizeKey` | `intPreferencesKey` | `maxSongCacheSize` |
| `PauseListenHistoryKey` | `booleanPreferencesKey` | `pauseListenHistory` |
| `PauseSearchHistoryKey` | `booleanPreferencesKey` | `pauseSearchHistory` |
| `ChipSortTypeKey` | `stringPreferencesKey` | `chipSortType` |
| `SongSortTypeKey` | `stringPreferencesKey` | `songSortType` |
| `SongSortDescendingKey` | `booleanPreferencesKey` | `songSortDescending` |
| `PodcastFilterKey` | `stringPreferencesKey` | `podcastFilter` |
| `PodcastSortTypeKey` | `stringPreferencesKey` | `podcastSortType` |
| `PodcastSortDescendingKey` | `booleanPreferencesKey` | `podcastSortDescending` |
| `PlaylistSongSortTypeKey` | `stringPreferencesKey` | `playlistSongSortType` |
| `PlaylistSongSortDescendingKey` | `booleanPreferencesKey` | `playlistSongSortDescending` |
| `ArtistSortTypeKey` | `stringPreferencesKey` | `artistSortType` |
| `ArtistSortDescendingKey` | `booleanPreferencesKey` | `artistSortDescending` |
| `AlbumSortTypeKey` | `stringPreferencesKey` | `albumSortType` |
| `AlbumSortDescendingKey` | `booleanPreferencesKey` | `albumSortDescending` |
| `PlaylistSortTypeKey` | `stringPreferencesKey` | `playlistSortType` |
| `PlaylistSortDescendingKey` | `booleanPreferencesKey` | `playlistSortDescending` |
| `ArtistSongSortTypeKey` | `stringPreferencesKey` | `artistSongSortType` |
| `ArtistSongSortDescendingKey` | `booleanPreferencesKey` | `artistSongSortDescending` |
| `MixSortTypeKey` | `stringPreferencesKey` | `mixSortType` |
| `MixSortDescendingKey` | `booleanPreferencesKey` | `mixSortDescending` |
| `OnboardingCompleteKey` | `booleanPreferencesKey` | `onboardingComplete` |
| `SongFilterKey` | `stringPreferencesKey` | `songFilter` |
| `ArtistFilterKey` | `stringPreferencesKey` | `artistFilter` |
| `AlbumFilterKey` | `stringPreferencesKey` | `albumFilter` |
| `HomeCacheKey` | `stringPreferencesKey` | `home_cache_json` |
| `ArtistProfilesCacheKey` | `stringPreferencesKey` | `artist_profiles_cache` |
| `ArtistProfilesCacheTimestampKey` | `longPreferencesKey` | `artist_profiles_cache_timestamp` |
| `ArtistViewTypeKey` | `stringPreferencesKey` | `artistViewType` |
| `AlbumViewTypeKey` | `stringPreferencesKey` | `albumViewType` |
| `PlaylistViewTypeKey` | `stringPreferencesKey` | `playlistViewType` |
| `PodcastViewTypeKey` | `stringPreferencesKey` | `podcastViewType` |
| `PlaylistEditLockKey` | `booleanPreferencesKey` | `playlistEditLock` |
| `QuickPicksKey` | `stringPreferencesKey` | `discover` |
| `QueueEditLockKey` | `booleanPreferencesKey` | `queueEditLock` |
| `AllowFemaleSingersKey` | `booleanPreferencesKey` | `allowFemaleSingers` |
| `FemalePasscodeHashKey` | `stringPreferencesKey` | `femalePasscodeHash` |
| `BlockVideosKey` | `booleanPreferencesKey` | `blockVideos` |
| `BlockPodcastsKey` | `booleanPreferencesKey` | `blockPodcasts` |
| `BlockPodcastsSeededKey` | `booleanPreferencesKey` | `blockPodcastsSeeded` |
| `VideoDownloadsInMusicKey` | `booleanPreferencesKey` | `videoDownloadsInMusic` |
| `EnableContentFiltersKey` | `booleanPreferencesKey` | `enableContentFilters` |
| `DeveloperModeEnabledKey` | `booleanPreferencesKey` | `developerModeEnabled` |
| `DebugLoggingEnabledKey` | `booleanPreferencesKey` | `debugLoggingEnabled` |
| `OfflineSubsetEnabledKey` | `booleanPreferencesKey` | `offlineSubsetEnabled` |
| `OfflineSubsetLastSyncedAtKey` | `longPreferencesKey` | `offlineSubsetLastSyncedAt` |
| `OfflineSubsetPromoDismissedKey` | `booleanPreferencesKey` | `offlineSubsetPromoDismissed` |
| `ContentFiltersAutoRestoredKey` | `booleanPreferencesKey` | `content_filters_auto_restored` |
| `ContentFiltersRestoredEmailKey` | `stringPreferencesKey` | `content_filters_restored_email` |
| `ContentFiltersLockedKey` | `booleanPreferencesKey` | `content_filters_locked` |
| `HomeRecentArtistsKey` | `stringPreferencesKey` | `home_recent_artists` |
| `ShowHomeGenresKey` | `booleanPreferencesKey` | `show_home_genres` |
| `ShowHomeStatusesKey` | `booleanPreferencesKey` | `show_home_statuses` |
| `HideTextStatusKey` | `booleanPreferencesKey` | `hide_text_status` |
| `HideImageStatusKey` | `booleanPreferencesKey` | `hide_image_status` |
| `ShowLikedPlaylistKey` | `booleanPreferencesKey` | `show_liked_playlist` |
| `ShowDownloadedPlaylistKey` | `booleanPreferencesKey` | `show_downloaded_playlist` |
| `ShowTopPlaylistKey` | `booleanPreferencesKey` | `show_top_playlist` |
| `ShowCachedPlaylistKey` | `booleanPreferencesKey` | `show_cached_playlist` |
| `TopSize` | `stringPreferencesKey` | `topSize` |
| `HistoryDuration` | `floatPreferencesKey` | `historyDuration` |
| `PlayerButtonsStyleKey` | `stringPreferencesKey` | `player_buttons_style` |
| `PlayerBackgroundStyleKey` | `stringPreferencesKey` | `playerBackgroundStyle` |
| `ShowLyricsKey` | `booleanPreferencesKey` | `showLyrics` |
| `LyricsTextPositionKey` | `stringPreferencesKey` | `lyricsTextPosition` |
| `LyricsClickKey` | `booleanPreferencesKey` | `lyricsClick` |
| `LyricsScrollKey` | `booleanPreferencesKey` | `lyricsScrollKey` |
| `LyricsWordSyncKey` | `booleanPreferencesKey` | `lyricsWordSync` |
| `LyricsSyncOffsetKey` | `intPreferencesKey` | `lyricsSyncOffsetMs` |
| `LyricsLineExtrasKey` | `stringPreferencesKey` | `lyricsLineExtras` |
| `PlayerVolumeKey` | `floatPreferencesKey` | `playerVolume` |
| `RepeatModeKey` | `intPreferencesKey` | `repeatMode` |
| `SwipeThumbnailKey` | `booleanPreferencesKey` | `swipeThumbnail` |
| `SwipeSensitivityKey` | `floatPreferencesKey` | `swipeSensitivity` |
| `TrackingDeviceIdKey` | `stringPreferencesKey` | `trackingDeviceId` |
| `TrackingBackfillCursorKey` | `longPreferencesKey` | `trackingBackfillCursor` |
| `TrackingBackfillBoundKey` | `longPreferencesKey` | `trackingBackfillBound` |
| `TrackingBackfillDoneKey` | `booleanPreferencesKey` | `trackingBackfillDone` |
| `TrackingActionBackfillDoneKey` | `booleanPreferencesKey` | `trackingActionBackfillDone` |
| `LyricsChainGenerationKey` | `intPreferencesKey` | `lyricsChainGeneration` |
| `TrackingActionBackfillSentKey` | `longPreferencesKey` | `trackingActionBackfillSent` |
| `VisitorDataKey` | `stringPreferencesKey` | `visitorData` |
| `DataSyncIdKey` | `stringPreferencesKey` | `dataSyncId` |
| `AndroidAutoYouTubePlaylistsKey` | `booleanPreferencesKey` | `androidAutoYoutubePlaylists` |
| `AndroidAutoSectionsOrderKey` | `stringPreferencesKey` | `androidAutoSectionsOrder` |
| `AndroidAutoTargetPlaylistKey` | `stringPreferencesKey` | `androidAutoTargetPlaylist` |
| `InnerTubeCookieKey` | `stringPreferencesKey` | `innerTubeCookie` |
| `AccountNameKey` | `stringPreferencesKey` | `accountName` |
| `AccountEmailKey` | `stringPreferencesKey` | `accountEmail` |
| `AccountChannelHandleKey` | `stringPreferencesKey` | `accountChannelHandle` |
| `UseLoginForBrowse` | `booleanPreferencesKey` | `useLoginForBrowse` |

## Preference keys declared outside `PreferenceKeys.kt`

Every other `*PreferencesKey(...)` call under `app/src/main/kotlin`. The `MusicWidget.kt` keys are Glance widget state keys.

| File | Kotlin val | Key type | Stored name |
| --- | --- | --- | --- |
| `statuses/StatusDownloadsStore.kt` | `key` | `stringPreferencesKey` | `status_downloads` |
| `statuses/StatusSeenStore.kt` | `key` | `stringSetPreferencesKey` | `status_seen_post_ids` |
| `sync/UserPreferencesRepository.kt` | `lastSyncTimeKey` | `longPreferencesKey` | `last_content_filter_sync_time` |
| `sync/UserPreferencesRepository.kt` | `deviceIdKey` | `stringPreferencesKey` | `current_device_id` |
| `sync/UserPreferencesRepository.kt` | `syncEnabledKey` | `booleanPreferencesKey` | `content_filter_sync_enabled` |
| `utils/DeviceIdGenerator.kt` | `deviceIdKey` | `stringPreferencesKey` | `device_id` |
| `widget/MusicWidget.kt` | `PREF_TITLE` | `stringPreferencesKey` | `title` |
| `widget/MusicWidget.kt` | `PREF_ARTIST` | `stringPreferencesKey` | `artist` |
| `widget/MusicWidget.kt` | `PREF_IS_PLAYING` | `booleanPreferencesKey` | `is_playing` |
| `widget/MusicWidget.kt` | `PREF_POSITION` | `longPreferencesKey` | `position_ms` |
| `widget/MusicWidget.kt` | `PREF_DURATION` | `longPreferencesKey` | `duration_ms` |

## Auth/sync/preference Kotlin files

Declarations are capped at 25 per file (same extraction as `reference/kotlin-files.md`).

| File | Lines | Package | Declarations |
| --- | ---: | --- | --- |
| `app/src/main/kotlin/com/jtech/zemer/App.kt` | 409 | `com.jtech.zemer` | class App, var applicationScope, var offlineSubsetSyncer, var databaseLazy, fun onCreate, var waitedMs, val persisted, val persistedVersion, fun checkForUpdatesOnStartup, val settings, val result, fun initializeSettings, val settings, val locale, val languageTag, val seededBlockPodcasts, val channel, val nm, fun observeSettingsChanges, fun newImageLoader, val cacheSize, val okHttpClient, class PendingUpdate, val version, val notes, … (+4 more) |
| `app/src/main/kotlin/com/jtech/zemer/auth/AuthState.kt` | 45 | `com.jtech.zemer.auth` | class AuthState, class SignedIn, val userId, val email, val displayName, val isEmailVerified, object SignedOut, object Loading, class Error, val exception, val isSignedIn, val isLoading, val isError |
| `app/src/main/kotlin/com/jtech/zemer/auth/UserAuthManager.kt` | 120 | `com.jtech.zemer.auth` | class UserAuthManager, val context, val auth, val googleSignInOptions, val googleSignInClient, val currentUser, val isUserSignedIn, val currentUserId, val currentUserEmail, val authStateFlow, val listener, val user, val state, fun signInWithGoogle, val firebaseCredential, val authResult, val user, fun signOut, fun getIdToken, val user, val tokenResult |
| `app/src/main/kotlin/com/jtech/zemer/auth/WebViewGoogleAuthManager.kt` | 33 | `com.jtech.zemer.auth` | class WebViewGoogleAuthManager, val auth, fun signInAnonymously, val authResult, val user |
| `app/src/main/kotlin/com/jtech/zemer/constants/PreferenceKeys.kt` | 659 | `com.jtech.zemer.constants` | val PlaybackModeKey, val RelayDeviceIdKey, val DynamicThemeKey, val EnableHighRefreshRateKey, val RefreshRateModeKey, val SelectedThemeColorKey, val DarkModeKey, val PureBlackKey, val DensityScaleKey, val CustomDensityScaleKey, val DefaultOpenTabKey, val HomeContentTabKey, val BottomNavigationBarEnabledKey, val SlimNavBarKey, val BottomNavigationItemsKey, val BottomNavArtistsRemovedKey, val RecognizeMusicFabKey, val GridItemsSizeKey, val SliderStyleKey, val SwipeToSongKey, val SwipeToRemoveSongKey, val FloatingMiniPlayerKey, val CastEnabledKey, val HidePlayerThumbnailKey, val CropAlbumArtKey, … (+169 more) |
| `app/src/main/kotlin/com/jtech/zemer/sync/ContentFilterSyncService.kt` | 336 | `com.jtech.zemer.sync` | class ContentFilterSyncService, val userPreferencesRepository, val authManager, val serviceScope, val _syncState, val syncState, var _isApplyingServerPreferences, fun initialize, val result, val config, val result, val error, fun performManualSync, val result, fun syncToServer, val result, fun setSyncEnabled, fun isSyncEnabled, fun getSyncStatusFlow, fun getUserDevices, fun handleAuthStateChange, fun handlePreferenceChange, fun performInitialSync, val result, val uploadResult, … (+15 more) |
| `app/src/main/kotlin/com/jtech/zemer/sync/ContentReportRepository.kt` | 54 | `com.jtech.zemer.sync` | class ContentReportRepository, val firestore, val auth, fun submitReport, val payload, fun buildReportPayload, val payload |
| `app/src/main/kotlin/com/jtech/zemer/sync/PodcastSyncLogic.kt` | 105 | `com.jtech.zemer.sync` | object PodcastSyncLogic, class UpsertAction, fun upsertAction, class EpisodeSyncPlan, val imported, val cleanupReference, class UnsaveAction, fun unsaveAction, fun episodePassesPodcastWhitelist, fun podcastCategoryAllowed |
| `app/src/main/kotlin/com/jtech/zemer/sync/UserPreferencesRepository.kt` | 692 | `com.jtech.zemer.sync` | fun ContentFilterConfig, fun com, class UserPreferencesRepository, val context, val firestore, val syncDataStore, val mainDataStore, val authManager, val deviceIdGenerator, val USER_PREFERENCES_COLLECTION, val DEVICE_PREFERENCES_SUBCOLLECTION, fun getDocumentId, fun classifyFirebaseError, val lastSyncTimeKey, val deviceIdKey, val syncEnabledKey, fun fetchDevicePreferences, val userId, val deviceId, val document, val entity, val deviceData, val config, val errorClassification, fun fetchDevicePreferencesByDeviceId, … (+75 more) |
| `app/src/main/kotlin/com/jtech/zemer/sync/models/DevicePreferencesEntity.kt` | 100 | `com.jtech.zemer.sync.models` | class DeviceContentFilters, val enableContentFilters, val allowFemaleSingers, val blockVideos, val blockPodcasts, val femalePasscodeHash, fun fromConfig, fun toConfig, class DeviceMetadata, val deviceName, val manufacturer, val model, val androidVersion, val sdkVersion, val appVersion, val firstSeen, val lastSeen, class UserDeviceData, val deviceId, val deviceInfo, val contentFilters, val createdAt, val lastSyncTime, class DevicePreferencesEntity, val userId, … (+6 more) |
| `app/src/main/kotlin/com/jtech/zemer/utils/ContentFilterConfig.kt` | 87 | `com.jtech.zemer.utils` | class ContentFilterConfig, val filtersEnabled, val allowFemaleSingers, val blockVideos, val blockPodcasts, val femalePasscodeHash, val lastSyncTime, val isSynced, fun ContentFilterConfig, object ContentFilterState, val _state, val state, var current, fun updateConfig, val currentConfig, fun updateContentFilters, val hasActiveFilters |
| `app/src/main/kotlin/com/jtech/zemer/utils/DataStore.kt` | 180 | `com.jtech.zemer.utils` | val Context, val context, val coroutineScope, val state, var value, fun component1, fun component2, val context, val coroutineScope, val state, var value, fun component1, fun component2 |
| `app/src/main/kotlin/com/jtech/zemer/utils/SyncUtils.kt` | 1162 | `com.jtech.zemer.utils` | class WhitelistSyncProgress, val current, val total, val isComplete, class SyncUtils, val databaseLazy, val context, val database, val syncScope, val isSyncingLikedSongs, val isSyncingLibrarySongs, val isSyncingLikedAlbums, val isSyncingArtists, val isSyncingPlaylists, val isSyncingWhitelist, val isSyncingPodcastSubscriptions, val isSyncingEpisodes, val isWhitelistSyncing, val _whitelistSyncProgress, val whitelistSyncProgress, val isSyncingPodcastWhitelist, val isPodcastWhitelistSyncing, val _podcastWhitelistSyncProgress, val podcastWhitelistSyncProgress, fun syncPodcastSubscriptions, … (+106 more) |
