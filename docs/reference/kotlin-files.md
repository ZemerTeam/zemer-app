# Kotlin file reference

Every tracked Kotlin file is listed with hard metadata extracted from the file text: line count, package, whether it declares any `@Composable`, import count, top-level declaration count (`Decls` - a high value flags a god-file), and the external import roots it depends on. Declaration counting is regex-based (after stripping comments and string literals). For the actual declaration names, read the file or use your editor's outline - they are not duplicated here.

## `app` Kotlin files (740)

| File | Lines | Package | Compose | Imports | Decls | External import roots |
| --- | ---: | --- | --- | ---: | ---: | --- |
| `app/src/androidTest/kotlin/com/jtech/zemer/OpusDevicePipelineTest.kt` | 108 | `com.jtech.zemer` | no | 7 | 29 | android.util, androidx.test, java.io, org.junit |
| `app/src/main/kotlin/com/dpi/ActivityLifecycleManager.kt` | 127 | `com.dpi` | no | 9 | 28 | android.annotation, android.app, android.os, java.util, timber.log |
| `app/src/main/kotlin/com/dpi/BaseLifecycleContentProvider.kt` | 36 | `com.dpi` | no | 4 | 7 | android.content, android.database, android.net |
| `app/src/main/kotlin/com/dpi/DensityConfiguration.kt` | 121 | `com.dpi` | no | 7 | 14 | android.annotation, android.app, android.content, android.util, timber.log |
| `app/src/main/kotlin/com/dpi/DensityMath.kt` | 25 | `com.dpi` | no | 1 | 3 | kotlin.math |
| `app/src/main/kotlin/com/dpi/DensityScaler.kt` | 80 | `com.dpi` | no | 4 | 13 | android.app, android.content, timber.log |
| `app/src/main/kotlin/com/jtech/zemer/App.kt` | 404 | `com.jtech.zemer` | no | 60 | 29 | android.app, android.content, android.os, android.util, android.webkit, androidx.datastore, coil3.ImageLoader, coil3.PlatformContext, coil3.SingletonImageLoader, coil3.disk, coil3.network, coil3.request, coil3.svg, com.google, com.zemer, dagger.hilt, java.util, javax.inject, kotlinx.coroutines, okhttp3.Dispatcher, okhttp3.OkHttpClient, timber.log |
| `app/src/main/kotlin/com/jtech/zemer/MainActivity.kt` | 2433 | `com.jtech.zemer` | no | 312 | 243 | android.annotation, android.app, android.content, android.os, android.view, androidx.activity, androidx.compose, androidx.core, androidx.datastore, androidx.hilt, androidx.lifecycle, androidx.media3, androidx.navigation, coil3.compose, coil3.imageLoader, coil3.request, coil3.toBitmap, com.google, com.valentinilk, dagger.hilt, java.net, java.util, javax.inject, kotlin.time, kotlinx.coroutines, timber.log |
| `app/src/main/kotlin/com/jtech/zemer/accessibility/ButtonMapperAccessibilityService.kt` | 45 | `com.jtech.zemer.accessibility` | no | 7 | 6 | android.accessibilityservice, android.annotation, android.view |
| `app/src/main/kotlin/com/jtech/zemer/auth/AuthState.kt` | 45 | `com.jtech.zemer.auth` | no | 0 | 13 |  |
| `app/src/main/kotlin/com/jtech/zemer/auth/UserAuthManager.kt` | 120 | `com.jtech.zemer.auth` | no | 13 | 21 | android.content, com.google, dagger.hilt, javax.inject, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/auth/WebViewGoogleAuthManager.kt` | 33 | `com.jtech.zemer.auth` | no | 5 | 5 | android.util, com.google, javax.inject, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/constants/Dimensions.kt` | 46 | `com.jtech.zemer.constants` | no | 4 | 23 | androidx.compose |
| `app/src/main/kotlin/com/jtech/zemer/constants/HistorySource.kt` | 7 | `com.jtech.zemer.constants` | no | 0 | 1 |  |
| `app/src/main/kotlin/com/jtech/zemer/constants/LibraryFilter.kt` | 14 | `com.jtech.zemer.constants` | no | 0 | 1 |  |
| `app/src/main/kotlin/com/jtech/zemer/constants/MediaSessionConstants.kt` | 23 | `com.jtech.zemer.constants` | no | 2 | 14 | android.os, androidx.media3 |
| `app/src/main/kotlin/com/jtech/zemer/constants/PlaybackMode.kt` | 11 | `com.jtech.zemer.constants` | no | 0 | 1 |  |
| `app/src/main/kotlin/com/jtech/zemer/constants/PreferenceKeys.kt` | 661 | `com.jtech.zemer.constants` | no | 9 | 197 | androidx.annotation, androidx.datastore, java.time |
| `app/src/main/kotlin/com/jtech/zemer/db/Converters.kt` | 20 | `com.jtech.zemer.db` | no | 4 | 3 | androidx.room, java.time |
| `app/src/main/kotlin/com/jtech/zemer/db/DatabaseDao.kt` | 1814 | `com.jtech.zemer.db` | no | 65 | 247 | androidx.room, androidx.sqlite, java.text, java.time, java.util, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/db/MusicDatabase.kt` | 654 | `com.jtech.zemer.db` | no | 44 | 82 | android.annotation, android.content, android.database, androidx.core, androidx.room, androidx.sqlite, java.time, java.util, timber.log |
| `app/src/main/kotlin/com/jtech/zemer/db/entities/ActionSnapshotRow.kt` | 13 | `com.jtech.zemer.db.entities` | no | 1 | 3 | java.time |
| `app/src/main/kotlin/com/jtech/zemer/db/entities/Album.kt` | 33 | `com.jtech.zemer.db.entities` | no | 4 | 8 | androidx.compose, androidx.room |
| `app/src/main/kotlin/com/jtech/zemer/db/entities/AlbumArtistMap.kt` | 29 | `com.jtech.zemer.db.entities` | no | 3 | 4 | androidx.room |
| `app/src/main/kotlin/com/jtech/zemer/db/entities/AlbumEntity.kt` | 64 | `com.jtech.zemer.db.entities` | no | 13 | 20 | androidx.compose, androidx.room, java.time, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/db/entities/AlbumWithSongs.kt` | 36 | `com.jtech.zemer.db.entities` | no | 4 | 4 | androidx.compose, androidx.room |
| `app/src/main/kotlin/com/jtech/zemer/db/entities/Artist.kt` | 19 | `com.jtech.zemer.db.entities` | no | 2 | 7 | androidx.compose, androidx.room |
| `app/src/main/kotlin/com/jtech/zemer/db/entities/ArtistEntity.kt` | 60 | `com.jtech.zemer.db.entities` | no | 13 | 13 | androidx.compose, androidx.room, java.time, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/db/entities/ArtistWhitelistEntity.kt` | 34 | `com.jtech.zemer.db.entities` | no | 6 | 14 | androidx.compose, androidx.room, java.time |
| `app/src/main/kotlin/com/jtech/zemer/db/entities/Event.kt` | 32 | `com.jtech.zemer.db.entities` | no | 7 | 5 | androidx.compose, androidx.room, java.time |
| `app/src/main/kotlin/com/jtech/zemer/db/entities/EventWithSong.kt` | 17 | `com.jtech.zemer.db.entities` | no | 3 | 3 | androidx.compose, androidx.room |
| `app/src/main/kotlin/com/jtech/zemer/db/entities/FormatEntity.kt` | 19 | `com.jtech.zemer.db.entities` | no | 2 | 11 | androidx.room |
| `app/src/main/kotlin/com/jtech/zemer/db/entities/LocalItem.kt` | 7 | `com.jtech.zemer.db.entities` | no | 0 | 4 |  |
| `app/src/main/kotlin/com/jtech/zemer/db/entities/LyricsEntity.kt` | 41 | `com.jtech.zemer.db.entities` | no | 2 | 8 | androidx.room |
| `app/src/main/kotlin/com/jtech/zemer/db/entities/PlayCountEntity.kt` | 16 | `com.jtech.zemer.db.entities` | no | 2 | 5 | androidx.compose, androidx.room |
| `app/src/main/kotlin/com/jtech/zemer/db/entities/Playlist.kt` | 40 | `com.jtech.zemer.db.entities` | no | 4 | 8 | androidx.compose, androidx.room |
| `app/src/main/kotlin/com/jtech/zemer/db/entities/PlaylistEntity.kt` | 67 | `com.jtech.zemer.db.entities` | no | 13 | 20 | androidx.compose, androidx.room, java.time, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/db/entities/PlaylistSong.kt` | 14 | `com.jtech.zemer.db.entities` | no | 2 | 3 | androidx.room |
| `app/src/main/kotlin/com/jtech/zemer/db/entities/PlaylistSongMap.kt` | 31 | `com.jtech.zemer.db.entities` | no | 4 | 6 | androidx.room |
| `app/src/main/kotlin/com/jtech/zemer/db/entities/PlaylistSongMapPreview.kt` | 14 | `com.jtech.zemer.db.entities` | no | 2 | 4 | androidx.room |
| `app/src/main/kotlin/com/jtech/zemer/db/entities/PodcastEntity.kt` | 29 | `com.jtech.zemer.db.entities` | no | 4 | 10 | androidx.compose, androidx.room, java.time |
| `app/src/main/kotlin/com/jtech/zemer/db/entities/PodcastWhitelistEntity.kt` | 26 | `com.jtech.zemer.db.entities` | no | 4 | 9 | androidx.compose, androidx.room, java.time |
| `app/src/main/kotlin/com/jtech/zemer/db/entities/RecognitionHistoryEntity.kt` | 31 | `com.jtech.zemer.db.entities` | no | 4 | 8 | androidx.room, java.time |
| `app/src/main/kotlin/com/jtech/zemer/db/entities/RelatedSongMap.kt` | 29 | `com.jtech.zemer.db.entities` | no | 4 | 4 | androidx.room |
| `app/src/main/kotlin/com/jtech/zemer/db/entities/SearchHistory.kt` | 19 | `com.jtech.zemer.db.entities` | no | 3 | 3 | androidx.room |
| `app/src/main/kotlin/com/jtech/zemer/db/entities/SetVideoIdEntity.kt` | 11 | `com.jtech.zemer.db.entities` | no | 2 | 3 | androidx.room |
| `app/src/main/kotlin/com/jtech/zemer/db/entities/Song.kt` | 52 | `com.jtech.zemer.db.entities` | no | 4 | 8 | androidx.compose, androidx.room |
| `app/src/main/kotlin/com/jtech/zemer/db/entities/SongAlbumMap.kt` | 29 | `com.jtech.zemer.db.entities` | no | 3 | 4 | androidx.room |
| `app/src/main/kotlin/com/jtech/zemer/db/entities/SongArtistMap.kt` | 29 | `com.jtech.zemer.db.entities` | no | 3 | 4 | androidx.room |
| `app/src/main/kotlin/com/jtech/zemer/db/entities/SongEntity.kt` | 105 | `com.jtech.zemer.db.entities` | no | 14 | 31 | androidx.compose, androidx.room, java.time, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/db/entities/SongWithStats.kt` | 12 | `com.jtech.zemer.db.entities` | no | 1 | 6 | androidx.compose |
| `app/src/main/kotlin/com/jtech/zemer/db/entities/SortedSongAlbumMap.kt` | 14 | `com.jtech.zemer.db.entities` | no | 2 | 4 | androidx.room |
| `app/src/main/kotlin/com/jtech/zemer/db/entities/SortedSongArtistMap.kt` | 14 | `com.jtech.zemer.db.entities` | no | 2 | 4 | androidx.room |
| `app/src/main/kotlin/com/jtech/zemer/di/AppModule.kt` | 90 | `com.jtech.zemer.di` | no | 22 | 8 | android.content, androidx.media3, com.google, dagger.Module, dagger.Provides, dagger.hilt, javax.inject, kotlinx.coroutines, timber.log |
| `app/src/main/kotlin/com/jtech/zemer/di/DataStoreQualifiers.kt` | 11 | `com.jtech.zemer.di` | no | 1 | 2 | javax.inject |
| `app/src/main/kotlin/com/jtech/zemer/di/LyricsHelperEntryPoint.kt` | 12 | `com.jtech.zemer.di` | no | 4 | 2 | dagger.hilt |
| `app/src/main/kotlin/com/jtech/zemer/di/NetworkModule.kt` | 21 | `com.jtech.zemer.di` | no | 8 | 2 | android.content, dagger.Module, dagger.Provides, dagger.hilt, javax.inject |
| `app/src/main/kotlin/com/jtech/zemer/di/Qualifiers.kt` | 15 | `com.jtech.zemer.di` | no | 1 | 3 | javax.inject |
| `app/src/main/kotlin/com/jtech/zemer/di/SyncModule.kt` | 121 | `com.jtech.zemer.di` | no | 16 | 9 | android.content, androidx.datastore, com.google, dagger.Module, dagger.Provides, dagger.hilt, javax.inject |
| `app/src/main/kotlin/com/jtech/zemer/di/ZemerSearchRepositoryEntryPoint.kt` | 29 | `com.jtech.zemer.di` | no | 6 | 3 | android.content, dagger.hilt |
| `app/src/main/kotlin/com/jtech/zemer/extensions/AccountState.kt` | 29 | `com.jtech.zemer.extensions` | no | 6 | 2 | android.content, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/extensions/ContextExt.kt` | 200 | `com.jtech.zemer.extensions` | no | 18 | 25 | android.content, android.net, android.widget, androidx.annotation, kotlin.contracts, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/extensions/CoroutineExt.kt` | 27 | `com.jtech.zemer.extensions` | no | 5 | 1 | kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/extensions/FileExt.kt` | 13 | `com.jtech.zemer.extensions` | no | 5 | 3 | java.io, java.util |
| `app/src/main/kotlin/com/jtech/zemer/extensions/ListExt.kt` | 11 | `com.jtech.zemer.extensions` | no | 0 | 0 |  |
| `app/src/main/kotlin/com/jtech/zemer/extensions/MediaItemExt.kt` | 76 | `com.jtech.zemer.extensions` | no | 9 | 5 | androidx.core, androidx.media3 |
| `app/src/main/kotlin/com/jtech/zemer/extensions/PlayerExt.kt` | 159 | `com.jtech.zemer.extensions` | no | 13 | 22 | androidx.annotation, androidx.media3, java.util |
| `app/src/main/kotlin/com/jtech/zemer/extensions/QueueExt.kt` | 100 | `com.jtech.zemer.extensions` | no | 8 | 3 |  |
| `app/src/main/kotlin/com/jtech/zemer/extensions/StringExt.kt` | 16 | `com.jtech.zemer.extensions` | no | 1 | 1 | androidx.sqlite |
| `app/src/main/kotlin/com/jtech/zemer/extensions/UtilExt.kt` | 8 | `com.jtech.zemer.extensions` | no | 0 | 0 |  |
| `app/src/main/kotlin/com/jtech/zemer/latestreleases/LatestReleaseCard.kt` | 106 | `com.jtech.zemer.latestreleases` | yes | 27 | 12 | androidx.compose, androidx.navigation |
| `app/src/main/kotlin/com/jtech/zemer/latestreleases/LatestReleaseCarouselItem.kt` | 147 | `com.jtech.zemer.latestreleases` | yes | 33 | 11 | androidx.compose, androidx.navigation, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/latestreleases/LatestReleaseDate.kt` | 16 | `com.jtech.zemer.latestreleases` | no | 2 | 2 | android.text, java.time |
| `app/src/main/kotlin/com/jtech/zemer/latestreleases/LatestReleaseFilter.kt` | 15 | `com.jtech.zemer.latestreleases` | no | 0 | 2 |  |
| `app/src/main/kotlin/com/jtech/zemer/latestreleases/LatestReleaseMapping.kt` | 18 | `com.jtech.zemer.latestreleases` | no | 2 | 1 |  |
| `app/src/main/kotlin/com/jtech/zemer/latestreleases/LatestReleasePlayback.kt` | 131 | `com.jtech.zemer.latestreleases` | no | 16 | 14 | android.content, androidx.navigation, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/latestreleases/LatestReleasesStore.kt` | 256 | `com.jtech.zemer.latestreleases` | no | 17 | 65 | android.content, io.ktor, java.io, kotlinx.coroutines, kotlinx.serialization, timber.log |
| `app/src/main/kotlin/com/jtech/zemer/lyrics/LrcLibLyricsProvider.kt` | 32 | `com.jtech.zemer.lyrics` | no | 5 | 5 | android.content |
| `app/src/main/kotlin/com/jtech/zemer/lyrics/LyricsEntry.kt` | 23 | `com.jtech.zemer.lyrics` | no | 0 | 9 |  |
| `app/src/main/kotlin/com/jtech/zemer/lyrics/LyricsHelper.kt` | 170 | `com.jtech.zemer.lyrics` | no | 18 | 27 | android.content, android.util, dagger.hilt, javax.inject, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/lyrics/LyricsProvider.kt` | 56 | `com.jtech.zemer.lyrics` | no | 1 | 10 | android.content |
| `app/src/main/kotlin/com/jtech/zemer/lyrics/LyricsProviderRegistry.kt` | 43 | `com.jtech.zemer.lyrics` | no | 1 | 9 |  |
| `app/src/main/kotlin/com/jtech/zemer/lyrics/LyricsUtils.kt` | 131 | `com.jtech.zemer.lyrics` | no | 1 | 33 | android.text |
| `app/src/main/kotlin/com/jtech/zemer/lyrics/MusixmatchLyricsProvider.kt` | 24 | `com.jtech.zemer.lyrics` | no | 6 | 7 | android.content |
| `app/src/main/kotlin/com/jtech/zemer/lyrics/SimpMusicLyricsProvider.kt` | 32 | `com.jtech.zemer.lyrics` | no | 5 | 5 | android.content |
| `app/src/main/kotlin/com/jtech/zemer/lyrics/YouTubeLyricsProvider.kt` | 31 | `com.jtech.zemer.lyrics` | no | 7 | 5 | android.content |
| `app/src/main/kotlin/com/jtech/zemer/lyrics/YouTubeSubtitleLyricsProvider.kt` | 21 | `com.jtech.zemer.lyrics` | no | 5 | 4 | android.content |
| `app/src/main/kotlin/com/jtech/zemer/lyrics/model/LyricsUnavailableException.kt` | 9 | `com.jtech.zemer.lyrics.model` | no | 0 | 2 |  |
| `app/src/main/kotlin/com/jtech/zemer/lyrics/musixmatch/MusixmatchLyrics.kt` | 223 | `com.jtech.zemer.lyrics.musixmatch` | no | 25 | 86 | android.content, androidx.datastore, io.ktor, java.text, kotlin.math, kotlinx.serialization |
| `app/src/main/kotlin/com/jtech/zemer/lyrics/zemer/JkaraokeLrc.kt` | 42 | `com.jtech.zemer.lyrics.zemer` | no | 3 | 25 | java.util, kotlinx.serialization |
| `app/src/main/kotlin/com/jtech/zemer/lyrics/zemer/JyricsParser.kt` | 53 | `com.jtech.zemer.lyrics.zemer` | no | 0 | 25 |  |
| `app/src/main/kotlin/com/jtech/zemer/lyrics/zemer/ShironetParser.kt` | 45 | `com.jtech.zemer.lyrics.zemer` | no | 0 | 22 |  |
| `app/src/main/kotlin/com/jtech/zemer/lyrics/zemer/ZemerLyricsClient.kt` | 112 | `com.jtech.zemer.lyrics.zemer` | no | 19 | 39 | io.ktor, kotlinx.serialization |
| `app/src/main/kotlin/com/jtech/zemer/lyrics/zemer/ZemerLyricsProvider.kt` | 73 | `com.jtech.zemer.lyrics.zemer` | no | 7 | 15 | android.content |
| `app/src/main/kotlin/com/jtech/zemer/lyrics/zemer/ZingParser.kt` | 33 | `com.jtech.zemer.lyrics.zemer` | no | 0 | 12 |  |
| `app/src/main/kotlin/com/jtech/zemer/models/DpadDirection.kt` | 27 | `com.jtech.zemer.models` | no | 9 | 5 | android.view, androidx.annotation, androidx.datastore |
| `app/src/main/kotlin/com/jtech/zemer/models/ItemsPage.kt` | 8 | `com.jtech.zemer.models` | no | 1 | 3 |  |
| `app/src/main/kotlin/com/jtech/zemer/models/MediaMetadata.kt` | 179 | `com.jtech.zemer.models` | no | 9 | 31 | androidx.compose, java.io, java.time |
| `app/src/main/kotlin/com/jtech/zemer/models/PersistPlayerState.kt` | 19 | `com.jtech.zemer.models` | no | 1 | 10 | java.io |
| `app/src/main/kotlin/com/jtech/zemer/models/PersistQueue.kt` | 91 | `com.jtech.zemer.models` | no | 1 | 41 | java.io |
| `app/src/main/kotlin/com/jtech/zemer/offline/OfflineReadProvider.kt` | 144 | `com.jtech.zemer.offline` | no | 24 | 29 | android.content, dagger.hilt, java.lang, javax.inject, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/offline/OfflineSubsetSyncer.kt` | 190 | `com.jtech.zemer.offline` | no | 23 | 31 | android.content, androidx.datastore, dagger.hilt, java.io, javax.inject, kotlinx.coroutines, timber.log |
| `app/src/main/kotlin/com/jtech/zemer/offline/SubsetCategories.kt` | 435 | `com.jtech.zemer.offline` | no | 9 | 165 | java.util |
| `app/src/main/kotlin/com/jtech/zemer/offline/SubsetCorpus.kt` | 176 | `com.jtech.zemer.offline` | no | 0 | 117 |  |
| `app/src/main/kotlin/com/jtech/zemer/offline/SubsetDecoder.kt` | 251 | `com.jtech.zemer.offline` | no | 17 | 54 | java.io, java.util, kotlinx.coroutines, kotlinx.serialization, timber.log |
| `app/src/main/kotlin/com/jtech/zemer/offline/SubsetFemale.kt` | 147 | `com.jtech.zemer.offline` | no | 0 | 39 |  |
| `app/src/main/kotlin/com/jtech/zemer/offline/SubsetHash.kt` | 23 | `com.jtech.zemer.offline` | no | 1 | 5 | java.security |
| `app/src/main/kotlin/com/jtech/zemer/offline/SubsetLiveWhitelist.kt` | 123 | `com.jtech.zemer.offline` | no | 1 | 20 | java.util |
| `app/src/main/kotlin/com/jtech/zemer/offline/SubsetManifest.kt` | 74 | `com.jtech.zemer.offline` | no | 1 | 21 | kotlinx.serialization |
| `app/src/main/kotlin/com/jtech/zemer/offline/SubsetNormalize.kt` | 115 | `com.jtech.zemer.offline` | no | 2 | 29 | java.text, kotlin.math |
| `app/src/main/kotlin/com/jtech/zemer/offline/SubsetReadLayer.kt` | 803 | `com.jtech.zemer.offline` | no | 24 | 128 | java.util |
| `app/src/main/kotlin/com/jtech/zemer/offline/SubsetSearch.kt` | 315 | `com.jtech.zemer.offline` | no | 4 | 116 | kotlin.math |
| `app/src/main/kotlin/com/jtech/zemer/offline/SubsetStore.kt` | 115 | `com.jtech.zemer.offline` | no | 5 | 26 | android.content, java.io, kotlinx.serialization |
| `app/src/main/kotlin/com/jtech/zemer/offline/SubsetSyncClient.kt` | 61 | `com.jtech.zemer.offline` | no | 12 | 10 | io.ktor, java.io, javax.inject, kotlinx.serialization |
| `app/src/main/kotlin/com/jtech/zemer/offline/SubsetSynonyms.kt` | 59 | `com.jtech.zemer.offline` | no | 0 | 14 |  |
| `app/src/main/kotlin/com/jtech/zemer/playback/CastAutoAdvance.kt` | 74 | `com.jtech.zemer.playback` | no | 0 | 13 |  |
| `app/src/main/kotlin/com/jtech/zemer/playback/CastAwarePlayer.kt` | 203 | `com.jtech.zemer.playback` | no | 5 | 46 | androidx.media3, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/playback/CastConnect.kt` | 139 | `com.jtech.zemer.playback` | no | 10 | 16 | java.net, kotlinx.coroutines, org.fcast |
| `app/src/main/kotlin/com/jtech/zemer/playback/CastConnector.kt` | 95 | `com.jtech.zemer.playback` | no | 2 | 21 | org.fcast |
| `app/src/main/kotlin/com/jtech/zemer/playback/CastController.kt` | 386 | `com.jtech.zemer.playback` | no | 17 | 46 | androidx.media3, kotlinx.coroutines, org.fcast, timber.log |
| `app/src/main/kotlin/com/jtech/zemer/playback/CastDeviceAddressResolver.kt` | 94 | `com.jtech.zemer.playback` | no | 13 | 15 | android.content, android.net, android.os, java.net, kotlin.coroutines, kotlinx.coroutines, org.fcast, timber.log |
| `app/src/main/kotlin/com/jtech/zemer/playback/CastDeviceCatalog.kt` | 80 | `com.jtech.zemer.playback` | no | 3 | 10 | org.fcast |
| `app/src/main/kotlin/com/jtech/zemer/playback/CastDeviceRefresher.kt` | 161 | `com.jtech.zemer.playback` | no | 20 | 24 | android.content, android.net, java.net, java.util, kotlinx.coroutines, org.fcast, timber.log |
| `app/src/main/kotlin/com/jtech/zemer/playback/CastErrorRecovery.kt` | 73 | `com.jtech.zemer.playback` | no | 0 | 8 |  |
| `app/src/main/kotlin/com/jtech/zemer/playback/CastIdleWatchdog.kt` | 53 | `com.jtech.zemer.playback` | no | 1 | 5 | org.fcast |
| `app/src/main/kotlin/com/jtech/zemer/playback/CastNativeLibLoader.kt` | 207 | `com.jtech.zemer.playback` | no | 11 | 44 | android.content, android.os, java.io, java.net, java.security, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/playback/CastPlayback.kt` | 97 | `com.jtech.zemer.playback` | no | 1 | 13 | org.fcast |
| `app/src/main/kotlin/com/jtech/zemer/playback/CastRelayProtocol.kt` | 89 | `com.jtech.zemer.playback` | no | 4 | 27 | java.net, java.security |
| `app/src/main/kotlin/com/jtech/zemer/playback/CastSessionLocks.kt` | 48 | `com.jtech.zemer.playback` | no | 4 | 6 | android.annotation, android.content, android.net, android.os |
| `app/src/main/kotlin/com/jtech/zemer/playback/CastStreamRelay.kt` | 375 | `com.jtech.zemer.playback` | no | 19 | 75 | java.io, java.net, java.nio, java.security, java.util, timber.log |
| `app/src/main/kotlin/com/jtech/zemer/playback/CastVolumeKeys.kt` | 57 | `com.jtech.zemer.playback` | no | 1 | 7 | android.view |
| `app/src/main/kotlin/com/jtech/zemer/playback/DeferredStatsPush.kt` | 51 | `com.jtech.zemer.playback` | no | 1 | 7 |  |
| `app/src/main/kotlin/com/jtech/zemer/playback/DeferredStatsQueue.kt` | 127 | `com.jtech.zemer.playback` | no | 6 | 23 | java.io, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/playback/DeferredStatsRecord.kt` | 53 | `com.jtech.zemer.playback` | no | 3 | 12 | kotlinx.serialization |
| `app/src/main/kotlin/com/jtech/zemer/playback/DownloadMenuLogic.kt` | 68 | `com.jtech.zemer.playback` | no | 0 | 4 |  |
| `app/src/main/kotlin/com/jtech/zemer/playback/DownloadStateResolver.kt` | 106 | `com.jtech.zemer.playback` | no | 3 | 11 |  |
| `app/src/main/kotlin/com/jtech/zemer/playback/DownloadUtil.kt` | 165 | `com.jtech.zemer.playback` | no | 34 | 23 | android.content, android.net, androidx.core, androidx.media3, dagger.hilt, java.util, javax.inject, kotlinx.coroutines, okhttp3.OkHttpClient |
| `app/src/main/kotlin/com/jtech/zemer/playback/EpisodePositionTracker.kt` | 157 | `com.jtech.zemer.playback` | no | 14 | 24 | androidx.media3, kotlin.time, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/playback/EpisodeResume.kt` | 39 | `com.jtech.zemer.playback` | no | 0 | 6 |  |
| `app/src/main/kotlin/com/jtech/zemer/playback/FCastDiscoveryHandler.kt` | 390 | `com.jtech.zemer.playback` | no | 5 | 73 | kotlinx.coroutines, org.fcast |
| `app/src/main/kotlin/com/jtech/zemer/playback/ListenAccumulator.kt` | 69 | `com.jtech.zemer.playback` | no | 0 | 12 |  |
| `app/src/main/kotlin/com/jtech/zemer/playback/MediaLibrarySessionCallback.kt` | 810 | `com.jtech.zemer.playback` | no | 58 | 80 | android.content, android.net, android.os, androidx.annotation, androidx.core, androidx.media3, com.google, dagger.hilt, javax.inject, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/playback/MediaStoreDownloadManager.kt` | 1226 | `com.jtech.zemer.playback` | no | 66 | 144 | android.content, android.media, android.net, androidx.core, dagger.hilt, java.io, java.time, java.util, javax.inject, kotlin.math, kotlinx.coroutines, okhttp3.OkHttpClient, okhttp3.Request, timber.log |
| `app/src/main/kotlin/com/jtech/zemer/playback/MediaStoreDownloadService.kt` | 302 | `com.jtech.zemer.playback` | no | 27 | 48 | android.app, android.content, android.os, androidx.core, dagger.hilt, javax.inject, kotlin.math, kotlinx.coroutines, timber.log |
| `app/src/main/kotlin/com/jtech/zemer/playback/MusicService.kt` | 3159 | `com.jtech.zemer.playback` | no | 192 | 338 | android.app, android.content, android.database, android.media, android.net, android.os, androidx.core, androidx.datastore, androidx.media3, com.zemer, dagger.hilt, java.io, java.time, java.util, javax.inject, kotlin.time, kotlinx.coroutines, okhttp3.OkHttpClient, org.fcast, timber.log |
| `app/src/main/kotlin/com/jtech/zemer/playback/PlaybackNonceRegistry.kt` | 76 | `com.jtech.zemer.playback` | no | 1 | 13 |  |
| `app/src/main/kotlin/com/jtech/zemer/playback/PlaybackProbe.kt` | 31 | `com.jtech.zemer.playback` | no | 0 | 8 |  |
| `app/src/main/kotlin/com/jtech/zemer/playback/PlayerConnection.kt` | 413 | `com.jtech.zemer.playback` | no | 30 | 71 | android.content, androidx.media3, kotlinx.coroutines, org.fcast |
| `app/src/main/kotlin/com/jtech/zemer/playback/PlayerVideoUiLogic.kt` | 49 | `com.jtech.zemer.playback` | no | 0 | 5 |  |
| `app/src/main/kotlin/com/jtech/zemer/playback/QueuePersist.kt` | 46 | `com.jtech.zemer.playback` | no | 1 | 4 |  |
| `app/src/main/kotlin/com/jtech/zemer/playback/RemoteVolumeTracker.kt` | 54 | `com.jtech.zemer.playback` | no | 3 | 9 | kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/playback/SeekMath.kt` | 15 | `com.jtech.zemer.playback` | no | 0 | 3 |  |
| `app/src/main/kotlin/com/jtech/zemer/playback/SleepTimer.kt` | 81 | `com.jtech.zemer.playback` | no | 11 | 12 | androidx.compose, androidx.media3, kotlin.time, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/playback/VideoAvailabilityCache.kt` | 69 | `com.jtech.zemer.playback` | no | 3 | 18 | kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/playback/VideoDecoderCaps.kt` | 35 | `com.jtech.zemer.playback` | no | 2 | 6 | android.media, java.util |
| `app/src/main/kotlin/com/jtech/zemer/playback/VideoModeController.kt` | 894 | `com.jtech.zemer.playback` | no | 33 | 124 | android.os, android.view, androidx.media3, kotlinx.coroutines, org.fcast, timber.log |
| `app/src/main/kotlin/com/jtech/zemer/playback/VideoModeLogic.kt` | 190 | `com.jtech.zemer.playback` | no | 1 | 11 |  |
| `app/src/main/kotlin/com/jtech/zemer/playback/VideoQualityLogic.kt` | 204 | `com.jtech.zemer.playback` | no | 1 | 39 |  |
| `app/src/main/kotlin/com/jtech/zemer/playback/VideoRendition.kt` | 95 | `com.jtech.zemer.playback` | no | 0 | 20 |  |
| `app/src/main/kotlin/com/jtech/zemer/playback/VideoSongIds.kt` | 28 | `com.jtech.zemer.playback` | no | 0 | 6 |  |
| `app/src/main/kotlin/com/jtech/zemer/playback/WatchTimeReporter.kt` | 491 | `com.jtech.zemer.playback` | no | 11 | 85 | androidx.media3, java.util, kotlinx.coroutines, timber.log |
| `app/src/main/kotlin/com/jtech/zemer/playback/WatchTimeSchedule.kt` | 41 | `com.jtech.zemer.playback` | no | 0 | 6 |  |
| `app/src/main/kotlin/com/jtech/zemer/playback/WatchTimeSegments.kt` | 117 | `com.jtech.zemer.playback` | no | 1 | 20 | java.util |
| `app/src/main/kotlin/com/jtech/zemer/playback/queues/EmptyQueue.kt` | 14 | `com.jtech.zemer.playback.queues` | no | 2 | 5 | androidx.media3 |
| `app/src/main/kotlin/com/jtech/zemer/playback/queues/ListQueue.kt` | 38 | `com.jtech.zemer.playback.queues` | no | 5 | 12 | androidx.media3 |
| `app/src/main/kotlin/com/jtech/zemer/playback/queues/LocalAlbumRadio.kt` | 62 | `com.jtech.zemer.playback.queues` | no | 10 | 14 | android.content, androidx.media3, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/playback/queues/Queue.kt` | 93 | `com.jtech.zemer.playback.queues` | no | 4 | 19 | androidx.media3 |
| `app/src/main/kotlin/com/jtech/zemer/playback/queues/StationQueue.kt` | 160 | `com.jtech.zemer.playback.queues` | no | 19 | 32 | android.content, androidx.media3, java.io, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/playback/queues/YouTubeQueue.kt` | 75 | `com.jtech.zemer.playback.queues` | no | 11 | 16 | androidx.media3, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/playback/queues/ZemerRadioQueue.kt` | 126 | `com.jtech.zemer.playback.queues` | no | 10 | 23 | android.content, androidx.media3, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/playback/relay/RelayDataSourceFactory.kt` | 62 | `com.jtech.zemer.playback.relay` | no | 11 | 7 | android.content, androidx.core, androidx.media3, java.util, okhttp3.OkHttpClient |
| `app/src/main/kotlin/com/jtech/zemer/playback/relay/RelayDeviceId.kt` | 72 | `com.jtech.zemer.playback.relay` | no | 10 | 10 | android.content, androidx.datastore, java.util, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/playback/relay/RelayDownload.kt` | 46 | `com.jtech.zemer.playback.relay` | no | 0 | 5 |  |
| `app/src/main/kotlin/com/jtech/zemer/playback/relay/RelayStream.kt` | 30 | `com.jtech.zemer.playback.relay` | no | 1 | 9 |  |
| `app/src/main/kotlin/com/jtech/zemer/playback/sabr/SabrBuffer.kt` | 245 | `com.jtech.zemer.playback.sabr` | no | 4 | 54 | java.io, java.util |
| `app/src/main/kotlin/com/jtech/zemer/playback/sabr/SabrDataSource.kt` | 273 | `com.jtech.zemer.playback.sabr` | no | 11 | 67 | android.net, androidx.media3, java.io, java.util, okhttp3.OkHttpClient, timber.log |
| `app/src/main/kotlin/com/jtech/zemer/playback/sabr/SabrMessages.kt` | 193 | `com.jtech.zemer.playback.sabr` | no | 4 | 54 |  |
| `app/src/main/kotlin/com/jtech/zemer/playback/sabr/SabrPlayerResolver.kt` | 282 | `com.jtech.zemer.playback.sabr` | no | 8 | 54 | android.net, com.zemer, kotlinx.coroutines, timber.log |
| `app/src/main/kotlin/com/jtech/zemer/playback/sabr/SabrProtection.kt` | 22 | `com.jtech.zemer.playback.sabr` | no | 0 | 5 |  |
| `app/src/main/kotlin/com/jtech/zemer/playback/sabr/SabrProto.kt` | 101 | `com.jtech.zemer.playback.sabr` | no | 1 | 40 | java.io |
| `app/src/main/kotlin/com/jtech/zemer/playback/sabr/SabrSeekLogic.kt` | 64 | `com.jtech.zemer.playback.sabr` | no | 0 | 15 |  |
| `app/src/main/kotlin/com/jtech/zemer/playback/sabr/SabrSession.kt` | 274 | `com.jtech.zemer.playback.sabr` | no | 5 | 75 | okhttp3.MediaType, okhttp3.OkHttpClient, okhttp3.Request, okhttp3.RequestBody, timber.log |
| `app/src/main/kotlin/com/jtech/zemer/playback/sabr/SabrSpool.kt` | 145 | `com.jtech.zemer.playback.sabr` | no | 3 | 41 | java.io, java.util, timber.log |
| `app/src/main/kotlin/com/jtech/zemer/playback/sabr/SabrStreamResolver.kt` | 178 | `com.jtech.zemer.playback.sabr` | no | 8 | 29 | android.util, java.io, java.util, kotlinx.coroutines, okhttp3.OkHttpClient, timber.log |
| `app/src/main/kotlin/com/jtech/zemer/playback/sabr/SabrUmp.kt` | 71 | `com.jtech.zemer.playback.sabr` | no | 0 | 27 |  |
| `app/src/main/kotlin/com/jtech/zemer/playback/sabr/SabrVideoResolver.kt` | 390 | `com.jtech.zemer.playback.sabr` | no | 15 | 101 | android.net, android.util, com.zemer, java.io, kotlinx.coroutines, timber.log |
| `app/src/main/kotlin/com/jtech/zemer/playback/sabr/SabrVideoSession.kt` | 290 | `com.jtech.zemer.playback.sabr` | no | 5 | 95 | okhttp3.MediaType, okhttp3.OkHttpClient, okhttp3.Request, okhttp3.RequestBody, timber.log |
| `app/src/main/kotlin/com/jtech/zemer/playback/sabr/SabrVideoStream.kt` | 238 | `com.jtech.zemer.playback.sabr` | no | 9 | 65 | android.net, androidx.media3, java.io, okhttp3.OkHttpClient, timber.log |
| `app/src/main/kotlin/com/jtech/zemer/recognition/AudioResampler.kt` | 118 | `com.jtech.zemer.recognition` | no | 6 | 24 | java.nio, kotlinx.coroutines, timber.log |
| `app/src/main/kotlin/com/jtech/zemer/recognition/RecognitionAudioCapture.kt` | 147 | `com.jtech.zemer.recognition` | no | 15 | 24 | android.Manifest, android.annotation, android.content, android.media, androidx.core, java.io, java.nio, kotlin.coroutines, kotlinx.coroutines, timber.log |
| `app/src/main/kotlin/com/jtech/zemer/recognition/RecognitionHistoryFilter.kt` | 25 | `com.jtech.zemer.recognition` | no | 0 | 4 |  |
| `app/src/main/kotlin/com/jtech/zemer/recognition/RecognitionHistoryPlayback.kt` | 34 | `com.jtech.zemer.recognition` | no | 2 | 4 |  |
| `app/src/main/kotlin/com/jtech/zemer/recognition/RecognitionMatchSelector.kt` | 51 | `com.jtech.zemer.recognition` | no | 1 | 5 |  |
| `app/src/main/kotlin/com/jtech/zemer/recognition/RecognitionMatcher.kt` | 108 | `com.jtech.zemer.recognition` | no | 1 | 31 | java.text |
| `app/src/main/kotlin/com/jtech/zemer/recognition/RecognitionResolver.kt` | 88 | `com.jtech.zemer.recognition` | no | 10 | 15 | kotlinx.coroutines, timber.log |
| `app/src/main/kotlin/com/jtech/zemer/recognition/ShazamSignatureGenerator.kt` | 395 | `com.jtech.zemer.recognition` | no | 10 | 109 | java.io, java.nio, java.util, kotlin.math, timber.log |
| `app/src/main/kotlin/com/jtech/zemer/recognition/VibraSignature.kt` | 22 | `com.jtech.zemer.recognition` | no | 0 | 3 |  |
| `app/src/main/kotlin/com/jtech/zemer/recognition/shazam/Shazam.kt` | 234 | `com.jtech.zemer.recognition.shazam` | no | 22 | 47 | io.ktor, java.util, kotlin.random, kotlinx.coroutines, kotlinx.serialization, timber.log |
| `app/src/main/kotlin/com/jtech/zemer/recognition/shazam/ShazamModels.kt` | 316 | `com.jtech.zemer.recognition.shazam` | no | 2 | 137 | kotlinx.serialization |
| `app/src/main/kotlin/com/jtech/zemer/repositories/CachedSongsRepository.kt` | 98 | `com.jtech.zemer.repositories` | no | 20 | 18 | android.content, androidx.media3, dagger.hilt, java.time, javax.inject, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/search/ResultDedupe.kt` | 78 | `com.jtech.zemer.search` | no | 2 | 9 |  |
| `app/src/main/kotlin/com/jtech/zemer/search/ZemerGenresModels.kt` | 149 | `com.jtech.zemer.search` | no | 1 | 42 | kotlinx.serialization |
| `app/src/main/kotlin/com/jtech/zemer/search/ZemerPodcastGenresModels.kt` | 81 | `com.jtech.zemer.search` | no | 1 | 26 | kotlinx.serialization |
| `app/src/main/kotlin/com/jtech/zemer/search/ZemerResultMapper.kt` | 701 | `com.jtech.zemer.search` | no | 20 | 106 |  |
| `app/src/main/kotlin/com/jtech/zemer/search/ZemerRoutes.kt` | 71 | `com.jtech.zemer.search` | no | 1 | 14 |  |
| `app/src/main/kotlin/com/jtech/zemer/search/ZemerSearchClient.kt` | 636 | `com.jtech.zemer.search` | no | 16 | 64 | io.ktor, java.io, javax.inject, kotlinx.serialization, timber.log |
| `app/src/main/kotlin/com/jtech/zemer/search/ZemerSearchModels.kt` | 422 | `com.jtech.zemer.search` | no | 2 | 160 | kotlinx.serialization |
| `app/src/main/kotlin/com/jtech/zemer/search/ZemerSearchOptions.kt` | 31 | `com.jtech.zemer.search` | no | 2 | 6 | android.content |
| `app/src/main/kotlin/com/jtech/zemer/search/ZemerSearchRepository.kt` | 489 | `com.jtech.zemer.search` | no | 36 | 69 | android.content, dagger.hilt, java.io, java.nio, javax.inject, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/search/ZemerStationsModels.kt` | 135 | `com.jtech.zemer.search` | no | 1 | 39 | kotlinx.serialization |
| `app/src/main/kotlin/com/jtech/zemer/statuses/StatusDownload.kt` | 65 | `com.jtech.zemer.statuses` | no | 2 | 16 | org.json |
| `app/src/main/kotlin/com/jtech/zemer/statuses/StatusDownloadManager.kt` | 118 | `com.jtech.zemer.statuses` | no | 14 | 26 | android.content, android.graphics, android.net, androidx.core, dagger.hilt, java.io, javax.inject, kotlinx.coroutines, okhttp3.OkHttpClient, okhttp3.Request |
| `app/src/main/kotlin/com/jtech/zemer/statuses/StatusDownloadNaming.kt` | 29 | `com.jtech.zemer.statuses` | no | 4 | 4 | java.time, java.util |
| `app/src/main/kotlin/com/jtech/zemer/statuses/StatusDownloadsStore.kt` | 47 | `com.jtech.zemer.statuses` | no | 9 | 8 | android.content, androidx.datastore, dagger.hilt, javax.inject, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/statuses/StatusDownloadsView.kt` | 30 | `com.jtech.zemer.statuses` | no | 0 | 5 |  |
| `app/src/main/kotlin/com/jtech/zemer/statuses/StatusGallery.kt` | 107 | `com.jtech.zemer.statuses` | no | 13 | 19 | android.content, android.graphics, android.net, android.os, android.provider, java.io, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/statuses/StatusSeenStore.kt` | 31 | `com.jtech.zemer.statuses` | no | 9 | 5 | android.content, androidx.datastore, dagger.hilt, javax.inject, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/statuses/StatusSourcesConfig.kt` | 162 | `com.jtech.zemer.statuses` | no | 2 | 35 | org.json |
| `app/src/main/kotlin/com/jtech/zemer/statuses/StatusText.kt` | 45 | `com.jtech.zemer.statuses` | no | 9 | 9 | android.util, androidx.compose |
| `app/src/main/kotlin/com/jtech/zemer/statuses/StatusTextImage.kt` | 60 | `com.jtech.zemer.statuses` | no | 8 | 12 | android.graphics, android.text, androidx.core |
| `app/src/main/kotlin/com/jtech/zemer/statuses/StatusTimeline.kt` | 76 | `com.jtech.zemer.statuses` | no | 4 | 20 | java.time, java.util |
| `app/src/main/kotlin/com/jtech/zemer/statuses/StatusesApi.kt` | 290 | `com.jtech.zemer.statuses` | no | 9 | 68 | java.net, kotlinx.coroutines, org.json |
| `app/src/main/kotlin/com/jtech/zemer/statuses/StatusesRepository.kt` | 293 | `com.jtech.zemer.statuses` | no | 22 | 51 | android.content, android.os, androidx.datastore, dagger.hilt, java.util, javax.inject, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/statuses/YidStatusApi.kt` | 147 | `com.jtech.zemer.statuses` | no | 8 | 29 | java.io, java.util, okhttp3.MediaType, okhttp3.OkHttpClient, okhttp3.Request, okhttp3.RequestBody, org.json |
| `app/src/main/kotlin/com/jtech/zemer/sync/ContentFilterSyncService.kt` | 336 | `com.jtech.zemer.sync` | no | 18 | 40 | android.util, javax.inject, kotlin.math, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/sync/ContentReportRepository.kt` | 54 | `com.jtech.zemer.sync` | no | 6 | 7 | com.google, javax.inject, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/sync/PodcastSyncLogic.kt` | 105 | `com.jtech.zemer.sync` | no | 0 | 10 |  |
| `app/src/main/kotlin/com/jtech/zemer/sync/UserPreferencesRepository.kt` | 692 | `com.jtech.zemer.sync` | no | 38 | 100 | android.content, android.util, androidx.datastore, com.google, dagger.hilt, java.util, javax.inject, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/sync/models/DevicePreferencesEntity.kt` | 100 | `com.jtech.zemer.sync.models` | no | 3 | 31 | com.google, java.util |
| `app/src/main/kotlin/com/jtech/zemer/tracking/FlushSchedule.kt` | 30 | `com.jtech.zemer.tracking` | no | 0 | 8 |  |
| `app/src/main/kotlin/com/jtech/zemer/tracking/ImpressionReporter.kt` | 99 | `com.jtech.zemer.tracking` | yes | 9 | 5 | androidx.compose, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/tracking/LibraryActionBackfill.kt` | 142 | `com.jtech.zemer.tracking` | no | 16 | 18 | android.content, androidx.datastore, java.time, java.util, kotlinx.coroutines, timber.log |
| `app/src/main/kotlin/com/jtech/zemer/tracking/PlayHistoryBackfill.kt` | 166 | `com.jtech.zemer.tracking` | no | 18 | 27 | android.content, androidx.datastore, java.time, java.util, kotlinx.coroutines, timber.log |
| `app/src/main/kotlin/com/jtech/zemer/tracking/PlaySource.kt` | 80 | `com.jtech.zemer.tracking` | no | 1 | 22 | java.util |
| `app/src/main/kotlin/com/jtech/zemer/tracking/Tracker.kt` | 303 | `com.jtech.zemer.tracking` | no | 18 | 57 | android.content, androidx.datastore, java.io, java.util, kotlinx.coroutines, kotlinx.serialization, timber.log |
| `app/src/main/kotlin/com/jtech/zemer/tracking/TrackingEvents.kt` | 235 | `com.jtech.zemer.tracking` | no | 9 | 39 | kotlinx.serialization |
| `app/src/main/kotlin/com/jtech/zemer/tracking/TrackingLifecycle.kt` | 58 | `com.jtech.zemer.tracking` | no | 4 | 13 | android.app, android.os |
| `app/src/main/kotlin/com/jtech/zemer/tracking/TrackingQueue.kt` | 98 | `com.jtech.zemer.tracking` | no | 1 | 16 | java.io |
| `app/src/main/kotlin/com/jtech/zemer/tracking/TrackingUploader.kt` | 119 | `com.jtech.zemer.tracking` | no | 13 | 23 | io.ktor, kotlinx.serialization, timber.log |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/AccountSettingsDialog.kt` | 69 | `com.jtech.zemer.ui.component` | yes | 20 | 1 | androidx.compose, androidx.navigation |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/AlphabetIndex.kt` | 21 | `com.jtech.zemer.ui.component` | no | 0 | 3 |  |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/AppBarTitle.kt` | 31 | `com.jtech.zemer.ui.component` | yes | 6 | 1 | androidx.compose |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/AppNameTitle.kt` | 35 | `com.jtech.zemer.ui.component` | yes | 9 | 1 | androidx.compose |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/AppStateViews.kt` | 123 | `com.jtech.zemer.ui.component` | yes | 28 | 1 | androidx.annotation, androidx.compose |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/ArtistBrowseComponents.kt` | 294 | `com.jtech.zemer.ui.component` | yes | 44 | 11 | androidx.compose |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/AutoResizeText.kt` | 97 | `com.jtech.zemer.ui.component` | yes | 20 | 9 | androidx.compose |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/AvatarShapeIndex.kt` | 13 | `com.jtech.zemer.ui.component` | no | 0 | 1 |  |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/BackTopAppBar.kt` | 59 | `com.jtech.zemer.ui.component` | yes | 9 | 3 | androidx.compose, androidx.navigation |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/BigSeekBar.kt` | 58 | `com.jtech.zemer.ui.component` | yes | 17 | 2 | androidx.compose |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/BottomSheet.kt` | 348 | `com.jtech.zemer.ui.component` | yes | 47 | 45 | androidx.activity, androidx.compose, kotlin.math, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/BottomSheetMenu.kt` | 87 | `com.jtech.zemer.ui.component` | yes | 23 | 8 | androidx.compose |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/BottomSheetPage.kt` | 166 | `com.jtech.zemer.ui.component` | yes | 47 | 10 | androidx.activity, androidx.compose |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/BrowseScreenScaffold.kt` | 608 | `com.jtech.zemer.ui.component` | yes | 62 | 46 | androidx.compose, androidx.navigation, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/CarouselHeroFrame.kt` | 114 | `com.jtech.zemer.ui.component` | yes | 30 | 5 | androidx.compose, coil3.compose |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/CastVolumeKeyHandler.kt` | 71 | `com.jtech.zemer.ui.component` | yes | 13 | 6 | androidx.compose |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/ChartRankCell.kt` | 231 | `com.jtech.zemer.ui.component` | yes | 29 | 23 | androidx.compose, java.time |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/ChipsRow.kt` | 165 | `com.jtech.zemer.ui.component` | yes | 41 | 10 | androidx.compose, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/CreatePlaylistDialog.kt` | 123 | `com.jtech.zemer.ui.component` | yes | 34 | 8 | androidx.compose, java.time, java.util, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/Dialog.kt` | 394 | `com.jtech.zemer.ui.component` | yes | 49 | 9 | androidx.compose, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/DownloadStatusUi.kt` | 170 | `com.jtech.zemer.ui.component` | yes | 28 | 18 | androidx.compose |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/DraggableLyricsProviderList.kt` | 77 | `com.jtech.zemer.ui.component` | yes | 27 | 8 | androidx.compose, sh.calvin |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/DraggableScrollBarOverlay.kt` | 246 | `com.jtech.zemer.ui.component` | yes | 34 | 52 | androidx.compose, kotlin.math, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/EmptyPlaceholder.kt` | 47 | `com.jtech.zemer.ui.component` | yes | 16 | 1 | androidx.annotation, androidx.compose |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/ErrorRetryState.kt` | 58 | `com.jtech.zemer.ui.component` | yes | 14 | 1 | androidx.compose |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/ExpandableStatusCaption.kt` | 98 | `com.jtech.zemer.ui.component` | yes | 32 | 7 | androidx.compose |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/ExpressiveShapes.kt` | 37 | `com.jtech.zemer.ui.component` | yes | 5 | 3 | androidx.compose |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/FocusBorder.kt` | 117 | `com.jtech.zemer.ui.component` | yes | 27 | 11 | androidx.compose, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/GenreCard.kt` | 166 | `com.jtech.zemer.ui.component` | yes | 39 | 19 | androidx.compose |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/GenreCardGrid.kt` | 84 | `com.jtech.zemer.ui.component` | yes | 15 | 3 | androidx.compose |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/GenreCatalogShimmer.kt` | 51 | `com.jtech.zemer.ui.component` | yes | 14 | 1 | androidx.compose |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/GenreChip.kt` | 115 | `com.jtech.zemer.ui.component` | yes | 33 | 6 | androidx.compose |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/GenreDetailHeader.kt` | 118 | `com.jtech.zemer.ui.component` | yes | 28 | 6 | androidx.annotation, androidx.compose, coil3.compose, coil3.request |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/GenreIcons.kt` | 85 | `com.jtech.zemer.ui.component` | no | 2 | 2 | androidx.annotation |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/HeroTitleOverlay.kt` | 65 | `com.jtech.zemer.ui.component` | yes | 17 | 1 | androidx.compose |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/HideOnScrollFAB.kt` | 117 | `com.jtech.zemer.ui.component` | yes | 21 | 3 | androidx.annotation, androidx.compose |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/IconButton.kt` | 203 | `com.jtech.zemer.ui.component` | yes | 41 | 9 | androidx.annotation, androidx.compose, androidx.navigation |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/IconCategoryCard.kt` | 92 | `com.jtech.zemer.ui.component` | yes | 24 | 1 | androidx.annotation, androidx.compose |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/Items.kt` | 1789 | `com.jtech.zemer.ui.component` | yes | 116 | 103 | android.annotation, androidx.compose, androidx.media3, coil3.compose, coil3.request, kotlin.math, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/LetterFastScrollbar.kt` | 217 | `com.jtech.zemer.ui.component` | yes | 37 | 23 | androidx.compose, kotlin.math |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/Library.kt` | 541 | `com.jtech.zemer.ui.component` | yes | 43 | 13 | android.annotation, androidx.compose, androidx.navigation, coil3.compose, coil3.request, coil3.size, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/LibraryFilterChip.kt` | 43 | `com.jtech.zemer.ui.component` | yes | 13 | 1 | androidx.compose |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/Lyrics.kt` | 986 | `com.jtech.zemer.ui.component` | yes | 119 | 117 | android.annotation, android.content, android.os, androidx.activity, androidx.annotation, androidx.compose, androidx.lifecycle, androidx.palette, coil3.imageLoader, coil3.request, coil3.toBitmap, kotlin.time, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/LyricsImageCard.kt` | 287 | `com.jtech.zemer.ui.component` | yes | 28 | 34 | android.annotation, androidx.compose, coil3.compose, coil3.request |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/Marquee.kt` | 65 | `com.jtech.zemer.ui.component` | yes | 9 | 7 | androidx.compose |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/Material3MenuItem.kt` | 135 | `com.jtech.zemer.ui.component` | yes | 31 | 13 | androidx.compose |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/Material3SettingsGroup.kt` | 206 | `com.jtech.zemer.ui.component` | yes | 36 | 13 | androidx.compose |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/MediaLoadingSpinner.kt` | 94 | `com.jtech.zemer.ui.component` | yes | 20 | 3 | androidx.compose |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/MenuDialogs.kt` | 163 | `com.jtech.zemer.ui.component` | yes | 28 | 7 | androidx.compose, coil3.compose |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/NavigationTitle.kt` | 81 | `com.jtech.zemer.ui.component` | yes | 22 | 1 | androidx.compose |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/NewMenuComponents.kt` | 154 | `com.jtech.zemer.ui.component` | yes | 32 | 14 | androidx.compose |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/OfflineBackupPromo.kt` | 91 | `com.jtech.zemer.ui.component` | yes | 22 | 5 | androidx.compose |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/OnboardingActionButton.kt` | 83 | `com.jtech.zemer.ui.component` | yes | 11 | 3 | androidx.compose |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/OnboardingChoiceCard.kt` | 87 | `com.jtech.zemer.ui.component` | yes | 19 | 2 | androidx.compose |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/OnboardingInfoCard.kt` | 89 | `com.jtech.zemer.ui.component` | yes | 22 | 1 | androidx.annotation, androidx.compose |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/OnboardingStatusPill.kt` | 49 | `com.jtech.zemer.ui.component` | yes | 15 | 2 | androidx.compose |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/OnboardingStepHeader.kt` | 42 | `com.jtech.zemer.ui.component` | yes | 12 | 1 | androidx.compose |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/OnboardingStepTitle.kt` | 32 | `com.jtech.zemer.ui.component` | yes | 7 | 1 | androidx.compose |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/PlayerSlider.kt` | 112 | `com.jtech.zemer.ui.component` | yes | 17 | 19 | androidx.compose |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/PlayingIndicator.kt` | 113 | `com.jtech.zemer.ui.component` | yes | 29 | 3 | androidx.compose, kotlin.random, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/PopScale.kt` | 71 | `com.jtech.zemer.ui.component` | yes | 9 | 8 | androidx.compose |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/Preference.kt` | 383 | `com.jtech.zemer.ui.component` | yes | 48 | 12 | androidx.compose, kotlin.math |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/RecognizeMusicFab.kt` | 29 | `com.jtech.zemer.ui.component` | yes | 6 | 1 | androidx.compose |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/SearchBar.kt` | 369 | `com.jtech.zemer.ui.component` | yes | 79 | 32 | androidx.activity, androidx.compose, kotlin.math |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/SearchableSelectableTopAppBar.kt` | 166 | `com.jtech.zemer.ui.component` | yes | 26 | 4 | androidx.annotation, androidx.compose, androidx.navigation |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/SelectionTopActions.kt` | 73 | `com.jtech.zemer.ui.component` | yes | 9 | 4 | androidx.compose |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/SettingsCardGroup.kt` | 98 | `com.jtech.zemer.ui.component` | yes | 13 | 12 | androidx.compose |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/SortHeader.kt` | 124 | `com.jtech.zemer.ui.component` | yes | 27 | 2 | androidx.annotation, androidx.compose |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/StatusCopyButton.kt` | 48 | `com.jtech.zemer.ui.component` | yes | 16 | 1 | androidx.compose |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/StatusCreatorCircle.kt` | 139 | `com.jtech.zemer.ui.component` | yes | 36 | 15 | androidx.compose, coil3.compose, coil3.request |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/StatusLoadingIndicator.kt` | 61 | `com.jtech.zemer.ui.component` | yes | 18 | 1 | androidx.compose, coil3.compose, coil3.request |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/StatusStoryTopOverlay.kt` | 159 | `com.jtech.zemer.ui.component` | yes | 37 | 6 | androidx.compose, androidx.navigation, coil3.compose, coil3.request |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/StatusVideoSurface.kt` | 46 | `com.jtech.zemer.ui.component` | yes | 7 | 1 | android.view, androidx.compose, androidx.media3 |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/SyncAccountWarning.kt` | 58 | `com.jtech.zemer.ui.component` | yes | 15 | 1 | androidx.compose |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/UpdateDownloadDialog.kt` | 131 | `com.jtech.zemer.ui.component` | yes | 21 | 7 | androidx.compose, java.io |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/ZemerCuratedPlaylistCard.kt` | 82 | `com.jtech.zemer.ui.component` | yes | 8 | 7 | androidx.compose, kotlin.math |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/ZemerFab.kt` | 38 | `com.jtech.zemer.ui.component` | yes | 8 | 1 | androidx.annotation, androidx.compose |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/ZemerLoadingIndicator.kt` | 60 | `com.jtech.zemer.ui.component` | yes | 10 | 2 | androidx.compose |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/ZemerStationCard.kt` | 70 | `com.jtech.zemer.ui.component` | yes | 15 | 1 | androidx.compose, coil3.compose |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/lyrics/LyricsComponents.kt` | 68 | `com.jtech.zemer.ui.component.lyrics` | yes | 29 | 4 | androidx.compose, coil3.compose |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/shimmer/BoxPlaceholder.kt` | 30 | `com.jtech.zemer.ui.component.shimmer` | yes | 9 | 1 | androidx.compose |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/shimmer/ButtonPlaceholder.kt` | 15 | `com.jtech.zemer.ui.component.shimmer` | yes | 5 | 1 | androidx.compose |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/shimmer/GridItemPlaceholder.kt` | 51 | `com.jtech.zemer.ui.component.shimmer` | yes | 14 | 1 | androidx.compose |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/shimmer/ListItemPlaceholder.kt` | 53 | `com.jtech.zemer.ui.component.shimmer` | yes | 18 | 1 | androidx.compose |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/shimmer/ShimmerHost.kt` | 80 | `com.jtech.zemer.ui.component.shimmer` | yes | 20 | 3 | androidx.compose, com.valentinilk |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/shimmer/TextPlaceholder.kt` | 33 | `com.jtech.zemer.ui.component.shimmer` | yes | 15 | 1 | androidx.compose, kotlin.random |
| `app/src/main/kotlin/com/jtech/zemer/ui/menu/AddToPlaylistDialog.kt` | 198 | `com.jtech.zemer.ui.menu` | yes | 36 | 11 | androidx.compose, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/ui/menu/AddToPlaylistDialogOnline.kt` | 207 | `com.jtech.zemer.ui.menu` | yes | 42 | 15 | androidx.compose, java.net, java.nio, kotlinx.coroutines, timber.log |
| `app/src/main/kotlin/com/jtech/zemer/ui/menu/AlbumMenu.kt` | 370 | `com.jtech.zemer.ui.menu` | yes | 63 | 21 | android.annotation, androidx.compose, androidx.navigation, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/ui/menu/ArtistMenu.kt` | 262 | `com.jtech.zemer.ui.menu` | yes | 55 | 9 | androidx.compose, coil3.compose, coil3.request, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/ui/menu/CustomThumbnailMenu.kt` | 63 | `com.jtech.zemer.ui.menu` | yes | 17 | 1 | androidx.compose |
| `app/src/main/kotlin/com/jtech/zemer/ui/menu/DownloadMenuItems.kt` | 71 | `com.jtech.zemer.ui.menu` | no | 12 | 2 | androidx.compose |
| `app/src/main/kotlin/com/jtech/zemer/ui/menu/LibraryMenuItems.kt` | 34 | `com.jtech.zemer.ui.menu` | no | 9 | 1 | androidx.compose |
| `app/src/main/kotlin/com/jtech/zemer/ui/menu/LoadingScreen.kt` | 23 | `com.jtech.zemer.ui.menu` | yes | 6 | 1 | androidx.compose |
| `app/src/main/kotlin/com/jtech/zemer/ui/menu/LyricsMenu.kt` | 417 | `com.jtech.zemer.ui.menu` | yes | 64 | 22 | android.app, android.content, androidx.compose, androidx.hilt, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/ui/menu/PlayerMenu.kt` | 524 | `com.jtech.zemer.ui.menu` | yes | 76 | 34 | android.content, android.media, androidx.activity, androidx.annotation, androidx.compose, androidx.media3, androidx.navigation, kotlin.math, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/ui/menu/PlaylistMenu.kt` | 232 | `com.jtech.zemer.ui.menu` | yes | 50 | 12 | androidx.compose, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/ui/menu/PodcastChannelMenu.kt` | 184 | `com.jtech.zemer.ui.menu` | yes | 42 | 7 | androidx.compose, androidx.navigation, coil3.compose, coil3.request |
| `app/src/main/kotlin/com/jtech/zemer/ui/menu/ReportContentDialog.kt` | 130 | `com.jtech.zemer.ui.menu` | yes | 31 | 8 | androidx.compose, androidx.hilt, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/ui/menu/SavedStatusMenu.kt` | 113 | `com.jtech.zemer.ui.menu` | yes | 32 | 1 | androidx.compose, coil3.compose, coil3.request |
| `app/src/main/kotlin/com/jtech/zemer/ui/menu/SelectionSongsMenu.kt` | 595 | `com.jtech.zemer.ui.menu` | yes | 52 | 43 | android.annotation, androidx.compose, androidx.media3, java.time, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/ui/menu/SongMenu.kt` | 556 | `com.jtech.zemer.ui.menu` | yes | 80 | 38 | androidx.activity, androidx.compose, androidx.hilt, androidx.navigation, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/ui/menu/VideoQualityMenu.kt` | 72 | `com.jtech.zemer.ui.menu` | yes | 15 | 1 | androidx.compose |
| `app/src/main/kotlin/com/jtech/zemer/ui/menu/ViewCollectionMenuItem.kt` | 56 | `com.jtech.zemer.ui.menu` | no | 12 | 2 | androidx.compose, androidx.navigation |
| `app/src/main/kotlin/com/jtech/zemer/ui/menu/YouTubeAlbumMenu.kt` | 358 | `com.jtech.zemer.ui.menu` | yes | 64 | 20 | android.annotation, androidx.compose, androidx.navigation, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/ui/menu/YouTubeArtistMenu.kt` | 204 | `com.jtech.zemer.ui.menu` | yes | 41 | 7 | androidx.compose |
| `app/src/main/kotlin/com/jtech/zemer/ui/menu/YouTubeItemMenu.kt` | 68 | `com.jtech.zemer.ui.menu` | yes | 9 | 1 | androidx.compose, androidx.navigation, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/ui/menu/YouTubePlaylistMenu.kt` | 472 | `com.jtech.zemer.ui.menu` | yes | 78 | 21 | android.annotation, androidx.compose, coil3.compose, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/ui/menu/YouTubeSongMenu.kt` | 504 | `com.jtech.zemer.ui.menu` | yes | 83 | 32 | android.annotation, androidx.compose, androidx.navigation, coil3.compose, java.time, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/ui/player/CastBottomSheet.kt` | 468 | `com.jtech.zemer.ui.player` | yes | 53 | 35 | android.content, androidx.compose, kotlinx.coroutines, org.fcast |
| `app/src/main/kotlin/com/jtech/zemer/ui/player/CastButton.kt` | 93 | `com.jtech.zemer.ui.player` | yes | 26 | 12 | androidx.compose |
| `app/src/main/kotlin/com/jtech/zemer/ui/player/EpisodeSpeed.kt` | 35 | `com.jtech.zemer.ui.player` | no | 1 | 7 | kotlin.math |
| `app/src/main/kotlin/com/jtech/zemer/ui/player/LyricsScreen.kt` | 251 | `com.jtech.zemer.ui.player` | yes | 79 | 35 | android.app, android.content, android.view, androidx.activity, androidx.compose, androidx.media3, coil3.compose, dagger.hilt, kotlinx.coroutines, timber.log |
| `app/src/main/kotlin/com/jtech/zemer/ui/player/MiniPlayer.kt` | 735 | `com.jtech.zemer.ui.player` | yes | 97 | 71 | android.annotation, android.content, androidx.compose, androidx.media3, coil3.compose, kotlin.math, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/ui/player/OverMediaChrome.kt` | 36 | `com.jtech.zemer.ui.player` | yes | 9 | 5 | androidx.compose |
| `app/src/main/kotlin/com/jtech/zemer/ui/player/PlaybackError.kt` | 45 | `com.jtech.zemer.ui.player` | yes | 15 | 1 | androidx.compose, androidx.media3 |
| `app/src/main/kotlin/com/jtech/zemer/ui/player/Player.kt` | 970 | `com.jtech.zemer.ui.player` | yes | 153 | 84 | android.annotation, android.app, android.content, androidx.activity, androidx.compose, androidx.core, androidx.media3, androidx.navigation, coil3.compose, coil3.request, kotlinx.coroutines, me.saket |
| `app/src/main/kotlin/com/jtech/zemer/ui/player/PlayerBackground.kt` | 116 | `com.jtech.zemer.ui.player` | yes | 19 | 11 | android.os, android.util, androidx.compose, androidx.palette, coil3.imageLoader, coil3.request, coil3.toBitmap, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/ui/player/PlayerTransport.kt` | 267 | `com.jtech.zemer.ui.player` | yes | 58 | 22 | androidx.compose, androidx.media3, kotlinx.coroutines, me.saket |
| `app/src/main/kotlin/com/jtech/zemer/ui/player/PlayerVideoFullscreen.kt` | 317 | `com.jtech.zemer.ui.player` | yes | 58 | 18 | android.app, android.content, androidx.activity, androidx.compose, androidx.core, androidx.media3, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/ui/player/PlayerVideoSurface.kt` | 111 | `com.jtech.zemer.ui.player` | yes | 27 | 12 | android.view, androidx.compose, androidx.media3 |
| `app/src/main/kotlin/com/jtech/zemer/ui/player/Queue.kt` | 944 | `com.jtech.zemer.ui.player` | yes | 122 | 51 | android.annotation, androidx.activity, androidx.compose, androidx.media3, androidx.navigation, kotlin.math, kotlinx.coroutines, sh.calvin |
| `app/src/main/kotlin/com/jtech/zemer/ui/player/StationLiveBar.kt` | 70 | `com.jtech.zemer.ui.player` | yes | 17 | 2 | androidx.compose, androidx.media3 |
| `app/src/main/kotlin/com/jtech/zemer/ui/player/Thumbnail.kt` | 577 | `com.jtech.zemer.ui.player` | yes | 80 | 61 | androidx.compose, androidx.media3, coil3.compose, kotlin.math, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/ui/player/VideoModePill.kt` | 139 | `com.jtech.zemer.ui.player` | yes | 30 | 7 | androidx.annotation, androidx.compose |
| `app/src/main/kotlin/com/jtech/zemer/ui/player/VideoQualitySelector.kt` | 104 | `com.jtech.zemer.ui.player` | yes | 27 | 3 | androidx.compose |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/AlbumScreen.kt` | 531 | `com.jtech.zemer.ui.screens` | yes | 100 | 37 | androidx.activity, androidx.compose, androidx.hilt, androidx.media3, androidx.navigation, coil3.compose, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/GenreScreen.kt` | 486 | `com.jtech.zemer.ui.screens` | yes | 91 | 22 | androidx.compose, androidx.hilt, androidx.navigation, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/GenreSectionScreen.kt` | 76 | `com.jtech.zemer.ui.screens` | yes | 20 | 3 | androidx.compose, androidx.hilt, androidx.navigation |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/GenresScreen.kt` | 102 | `com.jtech.zemer.ui.screens` | yes | 26 | 4 | androidx.compose, androidx.hilt, androidx.navigation |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/HistoryScreen.kt` | 408 | `com.jtech.zemer.ui.screens` | yes | 69 | 25 | androidx.activity, androidx.compose, androidx.hilt, androidx.navigation, java.time |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/HomeContentTab.kt` | 23 | `com.jtech.zemer.ui.screens` | no | 0 | 3 |  |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/HomeContinueListeningRow.kt` | 106 | `com.jtech.zemer.ui.screens` | yes | 33 | 4 | androidx.compose, coil3.compose |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/HomeGenresRow.kt` | 118 | `com.jtech.zemer.ui.screens` | yes | 31 | 8 | androidx.compose, androidx.navigation |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/HomeScreen.kt` | 1525 | `com.jtech.zemer.ui.screens` | yes | 151 | 104 | android.net, androidx.annotation, androidx.compose, androidx.datastore, androidx.hilt, androidx.lifecycle, androidx.navigation, kotlin.math, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/HomeSeeAllScreen.kt` | 369 | `com.jtech.zemer.ui.screens` | yes | 68 | 31 | androidx.compose, androidx.navigation |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/HomeStatusesRow.kt` | 58 | `com.jtech.zemer.ui.screens` | yes | 18 | 2 | androidx.compose |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/KidZoneScreen.kt` | 164 | `com.jtech.zemer.ui.screens` | yes | 29 | 11 | androidx.compose, androidx.hilt, androidx.navigation, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/KidZoneTab.kt` | 22 | `com.jtech.zemer.ui.screens` | no | 0 | 3 |  |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/LatestReleasesScreen.kt` | 104 | `com.jtech.zemer.ui.screens` | yes | 32 | 9 | androidx.compose, androidx.hilt, androidx.navigation |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/LoadingScreen.kt` | 141 | `com.jtech.zemer.ui.screens` | yes | 36 | 9 | android.graphics, androidx.compose, androidx.lifecycle, com.airbnb |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/LoginCapture.kt` | 39 | `com.jtech.zemer.ui.screens` | no | 0 | 7 |  |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/LoginGateScreen.kt` | 276 | `com.jtech.zemer.ui.screens` | yes | 57 | 23 | androidx.compose, androidx.datastore, androidx.navigation, io.ktor, kotlinx.coroutines, kotlinx.serialization |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/LoginScreen.kt` | 244 | `com.jtech.zemer.ui.screens` | yes | 40 | 20 | android.annotation, android.content, android.webkit, androidx.activity, androidx.compose, androidx.navigation, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/NavigationBuilder.kt` | 492 | `com.jtech.zemer.ui.screens` | no | 50 | 8 | androidx.compose, androidx.navigation |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/OnboardingScreen.kt` | 89 | `com.jtech.zemer.ui.screens` | yes | 22 | 8 | android.content, androidx.compose, androidx.hilt |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/PodcastGenreScreen.kt` | 132 | `com.jtech.zemer.ui.screens` | yes | 33 | 6 | androidx.compose, androidx.hilt, androidx.navigation |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/PodcastGenresScreen.kt` | 95 | `com.jtech.zemer.ui.screens` | yes | 26 | 3 | androidx.compose, androidx.hilt, androidx.navigation |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/Screens.kt` | 67 | `com.jtech.zemer.ui.screens` | no | 4 | 13 | androidx.annotation, androidx.compose |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/SplashScreen.kt` | 166 | `com.jtech.zemer.ui.screens` | yes | 42 | 5 | android.graphics, androidx.compose, com.airbnb |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/VideoHeroCarousel.kt` | 224 | `com.jtech.zemer.ui.screens` | yes | 43 | 14 | androidx.compose, androidx.navigation, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/WhitelistedArtistsScreen.kt` | 112 | `com.jtech.zemer.ui.screens` | yes | 21 | 6 | androidx.compose, androidx.hilt, androidx.navigation, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/WhitelistedPodcastsScreen.kt` | 341 | `com.jtech.zemer.ui.screens` | yes | 57 | 16 | androidx.compose, androidx.hilt, androidx.navigation, coil3.compose |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/ZemerPlaylistsScreen.kt` | 72 | `com.jtech.zemer.ui.screens` | yes | 26 | 2 | android.net, androidx.compose, androidx.hilt, androidx.navigation |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/artist/ArtistScreen.kt` | 904 | `com.jtech.zemer.ui.screens.artist` | yes | 128 | 44 | androidx.compose, androidx.hilt, androidx.navigation, coil3.compose, com.valentinilk |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/artist/ArtistSectionScreen.kt` | 365 | `com.jtech.zemer.ui.screens.artist` | yes | 61 | 31 | androidx.compose, androidx.hilt, androidx.navigation, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/library/LibraryAlbumsScreen.kt` | 299 | `com.jtech.zemer.ui.screens.library` | yes | 68 | 19 | androidx.compose, androidx.hilt, androidx.navigation, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/library/LibraryArtistsScreen.kt` | 286 | `com.jtech.zemer.ui.screens.library` | yes | 67 | 16 | androidx.compose, androidx.hilt, androidx.navigation, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/library/LibraryMixScreen.kt` | 774 | `com.jtech.zemer.ui.screens.library` | yes | 100 | 36 | androidx.compose, androidx.hilt, androidx.navigation, java.text, java.time, java.util, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/library/LibraryPlaylistsScreen.kt` | 526 | `com.jtech.zemer.ui.screens.library` | yes | 75 | 28 | androidx.compose, androidx.hilt, androidx.navigation, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/library/LibraryPodcastsScreen.kt` | 672 | `com.jtech.zemer.ui.screens.library` | yes | 90 | 35 | androidx.compose, androidx.hilt, androidx.lifecycle, androidx.navigation, coil3.compose, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/library/LibraryScreen.kt` | 106 | `com.jtech.zemer.ui.screens.library` | yes | 20 | 8 | androidx.compose, androidx.navigation |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/library/LibrarySongsScreen.kt` | 306 | `com.jtech.zemer.ui.screens.library` | yes | 71 | 20 | androidx.activity, androidx.compose, androidx.hilt, androidx.navigation |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/library/LibraryVideosScreen.kt` | 148 | `com.jtech.zemer.ui.screens.library` | yes | 41 | 9 | androidx.compose, androidx.hilt, androidx.navigation |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/onboarding/BottomNavSetupScreen.kt` | 118 | `com.jtech.zemer.ui.screens.onboarding` | yes | 30 | 4 | android.content, androidx.compose, androidx.core, androidx.datastore |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/onboarding/ContentFiltersScreen.kt` | 414 | `com.jtech.zemer.ui.screens.onboarding` | yes | 58 | 27 | androidx.activity, androidx.compose, androidx.datastore, androidx.hilt, com.google, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/onboarding/DensityScreen.kt` | 294 | `com.jtech.zemer.ui.screens.onboarding` | yes | 43 | 18 | android.content, androidx.compose, androidx.core, androidx.datastore |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/onboarding/OnboardingConnectivity.kt` | 44 | `com.jtech.zemer.ui.screens.onboarding` | yes | 11 | 8 | androidx.compose, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/onboarding/OnboardingNavigation.kt` | 39 | `com.jtech.zemer.ui.screens.onboarding` | no | 0 | 6 |  |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/onboarding/OnboardingSearchBackupScreen.kt` | 100 | `com.jtech.zemer.ui.screens.onboarding` | yes | 25 | 2 | androidx.compose, androidx.hilt |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/onboarding/PermissionsScreen.kt` | 350 | `com.jtech.zemer.ui.screens.onboarding` | yes | 46 | 27 | android.Manifest, android.annotation, android.content, android.net, android.os, android.provider, androidx.activity, androidx.compose, androidx.core, androidx.lifecycle, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/onboarding/WelcomeScreen.kt` | 220 | `com.jtech.zemer.ui.screens.onboarding` | yes | 43 | 10 | android.graphics, androidx.compose, com.airbnb |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/playlist/AutoPlaylistScreen.kt` | 391 | `com.jtech.zemer.ui.screens.playlist` | yes | 74 | 31 | androidx.activity, androidx.compose, androidx.hilt, androidx.navigation, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/playlist/CachePlaylistScreen.kt` | 357 | `com.jtech.zemer.ui.screens.playlist` | yes | 75 | 21 | androidx.activity, androidx.compose, androidx.hilt, androidx.navigation, coil3.compose, java.time |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/playlist/DownloadedContentScreen.kt` | 129 | `com.jtech.zemer.ui.screens.playlist` | yes | 31 | 9 | androidx.compose, androidx.hilt, androidx.navigation |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/playlist/DownloadedVideosScreen.kt` | 320 | `com.jtech.zemer.ui.screens.playlist` | yes | 67 | 24 | androidx.activity, androidx.compose, androidx.hilt, androidx.navigation |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/playlist/LocalPlaylistScreen.kt` | 1284 | `com.jtech.zemer.ui.screens.playlist` | yes | 148 | 82 | android.annotation, android.content, android.graphics, android.net, androidx.activity, androidx.compose, androidx.core, androidx.hilt, androidx.lifecycle, androidx.navigation, coil3.compose, coil3.request, com.yalantis, io.ktor, java.time, kotlinx.coroutines, sh.calvin |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/playlist/OnlinePlaylistScreen.kt` | 555 | `com.jtech.zemer.ui.screens.playlist` | yes | 100 | 26 | androidx.activity, androidx.compose, androidx.hilt, androidx.navigation, coil3.compose, coil3.request |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/playlist/PlaylistDetailShared.kt` | 220 | `com.jtech.zemer.ui.screens.playlist` | yes | 41 | 3 | androidx.annotation, androidx.compose, coil3.compose |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/playlist/PlaylistHeaderCover.kt` | 16 | `com.jtech.zemer.ui.screens.playlist` | no | 0 | 0 |  |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/playlist/TopPlaylistScreen.kt` | 355 | `com.jtech.zemer.ui.screens.playlist` | yes | 66 | 25 | androidx.activity, androidx.compose, androidx.hilt, androidx.navigation, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/playlist/ZemerCuratedPlaylistScreen.kt` | 459 | `com.jtech.zemer.ui.screens.playlist` | yes | 85 | 24 | androidx.compose, androidx.hilt, androidx.navigation, coil3.compose, coil3.request |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/podcast/OnlinePodcastScreen.kt` | 482 | `com.jtech.zemer.ui.screens.podcast` | yes | 95 | 18 | androidx.compose, androidx.hilt, androidx.navigation, coil3.compose, coil3.request, coil3.size, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/recognition/RecognitionHistoryScreen.kt` | 186 | `com.jtech.zemer.ui.screens.recognition` | yes | 53 | 5 | androidx.compose, androidx.hilt, androidx.navigation, coil3.compose |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/recognition/RecognizeMusicDialogActivity.kt` | 340 | `com.jtech.zemer.ui.screens.recognition` | yes | 64 | 19 | android.content, android.os, androidx.activity, androidx.compose, androidx.core, androidx.hilt, coil3.compose, dagger.hilt |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/search/OnlineSearchResult.kt` | 522 | `com.jtech.zemer.ui.screens.search` | yes | 90 | 36 | androidx.compose, androidx.hilt, androidx.navigation, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/search/OnlineSearchScreen.kt` | 531 | `com.jtech.zemer.ui.screens.search` | yes | 99 | 28 | androidx.compose, androidx.hilt, androidx.navigation, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/search/SearchFilterPolicy.kt` | 26 | `com.jtech.zemer.ui.screens.search` | no | 6 | 2 |  |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/settings/AboutScreen.kt` | 455 | `com.jtech.zemer.ui.screens.settings` | yes | 64 | 29 | androidx.annotation, androidx.compose, androidx.navigation, coil3.compose |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/settings/AccountSettings.kt` | 525 | `com.jtech.zemer.ui.screens.settings` | yes | 81 | 45 | androidx.compose, androidx.hilt, androidx.navigation, coil3.compose, io.ktor, kotlinx.coroutines, kotlinx.serialization |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/settings/AndroidAutoSettings.kt` | 270 | `com.jtech.zemer.ui.screens.settings` | yes | 51 | 29 | androidx.compose, androidx.lifecycle, androidx.navigation, kotlinx.coroutines, sh.calvin |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/settings/AppearanceSettings.kt` | 1130 | `com.jtech.zemer.ui.screens.settings` | yes | 131 | 103 | android.annotation, android.content, androidx.compose, androidx.core, androidx.navigation, kotlin.math, kotlinx.coroutines, me.saket |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/settings/BackupAndRestore.kt` | 203 | `com.jtech.zemer.ui.screens.settings` | yes | 48 | 16 | androidx.activity, androidx.compose, androidx.hilt, androidx.navigation, java.time, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/settings/ButtonSetupScreen.kt` | 374 | `com.jtech.zemer.ui.screens.settings` | yes | 58 | 17 | android.view, androidx.activity, androidx.compose, androidx.hilt, androidx.navigation |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/settings/ContentSettings.kt` | 678 | `com.jtech.zemer.ui.screens.settings` | yes | 94 | 72 | android.content, android.os, android.provider, androidx.activity, androidx.compose, androidx.core, androidx.hilt, androidx.navigation, com.google, dagger.hilt, java.text, java.util, javax.inject, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/settings/GeneralSettings.kt` | 82 | `com.jtech.zemer.ui.screens.settings` | yes | 29 | 4 | android.content, android.net, android.os, android.provider, androidx.compose, androidx.navigation |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/settings/LogViewerScreen.kt` | 378 | `com.jtech.zemer.ui.screens.settings` | yes | 73 | 30 | androidx.compose, androidx.navigation, java.text, java.time, java.util, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/settings/LyricsProviderDialogs.kt` | 108 | `com.jtech.zemer.ui.screens.settings` | yes | 30 | 17 | androidx.compose |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/settings/OfflineSearchSettings.kt` | 140 | `com.jtech.zemer.ui.screens.settings` | yes | 41 | 9 | android.text, androidx.compose, androidx.hilt, androidx.lifecycle, androidx.navigation |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/settings/PlayerSettings.kt` | 368 | `com.jtech.zemer.ui.screens.settings` | yes | 60 | 37 | androidx.compose, androidx.navigation |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/settings/PrivacySettings.kt` | 200 | `com.jtech.zemer.ui.screens.settings` | yes | 44 | 10 | androidx.compose, androidx.navigation |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/settings/SettingsScreen.kt` | 280 | `com.jtech.zemer.ui.screens.settings` | yes | 38 | 23 | android.os, androidx.compose, androidx.navigation, com.google |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/settings/StorageSettings.kt` | 468 | `com.jtech.zemer.ui.screens.settings` | yes | 71 | 36 | android.annotation, android.content, android.net, android.provider, androidx.activity, androidx.compose, androidx.navigation, coil3.annotation, coil3.imageLoader, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/settings/StreamSourceSettings.kt` | 297 | `com.jtech.zemer.ui.screens.settings` | yes | 57 | 24 | androidx.compose, androidx.navigation |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/settings/ThemeScreen.kt` | 590 | `com.jtech.zemer.ui.screens.settings` | yes | 88 | 42 | android.os, androidx.compose, androidx.navigation, com.materialkolor |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/settings/UpdaterSettings.kt` | 408 | `com.jtech.zemer.ui.screens.settings` | yes | 66 | 27 | android.content, androidx.compose, androidx.navigation, java.io, kotlinx.coroutines, rikka.shizuku, timber.log |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/statuses/SavedStatusScreen.kt` | 353 | `com.jtech.zemer.ui.screens.statuses` | yes | 62 | 38 | androidx.activity, androidx.compose, androidx.core, androidx.hilt, androidx.lifecycle, androidx.media3, androidx.navigation, coil3.compose, coil3.request, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/statuses/StatusDownloadsScreen.kt` | 439 | `com.jtech.zemer.ui.screens.statuses` | yes | 85 | 25 | androidx.annotation, androidx.compose, androidx.hilt, androidx.navigation, coil3.compose, coil3.request |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/statuses/StatusesScreen.kt` | 140 | `com.jtech.zemer.ui.screens.statuses` | yes | 43 | 11 | androidx.compose, androidx.hilt, androidx.navigation |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/statuses/StoryScreen.kt` | 925 | `com.jtech.zemer.ui.screens.statuses` | yes | 120 | 119 | androidx.activity, androidx.compose, androidx.hilt, androidx.lifecycle, androidx.media3, androidx.navigation, coil3.compose, coil3.imageLoader, coil3.request, java.time, java.util, kotlin.math, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/ui/theme/ChartColors.kt` | 30 | `com.jtech.zemer.ui.theme` | yes | 4 | 3 | androidx.compose |
| `app/src/main/kotlin/com/jtech/zemer/ui/theme/LogColors.kt` | 26 | `com.jtech.zemer.ui.theme` | yes | 5 | 2 | android.util, androidx.compose |
| `app/src/main/kotlin/com/jtech/zemer/ui/theme/PlayerColorExtractor.kt` | 136 | `com.jtech.zemer.ui.theme` | no | 5 | 25 | androidx.compose, androidx.palette, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/ui/theme/PlayerSliderColors.kt` | 129 | `com.jtech.zemer.ui.theme` | yes | 6 | 7 | androidx.compose |
| `app/src/main/kotlin/com/jtech/zemer/ui/theme/Theme.kt` | 177 | `com.jtech.zemer.ui.theme` | yes | 27 | 27 | android.graphics, android.os, androidx.compose, androidx.palette, com.materialkolor |
| `app/src/main/kotlin/com/jtech/zemer/ui/theme/ThemePalettes.kt` | 109 | `com.jtech.zemer.ui.theme` | no | 2 | 19 | androidx.compose |
| `app/src/main/kotlin/com/jtech/zemer/ui/theme/Type.kt` | 131 | `com.jtech.zemer.ui.theme` | no | 7 | 2 | androidx.compose |
| `app/src/main/kotlin/com/jtech/zemer/ui/utils/ActiveRowTap.kt` | 21 | `com.jtech.zemer.ui.utils` | no | 0 | 1 |  |
| `app/src/main/kotlin/com/jtech/zemer/ui/utils/AppBar.kt` | 75 | `com.jtech.zemer.ui.utils` | yes | 14 | 10 | androidx.compose |
| `app/src/main/kotlin/com/jtech/zemer/ui/utils/AppNavigation.kt` | 75 | `com.jtech.zemer.ui.utils` | no | 2 | 9 | androidx.navigation |
| `app/src/main/kotlin/com/jtech/zemer/ui/utils/CubeFace.kt` | 19 | `com.jtech.zemer.ui.utils` | no | 4 | 2 | androidx.compose, kotlin.math |
| `app/src/main/kotlin/com/jtech/zemer/ui/utils/FadingEdge.kt` | 89 | `com.jtech.zemer.ui.utils` | no | 7 | 2 | androidx.compose |
| `app/src/main/kotlin/com/jtech/zemer/ui/utils/ForceLightStatusBarIcons.kt` | 42 | `com.jtech.zemer.ui.utils` | yes | 11 | 6 | android.app, androidx.compose, androidx.core |
| `app/src/main/kotlin/com/jtech/zemer/ui/utils/HomeTitleEasterEgg.kt` | 51 | `com.jtech.zemer.ui.utils` | no | 11 | 7 | kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/ui/utils/ItemWrapper.kt` | 15 | `com.jtech.zemer.ui.utils` | no | 1 | 4 | androidx.compose |
| `app/src/main/kotlin/com/jtech/zemer/ui/utils/LazyGridSnapLayoutInfoProvider.kt` | 66 | `com.jtech.zemer.ui.utils` | no | 6 | 14 | androidx.compose |
| `app/src/main/kotlin/com/jtech/zemer/ui/utils/MediaViewerEffects.kt` | 27 | `com.jtech.zemer.ui.utils` | yes | 3 | 4 | androidx.compose |
| `app/src/main/kotlin/com/jtech/zemer/ui/utils/NavControllerUtils.kt` | 26 | `com.jtech.zemer.ui.utils` | no | 2 | 4 | androidx.navigation |
| `app/src/main/kotlin/com/jtech/zemer/ui/utils/ScrollUtils.kt` | 91 | `com.jtech.zemer.ui.utils` | yes | 14 | 11 | androidx.compose, androidx.navigation |
| `app/src/main/kotlin/com/jtech/zemer/ui/utils/SeeAll.kt` | 22 | `com.jtech.zemer.ui.utils` | no | 0 | 3 |  |
| `app/src/main/kotlin/com/jtech/zemer/ui/utils/ShapeUtils.kt` | 8 | `com.jtech.zemer.ui.utils` | no | 3 | 1 | androidx.compose |
| `app/src/main/kotlin/com/jtech/zemer/ui/utils/ShowMediaInfo.kt` | 218 | `com.jtech.zemer.ui.utils` | yes | 48 | 21 | android.text, androidx.annotation, androidx.compose, com.zemer |
| `app/src/main/kotlin/com/jtech/zemer/ui/utils/StatusNavigation.kt` | 17 | `com.jtech.zemer.ui.utils` | no | 0 | 2 |  |
| `app/src/main/kotlin/com/jtech/zemer/ui/utils/StringUtils.kt` | 34 | `com.jtech.zemer.ui.utils` | no | 2 | 5 | java.text, kotlin.math |
| `app/src/main/kotlin/com/jtech/zemer/ui/utils/VideoThumbnail.kt` | 45 | `com.jtech.zemer.ui.utils` | yes | 9 | 6 | android.graphics, android.media, android.util, androidx.compose, androidx.core, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/ui/utils/YouTubeUtils.kt` | 40 | `com.jtech.zemer.ui.utils` | no | 0 | 7 |  |
| `app/src/main/kotlin/com/jtech/zemer/utils/AccessibilityUtils.kt` | 91 | `com.jtech.zemer.utils` | yes | 19 | 18 | android.content, android.database, android.net, android.os, android.provider, android.text, androidx.compose, androidx.lifecycle |
| `app/src/main/kotlin/com/jtech/zemer/utils/ArtistThumbResolver.kt` | 88 | `com.jtech.zemer.utils` | no | 11 | 12 | javax.inject, kotlinx.coroutines, timber.log |
| `app/src/main/kotlin/com/jtech/zemer/utils/BlockedIdsCache.kt` | 70 | `com.jtech.zemer.utils` | no | 0 | 10 |  |
| `app/src/main/kotlin/com/jtech/zemer/utils/BottomNavItems.kt` | 19 | `com.jtech.zemer.utils` | no | 0 | 2 |  |
| `app/src/main/kotlin/com/jtech/zemer/utils/ButtonInputCapture.kt` | 40 | `com.jtech.zemer.utils` | no | 5 | 10 | android.view, java.util, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/utils/ButtonMapperBridge.kt` | 35 | `com.jtech.zemer.utils` | no | 5 | 8 | android.view, java.util |
| `app/src/main/kotlin/com/jtech/zemer/utils/CoilBitmapLoader.kt` | 54 | `com.jtech.zemer.utils` | no | 15 | 8 | android.content, android.graphics, android.net, androidx.core, androidx.media3, coil3.imageLoader, coil3.request, coil3.toBitmap, com.google, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/utils/ComposeToImage.kt` | 263 | `com.jtech.zemer.utils` | no | 32 | 63 | android.annotation, android.content, android.graphics, android.net, android.os, android.provider, android.text, androidx.core, coil3.imageLoader, coil3.request, coil3.toBitmap, java.io, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/utils/ContentFilterConfig.kt` | 87 | `com.jtech.zemer.utils` | no | 0 | 17 |  |
| `app/src/main/kotlin/com/jtech/zemer/utils/CoverArtEmbedder.kt` | 184 | `com.jtech.zemer.utils` | no | 12 | 29 | android.content, android.util, java.io, kotlinx.coroutines, okhttp3.OkHttpClient, okhttp3.Request |
| `app/src/main/kotlin/com/jtech/zemer/utils/CrashReportingTree.kt` | 35 | `com.jtech.zemer.utils` | no | 2 | 6 | android.util, timber.log |
| `app/src/main/kotlin/com/jtech/zemer/utils/DataStore.kt` | 180 | `com.jtech.zemer.utils` | yes | 21 | 13 | android.content, androidx.compose, androidx.datastore, kotlin.properties, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/utils/DeviceIdGenerator.kt` | 188 | `com.jtech.zemer.utils` | no | 15 | 29 | android.content, android.os, android.provider, android.util, androidx.datastore, dagger.hilt, java.util, javax.inject, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/utils/EnvironmentPaths.kt` | 25 | `com.jtech.zemer.utils` | no | 4 | 6 | android.net, android.os, android.provider, java.io |
| `app/src/main/kotlin/com/jtech/zemer/utils/FutureUtils.kt` | 43 | `com.jtech.zemer.utils` | no | 8 | 3 | androidx.concurrent, com.google, java.util, kotlin.coroutines, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/utils/IsraeliArtistRegistry.kt` | 56 | `com.jtech.zemer.utils` | no | 5 | 7 | com.google, kotlinx.coroutines, timber.log |
| `app/src/main/kotlin/com/jtech/zemer/utils/LogBufferTree.kt` | 71 | `com.jtech.zemer.utils` | no | 5 | 15 | android.util, java.util, kotlinx.coroutines, timber.log |
| `app/src/main/kotlin/com/jtech/zemer/utils/LogExport.kt` | 113 | `com.jtech.zemer.utils` | no | 11 | 19 | android.content, androidx.core, java.io, java.text, java.time, java.util |
| `app/src/main/kotlin/com/jtech/zemer/utils/MediaStoreHelper.kt` | 679 | `com.jtech.zemer.utils` | no | 17 | 82 | android.content, android.net, android.os, android.provider, androidx.documentfile, java.io, kotlinx.coroutines, timber.log |
| `app/src/main/kotlin/com/jtech/zemer/utils/NetworkConnectivityObserver.kt` | 88 | `com.jtech.zemer.utils` | no | 7 | 16 | android.content, android.net, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/utils/NewEpisodesFeed.kt` | 47 | `com.jtech.zemer.utils` | no | 11 | 9 | android.content, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/utils/NotificationUtils.kt` | 35 | `com.jtech.zemer.utils` | no | 6 | 2 | android.Manifest, android.content, android.os, androidx.core |
| `app/src/main/kotlin/com/jtech/zemer/utils/PermissionHelper.kt` | 204 | `com.jtech.zemer.utils` | no | 9 | 14 | android.Manifest, android.app, android.content, android.os, androidx.activity, androidx.core, timber.log |
| `app/src/main/kotlin/com/jtech/zemer/utils/PlaylistRemoteEdits.kt` | 49 | `com.jtech.zemer.utils` | no | 2 | 6 |  |
| `app/src/main/kotlin/com/jtech/zemer/utils/PodcastLibrarySources.kt` | 86 | `com.jtech.zemer.utils` | no | 8 | 10 | kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/utils/PodcastWhitelistCache.kt` | 42 | `com.jtech.zemer.utils` | no | 1 | 8 |  |
| `app/src/main/kotlin/com/jtech/zemer/utils/RankedContentGate.kt` | 32 | `com.jtech.zemer.utils` | no | 0 | 6 |  |
| `app/src/main/kotlin/com/jtech/zemer/utils/RefreshRateSelection.kt` | 72 | `com.jtech.zemer.utils` | no | 2 | 10 | kotlin.math |
| `app/src/main/kotlin/com/jtech/zemer/utils/StringUtils.kt` | 23 | `com.jtech.zemer.utils` | no | 0 | 6 |  |
| `app/src/main/kotlin/com/jtech/zemer/utils/SyncUtils.kt` | 1162 | `com.jtech.zemer.utils` | no | 49 | 131 | android.content, android.util, androidx.datastore, dagger.hilt, java.time, javax.inject, kotlinx.coroutines, timber.log |
| `app/src/main/kotlin/com/jtech/zemer/utils/UpdateChecker.kt` | 243 | `com.jtech.zemer.utils` | no | 17 | 65 | android.content, io.ktor, java.io, kotlinx.coroutines, kotlinx.serialization |
| `app/src/main/kotlin/com/jtech/zemer/utils/Updater.kt` | 55 | `com.jtech.zemer.utils` | no | 5 | 21 | io.ktor, org.json |
| `app/src/main/kotlin/com/jtech/zemer/utils/UrlValidator.kt` | 82 | `com.jtech.zemer.utils` | no | 2 | 8 | okhttp3.HttpUrl |
| `app/src/main/kotlin/com/jtech/zemer/utils/Utils.kt` | 30 | `com.jtech.zemer.utils` | no | 4 | 3 | android.content, java.util, timber.log |
| `app/src/main/kotlin/com/jtech/zemer/utils/VideoLinkBuilder.kt` | 36 | `com.jtech.zemer.utils` | no | 0 | 8 |  |
| `app/src/main/kotlin/com/jtech/zemer/utils/VideoMuxer.kt` | 138 | `com.jtech.zemer.utils` | no | 7 | 22 | android.media, java.io, java.nio, timber.log |
| `app/src/main/kotlin/com/jtech/zemer/utils/WhitelistCache.kt` | 40 | `com.jtech.zemer.utils` | no | 1 | 9 |  |
| `app/src/main/kotlin/com/jtech/zemer/utils/WhitelistFetcher.kt` | 230 | `com.jtech.zemer.utils` | no | 7 | 50 | com.google, java.time, kotlinx.coroutines, timber.log |
| `app/src/main/kotlin/com/jtech/zemer/utils/WhitelistFilter.kt` | 331 | `com.jtech.zemer.utils` | no | 10 | 32 | java.util, kotlinx.coroutines, timber.log |
| `app/src/main/kotlin/com/jtech/zemer/utils/YTPlayerUtils.kt` | 795 | `com.jtech.zemer.utils` | no | 31 | 102 | android.net, androidx.core, androidx.media3, com.zemer, kotlinx.coroutines, okhttp3.OkHttpClient, timber.log |
| `app/src/main/kotlin/com/jtech/zemer/utils/ZemerContentClient.kt` | 257 | `com.jtech.zemer.utils` | no | 21 | 65 | io.ktor, java.io, kotlinx.serialization, timber.log |
| `app/src/main/kotlin/com/jtech/zemer/utils/mp4/AudioRemux.kt` | 94 | `com.jtech.zemer.utils.mp4` | no | 8 | 16 | android.media, android.os, java.io, java.nio, timber.log |
| `app/src/main/kotlin/com/jtech/zemer/utils/mp4/Mp4MetadataWriter.kt` | 303 | `com.jtech.zemer.utils.mp4` | no | 3 | 76 | java.io, java.nio |
| `app/src/main/kotlin/com/jtech/zemer/utils/ogg/OggOpusTagger.kt` | 299 | `com.jtech.zemer.utils.ogg` | no | 3 | 100 | java.io, java.util |
| `app/src/main/kotlin/com/jtech/zemer/utils/updater/ApkInstallController.kt` | 102 | `com.jtech.zemer.utils.updater` | yes | 15 | 15 | androidx.activity, androidx.compose, java.io, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/utils/updater/AppInstaller.kt` | 267 | `com.jtech.zemer.utils.updater` | no | 28 | 41 | android.app, android.content, android.net, android.os, android.provider, androidx.core, com.topjohnwu, dev.rikka, java.io, kotlinx.coroutines, org.lsposed, rikka.shizuku, timber.log |
| `app/src/main/kotlin/com/jtech/zemer/utils/updater/AppRestarter.kt` | 30 | `com.jtech.zemer.utils.updater` | no | 1 | 4 | android.content |
| `app/src/main/kotlin/com/jtech/zemer/utils/updater/InstallReceiver.kt` | 66 | `com.jtech.zemer.utils.updater` | no | 10 | 7 | android.content, android.os, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/utils/updater/Installer.kt` | 24 | `com.jtech.zemer.utils.updater` | no | 2 | 4 | androidx.annotation |
| `app/src/main/kotlin/com/jtech/zemer/utils/updater/NightlyUpdates.kt` | 107 | `com.jtech.zemer.utils.updater` | no | 7 | 29 | java.io, java.util, kotlinx.serialization |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/AccountSettingsViewModel.kt` | 25 | `com.jtech.zemer.viewmodels` | no | 6 | 3 | androidx.lifecycle, dagger.hilt, javax.inject, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/AlbumViewModel.kt` | 147 | `com.jtech.zemer.viewmodels` | no | 18 | 14 | android.content, androidx.lifecycle, dagger.hilt, javax.inject, kotlinx.coroutines, timber.log |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/ArtistViewModel.kt` | 216 | `com.jtech.zemer.viewmodels` | no | 29 | 31 | android.content, androidx.compose, androidx.lifecycle, dagger.hilt, javax.inject, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/AutoPlaylistViewModel.kt` | 72 | `com.jtech.zemer.viewmodels` | no | 22 | 8 | android.content, androidx.lifecycle, dagger.hilt, javax.inject, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/BackupRestoreViewModel.kt` | 189 | `com.jtech.zemer.viewmodels` | no | 29 | 22 | android.content, android.net, androidx.lifecycle, dagger.hilt, java.io, java.util, javax.inject, kotlin.system, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/ButtonSetupViewModel.kt` | 136 | `com.jtech.zemer.viewmodels` | no | 22 | 26 | android.content, android.view, androidx.datastore, androidx.lifecycle, dagger.hilt, javax.inject, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/CachePlaylistViewModel.kt` | 26 | `com.jtech.zemer.viewmodels` | no | 7 | 4 | androidx.lifecycle, dagger.hilt, javax.inject, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/ContinueListeningViewModel.kt` | 38 | `com.jtech.zemer.viewmodels` | no | 12 | 2 | androidx.lifecycle, dagger.hilt, javax.inject, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/DownloadedContentViewModel.kt` | 51 | `com.jtech.zemer.viewmodels` | no | 17 | 5 | android.content, androidx.lifecycle, dagger.hilt, javax.inject, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/DownloadedVideosViewModel.kt` | 39 | `com.jtech.zemer.viewmodels` | no | 18 | 3 | android.content, androidx.lifecycle, dagger.hilt, javax.inject, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/HistoryViewModel.kt` | 108 | `com.jtech.zemer.viewmodels` | no | 20 | 22 | androidx.lifecycle, dagger.hilt, java.time, javax.inject, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/HomeSeeAllStore.kt` | 131 | `com.jtech.zemer.viewmodels` | no | 14 | 38 | androidx.annotation, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/HomeViewModel.kt` | 991 | `com.jtech.zemer.viewmodels` | no | 57 | 199 | android.content, androidx.datastore, androidx.lifecycle, com.google, dagger.hilt, javax.inject, kotlin.random, kotlinx.coroutines, timber.log |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/KidZoneViewModel.kt` | 99 | `com.jtech.zemer.viewmodels` | no | 21 | 17 | android.content, androidx.lifecycle, dagger.hilt, javax.inject, kotlinx.coroutines, timber.log |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/LatestReleasesViewModel.kt` | 74 | `com.jtech.zemer.viewmodels` | no | 18 | 12 | android.content, androidx.lifecycle, dagger.hilt, javax.inject, kotlinx.coroutines, timber.log |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/LibraryVideosViewModel.kt` | 41 | `com.jtech.zemer.viewmodels` | no | 13 | 8 | android.content, androidx.lifecycle, dagger.hilt, javax.inject, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/LibraryViewModels.kt` | 539 | `com.jtech.zemer.viewmodels` | no | 66 | 79 | android.content, androidx.lifecycle, dagger.hilt, java.time, javax.inject, kotlinx.coroutines, timber.log |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/LocalPlaylistViewModel.kt` | 93 | `com.jtech.zemer.viewmodels` | no | 25 | 7 | android.content, androidx.lifecycle, dagger.hilt, java.text, java.util, javax.inject, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/LyricsMenuViewModel.kt` | 96 | `com.jtech.zemer.viewmodels` | no | 22 | 15 | android.content, androidx.compose, androidx.lifecycle, dagger.hilt, javax.inject, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/OfflineSearchSettingsViewModel.kt` | 61 | `com.jtech.zemer.viewmodels` | no | 14 | 7 | android.content, androidx.datastore, androidx.lifecycle, dagger.hilt, javax.inject, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/OnboardingViewModel.kt` | 152 | `com.jtech.zemer.viewmodels` | no | 15 | 24 | androidx.lifecycle, dagger.hilt, javax.inject, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/OnlinePlaylistViewModel.kt` | 193 | `com.jtech.zemer.viewmodels` | no | 24 | 30 | android.content, androidx.lifecycle, dagger.hilt, javax.inject, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/OnlinePodcastViewModel.kt` | 187 | `com.jtech.zemer.viewmodels` | no | 33 | 28 | android.content, androidx.lifecycle, dagger.hilt, java.time, javax.inject, kotlinx.coroutines, timber.log |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/OnlineSearchSuggestionViewModel.kt` | 100 | `com.jtech.zemer.viewmodels` | no | 23 | 13 | android.content, androidx.lifecycle, dagger.hilt, javax.inject, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/OnlineSearchViewModel.kt` | 262 | `com.jtech.zemer.viewmodels` | no | 34 | 30 | android.content, android.net, androidx.compose, androidx.lifecycle, dagger.hilt, javax.inject, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/PodcastGenreCatalogViewModel.kt` | 65 | `com.jtech.zemer.viewmodels` | no | 17 | 12 | android.content, androidx.lifecycle, dagger.hilt, javax.inject, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/PodcastGenreViewModel.kt` | 71 | `com.jtech.zemer.viewmodels` | no | 17 | 15 | android.content, androidx.lifecycle, dagger.hilt, javax.inject, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/PodcastGenresHomeViewModel.kt` | 57 | `com.jtech.zemer.viewmodels` | no | 18 | 9 | android.content, androidx.lifecycle, dagger.hilt, javax.inject, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/PodcastHomeRowsViewModel.kt` | 74 | `com.jtech.zemer.viewmodels` | no | 20 | 13 | android.content, androidx.lifecycle, dagger.hilt, javax.inject, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/PodcastSubscriptionsHomeViewModel.kt` | 81 | `com.jtech.zemer.viewmodels` | no | 18 | 7 | android.content, androidx.lifecycle, dagger.hilt, javax.inject, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/RecognitionHistoryViewModel.kt` | 42 | `com.jtech.zemer.viewmodels` | no | 12 | 6 | androidx.lifecycle, dagger.hilt, javax.inject, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/RecognizeMusicViewModel.kt` | 104 | `com.jtech.zemer.viewmodels` | no | 18 | 22 | android.content, androidx.lifecycle, dagger.hilt, javax.inject, kotlinx.coroutines, timber.log |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/ReportContentViewModel.kt` | 37 | `com.jtech.zemer.viewmodels` | no | 6 | 3 | androidx.lifecycle, dagger.hilt, javax.inject, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/SavedStatusViewModel.kt` | 38 | `com.jtech.zemer.viewmodels` | no | 10 | 5 | androidx.lifecycle, dagger.hilt, javax.inject, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/StatusDownloadsViewModel.kt` | 34 | `com.jtech.zemer.viewmodels` | no | 10 | 5 | androidx.lifecycle, dagger.hilt, javax.inject, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/StoryViewModel.kt` | 120 | `com.jtech.zemer.viewmodels` | no | 25 | 17 | android.content, android.graphics, androidx.lifecycle, dagger.hilt, javax.inject, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/TopPlaylistViewModel.kt` | 38 | `com.jtech.zemer.viewmodels` | no | 14 | 4 | android.content, androidx.lifecycle, dagger.hilt, javax.inject, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/VideoHomeRowsViewModel.kt` | 96 | `com.jtech.zemer.viewmodels` | no | 24 | 18 | android.content, androidx.lifecycle, dagger.hilt, javax.inject, kotlinx.coroutines, timber.log |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/WhitelistedArtistsViewModel.kt` | 70 | `com.jtech.zemer.viewmodels` | no | 17 | 12 | androidx.lifecycle, dagger.hilt, javax.inject, kotlinx.coroutines, timber.log |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/WhitelistedPodcastsViewModel.kt` | 90 | `com.jtech.zemer.viewmodels` | no | 20 | 15 | android.content, androidx.lifecycle, dagger.hilt, javax.inject, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/ZemerCuratedPlaylistViewModel.kt` | 72 | `com.jtech.zemer.viewmodels` | no | 17 | 14 | android.content, androidx.lifecycle, dagger.hilt, javax.inject, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/ZemerCuratedPlaylistsViewModel.kt` | 81 | `com.jtech.zemer.viewmodels` | no | 20 | 10 | android.content, androidx.lifecycle, dagger.hilt, javax.inject, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/ZemerFlagRefetch.kt` | 29 | `com.jtech.zemer.viewmodels` | no | 8 | 1 | androidx.lifecycle, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/ZemerGenreCatalogViewModel.kt` | 69 | `com.jtech.zemer.viewmodels` | no | 18 | 12 | android.content, androidx.lifecycle, dagger.hilt, javax.inject, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/ZemerGenreSectionViewModel.kt` | 96 | `com.jtech.zemer.viewmodels` | no | 18 | 21 | android.content, androidx.lifecycle, dagger.hilt, javax.inject, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/ZemerGenreViewModel.kt` | 168 | `com.jtech.zemer.viewmodels` | no | 21 | 28 | android.content, androidx.lifecycle, coil3.imageLoader, coil3.request, dagger.hilt, javax.inject, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/ZemerGenresViewModel.kt` | 59 | `com.jtech.zemer.viewmodels` | no | 18 | 9 | android.content, androidx.lifecycle, dagger.hilt, javax.inject, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/ZemerStationsViewModel.kt` | 47 | `com.jtech.zemer.viewmodels` | no | 13 | 7 | androidx.lifecycle, dagger.hilt, javax.inject, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/ZemerStatusesViewModel.kt` | 63 | `com.jtech.zemer.viewmodels` | no | 18 | 6 | android.content, androidx.lifecycle, dagger.hilt, javax.inject, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/widget/MusicWidget.kt` | 391 | `com.jtech.zemer.widget` | no | 62 | 49 | android.content, android.graphics, androidx.compose, androidx.datastore, androidx.glance, coil3.SingletonImageLoader, coil3.request, coil3.toBitmap, java.io, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/widget/WidgetLayout.kt` | 14 | `com.jtech.zemer.widget` | no | 0 | 3 |  |
| `app/src/test/kotlin/com/dpi/DensityMathTest.kt` | 69 | `com.dpi` | no | 3 | 1 | org.junit |
| `app/src/test/kotlin/com/jtech/zemer/constants/PreferenceKeysTest.kt` | 26 | `com.jtech.zemer.constants` | no | 4 | 3 | androidx.datastore, java.lang, org.junit |
| `app/src/test/kotlin/com/jtech/zemer/db/entities/LyricsCachePolicyTest.kt` | 60 | `com.jtech.zemer.db.entities` | no | 6 | 5 | org.junit |
| `app/src/test/kotlin/com/jtech/zemer/db/entities/ShareLinkTest.kt` | 41 | `com.jtech.zemer.db.entities` | no | 3 | 2 | org.junit |
| `app/src/test/kotlin/com/jtech/zemer/extensions/ContextExtLogicTest.kt` | 27 | `com.jtech.zemer.extensions` | no | 3 | 1 | org.junit |
| `app/src/test/kotlin/com/jtech/zemer/extensions/PlayerIconResTest.kt` | 50 | `com.jtech.zemer.extensions` | no | 4 | 2 | androidx.media3, org.junit |
| `app/src/test/kotlin/com/jtech/zemer/latestreleases/LatestReleaseFilterTest.kt` | 46 | `com.jtech.zemer.latestreleases` | no | 2 | 11 | org.junit |
| `app/src/test/kotlin/com/jtech/zemer/latestreleases/LatestReleasePlaybackTest.kt` | 120 | `com.jtech.zemer.latestreleases` | no | 6 | 9 | org.junit |
| `app/src/test/kotlin/com/jtech/zemer/latestreleases/LatestReleasesStoreTest.kt` | 120 | `com.jtech.zemer.latestreleases` | no | 8 | 8 | java.io, java.nio, kotlinx.coroutines, org.junit |
| `app/src/test/kotlin/com/jtech/zemer/lyrics/LyricsUtilsTest.kt` | 60 | `com.jtech.zemer.lyrics` | no | 4 | 7 | org.junit |
| `app/src/test/kotlin/com/jtech/zemer/lyrics/musixmatch/MusixmatchGatesTest.kt` | 69 | `com.jtech.zemer.lyrics.musixmatch` | no | 7 | 11 | org.junit |
| `app/src/test/kotlin/com/jtech/zemer/lyrics/musixmatch/MusixmatchLiveTest.kt` | 18 | `com.jtech.zemer.lyrics.musixmatch` | no | 3 | 3 | kotlinx.coroutines, org.junit |
| `app/src/test/kotlin/com/jtech/zemer/lyrics/musixmatch/MusixmatchTokenLiveTest.kt` | 26 | `com.jtech.zemer.lyrics.musixmatch` | no | 9 | 4 | io.ktor, kotlinx.coroutines, org.junit |
| `app/src/test/kotlin/com/jtech/zemer/lyrics/zemer/JkaraokeLrcGoldenTest.kt` | 30 | `com.jtech.zemer.lyrics.zemer` | no | 6 | 5 | kotlinx.serialization, org.junit |
| `app/src/test/kotlin/com/jtech/zemer/lyrics/zemer/JyricsParserGoldenTest.kt` | 31 | `com.jtech.zemer.lyrics.zemer` | no | 6 | 6 | kotlinx.serialization, org.junit |
| `app/src/test/kotlin/com/jtech/zemer/lyrics/zemer/ShironetParserGoldenTest.kt` | 35 | `com.jtech.zemer.lyrics.zemer` | no | 6 | 6 | kotlinx.serialization, org.junit |
| `app/src/test/kotlin/com/jtech/zemer/lyrics/zemer/SyncIntegrationTest.kt` | 38 | `com.jtech.zemer.lyrics.zemer` | no | 7 | 7 | kotlinx.serialization, org.junit |
| `app/src/test/kotlin/com/jtech/zemer/lyrics/zemer/ZemerLyricsProviderTest.kt` | 57 | `com.jtech.zemer.lyrics.zemer` | no | 4 | 11 | kotlinx.coroutines, org.junit |
| `app/src/test/kotlin/com/jtech/zemer/lyrics/zemer/ZingParserGoldenTest.kt` | 28 | `com.jtech.zemer.lyrics.zemer` | no | 6 | 3 | kotlinx.serialization, org.junit |
| `app/src/test/kotlin/com/jtech/zemer/models/MediaMetadataNavResolutionTest.kt` | 82 | `com.jtech.zemer.models` | no | 7 | 11 | org.junit |
| `app/src/test/kotlin/com/jtech/zemer/models/PersistPlayerStateCompatTest.kt` | 87 | `com.jtech.zemer.models` | no | 7 | 6 | java.io, org.junit |
| `app/src/test/kotlin/com/jtech/zemer/models/PersistQueueCompatTest.kt` | 77 | `com.jtech.zemer.models` | no | 9 | 10 | java.io, java.time, org.junit |
| `app/src/test/kotlin/com/jtech/zemer/offline/SubsetDecoderTest.kt` | 153 | `com.jtech.zemer.offline` | no | 5 | 15 | org.junit |
| `app/src/test/kotlin/com/jtech/zemer/offline/SubsetFemaleSynonymsTest.kt` | 57 | `com.jtech.zemer.offline` | no | 4 | 5 | org.junit |
| `app/src/test/kotlin/com/jtech/zemer/offline/SubsetLiveWhitelistTest.kt` | 121 | `com.jtech.zemer.offline` | no | 6 | 8 | org.junit |
| `app/src/test/kotlin/com/jtech/zemer/offline/SubsetNormalizeTest.kt` | 53 | `com.jtech.zemer.offline` | no | 2 | 2 | org.junit |
| `app/src/test/kotlin/com/jtech/zemer/offline/SubsetPodcastReadTest.kt` | 221 | `com.jtech.zemer.offline` | no | 5 | 34 | org.junit |
| `app/src/test/kotlin/com/jtech/zemer/offline/SubsetReadLayerTest.kt` | 174 | `com.jtech.zemer.offline` | no | 5 | 25 | org.junit |
| `app/src/test/kotlin/com/jtech/zemer/offline/SubsetSearchTest.kt` | 159 | `com.jtech.zemer.offline` | no | 4 | 34 | org.junit |
| `app/src/test/kotlin/com/jtech/zemer/offline/SubsetStoreStagingTest.kt` | 74 | `com.jtech.zemer.offline` | no | 6 | 8 | org.junit |
| `app/src/test/kotlin/com/jtech/zemer/offline/SubsetSyncTest.kt` | 72 | `com.jtech.zemer.offline` | no | 4 | 13 | org.junit |
| `app/src/test/kotlin/com/jtech/zemer/playback/BlockedPodcastsQueueTest.kt` | 55 | `com.jtech.zemer.playback` | no | 6 | 4 | androidx.media3, org.junit |
| `app/src/test/kotlin/com/jtech/zemer/playback/CastAutoAdvanceTest.kt` | 184 | `com.jtech.zemer.playback` | no | 3 | 10 | org.junit |
| `app/src/test/kotlin/com/jtech/zemer/playback/CastConnectTest.kt` | 134 | `com.jtech.zemer.playback` | no | 13 | 13 | java.net, kotlinx.coroutines, org.fcast, org.junit |
| `app/src/test/kotlin/com/jtech/zemer/playback/CastDeviceCatalogTest.kt` | 145 | `com.jtech.zemer.playback` | no | 8 | 20 | org.fcast, org.junit |
| `app/src/test/kotlin/com/jtech/zemer/playback/CastErrorRecoveryTest.kt` | 120 | `com.jtech.zemer.playback` | no | 4 | 5 | org.junit |
| `app/src/test/kotlin/com/jtech/zemer/playback/CastIdleWatchdogTest.kt` | 65 | `com.jtech.zemer.playback` | no | 4 | 4 | org.fcast, org.junit |
| `app/src/test/kotlin/com/jtech/zemer/playback/CastNativeLibLoaderTest.kt` | 84 | `com.jtech.zemer.playback` | no | 5 | 2 | org.junit |
| `app/src/test/kotlin/com/jtech/zemer/playback/CastPlaybackTest.kt` | 160 | `com.jtech.zemer.playback` | no | 6 | 1 | org.fcast, org.junit |
| `app/src/test/kotlin/com/jtech/zemer/playback/CastRelayProtocolTest.kt` | 125 | `com.jtech.zemer.playback` | no | 6 | 7 | java.net, java.security, org.junit |
| `app/src/test/kotlin/com/jtech/zemer/playback/CastStreamRelayTest.kt` | 342 | `com.jtech.zemer.playback` | no | 15 | 63 | java.io, java.net, java.nio, java.util, org.junit |
| `app/src/test/kotlin/com/jtech/zemer/playback/CastVolumeKeysTest.kt` | 93 | `com.jtech.zemer.playback` | no | 3 | 1 | android.view, org.junit |
| `app/src/test/kotlin/com/jtech/zemer/playback/DeferredStatsPushTest.kt` | 98 | `com.jtech.zemer.playback` | no | 4 | 8 | kotlinx.coroutines, org.junit |
| `app/src/test/kotlin/com/jtech/zemer/playback/DeferredStatsQueueTest.kt` | 209 | `com.jtech.zemer.playback` | no | 8 | 24 | java.io, kotlinx.coroutines, org.junit |
| `app/src/test/kotlin/com/jtech/zemer/playback/DeferredStatsRecordTest.kt` | 58 | `com.jtech.zemer.playback` | no | 5 | 4 | org.junit |
| `app/src/test/kotlin/com/jtech/zemer/playback/DownloadCancellationContractTest.kt` | 75 | `com.jtech.zemer.playback` | no | 10 | 10 | java.util, kotlinx.coroutines, org.junit |
| `app/src/test/kotlin/com/jtech/zemer/playback/DownloadMenuLogicTest.kt` | 140 | `com.jtech.zemer.playback` | no | 3 | 21 | org.junit |
| `app/src/test/kotlin/com/jtech/zemer/playback/DownloadStateResolverTest.kt` | 186 | `com.jtech.zemer.playback` | no | 6 | 38 | org.junit |
| `app/src/test/kotlin/com/jtech/zemer/playback/EpisodeResumeTest.kt` | 69 | `com.jtech.zemer.playback` | no | 5 | 3 | org.junit |
| `app/src/test/kotlin/com/jtech/zemer/playback/ListenAccumulatorTest.kt` | 68 | `com.jtech.zemer.playback` | no | 3 | 8 | org.junit |
| `app/src/test/kotlin/com/jtech/zemer/playback/PlaybackNonceRegistryTest.kt` | 134 | `com.jtech.zemer.playback` | no | 5 | 28 | java.util, org.junit |
| `app/src/test/kotlin/com/jtech/zemer/playback/PlayerVideoUiLogicTest.kt` | 74 | `com.jtech.zemer.playback` | no | 3 | 3 | org.junit |
| `app/src/test/kotlin/com/jtech/zemer/playback/QueueContinuationTest.kt` | 42 | `com.jtech.zemer.playback` | no | 4 | 7 | androidx.media3, org.junit |
| `app/src/test/kotlin/com/jtech/zemer/playback/QueuePersistTest.kt` | 116 | `com.jtech.zemer.playback` | no | 3 | 3 | org.junit |
| `app/src/test/kotlin/com/jtech/zemer/playback/RemoteVolumeTrackerTest.kt` | 57 | `com.jtech.zemer.playback` | no | 3 | 6 | org.junit |
| `app/src/test/kotlin/com/jtech/zemer/playback/SeekMathTest.kt` | 35 | `com.jtech.zemer.playback` | no | 2 | 1 | org.junit |
| `app/src/test/kotlin/com/jtech/zemer/playback/VideoAvailabilityCacheTest.kt` | 75 | `com.jtech.zemer.playback` | no | 4 | 11 | org.junit |
| `app/src/test/kotlin/com/jtech/zemer/playback/VideoModeLogicTest.kt` | 254 | `com.jtech.zemer.playback` | no | 5 | 7 | org.junit |
| `app/src/test/kotlin/com/jtech/zemer/playback/VideoQualityLogicTest.kt` | 221 | `com.jtech.zemer.playback` | no | 5 | 23 | org.junit |
| `app/src/test/kotlin/com/jtech/zemer/playback/VideoRenditionTest.kt` | 87 | `com.jtech.zemer.playback` | no | 4 | 6 | org.junit |
| `app/src/test/kotlin/com/jtech/zemer/playback/VideoSongIdsTest.kt` | 45 | `com.jtech.zemer.playback` | no | 4 | 4 | java.util, org.junit |
| `app/src/test/kotlin/com/jtech/zemer/playback/WatchTimeReporterTest.kt` | 201 | `com.jtech.zemer.playback` | no | 8 | 29 | kotlinx.coroutines, org.junit |
| `app/src/test/kotlin/com/jtech/zemer/playback/WatchTimeScheduleTest.kt` | 59 | `com.jtech.zemer.playback` | no | 2 | 6 | org.junit |
| `app/src/test/kotlin/com/jtech/zemer/playback/WatchTimeSegmentsTest.kt` | 161 | `com.jtech.zemer.playback` | no | 5 | 18 | org.junit |
| `app/src/test/kotlin/com/jtech/zemer/playback/relay/RelayDeviceIdTest.kt` | 82 | `com.jtech.zemer.playback.relay` | no | 6 | 4 | java.util, org.junit |
| `app/src/test/kotlin/com/jtech/zemer/playback/relay/RelayDownloadTest.kt` | 67 | `com.jtech.zemer.playback.relay` | no | 2 | 11 | org.junit |
| `app/src/test/kotlin/com/jtech/zemer/playback/relay/RelayStreamTest.kt` | 55 | `com.jtech.zemer.playback.relay` | no | 2 | 1 | org.junit |
| `app/src/test/kotlin/com/jtech/zemer/playback/sabr/SabrAudioPickTest.kt` | 61 | `com.jtech.zemer.playback.sabr` | no | 3 | 9 | org.junit |
| `app/src/test/kotlin/com/jtech/zemer/playback/sabr/SabrBufferTest.kt` | 213 | `com.jtech.zemer.playback.sabr` | no | 6 | 34 | org.junit |
| `app/src/test/kotlin/com/jtech/zemer/playback/sabr/SabrMessagesTest.kt` | 166 | `com.jtech.zemer.playback.sabr` | no | 4 | 36 | org.junit |
| `app/src/test/kotlin/com/jtech/zemer/playback/sabr/SabrProtectionTest.kt` | 39 | `com.jtech.zemer.playback.sabr` | no | 4 | 2 | org.junit |
| `app/src/test/kotlin/com/jtech/zemer/playback/sabr/SabrProtoTest.kt` | 67 | `com.jtech.zemer.playback.sabr` | no | 3 | 8 | org.junit |
| `app/src/test/kotlin/com/jtech/zemer/playback/sabr/SabrSeekLogicTest.kt` | 76 | `com.jtech.zemer.playback.sabr` | no | 3 | 7 | org.junit |
| `app/src/test/kotlin/com/jtech/zemer/playback/sabr/SabrSpoolTest.kt` | 93 | `com.jtech.zemer.playback.sabr` | no | 10 | 10 | java.io, org.junit |
| `app/src/test/kotlin/com/jtech/zemer/playback/sabr/SabrStreamLifecycleTest.kt` | 246 | `com.jtech.zemer.playback.sabr` | no | 11 | 37 | okhttp3.OkHttpClient, org.junit |
| `app/src/test/kotlin/com/jtech/zemer/playback/sabr/SabrUmpTest.kt` | 70 | `com.jtech.zemer.playback.sabr` | no | 3 | 7 | org.junit |
| `app/src/test/kotlin/com/jtech/zemer/playback/sabr/SabrVideoRungPickTest.kt` | 73 | `com.jtech.zemer.playback.sabr` | no | 4 | 11 | org.junit |
| `app/src/test/kotlin/com/jtech/zemer/recognition/AudioResamplerTest.kt` | 59 | `com.jtech.zemer.recognition` | no | 6 | 15 | java.nio, kotlinx.coroutines, org.junit |
| `app/src/test/kotlin/com/jtech/zemer/recognition/RecognitionHistoryFilterTest.kt` | 47 | `com.jtech.zemer.recognition` | no | 4 | 2 | org.junit |
| `app/src/test/kotlin/com/jtech/zemer/recognition/RecognitionHistoryPlaybackTest.kt` | 64 | `com.jtech.zemer.recognition` | no | 4 | 7 | org.junit |
| `app/src/test/kotlin/com/jtech/zemer/recognition/RecognitionMatchSelectorTest.kt` | 106 | `com.jtech.zemer.recognition` | no | 8 | 13 | kotlinx.coroutines, org.junit |
| `app/src/test/kotlin/com/jtech/zemer/recognition/RecognitionMatcherTest.kt` | 72 | `com.jtech.zemer.recognition` | no | 4 | 8 | org.junit |
| `app/src/test/kotlin/com/jtech/zemer/recognition/ShazamSignatureGeneratorTest.kt` | 75 | `com.jtech.zemer.recognition` | no | 11 | 16 | java.nio, java.util, kotlin.math, org.junit |
| `app/src/test/kotlin/com/jtech/zemer/search/ChartMovementTest.kt` | 78 | `com.jtech.zemer.search` | no | 3 | 4 | org.junit |
| `app/src/test/kotlin/com/jtech/zemer/search/PodcastGenreSectionsTest.kt` | 64 | `com.jtech.zemer.search` | no | 4 | 9 | org.junit |
| `app/src/test/kotlin/com/jtech/zemer/search/ResultDedupeTest.kt` | 86 | `com.jtech.zemer.search` | no | 7 | 18 | org.junit |
| `app/src/test/kotlin/com/jtech/zemer/search/ZemerCuratedPlaylistsTest.kt` | 187 | `com.jtech.zemer.search` | no | 8 | 15 | org.junit |
| `app/src/test/kotlin/com/jtech/zemer/search/ZemerGenresTest.kt` | 272 | `com.jtech.zemer.search` | no | 5 | 24 | org.junit |
| `app/src/test/kotlin/com/jtech/zemer/search/ZemerPodcastMapperTest.kt` | 177 | `com.jtech.zemer.search` | no | 14 | 19 | org.junit |
| `app/src/test/kotlin/com/jtech/zemer/search/ZemerResultMapperTest.kt` | 729 | `com.jtech.zemer.search` | no | 18 | 86 | org.junit |
| `app/src/test/kotlin/com/jtech/zemer/search/ZemerRoutesTest.kt` | 75 | `com.jtech.zemer.search` | no | 3 | 3 | org.junit |
| `app/src/test/kotlin/com/jtech/zemer/search/ZemerSearchJsonTest.kt` | 89 | `com.jtech.zemer.search` | no | 3 | 10 | org.junit |
| `app/src/test/kotlin/com/jtech/zemer/search/ZemerSearchParametersTest.kt` | 72 | `com.jtech.zemer.search` | no | 2 | 4 | org.junit |
| `app/src/test/kotlin/com/jtech/zemer/search/ZemerSearchRoutingTest.kt` | 81 | `com.jtech.zemer.search` | no | 8 | 8 | java.io, java.nio, kotlinx.coroutines, org.junit |
| `app/src/test/kotlin/com/jtech/zemer/search/ZemerStationsTest.kt` | 127 | `com.jtech.zemer.search` | no | 5 | 11 | org.junit |
| `app/src/test/kotlin/com/jtech/zemer/statuses/StatusDownloadNamingTest.kt` | 39 | `com.jtech.zemer.statuses` | no | 3 | 2 | java.time, org.junit |
| `app/src/test/kotlin/com/jtech/zemer/statuses/StatusDownloadTest.kt` | 40 | `com.jtech.zemer.statuses` | no | 4 | 6 | org.junit |
| `app/src/test/kotlin/com/jtech/zemer/statuses/StatusDownloadsViewTest.kt` | 40 | `com.jtech.zemer.statuses` | no | 2 | 3 | org.junit |
| `app/src/test/kotlin/com/jtech/zemer/statuses/StatusSourcesConfigTest.kt` | 156 | `com.jtech.zemer.statuses` | no | 5 | 19 | org.junit |
| `app/src/test/kotlin/com/jtech/zemer/statuses/StatusTimelineTest.kt` | 105 | `com.jtech.zemer.statuses` | no | 5 | 8 | java.time, org.junit |
| `app/src/test/kotlin/com/jtech/zemer/statuses/StatusesApiTest.kt` | 208 | `com.jtech.zemer.statuses` | no | 6 | 28 | org.json, org.junit |
| `app/src/test/kotlin/com/jtech/zemer/statuses/YidStatusApiTest.kt` | 81 | `com.jtech.zemer.statuses` | no | 5 | 11 | org.json, org.junit |
| `app/src/test/kotlin/com/jtech/zemer/sync/ContentReportRepositoryTest.kt` | 81 | `com.jtech.zemer.sync` | no | 2 | 6 | org.junit |
| `app/src/test/kotlin/com/jtech/zemer/sync/PodcastSyncLogicTest.kt` | 171 | `com.jtech.zemer.sync` | no | 4 | 9 | org.junit |
| `app/src/test/kotlin/com/jtech/zemer/sync/models/DeviceContentFiltersTest.kt` | 80 | `com.jtech.zemer.sync.models` | no | 7 | 3 | org.junit |
| `app/src/test/kotlin/com/jtech/zemer/tracking/FlushScheduleTest.kt` | 57 | `com.jtech.zemer.tracking` | no | 2 | 8 | org.junit |
| `app/src/test/kotlin/com/jtech/zemer/tracking/LibraryActionBackfillTest.kt` | 101 | `com.jtech.zemer.tracking` | no | 9 | 10 | java.time, org.junit |
| `app/src/test/kotlin/com/jtech/zemer/tracking/PlayHistoryBackfillTest.kt` | 102 | `com.jtech.zemer.tracking` | no | 7 | 11 | java.time, org.junit |
| `app/src/test/kotlin/com/jtech/zemer/tracking/PlaySourceResolverTest.kt` | 95 | `com.jtech.zemer.tracking` | no | 2 | 9 | org.junit |
| `app/src/test/kotlin/com/jtech/zemer/tracking/TrackingEventsTest.kt` | 213 | `com.jtech.zemer.tracking` | no | 2 | 8 | org.junit |
| `app/src/test/kotlin/com/jtech/zemer/tracking/TrackingQueueTest.kt` | 100 | `com.jtech.zemer.tracking` | no | 5 | 13 | java.io, org.junit |
| `app/src/test/kotlin/com/jtech/zemer/ui/ActiveRowTapTest.kt` | 31 | `com.jtech.zemer.ui` | no | 4 | 1 | org.junit |
| `app/src/test/kotlin/com/jtech/zemer/ui/HomeTitleEasterEggTest.kt` | 36 | `com.jtech.zemer.ui` | no | 5 | 6 | org.junit |
| `app/src/test/kotlin/com/jtech/zemer/ui/component/AlphabetIndexTest.kt` | 28 | `com.jtech.zemer.ui.component` | no | 2 | 1 | org.junit |
| `app/src/test/kotlin/com/jtech/zemer/ui/component/AvatarShapeIndexTest.kt` | 37 | `com.jtech.zemer.ui.component` | no | 3 | 4 | org.junit |
| `app/src/test/kotlin/com/jtech/zemer/ui/component/BrowseScreenScaffoldTest.kt` | 178 | `com.jtech.zemer.ui.component` | no | 5 | 8 | org.junit |
| `app/src/test/kotlin/com/jtech/zemer/ui/component/GentleMarqueeTest.kt` | 42 | `com.jtech.zemer.ui.component` | no | 3 | 3 | org.junit |
| `app/src/test/kotlin/com/jtech/zemer/ui/component/SettingsCardGroupTest.kt` | 20 | `com.jtech.zemer.ui.component` | no | 2 | 2 | org.junit |
| `app/src/test/kotlin/com/jtech/zemer/ui/component/SortHeaderTest.kt` | 20 | `com.jtech.zemer.ui.component` | no | 4 | 2 | org.junit |
| `app/src/test/kotlin/com/jtech/zemer/ui/component/lyrics/LyricsSourceHeaderTest.kt` | 19 | `com.jtech.zemer.ui.component.lyrics` | no | 4 | 1 | org.junit |
| `app/src/test/kotlin/com/jtech/zemer/ui/menu/DownloadMenuItemsTest.kt` | 71 | `com.jtech.zemer.ui.menu` | no | 5 | 33 | org.junit |
| `app/src/test/kotlin/com/jtech/zemer/ui/menu/ViewCollectionMenuItemTest.kt` | 31 | `com.jtech.zemer.ui.menu` | no | 3 | 1 | org.junit |
| `app/src/test/kotlin/com/jtech/zemer/ui/player/EpisodeSpeedTest.kt` | 47 | `com.jtech.zemer.ui.player` | no | 2 | 1 | org.junit |
| `app/src/test/kotlin/com/jtech/zemer/ui/player/PlayerBackgroundTest.kt` | 53 | `com.jtech.zemer.ui.player` | no | 4 | 3 | androidx.compose, org.junit |
| `app/src/test/kotlin/com/jtech/zemer/ui/screens/GenreScreenTest.kt` | 154 | `com.jtech.zemer.ui.screens` | no | 11 | 11 | org.junit |
| `app/src/test/kotlin/com/jtech/zemer/ui/screens/HomeContentTabTest.kt` | 49 | `com.jtech.zemer.ui.screens` | no | 4 | 2 | org.junit |
| `app/src/test/kotlin/com/jtech/zemer/ui/screens/KidZoneTabTest.kt` | 27 | `com.jtech.zemer.ui.screens` | no | 2 | 3 | org.junit |
| `app/src/test/kotlin/com/jtech/zemer/ui/screens/LoginCaptureTest.kt` | 61 | `com.jtech.zemer.ui.screens` | no | 5 | 2 | org.junit |
| `app/src/test/kotlin/com/jtech/zemer/ui/screens/artist/ArtistSectionScreenTest.kt` | 36 | `com.jtech.zemer.ui.screens.artist` | no | 4 | 6 | org.junit |
| `app/src/test/kotlin/com/jtech/zemer/ui/screens/onboarding/OnboardingNavigationTest.kt` | 88 | `com.jtech.zemer.ui.screens.onboarding` | no | 2 | 12 | org.junit |
| `app/src/test/kotlin/com/jtech/zemer/ui/screens/playlist/PlaylistHeaderCoverTest.kt` | 23 | `com.jtech.zemer.ui.screens.playlist` | no | 3 | 1 | org.junit |
| `app/src/test/kotlin/com/jtech/zemer/ui/screens/playlist/ZemerCuratedPlaylistFilterTest.kt` | 73 | `com.jtech.zemer.ui.screens.playlist` | no | 7 | 4 | org.junit |
| `app/src/test/kotlin/com/jtech/zemer/ui/screens/search/SearchFilterPolicyTest.kt` | 53 | `com.jtech.zemer.ui.screens.search` | no | 11 | 5 | org.junit |
| `app/src/test/kotlin/com/jtech/zemer/ui/screens/settings/LyricsSyncOffsetTest.kt` | 28 | `com.jtech.zemer.ui.screens.settings` | no | 2 | 1 | org.junit |
| `app/src/test/kotlin/com/jtech/zemer/ui/theme/ThemePaletteSelectionTest.kt` | 84 | `com.jtech.zemer.ui.theme` | no | 7 | 11 | androidx.compose, org.junit |
| `app/src/test/kotlin/com/jtech/zemer/ui/utils/AppNavigationTest.kt` | 106 | `com.jtech.zemer.ui.utils` | no | 3 | 13 | org.junit |
| `app/src/test/kotlin/com/jtech/zemer/ui/utils/ItemWrapperTest.kt` | 15 | `com.jtech.zemer.ui.utils` | no | 2 | 1 | org.junit |
| `app/src/test/kotlin/com/jtech/zemer/ui/utils/NavControllerUtilsTest.kt` | 34 | `com.jtech.zemer.ui.utils` | no | 3 | 2 | org.junit |
| `app/src/test/kotlin/com/jtech/zemer/ui/utils/SeeAllTest.kt` | 39 | `com.jtech.zemer.ui.utils` | no | 6 | 2 | org.junit |
| `app/src/test/kotlin/com/jtech/zemer/ui/utils/StatusNavigationTest.kt` | 17 | `com.jtech.zemer.ui.utils` | no | 2 | 1 | org.junit |
| `app/src/test/kotlin/com/jtech/zemer/ui/utils/StringUtilsTest.kt` | 21 | `com.jtech.zemer.ui.utils` | no | 3 | 2 | java.util, org.junit |
| `app/src/test/kotlin/com/jtech/zemer/ui/utils/YouTubeUtilsTest.kt` | 76 | `com.jtech.zemer.ui.utils` | no | 2 | 10 | org.junit |
| `app/src/test/kotlin/com/jtech/zemer/utils/ArtistThumbResolverTest.kt` | 140 | `com.jtech.zemer.utils` | no | 5 | 8 | org.junit |
| `app/src/test/kotlin/com/jtech/zemer/utils/BlockedIdsCacheTest.kt` | 62 | `com.jtech.zemer.utils` | no | 5 | 7 | org.junit |
| `app/src/test/kotlin/com/jtech/zemer/utils/BottomNavItemsTest.kt` | 46 | `com.jtech.zemer.utils` | no | 2 | 8 | org.junit |
| `app/src/test/kotlin/com/jtech/zemer/utils/CrashReportingTreeTest.kt` | 64 | `com.jtech.zemer.utils` | no | 6 | 6 | org.junit, timber.log |
| `app/src/test/kotlin/com/jtech/zemer/utils/LogBufferTreeTest.kt` | 77 | `com.jtech.zemer.utils` | no | 6 | 7 | org.junit, timber.log |
| `app/src/test/kotlin/com/jtech/zemer/utils/LogExportTest.kt` | 100 | `com.jtech.zemer.utils` | no | 6 | 21 | java.time, org.junit |
| `app/src/test/kotlin/com/jtech/zemer/utils/PlaylistRemoteEditsTest.kt` | 87 | `com.jtech.zemer.utils` | no | 8 | 6 | org.junit |
| `app/src/test/kotlin/com/jtech/zemer/utils/PlaylistSongWhitelistTest.kt` | 52 | `com.jtech.zemer.utils` | no | 3 | 2 | org.junit |
| `app/src/test/kotlin/com/jtech/zemer/utils/PodcastLibrarySourcesTest.kt` | 78 | `com.jtech.zemer.utils` | no | 4 | 6 | org.junit |
| `app/src/test/kotlin/com/jtech/zemer/utils/PodcastWhitelistCacheTest.kt` | 44 | `com.jtech.zemer.utils` | no | 5 | 3 | org.junit |
| `app/src/test/kotlin/com/jtech/zemer/utils/RankedContentGateTest.kt` | 52 | `com.jtech.zemer.utils` | no | 4 | 3 | org.junit |
| `app/src/test/kotlin/com/jtech/zemer/utils/RefreshRateSelectionTest.kt` | 90 | `com.jtech.zemer.utils` | no | 4 | 11 | org.junit |
| `app/src/test/kotlin/com/jtech/zemer/utils/VideoLinkBuilderTest.kt` | 49 | `com.jtech.zemer.utils` | no | 2 | 1 | org.junit |
| `app/src/test/kotlin/com/jtech/zemer/utils/ZemerContentClientTest.kt` | 115 | `com.jtech.zemer.utils` | no | 7 | 13 | kotlinx.coroutines, kotlinx.serialization, org.junit |
| `app/src/test/kotlin/com/jtech/zemer/utils/mp4/Mp4MetadataWriterRealFileTest.kt` | 60 | `com.jtech.zemer.utils.mp4` | no | 7 | 8 | java.io, org.junit |
| `app/src/test/kotlin/com/jtech/zemer/utils/mp4/Mp4MetadataWriterTest.kt` | 225 | `com.jtech.zemer.utils.mp4` | no | 9 | 69 | java.io, java.nio, org.junit |
| `app/src/test/kotlin/com/jtech/zemer/utils/ogg/OggOpusTaggerTest.kt` | 195 | `com.jtech.zemer.utils.ogg` | no | 9 | 61 | java.io, java.util, org.junit |
| `app/src/test/kotlin/com/jtech/zemer/utils/updater/InstallerTest.kt` | 43 | `com.jtech.zemer.utils.updater` | no | 3 | 1 | org.junit |
| `app/src/test/kotlin/com/jtech/zemer/utils/updater/NightlyUpdatesTest.kt` | 188 | `com.jtech.zemer.utils.updater` | no | 9 | 15 | java.util, org.junit |
| `app/src/test/kotlin/com/jtech/zemer/viewmodels/ArtistChannelEpisodesTest.kt` | 67 | `com.jtech.zemer.viewmodels` | no | 7 | 11 | org.junit |
| `app/src/test/kotlin/com/jtech/zemer/viewmodels/StaleAlbumDeleteTest.kt` | 29 | `com.jtech.zemer.viewmodels` | no | 3 | 1 | org.junit |
| `app/src/test/kotlin/com/jtech/zemer/widget/WidgetLayoutTest.kt` | 26 | `com.jtech.zemer.widget` | no | 3 | 1 | org.junit |

