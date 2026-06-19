# Repository map

## Project identity and module graph

- The Gradle root project name is `Zemer`.
- Included Gradle modules are `:app`, `:innertube`, `:lrclib`, and `:simpmusic`.
- The build also includes the composite build `cipher`, substituting module `com.zemer:cipher` with project `:library`.
- Dependency repositories configured in `settings.gradle.kts` are `mavenLocal`, Google, Gradle Plugin Portal for plugin management, Maven Central, and JitPack.

## Android app configuration

| Fact | Value |
| --- | --- |
| Module | `app` |
| Android namespace | `com.jtech.zemer` |
| Application ID | `com.jtech.zemer` |
| Compile SDK | `36` |
| Minimum SDK | `26` |
| Target SDK | `36` |
| Version code/name | `27` / `27` |
| Java/Kotlin target | JVM 21 |
| Compose | Enabled |
| BuildConfig | Enabled |
| Room schema output | `app/schemas` |
| ABI filters | `arm64-v8a`, `armeabi-v7a` |
| Locale filters | `en`, `iw` |
| Native build | CMake at `app/src/main/cpp/CMakeLists.txt` unless `USE_PREBUILT_NATIVE=true` |

## Manifest-declared Android components

| Component type | Declared component / behavior |
| --- | --- |
| Application class | `.App` |
| Main activity | `.MainActivity`, exported, singleTop, fullSensor, handles launcher, voice/search, media search, YouTube/Zemer deep links, and text sharing. |
| File provider | `androidx.core.content.FileProvider` with `${applicationId}.FileProvider`. |
| Density provider | `com.dpi.DensityScaler`. |
| Crop activity | `com.yalantis.ucrop.UCropActivity`. |
| Media service | `.playback.MusicService`, exported, foreground type `mediaPlayback`, handles Media3 session/library and legacy media browser actions. |
| Download services | `.playback.ExoDownloadService` and `.playback.MediaStoreDownloadService`, foreground type `dataSync`. |
| Accessibility service | `.accessibility.ButtonMapperAccessibilityService`, bound with `android.permission.BIND_ACCESSIBILITY_SERVICE`. |
| Receivers | `androidx.media3.session.MediaButtonReceiver` and `.widget.MusicWidgetReceiver`. |
| Automotive metadata | `com.google.android.gms.car.application` pointing to `@xml/automotive_app_desc`. |

## Source module purposes visible from files

| Module | Source package(s) | Observable responsibility |
| --- | --- | --- |
| `app` | `com.jtech.zemer`, `com.dpi` | Android application, Compose UI, playback, Room database, DataStore preferences, Firebase auth/sync, whitelist filtering, widgets, accessibility, and density scaling. |
| `innertube` | `com.metrolist.innertube` | Ktor/OkHttp JVM library for YouTube Music InnerTube requests, NewPipe integration, response models, and page parsers. |
| `lrclib` | `com.metrolist.lrclib` | JVM library for LRCLIB lyric lookup and track matching models. |
| `simpmusic` | `com.metrolist.simpmusic` | JVM library for SimpMusic lyrics API responses and lookup helpers. |

## Latest committed Room schema (`InternalDatabase` schema 32)

| Table | Fields in schema 32 |
| --- | --- |
| `song` | `id`, `title`, `duration`, `thumbnailUrl`, `albumId`, `albumName`, `explicit`, `year`, `date`, `dateModified`, `liked`, `likedDate`, `totalPlayTime`, `inLibrary`, `dateDownload`, `isLocal`, `libraryAddToken`, `libraryRemoveToken`, `romanizeLyrics`, `isDownloaded`, `mediaStoreUri`, `isUploaded`, `isVideo` |
| `artist` | `id`, `name`, `thumbnailUrl`, `channelId`, `lastUpdateTime`, `bookmarkedAt`, `isLocal` |
| `album` | `id`, `playlistId`, `title`, `year`, `thumbnailUrl`, `themeColor`, `songCount`, `duration`, `explicit`, `lastUpdateTime`, `bookmarkedAt`, `likedDate`, `inLibrary`, `isLocal`, `isUploaded` |
| `playlist` | `id`, `name`, `browseId`, `createdAt`, `lastUpdateTime`, `isEditable`, `bookmarkedAt`, `remoteSongCount`, `playEndpointParams`, `thumbnailUrl`, `shuffleEndpointParams`, `radioEndpointParams`, `isLocal` |
| `song_artist_map` | `songId`, `artistId`, `position` |
| `song_album_map` | `songId`, `albumId`, `index` |
| `album_artist_map` | `albumId`, `artistId`, `order` |
| `playlist_song_map` | `id`, `playlistId`, `songId`, `position`, `setVideoId` |
| `search_history` | `id`, `query` |
| `format` | `id`, `itag`, `mimeType`, `codecs`, `bitrate`, `sampleRate`, `contentLength`, `loudnessDb`, `playbackUrl`, `streamClient` |
| `lyrics` | `id`, `lyrics` |
| `event` | `id`, `songId`, `timestamp`, `playTime` |
| `related_song_map` | `id`, `songId`, `relatedSongId` |
| `set_video_id` | `videoId`, `setVideoId` |
| `playCount` | `song`, `year`, `month`, `count` |
| `artist_whitelist` | `artistId`, `artistName`, `addedAt`, `source`, `lastSyncedAt`, `isFemale`, `isChasid`, `isGenZ`, `isKids`, `isKidZone` |

## File and declaration inventory

The following inventory is generated from repository files outside `.git`, `.gradle`, `build`, and `.kotlin` directories. It documents the files that define source, configuration, resources, schemas, scripts, or assets.

### Counts

- Files counted: `864`
- By extension:
  - `.kt`: `462`
  - `.xml`: `184`
  - `.mjs`: `72`
  - `.md`: `51`
  - `.json`: `36`
  - `.webp`: `15`
  - `[none]`: `7`
  - `.kts`: `6`
  - `.yml`: `5`
  - `.sh`: `4`
  - `.js`: `3`
  - `.lottie`: `3`
  - `.properties`: `3`
  - `.dm`: `2`
  - `.png`: `2`
  - `.py`: `2`
  - `.backup`: `1`
  - `.bat`: `1`
  - `.jar`: `1`
  - `.pro`: `1`
  - `.toml`: `1`
  - `.tsv`: `1`
  - `.txt`: `1`

### Every counted file

