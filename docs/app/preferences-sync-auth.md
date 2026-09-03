# Preferences, sync, and auth documentation

## Preference key inventory

Preference keys extracted from `PreferenceKeys.kt`: `166`.

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
| `EnableMusixmatchKey` | `booleanPreferencesKey` | `enableMusixmatch` |
| `MusixmatchTokenKey` | `stringPreferencesKey` | `musixmatchToken` |
| `MusixmatchLastStatusKey` | `stringPreferencesKey` | `musixmatchLastStatus` |
| `MusixmatchCooldownUntilKey` | `longPreferencesKey` | `musixmatchCooldownUntil` |
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
| `PlayerVolumeKey` | `floatPreferencesKey` | `playerVolume` |
| `RepeatModeKey` | `intPreferencesKey` | `repeatMode` |
| `SwipeThumbnailKey` | `booleanPreferencesKey` | `swipeThumbnail` |
| `SwipeSensitivityKey` | `floatPreferencesKey` | `swipeSensitivity` |
| `TrackingDeviceIdKey` | `stringPreferencesKey` | `trackingDeviceId` |
| `TrackingBackfillCursorKey` | `longPreferencesKey` | `trackingBackfillCursor` |
| `TrackingBackfillBoundKey` | `longPreferencesKey` | `trackingBackfillBound` |
| `TrackingBackfillDoneKey` | `booleanPreferencesKey` | `trackingBackfillDone` |
| `TrackingActionBackfillDoneKey` | `booleanPreferencesKey` | `trackingActionBackfillDone` |
| `LyricsCachePurgeDoneKey` | `booleanPreferencesKey` | `lyricsCachePurgeDone` |
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

## Auth/sync/preference Kotlin files