## `innertube` Kotlin files (88)

| File | Lines | Package | Compose | Imports | Decls | External import roots |
| --- | ---: | --- | --- | ---: | ---: | --- |
| `innertube/src/main/kotlin/com/metrolist/innertube/InnerTube.kt` | 728 | `com.metrolist.innertube` | no | 47 | 48 | io.ktor, java.util, kotlinx.serialization, okhttp3.ConnectionPool, okhttp3.Dispatcher |
| `innertube/src/main/kotlin/com/metrolist/innertube/YouTube.kt` | 989 | `com.metrolist.innertube` | no | 56 | 154 | io.ktor, kotlin.random, kotlinx.coroutines, kotlinx.serialization |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/AccountInfo.kt` | 8 | `com.metrolist.innertube.models` | no | 0 | 5 |  |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/AutomixPreviewVideoRenderer.kt` | 18 | `com.metrolist.innertube.models` | no | 1 | 6 | kotlinx.serialization |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/Badges.kt` | 13 | `com.metrolist.innertube.models` | no | 1 | 4 | kotlinx.serialization |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/Button.kt` | 16 | `com.metrolist.innertube.models` | no | 1 | 7 | kotlinx.serialization |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/Context.kt` | 60 | `com.metrolist.innertube.models` | no | 1 | 27 | kotlinx.serialization |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/Continuation.kt` | 20 | `com.metrolist.innertube.models` | no | 3 | 5 | kotlinx.serialization |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/ContinuationItemRenderer.kt` | 18 | `com.metrolist.innertube.models` | no | 1 | 6 | kotlinx.serialization |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/Endpoint.kt` | 126 | `com.metrolist.innertube.models` | no | 7 | 59 | kotlinx.serialization |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/GridRenderer.kt` | 26 | `com.metrolist.innertube.models` | no | 1 | 11 | kotlinx.serialization |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/Icon.kt` | 8 | `com.metrolist.innertube.models` | no | 1 | 2 | kotlinx.serialization |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/MediaInfo.kt` | 15 | `com.metrolist.innertube.models` | no | 0 | 12 |  |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/Menu.kt` | 52 | `com.metrolist.innertube.models` | no | 1 | 26 | kotlinx.serialization |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/MusicCardShelfRenderer.kt` | 30 | `com.metrolist.innertube.models` | no | 1 | 15 | kotlinx.serialization |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/MusicCarouselShelfRenderer.kt` | 31 | `com.metrolist.innertube.models` | no | 1 | 16 | kotlinx.serialization |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/MusicDescriptionShelfRenderer.kt` | 11 | `com.metrolist.innertube.models` | no | 1 | 5 | kotlinx.serialization |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/MusicEditablePlaylistDetailHeaderRenderer.kt` | 35 | `com.metrolist.innertube.models` | no | 1 | 17 | kotlinx.serialization |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/MusicNavigationButtonRenderer.kt` | 21 | `com.metrolist.innertube.models` | no | 1 | 9 | kotlinx.serialization |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/MusicPlaylistShelfRenderer.kt` | 11 | `com.metrolist.innertube.models` | no | 1 | 5 | kotlinx.serialization |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/MusicQueueRenderer.kt` | 25 | `com.metrolist.innertube.models` | no | 1 | 10 | kotlinx.serialization |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/MusicResponsiveHeaderRenderer.kt` | 24 | `com.metrolist.innertube.models` | no | 1 | 12 | kotlinx.serialization |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/MusicResponsiveListItemRenderer.kt` | 124 | `com.metrolist.innertube.models` | no | 10 | 34 | kotlinx.serialization |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/MusicShelfRenderer.kt` | 28 | `com.metrolist.innertube.models` | no | 1 | 11 | kotlinx.serialization |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/MusicTwoRowItemRenderer.kt` | 68 | `com.metrolist.innertube.models` | no | 7 | 14 | kotlinx.serialization |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/NavigationEndpoint.kt` | 27 | `com.metrolist.innertube.models` | no | 1 | 10 | kotlinx.serialization |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/PlaylistDeleteBody.kt` | 10 | `com.metrolist.innertube.models.body` | no | 2 | 3 | kotlinx.serialization |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/PlaylistPanelRenderer.kt` | 24 | `com.metrolist.innertube.models` | no | 1 | 13 | kotlinx.serialization |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/PlaylistPanelVideoRenderer.kt` | 19 | `com.metrolist.innertube.models` | no | 1 | 13 | kotlinx.serialization |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/PlaylistPanelVideoWrapperRenderer.kt` | 31 | `com.metrolist.innertube.models` | no | 1 | 7 | kotlinx.serialization |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/ResponseContext.kt` | 21 | `com.metrolist.innertube.models` | no | 1 | 9 | kotlinx.serialization |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/ReturnYouTubeDislikeResponse.kt` | 14 | `com.metrolist.innertube.models` | no | 1 | 8 | kotlinx.serialization |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/Runs.kt` | 43 | `com.metrolist.innertube.models` | no | 1 | 10 | kotlinx.serialization |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/SearchSuggestions.kt` | 6 | `com.metrolist.innertube.models` | no | 0 | 3 |  |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/SectionListRenderer.kt` | 73 | `com.metrolist.innertube.models` | no | 3 | 35 | kotlinx.serialization |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/SubscriptionButton.kt` | 14 | `com.metrolist.innertube.models` | no | 1 | 5 | kotlinx.serialization |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/Tabs.kt` | 34 | `com.metrolist.innertube.models` | no | 1 | 12 | kotlinx.serialization |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/ThumbnailRenderer.kt` | 29 | `com.metrolist.innertube.models` | no | 3 | 12 | kotlinx.serialization |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/Thumbnails.kt` | 15 | `com.metrolist.innertube.models` | no | 1 | 6 | kotlinx.serialization |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/TwoColumnBrowseResultsRenderer.kt` | 26 | `com.metrolist.innertube.models` | no | 1 | 11 | kotlinx.serialization |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/YTItem.kt` | 131 | `com.metrolist.innertube.models` | no | 0 | 85 |  |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/YouTubeClient.kt` | 141 | `com.metrolist.innertube.models` | no | 1 | 26 | kotlinx.serialization |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/YouTubeDataPage.kt` | 183 | `com.metrolist.innertube.models` | no | 2 | 63 | kotlinx.serialization |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/YouTubeLocale.kt` | 9 | `com.metrolist.innertube.models` | no | 1 | 3 | kotlinx.serialization |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/body/AccountMenuBody.kt` | 11 | `com.metrolist.innertube.models.body` | no | 2 | 4 | kotlinx.serialization |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/body/BrowseBody.kt` | 13 | `com.metrolist.innertube.models.body` | no | 3 | 5 | kotlinx.serialization |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/body/CreatePlaylistBody.kt` | 18 | `com.metrolist.innertube.models.body` | no | 2 | 9 | kotlinx.serialization |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/body/EditPlaylistBody.kt` | 79 | `com.metrolist.innertube.models.body` | no | 2 | 37 | kotlinx.serialization |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/body/FeedbackBody.kt` | 12 | `com.metrolist.innertube.models.body` | no | 2 | 5 | kotlinx.serialization |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/body/GetQueueBody.kt` | 11 | `com.metrolist.innertube.models.body` | no | 2 | 4 | kotlinx.serialization |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/body/GetTranscriptBody.kt` | 10 | `com.metrolist.innertube.models.body` | no | 2 | 3 | kotlinx.serialization |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/body/LikeBody.kt` | 18 | `com.metrolist.innertube.models.body` | no | 2 | 8 | kotlinx.serialization |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/body/NextBody.kt` | 15 | `com.metrolist.innertube.models.body` | no | 2 | 8 | kotlinx.serialization |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/body/PlayerBody.kt` | 30 | `com.metrolist.innertube.models.body` | no | 2 | 14 | kotlinx.serialization |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/body/SearchBody.kt` | 11 | `com.metrolist.innertube.models.body` | no | 2 | 4 | kotlinx.serialization |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/body/SubscribeBody.kt` | 11 | `com.metrolist.innertube.models.body` | no | 2 | 4 | kotlinx.serialization |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/response/AccountMenuResponse.kt` | 53 | `com.metrolist.innertube.models.response` | no | 5 | 18 | kotlinx.serialization |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/response/BrowseResponse.kt` | 133 | `com.metrolist.innertube.models.response` | no | 14 | 67 | kotlinx.serialization |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/response/CreatePlaylistResponse.kt` | 8 | `com.metrolist.innertube.models.response` | no | 1 | 2 | kotlinx.serialization |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/response/EditPlaylistResponse.kt` | 8 | `com.metrolist.innertube.models.response` | no | 1 | 2 | kotlinx.serialization |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/response/FeedbackResponse.kt` | 13 | `com.metrolist.innertube.models.response` | no | 1 | 4 | kotlinx.serialization |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/response/GetQueueResponse.kt` | 14 | `com.metrolist.innertube.models.response` | no | 2 | 4 | kotlinx.serialization |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/response/GetTranscriptResponse.kt` | 65 | `com.metrolist.innertube.models.response` | no | 1 | 26 | kotlinx.serialization |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/response/ImageUploadResponse.kt` | 8 | `com.metrolist.innertube.models.response` | no | 1 | 2 | kotlinx.serialization |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/response/NextResponse.kt` | 40 | `com.metrolist.innertube.models.response` | no | 5 | 15 | kotlinx.serialization |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/response/PlayerResponse.kt` | 140 | `com.metrolist.innertube.models.response` | no | 4 | 72 | kotlinx.serialization |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/response/SearchResponse.kt` | 33 | `com.metrolist.innertube.models.response` | no | 4 | 12 | kotlinx.serialization |
| `innertube/src/main/kotlin/com/metrolist/innertube/pages/AlbumPage.kt` | 58 | `com.metrolist.innertube.pages` | no | 6 | 4 |  |
| `innertube/src/main/kotlin/com/metrolist/innertube/pages/ArtistPage.kt` | 218 | `com.metrolist.innertube.pages` | no | 18 | 14 |  |
| `innertube/src/main/kotlin/com/metrolist/innertube/pages/BrowseResult.kt` | 13 | `com.metrolist.innertube.pages` | no | 1 | 6 |  |
| `innertube/src/main/kotlin/com/metrolist/innertube/pages/HistoryPage.kt` | 65 | `com.metrolist.innertube.pages` | no | 8 | 7 |  |
| `innertube/src/main/kotlin/com/metrolist/innertube/pages/HomePage.kt` | 151 | `com.metrolist.innertube.pages` | no | 12 | 22 |  |
| `innertube/src/main/kotlin/com/metrolist/innertube/pages/LibraryContinuationPage.kt` | 8 | `com.metrolist.innertube.pages` | no | 1 | 3 |  |
| `innertube/src/main/kotlin/com/metrolist/innertube/pages/LibraryPage.kt` | 165 | `com.metrolist.innertube.pages` | no | 13 | 7 |  |
| `innertube/src/main/kotlin/com/metrolist/innertube/pages/NextPage.kt` | 119 | `com.metrolist.innertube.pages` | no | 11 | 21 |  |
| `innertube/src/main/kotlin/com/metrolist/innertube/pages/PageHelper.kt` | 38 | `com.metrolist.innertube.pages` | no | 3 | 8 |  |
| `innertube/src/main/kotlin/com/metrolist/innertube/pages/PlaylistContinuationPage.kt` | 8 | `com.metrolist.innertube.pages` | no | 1 | 3 |  |
| `innertube/src/main/kotlin/com/metrolist/innertube/pages/PlaylistPage.kt` | 49 | `com.metrolist.innertube.pages` | no | 7 | 6 |  |
| `innertube/src/main/kotlin/com/metrolist/innertube/pages/PodcastPage.kt` | 15 | `com.metrolist.innertube.pages` | no | 2 | 4 |  |
| `innertube/src/main/kotlin/com/metrolist/innertube/pages/RelatedPage.kt` | 155 | `com.metrolist.innertube.pages` | no | 10 | 7 |  |
| `innertube/src/main/kotlin/com/metrolist/innertube/pages/SearchPage.kt` | 203 | `com.metrolist.innertube.pages` | no | 11 | 6 |  |
| `innertube/src/main/kotlin/com/metrolist/innertube/pages/SearchSummaryPage.kt` | 12 | `com.metrolist.innertube.pages` | no | 1 | 5 |  |
| `innertube/src/main/kotlin/com/metrolist/innertube/utils/ResilientDns.kt` | 84 | `com.metrolist.innertube.utils` | no | 5 | 10 | java.net, okhttp3.Dns, okhttp3.HttpUrl, okhttp3.OkHttpClient, okhttp3.dnsoverhttps |
| `innertube/src/main/kotlin/com/metrolist/innertube/utils/Utils.kt` | 91 | `com.metrolist.innertube.utils` | no | 4 | 22 | java.security |
| `innertube/src/test/kotlin/com/metrolist/innertube/pages/NextCounterpartTest.kt` | 127 | `com.metrolist.innertube.pages` | no | 6 | 9 | kotlinx.serialization, org.junit |
| `innertube/src/test/kotlin/com/zemer/innertube/models/MusicCarouselShelfHeaderTest.kt` | 34 | `com.zemer.innertube.models` | no | 5 | 5 | kotlinx.serialization, org.junit |
| `innertube/src/test/kotlin/com/zemer/innertube/models/WatchNextTabResolutionTest.kt` | 50 | `com.zemer.innertube.models` | no | 5 | 3 | kotlinx.serialization, org.junit |
| `innertube/src/test/kotlin/com/zemer/innertube/models/response/MusicHeaderThumbnailTest.kt` | 51 | `com.zemer.innertube.models.response` | no | 5 | 5 | kotlinx.serialization, org.junit |

## `lrclib` Kotlin files (3)

| File | Lines | Package | Compose | Imports | Decls | External import roots |
| --- | ---: | --- | --- | ---: | ---: | --- |
| `lrclib/src/main/kotlin/com/metrolist/lrclib/LrcLib.kt` | 273 | `com.metrolist.lrclib` | no | 13 | 46 | io.ktor, kotlin.math, kotlinx.coroutines, kotlinx.serialization |
| `lrclib/src/main/kotlin/com/metrolist/lrclib/models/Track.kt` | 137 | `com.metrolist.lrclib.models` | no | 2 | 29 | kotlin.math, kotlinx.serialization |
| `lrclib/src/test/kotlin/com/metrolist/lrclib/LrcLibIdentityTest.kt` | 55 | `com.metrolist.lrclib` | no | 6 | 3 | org.junit |

## `simpmusic` Kotlin files (3)

| File | Lines | Package | Compose | Imports | Decls | External import roots |
| --- | ---: | --- | --- | ---: | ---: | --- |
| `simpmusic/src/main/kotlin/com/metrolist/simpmusic/SimpMusicLyrics.kt` | 157 | `com.metrolist.simpmusic` | no | 15 | 23 | io.ktor, kotlin.math, kotlinx.serialization |
| `simpmusic/src/main/kotlin/com/metrolist/simpmusic/models/LyricsResponse.kt` | 32 | `com.metrolist.simpmusic.models` | no | 2 | 15 | kotlinx.serialization |
| `simpmusic/src/test/kotlin/com/zemer/simpmusic/SimpMusicLyricsTest.kt` | 82 | `com.zemer.simpmusic` | no | 9 | 1 | org.junit |