| Path | Lines/bytes | Kind |
| --- | ---: | --- |
| `.github/workflows/debug-build.yml` | 81 lines | `.yml` |
| `.github/workflows/docs-regenerate.yml` | 74 lines | `.yml` |
| `.github/workflows/player-monitor.yml` | 140 lines | `.yml` |
| `.github/workflows/release-build.yml` | 155 lines | `.yml` |
| `.github/workflows/ui-audit.yml` | 38 lines | `.yml` |
| `.gitignore` | 115 lines | `[none]` |
| `.gitmodules` | 6 lines | `[none]` |
| `AGENTS.md` | 214 lines | `.md` |
| `LICENSE` | 674 lines | `[none]` |
| `README.md` | 11 lines | `.md` |
| `app/.gitignore` | 1 lines | `[none]` |
| `app/build.gradle.kts` | 283 lines | `.kts` |
| `app/lint.xml` | 12 lines | `.xml` |
| `app/proguard-rules.pro` | 257 lines | `.pro` |
| `app/schemas/com.jtech.zemer.db.InternalDatabase/1.json` | 297 lines | `.json` |
| `app/schemas/com.jtech.zemer.db.InternalDatabase/10.json` | 814 lines | `.json` |
| `app/schemas/com.jtech.zemer.db.InternalDatabase/11.json` | 796 lines | `.json` |
| `app/schemas/com.jtech.zemer.db.InternalDatabase/12.json` | 812 lines | `.json` |
| `app/schemas/com.jtech.zemer.db.InternalDatabase/13.json` | 812 lines | `.json` |
| `app/schemas/com.jtech.zemer.db.InternalDatabase/14.json` | 824 lines | `.json` |
| `app/schemas/com.jtech.zemer.db.InternalDatabase/15.json` | 854 lines | `.json` |
| `app/schemas/com.jtech.zemer.db.InternalDatabase/16.json` | 931 lines | `.json` |
| `app/schemas/com.jtech.zemer.db.InternalDatabase/17.json` | 953 lines | `.json` |
| `app/schemas/com.jtech.zemer.db.InternalDatabase/18.json` | 993 lines | `.json` |
| `app/schemas/com.jtech.zemer.db.InternalDatabase/19.json` | 1006 lines | `.json` |
| `app/schemas/com.jtech.zemer.db.InternalDatabase/2.json` | 656 lines | `.json` |
| `app/schemas/com.jtech.zemer.db.InternalDatabase/20.json` | 1013 lines | `.json` |
| `app/schemas/com.jtech.zemer.db.InternalDatabase/21.json` | 1035 lines | `.json` |
| `app/schemas/com.jtech.zemer.db.InternalDatabase/22.json` | 1041 lines | `.json` |
| `app/schemas/com.jtech.zemer.db.InternalDatabase/23.json` | 1016 lines | `.json` |
| `app/schemas/com.jtech.zemer.db.InternalDatabase/24.json` | 1030 lines | `.json` |
| `app/schemas/com.jtech.zemer.db.InternalDatabase/25.json` | 1072 lines | `.json` |
| `app/schemas/com.jtech.zemer.db.InternalDatabase/26.json` | 1078 lines | `.json` |
| `app/schemas/com.jtech.zemer.db.InternalDatabase/27.json` | 1096 lines | `.json` |
| `app/schemas/com.jtech.zemer.db.InternalDatabase/28.json` | 1103 lines | `.json` |
| `app/schemas/com.jtech.zemer.db.InternalDatabase/29.json` | 1109 lines | `.json` |
| `app/schemas/com.jtech.zemer.db.InternalDatabase/3.json` | 718 lines | `.json` |
| `app/schemas/com.jtech.zemer.db.InternalDatabase/30.json` | 1115 lines | `.json` |
| `app/schemas/com.jtech.zemer.db.InternalDatabase/31.json` | 1151 lines | `.json` |
| `app/schemas/com.jtech.zemer.db.InternalDatabase/32.json` | 1156 lines | `.json` |
| `app/schemas/com.jtech.zemer.db.InternalDatabase/33.json` | 1229 lines | `.json` |
| `app/schemas/com.jtech.zemer.db.InternalDatabase/4.json` | 744 lines | `.json` |
| `app/schemas/com.jtech.zemer.db.InternalDatabase/5.json` | 748 lines | `.json` |
| `app/schemas/com.jtech.zemer.db.InternalDatabase/6.json` | 712 lines | `.json` |
| `app/schemas/com.jtech.zemer.db.InternalDatabase/7.json` | 718 lines | `.json` |
| `app/schemas/com.jtech.zemer.db.InternalDatabase/8.json` | 766 lines | `.json` |
| `app/schemas/com.jtech.zemer.db.InternalDatabase/9.json` | 840 lines | `.json` |
| `app/src/debug/res/values/app_name.xml` | 4 lines | `.xml` |
| `app/src/debug/res/xml-v25/shortcuts.xml` | 23 lines | `.xml` |
| `app/src/main/AndroidManifest.xml` | 300 lines | `.xml` |
| `app/src/main/assets/solver/astring.js` | 3 lines | `.js` |
| `app/src/main/assets/solver/meriyah.js` | 9210 lines | `.js` |
| `app/src/main/assets/solver/yt.solver.core.js` | 603 lines | `.js` |
| `app/src/main/cpp/CMakeLists.txt` | 5 lines | `.txt` |
| `app/src/main/ic_launcher-playstore.png` | 23742 bytes | `.png` |
| `app/src/main/kotlin/com/dpi/ActivityLifecycleManager.kt` | 116 lines | `.kt` |
| `app/src/main/kotlin/com/dpi/BaseLifecycleContentProvider.kt` | 36 lines | `.kt` |
| `app/src/main/kotlin/com/dpi/DensityConfiguration.kt` | 87 lines | `.kt` |
| `app/src/main/kotlin/com/dpi/DensityScaler.kt` | 46 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/App.kt` | 441 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/MainActivity.kt` | 2146 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/accessibility/ButtonMapperAccessibilityService.kt` | 45 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/auth/AuthState.kt` | 57 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/auth/UserAuthManager.kt` | 145 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/auth/WebViewGoogleAuthManager.kt` | 133 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/constants/Dimensions.kt` | 39 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/constants/HistorySource.kt` | 7 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/constants/LibraryFilter.kt` | 13 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/constants/MediaSessionConstants.kt` | 23 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/constants/PreferenceKeys.kt` | 572 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/constants/StatPeriod.kt` | 97 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/db/Converters.kt` | 20 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/db/DatabaseDao.kt` | 1698 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/db/MusicDatabase.kt` | 594 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/db/entities/Album.kt` | 33 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/db/entities/AlbumArtistMap.kt` | 29 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/db/entities/AlbumEntity.kt` | 55 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/db/entities/AlbumWithSongs.kt` | 36 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/db/entities/Artist.kt` | 19 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/db/entities/ArtistEntity.kt` | 55 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/db/entities/ArtistWhitelistEntity.kt` | 21 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/db/entities/Event.kt` | 32 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/db/entities/EventWithSong.kt` | 17 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/db/entities/FormatEntity.kt` | 19 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/db/entities/LocalItem.kt` | 7 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/db/entities/LyricsEntity.kt` | 14 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/db/entities/PlayCountEntity.kt` | 16 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/db/entities/Playlist.kt` | 40 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/db/entities/PlaylistEntity.kt` | 62 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/db/entities/PlaylistSong.kt` | 14 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/db/entities/PlaylistSongMap.kt` | 31 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/db/entities/PlaylistSongMapPreview.kt` | 14 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/db/entities/RecognitionHistoryEntity.kt` | 31 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/db/entities/RelatedSongMap.kt` | 29 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/db/entities/SearchHistory.kt` | 19 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/db/entities/SetVideoIdEntity.kt` | 11 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/db/entities/Song.kt` | 54 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/db/entities/SongAlbumMap.kt` | 29 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/db/entities/SongArtistMap.kt` | 29 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/db/entities/SongEntity.kt` | 86 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/db/entities/SongWithStats.kt` | 12 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/db/entities/SortedSongAlbumMap.kt` | 14 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/db/entities/SortedSongArtistMap.kt` | 14 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/di/AppModule.kt` | 90 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/di/DataStoreQualifiers.kt` | 11 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/di/LyricsHelperEntryPoint.kt` | 12 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/di/NetworkModule.kt` | 21 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/di/Qualifiers.kt` | 15 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/di/SyncModule.kt` | 121 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/extensions/AccountState.kt` | 29 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/extensions/ContextExt.kt` | 122 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/extensions/CoroutineExt.kt` | 27 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/extensions/FileExt.kt` | 13 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/extensions/ListExt.kt` | 54 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/extensions/MediaItemExt.kt` | 70 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/extensions/PlayerExt.kt` | 121 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/extensions/QueueExt.kt` | 112 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/extensions/StringExt.kt` | 23 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/extensions/UtilExt.kt` | 8 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/latestreleases/LatestReleaseCard.kt` | 133 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/latestreleases/LatestReleaseDate.kt` | 16 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/latestreleases/LatestReleaseFilter.kt` | 15 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/latestreleases/LatestReleaseMapping.kt` | 19 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/latestreleases/LatestReleasePlayback.kt` | 80 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/latestreleases/LatestReleasesStore.kt` | 256 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/lyrics/LrcLibLyricsProvider.kt` | 32 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/lyrics/LyricsEntry.kt` | 15 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/lyrics/LyricsHelper.kt` | 166 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/lyrics/LyricsProvider.kt` | 28 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/lyrics/LyricsUtils.kt` | 781 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/lyrics/SimpMusicLyricsProvider.kt` | 29 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/lyrics/YouTubeLyricsProvider.kt` | 28 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/lyrics/YouTubeSubtitleLyricsProvider.kt` | 18 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/lyrics/model/LyricsUnavailableException.kt` | 9 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/models/DpadDirection.kt` | 27 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/models/ItemsPage.kt` | 8 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/models/MediaMetadata.kt` | 108 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/models/PersistPlayerState.kt` | 14 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/models/PersistQueue.kt` | 54 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/playback/AudioOnlyRenderersFactory.kt` | 25 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/playback/DownloadMenuLogic.kt` | 68 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/playback/DownloadStateResolver.kt` | 106 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/playback/DownloadUtil.kt` | 319 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/playback/ExoDownloadService.kt` | 111 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/playback/MediaLibrarySessionCallback.kt` | 808 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/playback/MediaStoreDownloadManager.kt` | 806 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/playback/MediaStoreDownloadService.kt` | 306 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/playback/MusicService.kt` | 1744 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/playback/PlayerConnection.kt` | 207 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/playback/SleepTimer.kt` | 68 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/playback/queues/EmptyQueue.kt` | 14 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/playback/queues/ListQueue.kt` | 19 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/playback/queues/LocalAlbumRadio.kt` | 65 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/playback/queues/Queue.kt` | 40 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/playback/queues/YouTubeAlbumRadio.kt` | 60 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/playback/queues/YouTubeQueue.kt` | 58 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/recognition/AudioResampler.kt` | 118 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/recognition/RecognitionAudioCapture.kt` | 147 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/recognition/RecognitionHistoryFilter.kt` | 25 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/recognition/RecognitionMatchSelector.kt` | 51 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/recognition/RecognitionMatcher.kt` | 108 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/recognition/RecognitionResolver.kt` | 88 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/recognition/ShazamSignatureGenerator.kt` | 395 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/recognition/VibraSignature.kt` | 22 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/recognition/shazam/Shazam.kt` | 234 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/recognition/shazam/ShazamModels.kt` | 316 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/repositories/CachedSongsRepository.kt` | 96 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/sync/ContentFilterSyncService.kt` | 446 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/sync/ContentReportRepository.kt` | 54 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/sync/UserPreferencesRepository.kt` | 760 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/sync/models/DevicePreferencesEntity.kt` | 126 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/sync/models/UserPreferencesEntity.kt` | 92 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/AccountSettingsDialog.kt` | 66 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/AnonymousAuthEmailDialog.kt` | 123 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/AppStateViews.kt` | 111 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/AutoResizeText.kt` | 97 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/BigSeekBar.kt` | 58 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/BottomSheet.kt` | 348 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/BottomSheetMenu.kt` | 86 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/BottomSheetPage.kt` | 166 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/ChipsRow.kt` | 254 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/CreatePlaylistDialog.kt` | 131 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/Dialog.kt` | 389 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/DownloadStatusUi.kt` | 170 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/DraggableScrollBarOverlay.kt` | 242 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/EmptyPlaceholder.kt` | 47 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/FocusBorder.kt` | 50 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/GridMenu.kt` | 158 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/HideOnScrollFAB.kt` | 117 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/IconButton.kt` | 118 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/Items.kt` | 1571 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/Library.kt` | 410 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/Lyrics.kt` | 970 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/LyricsImageCard.kt` | 287 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/Material3MenuItem.kt` | 139 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/Material3SettingsGroup.kt` | 215 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/MenuDialogs.kt` | 124 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/NavigationTile.kt` | 59 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/NavigationTitle.kt` | 77 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/NetworkRequiredDialog.kt` | 100 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/NewMenuComponents.kt` | 154 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/PlayerSlider.kt` | 112 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/PlayingIndicator.kt` | 113 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/Preference.kt` | 368 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/RecognizeMusicFab.kt` | 33 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/SearchBar.kt` | 369 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/SelectionTopActions.kt` | 77 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/SortHeader.kt` | 108 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/SyncAccountWarning.kt` | 58 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/UpdateDownloadDialog.kt` | 129 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/WebViewAuthDialog.kt` | 130 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/shimmer/ButtonPlaceholder.kt` | 21 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/shimmer/GridItemPlaceholder.kt` | 56 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/shimmer/ListItemPlaceholder.kt` | 53 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/shimmer/ShimmerHost.kt` | 64 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/shimmer/TextPlaceholder.kt` | 33 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/menu/AddToPlaylistDialog.kt` | 195 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/menu/AddToPlaylistDialogOnline.kt` | 207 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/menu/AlbumMenu.kt` | 365 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/menu/ArtistMenu.kt` | 266 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/menu/CustomThumbnailMenu.kt` | 63 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/menu/DownloadMenuItems.kt` | 71 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/menu/ImportPlaylistDialog.kt` | 63 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/menu/LibraryMenuItems.kt` | 34 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/menu/LoadingScreen.kt` | 23 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/menu/LyricsMenu.kt` | 372 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/menu/PlayerMenu.kt` | 488 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/menu/PlaylistMenu.kt` | 234 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/menu/ReportContentDialog.kt` | 130 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/menu/SelectionSongsMenu.kt` | 588 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/menu/SongMenu.kt` | 550 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/menu/YouTubeAlbumMenu.kt` | 348 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/menu/YouTubeArtistMenu.kt` | 204 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/menu/YouTubePlaylistMenu.kt` | 477 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/menu/YouTubeSongMenu.kt` | 469 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/player/LyricsScreen.kt` | 748 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/player/MiniPlayer.kt` | 1040 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/player/PlaybackError.kt` | 45 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/player/Player.kt` | 1394 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/player/PlayerBackground.kt` | 116 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/player/Queue.kt` | 1130 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/player/Thumbnail.kt` | 481 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/AccountScreen.kt` | 200 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/AlbumScreen.kt` | 635 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/BrowseScreen.kt` | 143 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/ChartsScreen.kt` | 307 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/ExploreScreen.kt` | 400 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/HistoryScreen.kt` | 517 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/HomeScreen.kt` | 924 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/KidZoneScreen.kt` | 336 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/LatestReleasesScreen.kt` | 122 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/LoginGateScreen.kt` | 253 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/LoginScreen.kt` | 227 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/MoodAndGenresScreen.kt` | 171 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/NavigationBuilder.kt` | 353 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/NewReleaseScreen.kt` | 254 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/OnboardingScreen.kt` | 2072 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/Screens.kt` | 53 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/SplashScreen.kt` | 164 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/StatsScreen.kt` | 426 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/VideoNavigation.kt` | 12 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/WhitelistedArtistsScreen.kt` | 414 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/YouTubeBrowseScreen.kt` | 284 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/artist/ArtistAlbumsScreen.kt` | 157 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/artist/ArtistItemsScreen.kt` | 329 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/artist/ArtistScreen.kt` | 861 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/artist/ArtistSongsScreen.kt` | 211 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/library/LibraryAlbumsScreen.kt` | 324 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/library/LibraryArtistsScreen.kt` | 302 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/library/LibraryMixScreen.kt` | 797 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/library/LibraryPlaylistsScreen.kt` | 545 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/library/LibraryScreen.kt` | 87 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/library/LibrarySongsScreen.kt` | 327 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/library/LibraryVideosScreen.kt` | 156 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/player/VideoPlayerScreen.kt` | 1060 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/playlist/AutoPlaylistScreen.kt` | 607 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/playlist/CachePlaylistScreen.kt` | 474 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/playlist/DownloadedContentScreen.kt` | 182 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/playlist/DownloadedVideosScreen.kt` | 489 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/playlist/LocalPlaylistScreen.kt` | 1428 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/playlist/OnlinePlaylistScreen.kt` | 730 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/playlist/TopPlaylistScreen.kt` | 560 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/recognition/RecognitionHistoryScreen.kt` | 191 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/recognition/RecognizeMusicDialogActivity.kt` | 336 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/search/OnlineSearchResult.kt` | 441 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/search/OnlineSearchScreen.kt` | 458 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/settings/AboutScreen.kt` | 191 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/settings/AccountSettings.kt` | 424 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/settings/AndroidAutoSettings.kt` | 269 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/settings/AppearanceSettings.kt` | 1059 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/settings/BackupAndRestore.kt` | 190 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/settings/ButtonSetupScreen.kt` | 374 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/settings/ContentSettings.kt` | 679 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/settings/ContributeScreen.kt` | 390 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/settings/GeneralSettings.kt` | 88 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/settings/PlayerSettings.kt` | 246 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/settings/PrivacySettings.kt` | 208 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/settings/SettingsScreen.kt` | 263 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/settings/StorageSettings.kt` | 440 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/settings/StreamSourceSettings.kt` | 218 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/settings/UpdaterSettings.kt` | 328 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/settings/integrations/IntegrationScreen.kt` | 50 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/theme/PlayerColorExtractor.kt` | 159 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/theme/PlayerSliderColors.kt` | 146 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/theme/Theme.kt` | 134 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/theme/Type.kt` | 123 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/utils/AppBar.kt` | 75 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/utils/FadingEdge.kt` | 89 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/utils/ItemWrapper.kt` | 15 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/utils/KeyUtils.kt` | 50 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/utils/LazyGridSnapLayoutInfoProvider.kt` | 66 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/utils/NavControllerUtils.kt` | 14 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/utils/ScrollUtils.kt` | 59 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/utils/ShapeUtils.kt` | 8 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/utils/ShowMediaInfo.kt` | 194 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/utils/StringUtils.kt` | 34 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/ui/utils/YouTubeUtils.kt` | 27 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/utils/AccessibilityUtils.kt` | 91 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/utils/ButtonInputCapture.kt` | 40 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/utils/ButtonMapperBridge.kt` | 35 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/utils/CoilBitmapLoader.kt` | 54 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/utils/ComposeDebugUtils.kt` | 99 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/utils/ComposeToImage.kt` | 263 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/utils/ConnectivityManager.kt` | 75 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/utils/ContentFilterConfig.kt` | 108 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/utils/CoverArtEmbedder.kt` | 170 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/utils/CoverArtNative.kt` | 47 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/utils/CrashReportingTree.kt` | 35 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/utils/DataStore.kt` | 193 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/utils/DeviceIdGenerator.kt` | 198 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/utils/EnvironmentPaths.kt` | 25 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/utils/FirestoreUtils.kt` | 41 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/utils/FutureUtils.kt` | 43 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/utils/IsraeliArtistRegistry.kt` | 51 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/utils/MediaStoreHelper.kt` | 688 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/utils/NetworkConnectivityObserver.kt` | 74 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/utils/NetworkUtils.kt` | 20 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/utils/NotificationUtils.kt` | 35 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/utils/PermissionHelper.kt` | 247 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/utils/StringUtils.kt` | 31 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/utils/SyncUtils.kt` | 733 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/utils/UpdateChecker.kt` | 155 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/utils/Updater.kt` | 55 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/utils/UrlValidator.kt` | 109 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/utils/Utils.kt` | 30 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/utils/VideoLinkBuilder.kt` | 10 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/utils/WhitelistCache.kt` | 40 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/utils/WhitelistFetcher.kt` | 72 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/utils/WhitelistFilter.kt` | 262 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/utils/YTPlayerUtils.kt` | 629 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/utils/ZemerLinkBuilder.kt` | 16 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/utils/sabr/EjsNTransformSolver.kt` | 307 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/utils/sabr/SabrException.kt` | 3 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/utils/updater/ApkInstallController.kt` | 102 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/utils/updater/AppInstaller.kt` | 267 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/utils/updater/AppRestarter.kt` | 30 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/utils/updater/InstallReceiver.kt` | 70 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/utils/updater/Installer.kt` | 24 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/AccountSettingsViewModel.kt` | 42 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/AccountViewModel.kt` | 81 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/AlbumViewModel.kt` | 58 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/ArtistAlbumsViewModel.kt` | 25 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/ArtistItemsViewModel.kt` | 98 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/ArtistViewModel.kt` | 134 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/AutoPlaylistViewModel.kt` | 76 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/BackupRestoreViewModel.kt` | 197 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/BrowseViewModel.kt` | 55 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/ButtonSetupViewModel.kt` | 159 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/CachePlaylistViewModel.kt` | 26 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/ChartsViewModel.kt` | 87 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/ContributeViewModel.kt` | 296 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/DownloadedContentViewModel.kt` | 24 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/DownloadedVideosViewModel.kt` | 46 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/ExploreViewModel.kt` | 78 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/HistoryViewModel.kt` | 108 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/HomeViewModel.kt` | 1490 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/KidZoneViewModel.kt` | 73 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/LatestReleasesViewModel.kt` | 74 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/LibraryVideosViewModel.kt` | 47 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/LibraryViewModels.kt` | 462 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/LocalPlaylistViewModel.kt` | 93 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/LocalSearchViewModel.kt` | 93 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/LyricsMenuViewModel.kt` | 100 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/MoodAndGenresViewModel.kt` | 44 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/NewReleaseViewModel.kt` | 110 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/OnboardingViewModel.kt` | 205 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/OnboardingViewModel.kt.backup` | 215 lines | `.backup` |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/OnlinePlaylistViewModel.kt` | 167 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/OnlineSearchSuggestionViewModel.kt` | 129 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/OnlineSearchViewModel.kt` | 286 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/RecognitionHistoryViewModel.kt` | 42 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/RecognizeMusicViewModel.kt` | 111 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/ReportContentViewModel.kt` | 37 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/StatsViewModel.kt` | 175 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/TopPlaylistViewModel.kt` | 38 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/WhitelistedArtistsViewModel.kt` | 82 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/YouTubeBrowseViewModel.kt` | 52 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/widget/MusicWidget.kt` | 391 lines | `.kt` |
| `app/src/main/kotlin/com/jtech/zemer/widget/WidgetLayout.kt` | 14 lines | `.kt` |
| `app/src/main/res/drawable-night/widget_background.xml` | 6 lines | `.xml` |
| `app/src/main/res/drawable-v31/ic_launcher_background_v31.xml` | 7 lines | `.xml` |
| `app/src/main/res/drawable/account.xml` | 9 lines | `.xml` |
| `app/src/main/res/drawable/add.xml` | 9 lines | `.xml` |
| `app/src/main/res/drawable/add_circle.xml` | 9 lines | `.xml` |
| `app/src/main/res/drawable/album.xml` | 16 lines | `.xml` |
| `app/src/main/res/drawable/alphabet_cyrillic.xml` | 9 lines | `.xml` |
| `app/src/main/res/drawable/arrow_back.xml` | 16 lines | `.xml` |
| `app/src/main/res/drawable/arrow_downward.xml` | 9 lines | `.xml` |
| `app/src/main/res/drawable/arrow_forward.xml` | 10 lines | `.xml` |
| `app/src/main/res/drawable/arrow_top_left.xml` | 9 lines | `.xml` |
| `app/src/main/res/drawable/arrow_upward.xml` | 9 lines | `.xml` |
| `app/src/main/res/drawable/artist.xml` | 9 lines | `.xml` |
| `app/src/main/res/drawable/backup.xml` | 9 lines | `.xml` |
| `app/src/main/res/drawable/bedtime.xml` | 9 lines | `.xml` |
| `app/src/main/res/drawable/bookmark.xml` | 9 lines | `.xml` |
| `app/src/main/res/drawable/bookmark_filled.xml` | 9 lines | `.xml` |
| `app/src/main/res/drawable/cached.xml` | 9 lines | `.xml` |
| `app/src/main/res/drawable/casino.xml` | 10 lines | `.xml` |
| `app/src/main/res/drawable/check.xml` | 9 lines | `.xml` |
| `app/src/main/res/drawable/check_box.xml` | 9 lines | `.xml` |
| `app/src/main/res/drawable/clear_all.xml` | 9 lines | `.xml` |
| `app/src/main/res/drawable/close.xml` | 9 lines | `.xml` |
| `app/src/main/res/drawable/contrast.xml` | 9 lines | `.xml` |
| `app/src/main/res/drawable/dark_mode.xml` | 9 lines | `.xml` |
| `app/src/main/res/drawable/delete.xml` | 9 lines | `.xml` |
| `app/src/main/res/drawable/delete_history.xml` | 9 lines | `.xml` |
| `app/src/main/res/drawable/deselect.xml` | 10 lines | `.xml` |
| `app/src/main/res/drawable/discord.xml` | 31 lines | `.xml` |
| `app/src/main/res/drawable/discover_tune.xml` | 9 lines | `.xml` |
| `app/src/main/res/drawable/done.xml` | 9 lines | `.xml` |
| `app/src/main/res/drawable/download.xml` | 9 lines | `.xml` |
| `app/src/main/res/drawable/drag_handle.xml` | 9 lines | `.xml` |
| `app/src/main/res/drawable/drop_down.xml` | 9 lines | `.xml` |
| `app/src/main/res/drawable/edit.xml` | 9 lines | `.xml` |
| `app/src/main/res/drawable/equalizer.xml` | 9 lines | `.xml` |
| `app/src/main/res/drawable/error.xml` | 10 lines | `.xml` |
| `app/src/main/res/drawable/expand_less.xml` | 9 lines | `.xml` |
| `app/src/main/res/drawable/expand_more.xml` | 9 lines | `.xml` |
| `app/src/main/res/drawable/explicit.xml` | 9 lines | `.xml` |
| `app/src/main/res/drawable/explore_filled.xml` | 9 lines | `.xml` |
| `app/src/main/res/drawable/explore_outlined.xml` | 16 lines | `.xml` |
| `app/src/main/res/drawable/fast_forward.xml` | 9 lines | `.xml` |
| `app/src/main/res/drawable/favorite.xml` | 9 lines | `.xml` |
| `app/src/main/res/drawable/favorite_border.xml` | 9 lines | `.xml` |
| `app/src/main/res/drawable/format_align_center.xml` | 9 lines | `.xml` |
| `app/src/main/res/drawable/format_align_left.xml` | 10 lines | `.xml` |
| `app/src/main/res/drawable/github.xml` | 9 lines | `.xml` |
| `app/src/main/res/drawable/google_logo.xml` | 18 lines | `.xml` |
| `app/src/main/res/drawable/google_webview.xml` | 9 lines | `.xml` |
| `app/src/main/res/drawable/gradient.xml` | 10 lines | `.xml` |
| `app/src/main/res/drawable/graphic_eq.xml` | 9 lines | `.xml` |
| `app/src/main/res/drawable/grid_view.xml` | 9 lines | `.xml` |
| `app/src/main/res/drawable/hide_image.xml` | 9 lines | `.xml` |
| `app/src/main/res/drawable/history.xml` | 9 lines | `.xml` |
| `app/src/main/res/drawable/home_filled.xml` | 10 lines | `.xml` |
| `app/src/main/res/drawable/home_outlined.xml` | 13 lines | `.xml` |
| `app/src/main/res/drawable/ic_android_auto.xml` | 22 lines | `.xml` |
| `app/src/main/res/drawable/ic_fullscreen.xml` | 9 lines | `.xml` |
| `app/src/main/res/drawable/ic_launcher_background.xml` | 74 lines | `.xml` |
| `app/src/main/res/drawable/ic_launcher_background_v31.xml` | 8 lines | `.xml` |
| `app/src/main/res/drawable/ic_launcher_foreground.xml` | 17 lines | `.xml` |
| `app/src/main/res/drawable/ic_launcher_foreground_v31.xml` | 17 lines | `.xml` |
| `app/src/main/res/drawable/ic_launcher_monochrome.xml` | 17 lines | `.xml` |
| `app/src/main/res/drawable/ic_pip.xml` | 9 lines | `.xml` |
| `app/src/main/res/drawable/ic_rotate_screen.xml` | 9 lines | `.xml` |
| `app/src/main/res/drawable/ic_speedometer.xml` | 9 lines | `.xml` |
| `app/src/main/res/drawable/ic_video_hd.xml` | 9 lines | `.xml` |
| `app/src/main/res/drawable/incognito.xml` | 9 lines | `.xml` |
| `app/src/main/res/drawable/info.xml` | 9 lines | `.xml` |
| `app/src/main/res/drawable/input.xml` | 10 lines | `.xml` |
| `app/src/main/res/drawable/insert_photo.xml` | 9 lines | `.xml` |
| `app/src/main/res/drawable/instagram.xml` | 9 lines | `.xml` |
| `app/src/main/res/drawable/integration.xml` | 9 lines | `.xml` |
| `app/src/main/res/drawable/kid_zone.xml` | 18 lines | `.xml` |
| `app/src/main/res/drawable/language.xml` | 9 lines | `.xml` |
| `app/src/main/res/drawable/language_japanese_latin.xml` | 9 lines | `.xml` |
| `app/src/main/res/drawable/language_korean_latin.xml` | 9 lines | `.xml` |
| `app/src/main/res/drawable/library_add.xml` | 19 lines | `.xml` |
| `app/src/main/res/drawable/library_add_check.xml` | 16 lines | `.xml` |
| `app/src/main/res/drawable/library_music.xml` | 16 lines | `.xml` |
| `app/src/main/res/drawable/library_music_filled.xml` | 16 lines | `.xml` |
| `app/src/main/res/drawable/library_music_outlined.xml` | 16 lines | `.xml` |
| `app/src/main/res/drawable/link.xml` | 9 lines | `.xml` |
| `app/src/main/res/drawable/list.xml` | 10 lines | `.xml` |
| `app/src/main/res/drawable/location_on.xml` | 9 lines | `.xml` |
| `app/src/main/res/drawable/lock.xml` | 9 lines | `.xml` |
| `app/src/main/res/drawable/lock_open.xml` | 9 lines | `.xml` |
| `app/src/main/res/drawable/login.xml` | 10 lines | `.xml` |
| `app/src/main/res/drawable/logout.xml` | 9 lines | `.xml` |
| `app/src/main/res/drawable/lyrics.xml` | 37 lines | `.xml` |
| `app/src/main/res/drawable/manage_search.xml` | 9 lines | `.xml` |
| `app/src/main/res/drawable/menu.xml` | 9 lines | `.xml` |
| `app/src/main/res/drawable/mic.xml` | 10 lines | `.xml` |
| `app/src/main/res/drawable/mood.xml` | 9 lines | `.xml` |
| `app/src/main/res/drawable/more_horiz.xml` | 9 lines | `.xml` |
| `app/src/main/res/drawable/more_vert.xml` | 9 lines | `.xml` |
| `app/src/main/res/drawable/music_note.xml` | 9 lines | `.xml` |
| `app/src/main/res/drawable/nav_bar.xml` | 9 lines | `.xml` |
| `app/src/main/res/drawable/navigate_next.xml` | 10 lines | `.xml` |
| `app/src/main/res/drawable/notification.xml` | 9 lines | `.xml` |
| `app/src/main/res/drawable/offline.xml` | 9 lines | `.xml` |
| `app/src/main/res/drawable/palette.xml` | 9 lines | `.xml` |
| `app/src/main/res/drawable/pause.xml` | 9 lines | `.xml` |
| `app/src/main/res/drawable/person.xml` | 10 lines | `.xml` |
| `app/src/main/res/drawable/play.xml` | 9 lines | `.xml` |
| `app/src/main/res/drawable/playlist_add.xml` | 9 lines | `.xml` |
| `app/src/main/res/drawable/playlist_import.xml` | 9 lines | `.xml` |
| `app/src/main/res/drawable/playlist_play.xml` | 9 lines | `.xml` |
| `app/src/main/res/drawable/queue_music.xml` | 10 lines | `.xml` |
| `app/src/main/res/drawable/radio.xml` | 9 lines | `.xml` |
| `app/src/main/res/drawable/radio_button_checked.xml` | 10 lines | `.xml` |
| `app/src/main/res/drawable/radio_button_unchecked.xml` | 9 lines | `.xml` |
| `app/src/main/res/drawable/remove.xml` | 9 lines | `.xml` |
| `app/src/main/res/drawable/repeat.xml` | 9 lines | `.xml` |
| `app/src/main/res/drawable/repeat_on.xml` | 9 lines | `.xml` |
| `app/src/main/res/drawable/repeat_one.xml` | 9 lines | `.xml` |
| `app/src/main/res/drawable/repeat_one_on.xml` | 9 lines | `.xml` |
| `app/src/main/res/drawable/replay.xml` | 9 lines | `.xml` |
| `app/src/main/res/drawable/restore.xml` | 9 lines | `.xml` |
| `app/src/main/res/drawable/screenshot.xml` | 9 lines | `.xml` |
| `app/src/main/res/drawable/search.xml` | 9 lines | `.xml` |
| `app/src/main/res/drawable/search_off.xml` | 9 lines | `.xml` |
| `app/src/main/res/drawable/security.xml` | 9 lines | `.xml` |
| `app/src/main/res/drawable/select.xml` | 9 lines | `.xml` |
| `app/src/main/res/drawable/select_all.xml` | 9 lines | `.xml` |
| `app/src/main/res/drawable/settings.xml` | 9 lines | `.xml` |
| `app/src/main/res/drawable/share.xml` | 15 lines | `.xml` |
| `app/src/main/res/drawable/shortcut_explore.xml` | 11 lines | `.xml` |
| `app/src/main/res/drawable/shortcut_library.xml` | 15 lines | `.xml` |
| `app/src/main/res/drawable/shortcut_search.xml` | 14 lines | `.xml` |
| `app/src/main/res/drawable/shuffle.xml` | 9 lines | `.xml` |
| `app/src/main/res/drawable/shuffle_on.xml` | 9 lines | `.xml` |
| `app/src/main/res/drawable/similar.xml` | 9 lines | `.xml` |
| `app/src/main/res/drawable/skip_next.xml` | 9 lines | `.xml` |
| `app/src/main/res/drawable/skip_previous.xml` | 9 lines | `.xml` |
| `app/src/main/res/drawable/sliders.xml` | 9 lines | `.xml` |
| `app/src/main/res/drawable/slow_motion_video.xml` | 9 lines | `.xml` |
| `app/src/main/res/drawable/small_icon.xml` | 17 lines | `.xml` |
| `app/src/main/res/drawable/speed.xml` | 9 lines | `.xml` |
| `app/src/main/res/drawable/stats.xml` | 9 lines | `.xml` |
| `app/src/main/res/drawable/storage.xml` | 9 lines | `.xml` |
| `app/src/main/res/drawable/subscribe.xml` | 9 lines | `.xml` |
| `app/src/main/res/drawable/subscribed.xml` | 9 lines | `.xml` |
| `app/src/main/res/drawable/swipe.xml` | 16 lines | `.xml` |
| `app/src/main/res/drawable/sync.xml` | 9 lines | `.xml` |
| `app/src/main/res/drawable/tab.xml` | 9 lines | `.xml` |
| `app/src/main/res/drawable/token.xml` | 9 lines | `.xml` |
| `app/src/main/res/drawable/trending_up.xml` | 10 lines | `.xml` |
| `app/src/main/res/drawable/tune.xml` | 9 lines | `.xml` |
| `app/src/main/res/drawable/uncheck_box.xml` | 9 lines | `.xml` |
| `app/src/main/res/drawable/update.xml` | 9 lines | `.xml` |
| `app/src/main/res/drawable/volume_off.xml` | 10 lines | `.xml` |
| `app/src/main/res/drawable/volume_up.xml` | 10 lines | `.xml` |
| `app/src/main/res/drawable/warning.xml` | 9 lines | `.xml` |
| `app/src/main/res/drawable/waves.xml` | 10 lines | `.xml` |
| `app/src/main/res/drawable/widget_background.xml` | 6 lines | `.xml` |
| `app/src/main/res/drawable/widget_preview.xml` | 18 lines | `.xml` |
| `app/src/main/res/drawable/wifi_proxy.xml` | 9 lines | `.xml` |
| `app/src/main/res/layout/widget_loading.xml` | 45 lines | `.xml` |
| `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` | 5 lines | `.xml` |
| `app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml` | 5 lines | `.xml` |
| `app/src/main/res/mipmap-hdpi/ic_launcher.webp` | 730 bytes | `.webp` |
| `app/src/main/res/mipmap-hdpi/ic_launcher_foreground.webp` | 744 bytes | `.webp` |
| `app/src/main/res/mipmap-hdpi/ic_launcher_round.webp` | 1610 bytes | `.webp` |
| `app/src/main/res/mipmap-mdpi/ic_launcher.webp` | 534 bytes | `.webp` |
| `app/src/main/res/mipmap-mdpi/ic_launcher_foreground.webp` | 432 bytes | `.webp` |
| `app/src/main/res/mipmap-mdpi/ic_launcher_round.webp` | 1126 bytes | `.webp` |
| `app/src/main/res/mipmap-xhdpi/ic_launcher.webp` | 914 bytes | `.webp` |
| `app/src/main/res/mipmap-xhdpi/ic_launcher_foreground.webp` | 922 bytes | `.webp` |
| `app/src/main/res/mipmap-xhdpi/ic_launcher_round.webp` | 2204 bytes | `.webp` |
| `app/src/main/res/mipmap-xhdpi/tv_banner.png` | 2120 bytes | `.png` |
| `app/src/main/res/mipmap-xxhdpi/ic_launcher.webp` | 1344 bytes | `.webp` |
| `app/src/main/res/mipmap-xxhdpi/ic_launcher_foreground.webp` | 1472 bytes | `.webp` |
| `app/src/main/res/mipmap-xxhdpi/ic_launcher_round.webp` | 3556 bytes | `.webp` |
| `app/src/main/res/mipmap-xxxhdpi/ic_launcher.webp` | 1942 bytes | `.webp` |
| `app/src/main/res/mipmap-xxxhdpi/ic_launcher_foreground.webp` | 2592 bytes | `.webp` |
| `app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.webp` | 4890 bytes | `.webp` |
| `app/src/main/res/raw/loading_bar_progress.lottie` | 7909 bytes | `.lottie` |
| `app/src/main/res/raw/loading_dots_blue.lottie` | 1221 bytes | `.lottie` |
| `app/src/main/res/raw/welcome.lottie` | 2138 bytes | `.lottie` |
| `app/src/main/res/resources.properties` | 1 lines | `.properties` |
| `app/src/main/res/values-iw/metrolist_strings.xml` | 44 lines | `.xml` |
| `app/src/main/res/values-iw/strings.xml` | 376 lines | `.xml` |
| `app/src/main/res/values-night/colors.xml` | 6 lines | `.xml` |
| `app/src/main/res/values/app_name.xml` | 4 lines | `.xml` |
| `app/src/main/res/values/colors.xml` | 9 lines | `.xml` |
| `app/src/main/res/values/ic_launcher_background.xml` | 6 lines | `.xml` |
| `app/src/main/res/values/metrolist_strings.xml` | 553 lines | `.xml` |
| `app/src/main/res/values/strings.xml` | 560 lines | `.xml` |
| `app/src/main/res/values/styles.xml` | 26 lines | `.xml` |
| `app/src/main/res/values/values.xml` | 8 lines | `.xml` |
| `app/src/main/res/xml-v25/shortcuts.xml` | 23 lines | `.xml` |
| `app/src/main/res/xml/accessibility_service_config.xml` | 9 lines | `.xml` |
| `app/src/main/res/xml/automotive_app_desc.xml` | 4 lines | `.xml` |
| `app/src/main/res/xml/backup_rules.xml` | 12 lines | `.xml` |
| `app/src/main/res/xml/data_extraction_rules.xml` | 29 lines | `.xml` |
| `app/src/main/res/xml/music_widget_info.xml` | 18 lines | `.xml` |
| `app/src/main/res/xml/provider_paths.xml` | 12 lines | `.xml` |
| `app/src/test/kotlin/com/jtech/zemer/latestreleases/LatestReleaseFilterTest.kt` | 46 lines | `.kt` |
| `app/src/test/kotlin/com/jtech/zemer/latestreleases/LatestReleasePlaybackTest.kt` | 120 lines | `.kt` |
| `app/src/test/kotlin/com/jtech/zemer/latestreleases/LatestReleasesStoreTest.kt` | 120 lines | `.kt` |
| `app/src/test/kotlin/com/jtech/zemer/playback/DownloadCancellationContractTest.kt` | 75 lines | `.kt` |
| `app/src/test/kotlin/com/jtech/zemer/playback/DownloadMenuLogicTest.kt` | 140 lines | `.kt` |
| `app/src/test/kotlin/com/jtech/zemer/playback/DownloadStateResolverTest.kt` | 186 lines | `.kt` |
| `app/src/test/kotlin/com/jtech/zemer/recognition/AudioResamplerTest.kt` | 59 lines | `.kt` |
| `app/src/test/kotlin/com/jtech/zemer/recognition/RecognitionHistoryFilterTest.kt` | 47 lines | `.kt` |
| `app/src/test/kotlin/com/jtech/zemer/recognition/RecognitionMatchSelectorTest.kt` | 106 lines | `.kt` |
| `app/src/test/kotlin/com/jtech/zemer/recognition/RecognitionMatcherTest.kt` | 72 lines | `.kt` |
| `app/src/test/kotlin/com/jtech/zemer/recognition/ShazamSignatureGeneratorTest.kt` | 75 lines | `.kt` |
| `app/src/test/kotlin/com/jtech/zemer/sync/ContentReportRepositoryTest.kt` | 81 lines | `.kt` |
| `app/src/test/kotlin/com/jtech/zemer/ui/menu/DownloadMenuItemsTest.kt` | 71 lines | `.kt` |
| `app/src/test/kotlin/com/jtech/zemer/ui/player/PlayerBackgroundTest.kt` | 53 lines | `.kt` |
| `app/src/test/kotlin/com/jtech/zemer/ui/utils/StringUtilsTest.kt` | 21 lines | `.kt` |
| `app/src/test/kotlin/com/jtech/zemer/ui/utils/YouTubeUtilsTest.kt` | 45 lines | `.kt` |
| `app/src/test/kotlin/com/jtech/zemer/utils/CrashReportingTreeTest.kt` | 64 lines | `.kt` |
| `app/src/test/kotlin/com/jtech/zemer/utils/updater/InstallerTest.kt` | 43 lines | `.kt` |
| `app/src/test/kotlin/com/jtech/zemer/widget/WidgetLayoutTest.kt` | 26 lines | `.kt` |
| `app/universal/release/baselineProfiles/0/app-universal-release.dm` | 10017 bytes | `.dm` |
| `app/universal/release/baselineProfiles/1/app-universal-release.dm` | 9981 bytes | `.dm` |
| `build.gradle.kts` | 37 lines | `.kts` |
| `docs/README.md` | 29 lines | `.md` |
| `docs/app/README.md` | 132 lines | `.md` |
| `docs/app/database.md` | 567 lines | `.md` |
| `docs/app/playback.md` | 38 lines | `.md` |
| `docs/app/preferences-sync-auth.md` | 153 lines | `.md` |
| `docs/app/viewmodels.md` | 40 lines | `.md` |
| `docs/build-release.md` | 67 lines | `.md` |
| `docs/generate.py` | 487 lines | `.py` |
| `docs/innertube/README.md` | 183 lines | `.md` |
| `docs/latest_releases/01-architecture-and-data-flow.md` | 94 lines | `.md` |
| `docs/latest_releases/02-feed-format-and-server.md` | 95 lines | `.md` |
| `docs/latest_releases/03-runtime-store.md` | 128 lines | `.md` |
| `docs/latest_releases/04-viewmodel-and-filtering.md` | 121 lines | `.md` |
| `docs/latest_releases/05-ui.md` | 173 lines | `.md` |
| `docs/latest_releases/06-test-harness.md` | 127 lines | `.md` |
| `docs/latest_releases/07-runbook.md` | 110 lines | `.md` |
| `docs/latest_releases/README.md` | 80 lines | `.md` |
| `docs/multi_update/01-architecture.md` | 80 lines | `.md` |
| `docs/multi_update/02-install-methods.md` | 98 lines | `.md` |
| `docs/multi_update/03-restart.md` | 70 lines | `.md` |
| `docs/multi_update/04-wiring.md` | 95 lines | `.md` |
| `docs/multi_update/05-runbook.md` | 83 lines | `.md` |
| `docs/multi_update/README.md` | 77 lines | `.md` |
| `docs/recognize_music/01-architecture-and-pipeline.md` | 92 lines | `.md` |
| `docs/recognize_music/02-whitelist-guarantee.md` | 100 lines | `.md` |
| `docs/recognize_music/03-entry-points-and-ui.md` | 72 lines | `.md` |
| `docs/recognize_music/04-recognition-history.md` | 78 lines | `.md` |
| `docs/recognize_music/05-widget.md` | 72 lines | `.md` |
| `docs/recognize_music/06-testing-and-maintenance.md` | 54 lines | `.md` |
| `docs/recognize_music/README.md` | 71 lines | `.md` |
| `docs/reference/kotlin-files.md` | 485 lines | `.md` |
| `docs/reference/non-kotlin-files.md` | 365 lines | `.md` |
| `docs/reference/resource-index.md` | 288 lines | `.md` |
| `docs/remote_cipher_config/01-why-it-exists.md` | 88 lines | `.md` |
| `docs/remote_cipher_config/02-file-format.md` | 116 lines | `.md` |
| `docs/remote_cipher_config/03-runtime-store.md` | 156 lines | `.md` |
| `docs/remote_cipher_config/04-validation-and-security.md` | 105 lines | `.md` |
| `docs/remote_cipher_config/05-extraction-and-self-heal.md` | 144 lines | `.md` |
| `docs/remote_cipher_config/06-harness-and-monitor.md` | 101 lines | `.md` |
| `docs/remote_cipher_config/07-runbook.md` | 101 lines | `.md` |
| `docs/remote_cipher_config/README.md` | 112 lines | `.md` |
| `docs/repository-map.md` | 973 lines | `.md` |
| `docs/ui/README.md` | 329 lines | `.md` |
| `docs/ui/standards.md` | 290 lines | `.md` |
| `docs/whitelist/README.md` | 181 lines | `.md` |
| `gradle.properties` | 40 lines | `.properties` |
| `gradle/libs.versions.toml` | 159 lines | `.toml` |
| `gradle/wrapper/gradle-wrapper.jar` | 45457 bytes | `.jar` |
| `gradle/wrapper/gradle-wrapper.properties` | 8 lines | `.properties` |
| `gradlew` | 248 lines | `[none]` |
| `gradlew.bat` | 93 lines | `.bat` |
| `innertube/.gitignore` | 1 lines | `[none]` |
| `innertube/build.gradle.kts` | 21 lines | `.kts` |
| `innertube/src/main/kotlin/com/metrolist/innertube/InnerTube.kt` | 705 lines | `.kt` |
| `innertube/src/main/kotlin/com/metrolist/innertube/YouTube.kt` | 1254 lines | `.kt` |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/AccountInfo.kt` | 8 lines | `.kt` |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/AutomixPreviewVideoRenderer.kt` | 18 lines | `.kt` |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/Badges.kt` | 13 lines | `.kt` |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/Button.kt` | 16 lines | `.kt` |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/Context.kt` | 60 lines | `.kt` |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/Continuation.kt` | 20 lines | `.kt` |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/ContinuationItemRenderer.kt` | 18 lines | `.kt` |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/Endpoint.kt` | 118 lines | `.kt` |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/GridRenderer.kt` | 26 lines | `.kt` |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/Icon.kt` | 8 lines | `.kt` |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/MediaInfo.kt` | 15 lines | `.kt` |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/Menu.kt` | 52 lines | `.kt` |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/MusicCardShelfRenderer.kt` | 30 lines | `.kt` |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/MusicCarouselShelfRenderer.kt` | 31 lines | `.kt` |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/MusicDescriptionShelfRenderer.kt` | 11 lines | `.kt` |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/MusicEditablePlaylistDetailHeaderRenderer.kt` | 35 lines | `.kt` |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/MusicNavigationButtonRenderer.kt` | 21 lines | `.kt` |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/MusicPlaylistShelfRenderer.kt` | 11 lines | `.kt` |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/MusicQueueRenderer.kt` | 25 lines | `.kt` |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/MusicResponsiveHeaderRenderer.kt` | 24 lines | `.kt` |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/MusicResponsiveListItemRenderer.kt` | 98 lines | `.kt` |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/MusicShelfRenderer.kt` | 28 lines | `.kt` |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/MusicTwoRowItemRenderer.kt` | 52 lines | `.kt` |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/NavigationEndpoint.kt` | 27 lines | `.kt` |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/PlaylistDeleteBody.kt` | 10 lines | `.kt` |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/PlaylistPanelRenderer.kt` | 21 lines | `.kt` |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/PlaylistPanelVideoRenderer.kt` | 19 lines | `.kt` |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/ResponseContext.kt` | 21 lines | `.kt` |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/ReturnYouTubeDislikeResponse.kt` | 14 lines | `.kt` |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/Runs.kt` | 43 lines | `.kt` |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/SearchSuggestions.kt` | 6 lines | `.kt` |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/SearchSuggestionsSectionRenderer.kt` | 20 lines | `.kt` |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/SectionListRenderer.kt` | 73 lines | `.kt` |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/SubscriptionButton.kt` | 14 lines | `.kt` |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/Tabs.kt` | 26 lines | `.kt` |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/ThumbnailRenderer.kt` | 29 lines | `.kt` |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/Thumbnails.kt` | 15 lines | `.kt` |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/TwoColumnBrowseResultsRenderer.kt` | 26 lines | `.kt` |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/YTItem.kt` | 92 lines | `.kt` |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/YouTubeClient.kt` | 261 lines | `.kt` |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/YouTubeDataPage.kt` | 183 lines | `.kt` |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/YouTubeLocale.kt` | 9 lines | `.kt` |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/body/AccountMenuBody.kt` | 11 lines | `.kt` |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/body/BrowseBody.kt` | 13 lines | `.kt` |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/body/CreatePlaylistBody.kt` | 18 lines | `.kt` |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/body/EditPlaylistBody.kt` | 79 lines | `.kt` |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/body/FeedbackBody.kt` | 12 lines | `.kt` |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/body/GetQueueBody.kt` | 11 lines | `.kt` |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/body/GetSearchSuggestionsBody.kt` | 10 lines | `.kt` |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/body/GetTranscriptBody.kt` | 10 lines | `.kt` |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/body/LikeBody.kt` | 18 lines | `.kt` |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/body/NextBody.kt` | 15 lines | `.kt` |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/body/PlayerBody.kt` | 30 lines | `.kt` |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/body/SearchBody.kt` | 11 lines | `.kt` |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/body/SubscribeBody.kt` | 10 lines | `.kt` |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/response/AccountMenuResponse.kt` | 53 lines | `.kt` |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/response/AddItemYouTubePlaylistResponse.kt` | 20 lines | `.kt` |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/response/BrowseResponse.kt` | 142 lines | `.kt` |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/response/ContinuationResponse.kt` | 20 lines | `.kt` |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/response/CreatePlaylistResponse.kt` | 8 lines | `.kt` |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/response/EditPlaylistResponse.kt` | 8 lines | `.kt` |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/response/FeedbackResponse.kt` | 13 lines | `.kt` |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/response/GetQueueResponse.kt` | 14 lines | `.kt` |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/response/GetSearchSuggestionsResponse.kt` | 14 lines | `.kt` |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/response/GetTranscriptResponse.kt` | 65 lines | `.kt` |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/response/ImageUploadResponse.kt` | 8 lines | `.kt` |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/response/NextResponse.kt` | 40 lines | `.kt` |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/response/PlayerResponse.kt` | 117 lines | `.kt` |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/response/SearchResponse.kt` | 33 lines | `.kt` |
| `innertube/src/main/kotlin/com/metrolist/innertube/pages/AlbumPage.kt` | 127 lines | `.kt` |
| `innertube/src/main/kotlin/com/metrolist/innertube/pages/ArtistItemsContinuationPage.kt` | 8 lines | `.kt` |
| `innertube/src/main/kotlin/com/metrolist/innertube/pages/ArtistItemsPage.kt` | 122 lines | `.kt` |
| `innertube/src/main/kotlin/com/metrolist/innertube/pages/ArtistPage.kt` | 187 lines | `.kt` |
| `innertube/src/main/kotlin/com/metrolist/innertube/pages/BrowseResult.kt` | 31 lines | `.kt` |
| `innertube/src/main/kotlin/com/metrolist/innertube/pages/ChartsPage.kt` | 18 lines | `.kt` |
| `innertube/src/main/kotlin/com/metrolist/innertube/pages/ExplorePage.kt` | 8 lines | `.kt` |
| `innertube/src/main/kotlin/com/metrolist/innertube/pages/HistoryPage.kt` | 68 lines | `.kt` |
| `innertube/src/main/kotlin/com/metrolist/innertube/pages/HomePage.kt` | 166 lines | `.kt` |
| `innertube/src/main/kotlin/com/metrolist/innertube/pages/LibraryAlbumsPage.kt` | 36 lines | `.kt` |
| `innertube/src/main/kotlin/com/metrolist/innertube/pages/LibraryContinuationPage.kt` | 8 lines | `.kt` |
| `innertube/src/main/kotlin/com/metrolist/innertube/pages/LibraryPage.kt` | 148 lines | `.kt` |
| `innertube/src/main/kotlin/com/metrolist/innertube/pages/MoodAndGenres.kt` | 47 lines | `.kt` |
| `innertube/src/main/kotlin/com/metrolist/innertube/pages/NewPipe.kt` | 133 lines | `.kt` |
| `innertube/src/main/kotlin/com/metrolist/innertube/pages/NewReleaseAlbumPage.kt` | 44 lines | `.kt` |
| `innertube/src/main/kotlin/com/metrolist/innertube/pages/NextPage.kt` | 76 lines | `.kt` |
| `innertube/src/main/kotlin/com/metrolist/innertube/pages/PageHelper.kt` | 38 lines | `.kt` |
| `innertube/src/main/kotlin/com/metrolist/innertube/pages/PlaylistContinuationPage.kt` | 8 lines | `.kt` |
| `innertube/src/main/kotlin/com/metrolist/innertube/pages/PlaylistPage.kt` | 52 lines | `.kt` |
| `innertube/src/main/kotlin/com/metrolist/innertube/pages/RelatedPage.kt` | 163 lines | `.kt` |
| `innertube/src/main/kotlin/com/metrolist/innertube/pages/SearchPage.kt` | 210 lines | `.kt` |
| `innertube/src/main/kotlin/com/metrolist/innertube/pages/SearchSuggestionPage.kt` | 146 lines | `.kt` |
| `innertube/src/main/kotlin/com/metrolist/innertube/pages/SearchSummaryPage.kt` | 377 lines | `.kt` |
| `innertube/src/main/kotlin/com/metrolist/innertube/utils/ResilientDns.kt` | 84 lines | `.kt` |
| `innertube/src/main/kotlin/com/metrolist/innertube/utils/Utils.kt` | 95 lines | `.kt` |
| `lint.xml` | 6 lines | `.xml` |
| `lrclib/.gitignore` | 1 lines | `[none]` |
| `lrclib/build.gradle.kts` | 16 lines | `.kts` |
| `lrclib/src/main/kotlin/com/metrolist/lrclib/LrcLib.kt` | 293 lines | `.kt` |
| `lrclib/src/main/kotlin/com/metrolist/lrclib/models/Track.kt` | 137 lines | `.kt` |
| `scripts/check-16kb-alignment.sh` | 67 lines | `.sh` |
| `scripts/check-download-unification.sh` | 58 lines | `.sh` |
| `scripts/telegram-chats.sh` | 38 lines | `.sh` |
| `scripts/ui-audit-baseline.tsv` | 13 lines | `.tsv` |
| `scripts/ui-audit.sh` | 125 lines | `.sh` |
| `scripts/ui-strings-scan.py` | 96 lines | `.py` |
| `settings.gradle.kts` | 56 lines | `.kts` |
| `simpmusic/build.gradle.kts` | 15 lines | `.kts` |
| `simpmusic/src/main/kotlin/com/metrolist/simpmusic/SimpMusicLyrics.kt` | 119 lines | `.kt` |
| `simpmusic/src/main/kotlin/com/metrolist/simpmusic/models/LyricsResponse.kt` | 32 lines | `.kt` |
| `tests/INVESTIGATION.md` | 276 lines | `.md` |
| `tests/MWEB-INVESTIGATION.md` | 83 lines | `.md` |
| `tests/README.md` | 161 lines | `.md` |
| `tests/analyze-player.mjs` | 53 lines | `.mjs` |
| `tests/broken-clients.mjs` | 142 lines | `.mjs` |
| `tests/check-live-player.mjs` | 81 lines | `.mjs` |
| `tests/cipher-check.mjs` | 86 lines | `.mjs` |
| `tests/cipher.mjs` | 197 lines | `.mjs` |
| `tests/client-fulldownload.mjs` | 109 lines | `.mjs` |
| `tests/clients.mjs` | 69 lines | `.mjs` |
| `tests/config-covers.mjs` | 24 lines | `.mjs` |
| `tests/cred.mjs` | 82 lines | `.mjs` |
| `tests/derive-player-config.mjs` | 89 lines | `.mjs` |
| `tests/discover-clients.mjs` | 116 lines | `.mjs` |
| `tests/full-stream.mjs` | 87 lines | `.mjs` |
| `tests/gen-player-dates.mjs` | 41 lines | `.mjs` |
| `tests/inspect-player.mjs` | 48 lines | `.mjs` |
| `tests/package-lock.json` | 554 lines | `.json` |
| `tests/package.json` | 7 lines | `.json` |
| `tests/player-configs.mjs` | 96 lines | `.mjs` |
| `tests/player-configs.test.mjs` | 120 lines | `.mjs` |
| `tests/pot-probe.mjs` | 139 lines | `.mjs` |
| `tests/potoken.mjs` | 132 lines | `.mjs` |
| `tests/probe-all-ids.mjs` | 126 lines | `.mjs` |
| `tests/probe-client-auth.mjs` | 208 lines | `.mjs` |
| `tests/probe-client-detail.mjs` | 156 lines | `.mjs` |
| `tests/probe-client-v2.mjs` | 203 lines | `.mjs` |
| `tests/probe-client-v3.mjs` | 179 lines | `.mjs` |
| `tests/probe-clients.mjs` | 98 lines | `.mjs` |
| `tests/probe-extended.mjs` | 221 lines | `.mjs` |
| `tests/probe-mweb-app-exact.mjs` | 201 lines | `.mjs` |
| `tests/probe-mweb-deobfuscate.mjs` | 200 lines | `.mjs` |
| `tests/probe-mweb-drain.mjs` | 158 lines | `.mjs` |
| `tests/probe-mweb-full.mjs` | 171 lines | `.mjs` |
| `tests/probe-mweb-itag140.mjs` | 116 lines | `.mjs` |
| `tests/probe-mweb-nopot.mjs` | 105 lines | `.mjs` |
| `tests/probe-mweb-pervideo.mjs` | 129 lines | `.mjs` |
| `tests/probe-mweb-playerjs.mjs` | 46 lines | `.mjs` |
| `tests/probe-mweb-reqpot.mjs` | 108 lines | `.mjs` |
| `tests/probe-mweb-stream.mjs` | 111 lines | `.mjs` |
| `tests/probe-mweb-verdict.mjs` | 156 lines | `.mjs` |
| `tests/probe-mweb-wall-absolute.mjs` | 95 lines | `.mjs` |
| `tests/probe-mweb.mjs` | 136 lines | `.mjs` |
| `tests/probe-proper.mjs` | 202 lines | `.mjs` |
| `tests/probe-yt-endpoint.mjs` | 203 lines | `.mjs` |
| `tests/re-apple.mjs` | 51 lines | `.mjs` |
| `tests/re-compare.mjs` | 58 lines | `.mjs` |
| `tests/re-deep.mjs` | 78 lines | `.mjs` |
| `tests/re-ios.mjs` | 68 lines | `.mjs` |
| `tests/re-oauth.mjs` | 115 lines | `.mjs` |
| `tests/recent-releases/README.md` | 62 lines | `.md` |
| `tests/recent-releases/build-feed.mjs` | 145 lines | `.mjs` |
| `tests/recent-releases/lib.mjs` | 239 lines | `.mjs` |
| `tests/recent-releases/probe-dates.mjs` | 80 lines | `.mjs` |
| `tests/recent-releases/probe-order.mjs` | 103 lines | `.mjs` |
| `tests/recent-releases/self-test.mjs` | 103 lines | `.mjs` |
| `tests/recent-releases/whitelist.mjs` | 62 lines | `.mjs` |
| `tests/results.json` | 222 lines | `.json` |
| `tests/retest-web.mjs` | 58 lines | `.mjs` |
| `tests/run.mjs` | 181 lines | `.mjs` |
| `tests/scan-live-players.mjs` | 115 lines | `.mjs` |
| `tests/scan-live-players.test.mjs` | 44 lines | `.mjs` |
| `tests/search/README.md` | 114 lines | `.md` |
| `tests/search/album-facet-probe.mjs` | 42 lines | `.mjs` |
| `tests/search/coverage.mjs` | 202 lines | `.mjs` |
| `tests/search/diag-auth.mjs` | 77 lines | `.mjs` |
| `tests/search/fetch-whitelist.mjs` | 59 lines | `.mjs` |
| `tests/search/lib.mjs` | 163 lines | `.mjs` |
| `tests/search/parsers.mjs` | 225 lines | `.mjs` |
| `tests/search/pill-survival.mjs` | 114 lines | `.mjs` |
| `tests/search/run.mjs` | 246 lines | `.mjs` |
| `tests/search/schema.mjs` | 239 lines | `.mjs` |
| `tests/search/self-test.mjs` | 136 lines | `.mjs` |
| `tests/search/verify-album-fix.mjs` | 61 lines | `.mjs` |
| `tests/search/whitelist-findable.mjs` | 114 lines | `.mjs` |
| `tests/sts-mismatch.mjs` | 125 lines | `.mjs` |
| `tests/test-mweb-cipher.mjs` | 142 lines | `.mjs` |
| `tests/validate-player-config.mjs` | 186 lines | `.mjs` |
| `tests/web-creator-stream.mjs` | 142 lines | `.mjs` |
| `tests/web-remix-stream.mjs` | 277 lines | `.mjs` |