| File | Lines | Package | Declarations |
| --- | ---: | --- | --- |
| `app/src/main/kotlin/com/jtech/zemer/App.kt` | 412 | `com.jtech.zemer` | class App, fun checkForUpdatesOnStartup, val settings, fun fetchAnonymousTokenOnStartup, fun sanitizeCookie, val trimmed, val httpClient, val responseText, val json, val visitorData, val clientVersion, val timestamp, val expiresAt, val cookie, val dataSyncId, val accountName, val accountEmail, val accountChannelHandle, val isValidToken, val expiresIn, val minutesLeft, fun initializeSettings, val settings, val locale, val languageTag |
| `app/src/main/kotlin/com/jtech/zemer/auth/AuthState.kt` | 44 | `com.jtech.zemer.auth` | class AuthState, class SignedIn, val userId, val email, val displayName, val isEmailVerified, object SignedOut, object Loading, class Error, val exception, val isSignedIn, val isLoading, val isError |
| `app/src/main/kotlin/com/jtech/zemer/auth/UserAuthManager.kt` | 119 | `com.jtech.zemer.auth` | class UserAuthManager, val auth, val googleSignInOptions, val googleSignInClient, val currentUser, val isUserSignedIn, val currentUserId, val currentUserEmail, val authStateFlow, val listener, val user, val state, fun signInWithGoogle, val firebaseCredential, val authResult, val user, fun signOut, fun getIdToken, val user, val tokenResult |
| `app/src/main/kotlin/com/jtech/zemer/auth/WebViewGoogleAuthManager.kt` | 33 | `com.jtech.zemer.auth` | class WebViewGoogleAuthManager, val auth, fun signInAnonymously, val authResult, val user |
| `app/src/main/kotlin/com/jtech/zemer/constants/PreferenceKeys.kt` | 661 | `com.jtech.zemer.constants` | val PlaybackModeKey, val RelayDeviceIdKey, val DynamicThemeKey, val EnableHighRefreshRateKey, val RefreshRateModeKey, val SelectedThemeColorKey, val DarkModeKey, val PureBlackKey, val DensityScaleKey, val CustomDensityScaleKey, val DefaultOpenTabKey, val HomeContentTabKey, val BottomNavigationBarEnabledKey, val SlimNavBarKey, val BottomNavigationItemsKey, val BottomNavArtistsRemovedKey, val RecognizeMusicFabKey, val GridItemsSizeKey, val SliderStyleKey, val SwipeToSongKey, val SwipeToRemoveSongKey, val FloatingMiniPlayerKey, val CastEnabledKey, val HidePlayerThumbnailKey, val CropAlbumArtKey |
| `app/src/main/kotlin/com/jtech/zemer/sync/ContentFilterSyncService.kt` | 446 | `com.jtech.zemer.sync` | class ContentFilterSyncService, val userPreferencesRepository, val authManager, val serviceScope, val _syncState, val syncState, val _lastSyncResult, val lastSyncResult, var _isApplyingServerPreferences, fun initialize, val result, val config, val result, val error, fun performManualSync, val result, fun pullFromServer, val result, fun syncToServer, val result, fun syncFromServer, val result, fun setSyncEnabled, fun isSyncEnabled, fun getSyncStatusFlow |
| `app/src/main/kotlin/com/jtech/zemer/sync/UserPreferencesRepository.kt` | 694 | `com.jtech.zemer.sync` | fun ContentFilterConfig, fun com, class UserPreferencesRepository, val firestore, val authManager, val deviceIdGenerator, fun getDocumentId, fun classifyFirebaseError, val lastSyncTimeKey, val deviceIdKey, val syncEnabledKey, fun fetchDevicePreferences, val userId, val deviceId, val document, val entity, val deviceData, val config, val errorClassification, fun fetchDevicePreferencesByDeviceId, val deviceId, val query, var foundConfig, val entity, val deviceData |
| `app/src/main/kotlin/com/jtech/zemer/sync/models/DevicePreferencesEntity.kt` | 100 | `com.jtech.zemer.sync.models` | class DeviceContentFilters, val enableContentFilters, val allowFemaleSingers, val blockVideos, val femalePasscodeHash, fun fromConfig, fun toConfig, class DeviceMetadata, val deviceName, val manufacturer, val model, val androidVersion, val sdkVersion, val appVersion, val firstSeen, val lastSeen, fun fromLocalInfo, class UserDeviceData, val deviceId, val deviceInfo, val contentFilters, val createdAt, val lastSyncTime, class DevicePreferencesEntity, val userId |
| `app/src/main/kotlin/com/jtech/zemer/utils/ContentFilterConfig.kt` | 87 | `com.jtech.zemer.utils` | class ContentFilterConfig, val filtersEnabled, val allowFemaleSingers, val blockVideos, val femalePasscodeHash, val lastSyncTime, val isSynced, object ContentFilterState, val _state, val state, var current, fun updateConfig, val currentConfig, fun updateContentFilters, val currentConfig, fun updateSyncMetadata, fun markAsModified, fun resetToDefaults, val hasUnsyncedChanges, val hasActiveFilters |
| `app/src/main/kotlin/com/jtech/zemer/utils/DataStore.kt` | 180 | `com.jtech.zemer.utils` | val dataStore, fun DataStore, fun DataStore, fun DataStore, fun DataStore, fun preference, fun rememberPreference, val context, val coroutineScope, val state, var value, fun component1, fun component2, val context, val coroutineScope, val state, var value, fun component1, fun component2 |
| `app/src/main/kotlin/com/jtech/zemer/utils/SyncUtils.kt` | 724 | `com.jtech.zemer.utils` | class WhitelistSyncProgress, val current, val total, val currentArtistName, val isComplete, class SyncUtils, val databaseLazy, val database, val syncScope, val isSyncingLikedSongs, val isSyncingLibrarySongs, val isSyncingUploadedSongs, val isSyncingLikedAlbums, val isSyncingUploadedAlbums, val isSyncingArtists, val isSyncingPlaylists, val isSyncingWhitelist, val isBackfillingThumbs, val isWhitelistSyncing, val _whitelistSyncProgress, val whitelistSyncProgress, fun likeSong, fun syncLikedSongs, val remoteSongs |
