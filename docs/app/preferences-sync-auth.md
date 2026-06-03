# Preferences, sync, and auth documentation

## Preference key inventory

Preference keys extracted from `PreferenceKeys.kt`: `128`.

| Kotlin val | Key type | Stored name |
| --- | --- | --- |
| `DynamicThemeKey` | `booleanPreferencesKey` | `dynamicTheme` |
| `DarkModeKey` | `stringPreferencesKey` | `darkMode` |
| `PureBlackKey` | `booleanPreferencesKey` | `pureBlack` |
| `DensityScaleKey` | `floatPreferencesKey` | `density_scale_factor` |
| `CustomDensityScaleKey` | `floatPreferencesKey` | `custom_density_scale_value` |
| `DefaultOpenTabKey` | `stringPreferencesKey` | `defaultOpenTab` |
| `BottomNavigationBarEnabledKey` | `booleanPreferencesKey` | `bottomNavigationBarEnabled` |
| `SlimNavBarKey` | `booleanPreferencesKey` | `slimNavBar` |
| `BottomNavigationItemsKey` | `stringPreferencesKey` | `bottomNavigationItems` |
| `GridItemsSizeKey` | `stringPreferencesKey` | `gridItemSize` |
| `SliderStyleKey` | `stringPreferencesKey` | `sliderStyle` |
| `SwipeToSongKey` | `booleanPreferencesKey` | `SwipeToSong` |
| `SwipeToRemoveSongKey` | `booleanPreferencesKey` | `SwipeToRemoveSong` |
| `UseNewPlayerDesignKey` | `booleanPreferencesKey` | `useNewPlayerDesign` |
| `UseNewMiniPlayerDesignKey` | `booleanPreferencesKey` | `useNewMiniPlayerDesign` |
| `FloatingMiniPlayerKey` | `booleanPreferencesKey` | `floatingMiniPlayerEnabled` |
| `HidePlayerThumbnailKey` | `booleanPreferencesKey` | `hidePlayerThumbnail` |
| `ButtonDpadRightKey` | `intPreferencesKey` | `buttonDpadRight` |
| `ButtonDpadLeftKey` | `intPreferencesKey` | `buttonDpadLeft` |
| `ButtonDpadUpKey` | `intPreferencesKey` | `buttonDpadUp` |
| `ButtonDpadDownKey` | `intPreferencesKey` | `buttonDpadDown` |
| `ButtonDpadCenterKey` | `intPreferencesKey` | `buttonDpadCenter` |
| `AppLanguageKey` | `stringPreferencesKey` | `appLanguage` |
| `ContentLanguageKey` | `stringPreferencesKey` | `contentLanguage` |
| `ContentCountryKey` | `stringPreferencesKey` | `contentCountry` |
| `EnableLrcLibKey` | `booleanPreferencesKey` | `enableLrclib` |
| `HideExplicitKey` | `booleanPreferencesKey` | `hideExplicit` |
| `ProxyEnabledKey` | `booleanPreferencesKey` | `proxyEnabled` |
| `ProxyUrlKey` | `stringPreferencesKey` | `proxyUrl` |
| `ProxyTypeKey` | `stringPreferencesKey` | `proxyType` |
| `ProxyUsernameKey` | `stringPreferencesKey` | `proxyUsername` |
| `ProxyPasswordKey` | `stringPreferencesKey` | `proxyPassword` |
| `YtmSyncKey` | `booleanPreferencesKey` | `ytmSync` |
| `LastWhitelistSyncTimeKey` | `longPreferencesKey` | `lastWhitelistSyncTime` |
| `CheckForUpdatesKey` | `booleanPreferencesKey` | `checkForUpdates` |
| `UpdateNotificationsEnabledKey` | `booleanPreferencesKey` | `updateNotifications` |
| `LastWhitelistVersionKey` | `longPreferencesKey` | `lastWhitelistVersion` |
| `AudioQualityKey` | `stringPreferencesKey` | `audioQuality` |
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
| `DisableScreenshotKey` | `booleanPreferencesKey` | `disableScreenshot` |
| `ChipSortTypeKey` | `stringPreferencesKey` | `chipSortType` |
| `SongSortTypeKey` | `stringPreferencesKey` | `songSortType` |
| `SongSortDescendingKey` | `booleanPreferencesKey` | `songSortDescending` |
| `PlaylistSongSortTypeKey` | `stringPreferencesKey` | `playlistSongSortType` |
| `PlaylistSongSortDescendingKey` | `booleanPreferencesKey` | `playlistSongSortDescending` |
| `AutoPlaylistSongSortTypeKey` | `stringPreferencesKey` | `autoPlaylistSongSortType` |
| `AutoPlaylistSongSortDescendingKey` | `booleanPreferencesKey` | `autoPlaylistSongSortDescending` |
| `ArtistSortTypeKey` | `stringPreferencesKey` | `artistSortType` |
| `ArtistSortDescendingKey` | `booleanPreferencesKey` | `artistSortDescending` |
| `AlbumSortTypeKey` | `stringPreferencesKey` | `albumSortType` |
| `AlbumSortDescendingKey` | `booleanPreferencesKey` | `albumSortDescending` |
| `PlaylistSortTypeKey` | `stringPreferencesKey` | `playlistSortType` |
| `PlaylistSortDescendingKey` | `booleanPreferencesKey` | `playlistSortDescending` |
| `ArtistSongSortTypeKey` | `stringPreferencesKey` | `artistSongSortType` |
| `ArtistSongSortDescendingKey` | `booleanPreferencesKey` | `artistSongSortDescending` |
| `MixSortTypeKey` | `stringPreferencesKey` | `mixSortType` |
| `MixSortDescendingKey` | `booleanPreferencesKey` | `albumSortDescending` |
| `OnboardingCompleteKey` | `booleanPreferencesKey` | `onboardingComplete` |
| `SongFilterKey` | `stringPreferencesKey` | `songFilter` |
| `ArtistFilterKey` | `stringPreferencesKey` | `artistFilter` |
| `AlbumFilterKey` | `stringPreferencesKey` | `albumFilter` |
| `LastLikeSongSyncKey` | `longPreferencesKey` | `last_like_song_sync` |
| `LastLibSongSyncKey` | `longPreferencesKey` | `last_library_song_sync` |
| `LastAlbumSyncKey` | `longPreferencesKey` | `last_album_sync` |
| `LastArtistSyncKey` | `longPreferencesKey` | `last_artist_sync` |
| `LastPlaylistSyncKey` | `longPreferencesKey` | `last_playlist_sync` |
| `HomeCacheKey` | `stringPreferencesKey` | `home_cache_json` |
| `HomeCacheTimestampKey` | `longPreferencesKey` | `home_cache_timestamp` |
| `ArtistProfilesCacheKey` | `stringPreferencesKey` | `artist_profiles_cache` |
| `ArtistProfilesCacheTimestampKey` | `longPreferencesKey` | `artist_profiles_cache_timestamp` |
| `ArtistViewTypeKey` | `stringPreferencesKey` | `artistViewType` |
| `AlbumViewTypeKey` | `stringPreferencesKey` | `albumViewType` |
| `PlaylistViewTypeKey` | `stringPreferencesKey` | `playlistViewType` |
| `PlaylistEditLockKey` | `booleanPreferencesKey` | `playlistEditLock` |
| `QuickPicksKey` | `stringPreferencesKey` | `discover` |
| `PreferredLyricsProviderKey` | `stringPreferencesKey` | `lyricsProvider` |
| `QueueEditLockKey` | `booleanPreferencesKey` | `queueEditLock` |
| `AllowFemaleSingersKey` | `booleanPreferencesKey` | `allowFemaleSingers` |
| `FemalePasscodeHashKey` | `stringPreferencesKey` | `femalePasscodeHash` |
| `AllowChasidishKey` | `booleanPreferencesKey` | `allowChasidish` |
| `BlockVideosKey` | `booleanPreferencesKey` | `blockVideos` |
| `EnableContentFiltersKey` | `booleanPreferencesKey` | `enableContentFilters` |
| `LastContentFilterSyncTimeKey` | `longPreferencesKey` | `last_content_filter_sync_time` |
| `IsContentFilterSyncEnabledKey` | `booleanPreferencesKey` | `content_filter_sync_enabled` |
| `CurrentUserIdKey` | `stringPreferencesKey` | `current_user_id` |
| `IsAuthenticatedKey` | `booleanPreferencesKey` | `is_authenticated` |
| `DeviceIdKey` | `stringPreferencesKey` | `device_id` |
| `DeviceNameKey` | `stringPreferencesKey` | `device_name` |
| `ContentFiltersAutoRestoredKey` | `booleanPreferencesKey` | `content_filters_auto_restored` |
| `ContentFiltersRestoredEmailKey` | `stringPreferencesKey` | `content_filters_restored_email` |
| `ContentFiltersLockedKey` | `booleanPreferencesKey` | `content_filters_locked` |
| `HomeRecentArtistsKey` | `stringPreferencesKey` | `home_recent_artists` |
| `ShowLikedPlaylistKey` | `booleanPreferencesKey` | `show_liked_playlist` |
| `ShowDownloadedPlaylistKey` | `booleanPreferencesKey` | `show_downloaded_playlist` |
| `ShowTopPlaylistKey` | `booleanPreferencesKey` | `show_top_playlist` |
| `ShowCachedPlaylistKey` | `booleanPreferencesKey` | `show_cached_playlist` |
| `ShowUploadedPlaylistKey` | `booleanPreferencesKey` | `show_uploaded_playlist` |
| `DefaultLinkHandlerKey` | `booleanPreferencesKey` | `defaultLinkHandler` |
| `PlayerButtonsStyleKey` | `stringPreferencesKey` | `player_buttons_style` |
| `PlayerBackgroundStyleKey` | `stringPreferencesKey` | `playerBackgroundStyle` |
| `ShowLyricsKey` | `booleanPreferencesKey` | `showLyrics` |
| `LyricsTextPositionKey` | `stringPreferencesKey` | `lyricsTextPosition` |
| `LyricsClickKey` | `booleanPreferencesKey` | `lyricsClick` |
| `LyricsScrollKey` | `booleanPreferencesKey` | `lyricsScrollKey` |
| `TranslateLyricsKey` | `booleanPreferencesKey` | `translateLyrics` |
| `PlayerVolumeKey` | `floatPreferencesKey` | `playerVolume` |
| `RepeatModeKey` | `intPreferencesKey` | `repeatMode` |
| `SearchSourceKey` | `stringPreferencesKey` | `searchSource` |
| `SwipeThumbnailKey` | `booleanPreferencesKey` | `swipeThumbnail` |
| `SwipeSensitivityKey` | `floatPreferencesKey` | `swipeSensitivity` |
| `VisitorDataKey` | `stringPreferencesKey` | `visitorData` |
| `DataSyncIdKey` | `stringPreferencesKey` | `dataSyncId` |
| `InnerTubeCookieKey` | `stringPreferencesKey` | `innerTubeCookie` |
| `AccountNameKey` | `stringPreferencesKey` | `accountName` |
| `AccountEmailKey` | `stringPreferencesKey` | `accountEmail` |
| `AccountChannelHandleKey` | `stringPreferencesKey` | `accountChannelHandle` |

## Auth/sync/preference Kotlin files

| File | Lines | Package | Declarations |
| --- | ---: | --- | --- |
| `app/src/main/kotlin/com/jtech/zemer/App.kt` | 412 | `com.jtech.zemer` | class App, fun checkForUpdatesOnStartup, val settings, fun fetchAnonymousTokenOnStartup, fun sanitizeCookie, val trimmed, val httpClient, val responseText, val json, val visitorData, val clientVersion, val timestamp, val expiresAt, val cookie, val dataSyncId, val accountName, val accountEmail, val accountChannelHandle, val isValidToken, val expiresIn, val minutesLeft, fun initializeSettings, val settings, val locale, val languageTag |
| `app/src/main/kotlin/com/jtech/zemer/auth/AuthState.kt` | 57 | `com.jtech.zemer.auth` | class AuthState, class SignedIn, val userId, val email, val displayName, val isEmailVerified, object SignedOut, object Loading, class Error, val isSignedIn, val isSignedOut, val isLoading, val isError, fun getSignedInUser, fun getError |
| `app/src/main/kotlin/com/jtech/zemer/auth/UserAuthManager.kt` | 145 | `com.jtech.zemer.auth` | class UserAuthManager, val auth, val googleSignInOptions, val googleSignInClient, val currentUser, val isUserSignedIn, val currentUserId, val currentUserEmail, val authStateFlow, val listener, val user, val state, fun signInWithGoogle, val firebaseCredential, val authResult, val user, fun signOut, fun refreshToken, val user, val tokenResult, fun getIdToken, val user, val tokenResult, fun deleteAccount, val user |
| `app/src/main/kotlin/com/jtech/zemer/auth/WebViewGoogleAuthManager.kt` | 133 | `com.jtech.zemer.auth` | class WebViewGoogleAuthManager, val auth, var state, fun getFirebaseOAuthUrl, val provider, val pendingResultTask, fun signInWithGoogle, fun signInAndEnableSync, fun signInWithGoogleToken, val firebaseCredential, val authResult, val user, fun generateRandomString, val chars, fun signInAnonymously, val authResult, val user, fun updateAuthState |
| `app/src/main/kotlin/com/jtech/zemer/constants/PreferenceKeys.kt` | 554 | `com.jtech.zemer.constants` | val DynamicThemeKey, val DarkModeKey, val PureBlackKey, val DensityScaleKey, val CustomDensityScaleKey, val DefaultOpenTabKey, val BottomNavigationBarEnabledKey, val SlimNavBarKey, val BottomNavigationItemsKey, val GridItemsSizeKey, val SliderStyleKey, val SwipeToSongKey, val SwipeToRemoveSongKey, val UseNewPlayerDesignKey, val UseNewMiniPlayerDesignKey, val FloatingMiniPlayerKey, val HidePlayerThumbnailKey, val SeekExtraSeconds, val ButtonDpadRightKey, val ButtonDpadLeftKey, val ButtonDpadUpKey, val ButtonDpadDownKey, val ButtonDpadCenterKey, class DensityScale, fun fromValue |
| `app/src/main/kotlin/com/jtech/zemer/sync/ContentFilterSyncService.kt` | 446 | `com.jtech.zemer.sync` | class ContentFilterSyncService, val userPreferencesRepository, val authManager, val serviceScope, val _syncState, val syncState, val _lastSyncResult, val lastSyncResult, var _isApplyingServerPreferences, fun initialize, val result, val config, val result, val error, fun performManualSync, val result, fun pullFromServer, val result, fun syncToServer, val result, fun syncFromServer, val result, fun setSyncEnabled, fun isSyncEnabled, fun getSyncStatusFlow |
| `app/src/main/kotlin/com/jtech/zemer/sync/UserPreferencesRepository.kt` | 760 | `com.jtech.zemer.sync` | fun ContentFilterConfig, fun com, class UserPreferencesRepository, val firestore, val authManager, val deviceIdGenerator, fun getDocumentId, fun classifyFirebaseError, val lastSyncTimeKey, val deviceIdKey, val syncEnabledKey, fun fetchDevicePreferences, val userId, val deviceId, val document, val entity, val deviceData, val config, val errorClassification, fun fetchDevicePreferencesByDeviceId, val deviceId, val query, var foundConfig, val entity, val deviceData |
| `app/src/main/kotlin/com/jtech/zemer/sync/models/DevicePreferencesEntity.kt` | 126 | `com.jtech.zemer.sync.models` | class DeviceContentFilters, val enableContentFilters, val allowFemaleSingers, val blockVideos, val femalePasscodeHash, fun fromConfig, fun toConfig, class DeviceMetadata, val deviceName, val manufacturer, val model, val androidVersion, val sdkVersion, val appVersion, val firstSeen, val lastSeen, fun fromLocalInfo, class UserDeviceData, val deviceId, val deviceInfo, val contentFilters, val createdAt, val lastSyncTime, class DevicePreferencesEntity, val userId |
| `app/src/main/kotlin/com/jtech/zemer/sync/models/UserPreferencesEntity.kt` | 92 | `com.jtech.zemer.sync.models` | class UserPreferencesEntity, val userEmail, val userId, val contentFilters, val currentDevice, val allDevices, val isLocked, val createdAt, val updatedAt, fun fromConfig, val updatedDevices, val currentDeviceMetadata, val existingDeviceIndex, fun toConfig |
| `app/src/main/kotlin/com/jtech/zemer/utils/ContentFilterConfig.kt` | 108 | `com.jtech.zemer.utils` | class ContentFilterConfig, val filtersEnabled, val allowFemaleSingers, val blockVideos, val femalePasscodeHash, val lastSyncTime, val isSynced, object ContentFilterState, val _state, val state, var current, fun updateConfig, val currentConfig, fun updateContentFilters, val currentConfig, fun updateSyncMetadata, fun markAsModified, fun resetToDefaults, val hasUnsyncedChanges, val hasActiveFilters |
| `app/src/main/kotlin/com/jtech/zemer/utils/DataStore.kt` | 193 | `com.jtech.zemer.utils` | val Context, val context, val coroutineScope, val state, val context, val coroutineScope, val state |
| `app/src/main/kotlin/com/jtech/zemer/utils/SyncUtils.kt` | 724 | `com.jtech.zemer.utils` | class WhitelistSyncProgress, val current, val total, val currentArtistName, val isComplete, class SyncUtils, val databaseLazy, val database, val syncScope, val isSyncingLikedSongs, val isSyncingLibrarySongs, val isSyncingUploadedSongs, val isSyncingLikedAlbums, val isSyncingUploadedAlbums, val isSyncingArtists, val isSyncingPlaylists, val isSyncingWhitelist, val isBackfillingThumbs, val isWhitelistSyncing, val _whitelistSyncProgress, val whitelistSyncProgress, fun runAllSyncs, fun likeSong, fun syncLikedSongs, val remoteSongs |
