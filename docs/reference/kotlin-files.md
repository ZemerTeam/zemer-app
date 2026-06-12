# Kotlin file reference

Every tracked Kotlin file is listed with hard metadata extracted from the file text: line count, package, whether it declares any `@Composable`, import count, top-level declaration count (`Decls` — a high value flags a god-file), and the external import roots it depends on. Declaration counting is regex-based (after stripping comments and string literals). For the actual declaration names, read the file or use your editor's outline — they are not duplicated here.

## `app` Kotlin files (305)

| File | Lines | Package | Compose | Imports | Decls | External import roots |
| --- | ---: | --- | --- | ---: | ---: | --- |
| `app/src/main/kotlin/com/dpi/ActivityLifecycleManager.kt` | 116 | `com.dpi` | no | 9 | 27 | android.annotation, android.app, android.os, java.util, timber.log |
| `app/src/main/kotlin/com/dpi/BaseLifecycleContentProvider.kt` | 36 | `com.dpi` | no | 4 | 7 | android.content, android.database, android.net |
| `app/src/main/kotlin/com/dpi/DensityConfiguration.kt` | 87 | `com.dpi` | no | 8 | 13 | android.annotation, android.app, android.content, android.util, kotlin.math, timber.log |
| `app/src/main/kotlin/com/dpi/DensityScaler.kt` | 46 | `com.dpi` | no | 2 | 9 | android.content, timber.log |
| `app/src/main/kotlin/com/jtech/zemer/App.kt` | 424 | `com.jtech.zemer` | no | 61 | 43 | android.app, android.content, android.os, android.util, android.webkit, android.widget, androidx.datastore, coil3.ImageLoader, coil3.PlatformContext, coil3.SingletonImageLoader, coil3.disk, coil3.network, coil3.request, com.google, com.zemer, dagger.hilt, io.ktor, java.net, java.util, javax.inject, kotlinx.coroutines, kotlinx.serialization, okhttp3.Credentials, okhttp3.OkHttpClient, timber.log |
| `app/src/main/kotlin/com/jtech/zemer/MainActivity.kt` | 2119 | `com.jtech.zemer` | no | 258 | 217 | android.annotation, android.app, android.content, android.os, android.view, androidx.activity, androidx.compose, androidx.core, androidx.datastore, androidx.hilt, androidx.lifecycle, androidx.media3, androidx.navigation, coil3.compose, coil3.imageLoader, coil3.request, coil3.toBitmap, com.google, com.valentinilk, dagger.hilt, java.net, java.util, javax.inject, kotlin.time, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/accessibility/ButtonMapperAccessibilityService.kt` | 45 | `com.jtech.zemer.accessibility` | no | 7 | 6 | android.accessibilityservice, android.annotation, android.view |
| `app/src/main/kotlin/com/jtech/zemer/auth/AuthState.kt` | 57 | `com.jtech.zemer.auth` | no | 0 | 16 |  |
| `app/src/main/kotlin/com/jtech/zemer/auth/UserAuthManager.kt` | 145 | `com.jtech.zemer.auth` | no | 13 | 26 | android.content, com.google, dagger.hilt, javax.inject, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/auth/WebViewGoogleAuthManager.kt` | 133 | `com.jtech.zemer.auth` | no | 20 | 18 | android.content, android.net, android.util, android.webkit, androidx.compose, com.google, javax.inject, kotlin.coroutines, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/constants/Dimensions.kt` | 39 | `com.jtech.zemer.constants` | no | 4 | 24 | androidx.compose |
| `app/src/main/kotlin/com/jtech/zemer/constants/HistorySource.kt` | 7 | `com.jtech.zemer.constants` | no | 0 | 1 |  |
| `app/src/main/kotlin/com/jtech/zemer/constants/LibraryFilter.kt` | 13 | `com.jtech.zemer.constants` | no | 0 | 1 |  |
| `app/src/main/kotlin/com/jtech/zemer/constants/MediaSessionConstants.kt` | 23 | `com.jtech.zemer.constants` | no | 2 | 14 | android.os, androidx.media3 |
| `app/src/main/kotlin/com/jtech/zemer/constants/PreferenceKeys.kt` | 568 | `com.jtech.zemer.constants` | no | 7 | 176 | androidx.datastore, java.time |
| `app/src/main/kotlin/com/jtech/zemer/constants/StatPeriod.kt` | 97 | `com.jtech.zemer.constants` | no | 3 | 4 | java.time |
| `app/src/main/kotlin/com/jtech/zemer/db/Converters.kt` | 20 | `com.jtech.zemer.db` | no | 4 | 3 | androidx.room, java.time |
| `app/src/main/kotlin/com/jtech/zemer/db/DatabaseDao.kt` | 1681 | `com.jtech.zemer.db` | no | 58 | 224 | androidx.room, androidx.sqlite, java.text, java.time, java.util, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/db/MusicDatabase.kt` | 590 | `com.jtech.zemer.db` | no | 41 | 78 | android.annotation, android.content, android.database, androidx.core, androidx.room, androidx.sqlite, java.time, java.util, timber.log |
| `app/src/main/kotlin/com/jtech/zemer/db/entities/Album.kt` | 33 | `com.jtech.zemer.db.entities` | no | 4 | 8 | androidx.compose, androidx.room |
| `app/src/main/kotlin/com/jtech/zemer/db/entities/AlbumArtistMap.kt` | 29 | `com.jtech.zemer.db.entities` | no | 3 | 4 | androidx.room |
| `app/src/main/kotlin/com/jtech/zemer/db/entities/AlbumEntity.kt` | 51 | `com.jtech.zemer.db.entities` | no | 10 | 19 | androidx.compose, androidx.room, java.time, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/db/entities/AlbumWithSongs.kt` | 36 | `com.jtech.zemer.db.entities` | no | 4 | 4 | androidx.compose, androidx.room |
| `app/src/main/kotlin/com/jtech/zemer/db/entities/Artist.kt` | 19 | `com.jtech.zemer.db.entities` | no | 2 | 7 | androidx.compose, androidx.room |
| `app/src/main/kotlin/com/jtech/zemer/db/entities/ArtistEntity.kt` | 51 | `com.jtech.zemer.db.entities` | no | 10 | 13 | androidx.compose, androidx.room, java.time, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/db/entities/ArtistWhitelistEntity.kt` | 21 | `com.jtech.zemer.db.entities` | no | 4 | 11 | androidx.compose, androidx.room, java.time |
| `app/src/main/kotlin/com/jtech/zemer/db/entities/Event.kt` | 32 | `com.jtech.zemer.db.entities` | no | 7 | 5 | androidx.compose, androidx.room, java.time |
| `app/src/main/kotlin/com/jtech/zemer/db/entities/EventWithSong.kt` | 17 | `com.jtech.zemer.db.entities` | no | 3 | 3 | androidx.compose, androidx.room |
| `app/src/main/kotlin/com/jtech/zemer/db/entities/FormatEntity.kt` | 19 | `com.jtech.zemer.db.entities` | no | 2 | 11 | androidx.room |
| `app/src/main/kotlin/com/jtech/zemer/db/entities/LocalItem.kt` | 7 | `com.jtech.zemer.db.entities` | no | 0 | 4 |  |
| `app/src/main/kotlin/com/jtech/zemer/db/entities/LyricsEntity.kt` | 14 | `com.jtech.zemer.db.entities` | no | 2 | 4 | androidx.room |
| `app/src/main/kotlin/com/jtech/zemer/db/entities/PlayCountEntity.kt` | 16 | `com.jtech.zemer.db.entities` | no | 2 | 5 | androidx.compose, androidx.room |
| `app/src/main/kotlin/com/jtech/zemer/db/entities/Playlist.kt` | 40 | `com.jtech.zemer.db.entities` | no | 4 | 8 | androidx.compose, androidx.room |
| `app/src/main/kotlin/com/jtech/zemer/db/entities/PlaylistEntity.kt` | 58 | `com.jtech.zemer.db.entities` | no | 10 | 20 | androidx.compose, androidx.room, java.time, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/db/entities/PlaylistSong.kt` | 14 | `com.jtech.zemer.db.entities` | no | 2 | 3 | androidx.room |
| `app/src/main/kotlin/com/jtech/zemer/db/entities/PlaylistSongMap.kt` | 31 | `com.jtech.zemer.db.entities` | no | 4 | 6 | androidx.room |
| `app/src/main/kotlin/com/jtech/zemer/db/entities/PlaylistSongMapPreview.kt` | 14 | `com.jtech.zemer.db.entities` | no | 2 | 4 | androidx.room |
| `app/src/main/kotlin/com/jtech/zemer/db/entities/RelatedSongMap.kt` | 29 | `com.jtech.zemer.db.entities` | no | 4 | 4 | androidx.room |
| `app/src/main/kotlin/com/jtech/zemer/db/entities/SearchHistory.kt` | 19 | `com.jtech.zemer.db.entities` | no | 3 | 3 | androidx.room |
| `app/src/main/kotlin/com/jtech/zemer/db/entities/SetVideoIdEntity.kt` | 11 | `com.jtech.zemer.db.entities` | no | 2 | 3 | androidx.room |
| `app/src/main/kotlin/com/jtech/zemer/db/entities/Song.kt` | 54 | `com.jtech.zemer.db.entities` | no | 4 | 9 | androidx.compose, androidx.room |
| `app/src/main/kotlin/com/jtech/zemer/db/entities/SongAlbumMap.kt` | 29 | `com.jtech.zemer.db.entities` | no | 3 | 4 | androidx.room |
| `app/src/main/kotlin/com/jtech/zemer/db/entities/SongArtistMap.kt` | 29 | `com.jtech.zemer.db.entities` | no | 3 | 4 | androidx.room |
| `app/src/main/kotlin/com/jtech/zemer/db/entities/SongEntity.kt` | 82 | `com.jtech.zemer.db.entities` | no | 11 | 28 | androidx.compose, androidx.room, java.time, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/db/entities/SongWithStats.kt` | 12 | `com.jtech.zemer.db.entities` | no | 1 | 6 | androidx.compose |
| `app/src/main/kotlin/com/jtech/zemer/db/entities/SortedSongAlbumMap.kt` | 14 | `com.jtech.zemer.db.entities` | no | 2 | 4 | androidx.room |
| `app/src/main/kotlin/com/jtech/zemer/db/entities/SortedSongArtistMap.kt` | 14 | `com.jtech.zemer.db.entities` | no | 2 | 4 | androidx.room |
| `app/src/main/kotlin/com/jtech/zemer/di/AppModule.kt` | 90 | `com.jtech.zemer.di` | no | 22 | 8 | android.content, androidx.media3, com.google, dagger.Module, dagger.Provides, dagger.hilt, javax.inject, kotlinx.coroutines, timber.log |
| `app/src/main/kotlin/com/jtech/zemer/di/DataStoreQualifiers.kt` | 11 | `com.jtech.zemer.di` | no | 1 | 2 | javax.inject |
| `app/src/main/kotlin/com/jtech/zemer/di/LyricsHelperEntryPoint.kt` | 12 | `com.jtech.zemer.di` | no | 4 | 2 | dagger.hilt |
| `app/src/main/kotlin/com/jtech/zemer/di/NetworkModule.kt` | 21 | `com.jtech.zemer.di` | no | 8 | 2 | android.content, dagger.Module, dagger.Provides, dagger.hilt, javax.inject |
| `app/src/main/kotlin/com/jtech/zemer/di/Qualifiers.kt` | 15 | `com.jtech.zemer.di` | no | 1 | 3 | javax.inject |
| `app/src/main/kotlin/com/jtech/zemer/di/SyncModule.kt` | 121 | `com.jtech.zemer.di` | no | 16 | 9 | android.content, androidx.datastore, com.google, dagger.Module, dagger.Provides, dagger.hilt, javax.inject |
| `app/src/main/kotlin/com/jtech/zemer/extensions/ContextExt.kt` | 122 | `com.jtech.zemer.extensions` | no | 11 | 12 | android.content, android.net, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/extensions/CoroutineExt.kt` | 27 | `com.jtech.zemer.extensions` | no | 5 | 1 | kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/extensions/FileExt.kt` | 13 | `com.jtech.zemer.extensions` | no | 5 | 3 | java.io, java.util |
| `app/src/main/kotlin/com/jtech/zemer/extensions/ListExt.kt` | 54 | `com.jtech.zemer.extensions` | no | 2 | 5 |  |
| `app/src/main/kotlin/com/jtech/zemer/extensions/MediaItemExt.kt` | 70 | `com.jtech.zemer.extensions` | no | 8 | 4 | androidx.core, androidx.media3 |
| `app/src/main/kotlin/com/jtech/zemer/extensions/PlayerExt.kt` | 121 | `com.jtech.zemer.extensions` | no | 10 | 19 | androidx.media3, java.util |
| `app/src/main/kotlin/com/jtech/zemer/extensions/QueueExt.kt` | 112 | `com.jtech.zemer.extensions` | no | 9 | 3 |  |
| `app/src/main/kotlin/com/jtech/zemer/extensions/StringExt.kt` | 23 | `com.jtech.zemer.extensions` | no | 3 | 4 | androidx.sqlite, java.net |
| `app/src/main/kotlin/com/jtech/zemer/extensions/UtilExt.kt` | 8 | `com.jtech.zemer.extensions` | no | 0 | 0 |  |
| `app/src/main/kotlin/com/jtech/zemer/lyrics/LrcLibLyricsProvider.kt` | 32 | `com.jtech.zemer.lyrics` | no | 5 | 5 | android.content |
| `app/src/main/kotlin/com/jtech/zemer/lyrics/LyricsEntry.kt` | 15 | `com.jtech.zemer.lyrics` | no | 1 | 6 | kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/lyrics/LyricsHelper.kt` | 166 | `com.jtech.zemer.lyrics` | no | 15 | 24 | android.content, android.util, dagger.hilt, javax.inject, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/lyrics/LyricsProvider.kt` | 28 | `com.jtech.zemer.lyrics` | no | 1 | 5 | android.content |
| `app/src/main/kotlin/com/jtech/zemer/lyrics/LyricsUtils.kt` | 781 | `com.jtech.zemer.lyrics` | no | 3 | 122 | android.text, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/lyrics/SimpMusicLyricsProvider.kt` | 29 | `com.jtech.zemer.lyrics` | no | 2 | 5 | android.content |
| `app/src/main/kotlin/com/jtech/zemer/lyrics/YouTubeLyricsProvider.kt` | 28 | `com.jtech.zemer.lyrics` | no | 4 | 5 | android.content |
| `app/src/main/kotlin/com/jtech/zemer/lyrics/YouTubeSubtitleLyricsProvider.kt` | 18 | `com.jtech.zemer.lyrics` | no | 2 | 4 | android.content |
| `app/src/main/kotlin/com/jtech/zemer/lyrics/model/LyricsUnavailableException.kt` | 9 | `com.jtech.zemer.lyrics.model` | no | 0 | 2 |  |
| `app/src/main/kotlin/com/jtech/zemer/models/DpadDirection.kt` | 27 | `com.jtech.zemer.models` | no | 9 | 5 | android.view, androidx.annotation, androidx.datastore |
| `app/src/main/kotlin/com/jtech/zemer/models/ItemsPage.kt` | 8 | `com.jtech.zemer.models` | no | 1 | 3 |  |
| `app/src/main/kotlin/com/jtech/zemer/models/MediaMetadata.kt` | 108 | `com.jtech.zemer.models` | no | 7 | 24 | androidx.compose, java.io, java.time |
| `app/src/main/kotlin/com/jtech/zemer/models/PersistPlayerState.kt` | 14 | `com.jtech.zemer.models` | no | 1 | 9 | java.io |
| `app/src/main/kotlin/com/jtech/zemer/models/PersistQueue.kt` | 54 | `com.jtech.zemer.models` | no | 1 | 31 | java.io |
| `app/src/main/kotlin/com/jtech/zemer/playback/AudioOnlyRenderersFactory.kt` | 25 | `com.jtech.zemer.playback` | no | 7 | 2 | android.content, android.os, androidx.media3 |
| `app/src/main/kotlin/com/jtech/zemer/playback/DownloadUtil.kt` | 361 | `com.jtech.zemer.playback` | no | 49 | 51 | android.content, android.net, androidx.core, androidx.media3, dagger.hilt, java.time, java.util, javax.inject, kotlinx.coroutines, okhttp3.OkHttpClient |
| `app/src/main/kotlin/com/jtech/zemer/playback/ExoDownloadService.kt` | 111 | `com.jtech.zemer.playback` | no | 17 | 16 | android.app, android.content, android.graphics, androidx.media3, dagger.hilt, javax.inject |
| `app/src/main/kotlin/com/jtech/zemer/playback/MediaLibrarySessionCallback.kt` | 832 | `com.jtech.zemer.playback` | no | 59 | 82 | android.content, android.net, android.os, androidx.annotation, androidx.core, androidx.media3, com.google, dagger.hilt, javax.inject, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/playback/MediaStoreDownloadManager.kt` | 728 | `com.jtech.zemer.playback` | no | 38 | 93 | android.content, android.net, androidx.core, dagger.hilt, java.io, java.time, javax.inject, kotlin.math, kotlinx.coroutines, okhttp3.OkHttpClient, okhttp3.Request, timber.log |
| `app/src/main/kotlin/com/jtech/zemer/playback/MediaStoreDownloadService.kt` | 307 | `com.jtech.zemer.playback` | no | 27 | 50 | android.app, android.content, android.os, androidx.core, dagger.hilt, javax.inject, kotlin.math, kotlinx.coroutines, timber.log |
| `app/src/main/kotlin/com/jtech/zemer/playback/MusicService.kt` | 1650 | `com.jtech.zemer.playback` | no | 160 | 185 | android.app, android.content, android.media, android.net, android.os, android.widget, androidx.core, androidx.datastore, androidx.media3, dagger.hilt, java.io, java.sql, java.time, java.util, javax.inject, kotlin.time, kotlinx.coroutines, okhttp3.OkHttpClient, timber.log |
| `app/src/main/kotlin/com/jtech/zemer/playback/PlayerConnection.kt` | 207 | `com.jtech.zemer.playback` | no | 25 | 40 | android.content, androidx.media3, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/playback/SleepTimer.kt` | 68 | `com.jtech.zemer.playback` | no | 11 | 11 | androidx.compose, androidx.media3, kotlin.time, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/playback/queues/EmptyQueue.kt` | 14 | `com.jtech.zemer.playback.queues` | no | 2 | 5 | androidx.media3 |
| `app/src/main/kotlin/com/jtech/zemer/playback/queues/ListQueue.kt` | 19 | `com.jtech.zemer.playback.queues` | no | 2 | 9 | androidx.media3 |
| `app/src/main/kotlin/com/jtech/zemer/playback/queues/LocalAlbumRadio.kt` | 65 | `com.jtech.zemer.playback.queues` | no | 11 | 16 | androidx.media3, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/playback/queues/Queue.kt` | 40 | `com.jtech.zemer.playback.queues` | no | 3 | 12 | androidx.media3 |
| `app/src/main/kotlin/com/jtech/zemer/playback/queues/YouTubeAlbumRadio.kt` | 60 | `com.jtech.zemer.playback.queues` | no | 10 | 15 | androidx.media3, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/playback/queues/YouTubeQueue.kt` | 58 | `com.jtech.zemer.playback.queues` | no | 10 | 13 | androidx.media3, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/repositories/CachedSongsRepository.kt` | 96 | `com.jtech.zemer.repositories` | no | 22 | 18 | android.content, androidx.media3, dagger.hilt, java.time, javax.inject, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/sync/ContentFilterSyncService.kt` | 446 | `com.jtech.zemer.sync` | no | 18 | 61 | android.util, javax.inject, kotlin.math, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/sync/UserPreferencesRepository.kt` | 760 | `com.jtech.zemer.sync` | no | 38 | 109 | android.content, android.util, androidx.datastore, com.google, dagger.hilt, java.util, javax.inject, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/sync/models/DevicePreferencesEntity.kt` | 126 | `com.jtech.zemer.sync.models` | no | 3 | 51 | com.google, java.util |
| `app/src/main/kotlin/com/jtech/zemer/sync/models/UserPreferencesEntity.kt` | 92 | `com.jtech.zemer.sync.models` | no | 3 | 22 | com.google, java.util |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/AccountSettingsDialog.kt` | 66 | `com.jtech.zemer.ui.component` | yes | 20 | 1 | androidx.compose, androidx.navigation |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/AnonymousAuthEmailDialog.kt` | 121 | `com.jtech.zemer.ui.component` | yes | 23 | 3 | androidx.compose, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/AppStateViews.kt` | 111 | `com.jtech.zemer.ui.component` | yes | 26 | 1 | androidx.annotation, androidx.compose |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/AutoResizeText.kt` | 97 | `com.jtech.zemer.ui.component` | yes | 20 | 9 | androidx.compose |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/BigSeekBar.kt` | 58 | `com.jtech.zemer.ui.component` | yes | 17 | 2 | androidx.compose |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/BottomSheet.kt` | 348 | `com.jtech.zemer.ui.component` | yes | 47 | 45 | androidx.activity, androidx.compose, kotlin.math, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/BottomSheetMenu.kt` | 86 | `com.jtech.zemer.ui.component` | yes | 23 | 8 | androidx.compose |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/BottomSheetPage.kt` | 166 | `com.jtech.zemer.ui.component` | yes | 47 | 10 | androidx.activity, androidx.compose |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/ChipsRow.kt` | 247 | `com.jtech.zemer.ui.component` | yes | 56 | 9 | android.annotation, androidx.compose |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/CreatePlaylistDialog.kt` | 129 | `com.jtech.zemer.ui.component` | yes | 34 | 8 | android.widget, androidx.compose, java.time, java.util, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/Dialog.kt` | 367 | `com.jtech.zemer.ui.component` | yes | 47 | 8 | androidx.compose, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/DraggableScrollBarOverlay.kt` | 242 | `com.jtech.zemer.ui.component` | yes | 34 | 51 | androidx.compose, kotlin.math, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/EmptyPlaceholder.kt` | 47 | `com.jtech.zemer.ui.component` | yes | 16 | 1 | androidx.annotation, androidx.compose |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/GridMenu.kt` | 197 | `com.jtech.zemer.ui.component` | yes | 34 | 6 | androidx.annotation, androidx.compose, androidx.media3 |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/HideOnScrollFAB.kt` | 117 | `com.jtech.zemer.ui.component` | yes | 21 | 3 | androidx.annotation, androidx.compose |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/IconButton.kt` | 118 | `com.jtech.zemer.ui.component` | yes | 36 | 6 | androidx.annotation, androidx.compose |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/Items.kt` | 1619 | `com.jtech.zemer.ui.component` | yes | 115 | 92 | android.annotation, android.widget, androidx.compose, androidx.media3, coil3.compose, coil3.request, kotlin.math, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/Library.kt` | 410 | `com.jtech.zemer.ui.component` | yes | 33 | 8 | android.annotation, androidx.compose, androidx.navigation, coil3.compose, coil3.request, coil3.size, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/Lyrics.kt` | 1013 | `com.jtech.zemer.ui.component` | yes | 118 | 112 | android.annotation, android.content, android.os, android.widget, androidx.activity, androidx.annotation, androidx.compose, androidx.lifecycle, androidx.palette, coil3.imageLoader, coil3.request, coil3.toBitmap, kotlin.time, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/LyricsImageCard.kt` | 287 | `com.jtech.zemer.ui.component` | yes | 28 | 34 | android.annotation, androidx.compose, coil3.compose, coil3.request |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/Material3SettingsGroup.kt` | 215 | `com.jtech.zemer.ui.component` | yes | 37 | 13 | androidx.compose |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/NavigationTile.kt` | 59 | `com.jtech.zemer.ui.component` | yes | 19 | 1 | androidx.annotation, androidx.compose |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/NavigationTitle.kt` | 77 | `com.jtech.zemer.ui.component` | yes | 22 | 1 | androidx.compose |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/NetworkRequiredDialog.kt` | 98 | `com.jtech.zemer.ui.component` | yes | 23 | 4 | androidx.compose, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/NewMenuComponents.kt` | 322 | `com.jtech.zemer.ui.component` | yes | 41 | 25 | android.annotation, androidx.compose |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/PlayerSlider.kt` | 112 | `com.jtech.zemer.ui.component` | yes | 17 | 19 | androidx.compose |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/PlayingIndicator.kt` | 113 | `com.jtech.zemer.ui.component` | yes | 29 | 3 | androidx.compose, kotlin.random, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/Preference.kt` | 367 | `com.jtech.zemer.ui.component` | yes | 47 | 12 | androidx.compose, kotlin.math |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/SearchBar.kt` | 367 | `com.jtech.zemer.ui.component` | yes | 78 | 30 | androidx.activity, androidx.compose, kotlin.math |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/SortHeader.kt` | 109 | `com.jtech.zemer.ui.component` | yes | 26 | 1 | androidx.compose |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/UpdateDownloadDialog.kt` | 125 | `com.jtech.zemer.ui.component` | yes | 17 | 7 | androidx.compose, java.io |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/WebViewAuthDialog.kt` | 129 | `com.jtech.zemer.ui.component` | yes | 35 | 4 | android.annotation, android.util, android.view, android.webkit, androidx.compose, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/shimmer/ButtonPlaceholder.kt` | 21 | `com.jtech.zemer.ui.component.shimmer` | yes | 9 | 1 | androidx.compose |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/shimmer/GridItemPlaceholder.kt` | 56 | `com.jtech.zemer.ui.component.shimmer` | yes | 17 | 1 | androidx.compose |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/shimmer/ListItemPlaceholder.kt` | 53 | `com.jtech.zemer.ui.component.shimmer` | yes | 18 | 1 | androidx.compose |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/shimmer/ShimmerHost.kt` | 64 | `com.jtech.zemer.ui.component.shimmer` | yes | 17 | 2 | androidx.compose, com.valentinilk |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/shimmer/TextPlaceholder.kt` | 33 | `com.jtech.zemer.ui.component.shimmer` | yes | 15 | 1 | androidx.compose, kotlin.random |
| `app/src/main/kotlin/com/jtech/zemer/ui/menu/AddToPlaylistDialog.kt` | 191 | `com.jtech.zemer.ui.menu` | yes | 35 | 10 | androidx.compose, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/ui/menu/AddToPlaylistDialogOnline.kt` | 283 | `com.jtech.zemer.ui.menu` | yes | 46 | 19 | androidx.compose, java.net, java.nio, kotlinx.coroutines, timber.log |
| `app/src/main/kotlin/com/jtech/zemer/ui/menu/AlbumMenu.kt` | 676 | `com.jtech.zemer.ui.menu` | yes | 84 | 38 | android.annotation, android.content, android.widget, androidx.compose, androidx.navigation, coil3.compose, com.google, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/ui/menu/ArtistMenu.kt` | 364 | `com.jtech.zemer.ui.menu` | yes | 64 | 20 | android.content, android.widget, androidx.compose, coil3.compose, coil3.request, com.google, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/ui/menu/CustomThumbnailMenu.kt` | 70 | `com.jtech.zemer.ui.menu` | yes | 16 | 1 | androidx.compose |
| `app/src/main/kotlin/com/jtech/zemer/ui/menu/ImportPlaylistDialog.kt` | 63 | `com.jtech.zemer.ui.menu` | yes | 18 | 7 | androidx.compose, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/ui/menu/LoadingScreen.kt` | 35 | `com.jtech.zemer.ui.menu` | yes | 8 | 1 | androidx.compose |
| `app/src/main/kotlin/com/jtech/zemer/ui/menu/LyricsMenu.kt` | 378 | `com.jtech.zemer.ui.menu` | yes | 60 | 18 | android.app, android.content, android.widget, androidx.compose, androidx.hilt |
| `app/src/main/kotlin/com/jtech/zemer/ui/menu/PlayerMenu.kt` | 725 | `com.jtech.zemer.ui.menu` | yes | 81 | 35 | android.content, android.media, android.widget, androidx.activity, androidx.annotation, androidx.compose, androidx.media3, androidx.navigation, com.google, kotlin.math, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/ui/menu/PlaylistMenu.kt` | 332 | `com.jtech.zemer.ui.menu` | yes | 62 | 19 | android.content, android.widget, androidx.compose, com.google, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/ui/menu/SelectionSongsMenu.kt` | 902 | `com.jtech.zemer.ui.menu` | yes | 64 | 43 | android.annotation, android.content, android.widget, androidx.compose, androidx.media3, com.google, java.time, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/ui/menu/SongMenu.kt` | 862 | `com.jtech.zemer.ui.menu` | yes | 96 | 51 | android.content, android.widget, androidx.activity, androidx.compose, androidx.hilt, androidx.navigation, coil3.compose, com.google, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/ui/menu/YouTubeAlbumMenu.kt` | 610 | `com.jtech.zemer.ui.menu` | yes | 86 | 27 | android.annotation, android.content, android.widget, androidx.compose, androidx.media3, androidx.navigation, com.google, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/ui/menu/YouTubeArtistMenu.kt` | 319 | `com.jtech.zemer.ui.menu` | yes | 57 | 19 | android.content, android.widget, androidx.compose, com.google, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/ui/menu/YouTubePlaylistMenu.kt` | 561 | `com.jtech.zemer.ui.menu` | yes | 75 | 20 | android.annotation, android.content, androidx.compose, androidx.media3, coil3.compose, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/ui/menu/YouTubeSongMenu.kt` | 750 | `com.jtech.zemer.ui.menu` | yes | 98 | 38 | android.annotation, android.content, android.widget, androidx.compose, androidx.media3, androidx.navigation, coil3.compose, com.google, java.time, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/ui/player/LyricsScreen.kt` | 796 | `com.jtech.zemer.ui.player` | yes | 96 | 35 | android.app, android.content, android.view, androidx.activity, androidx.compose, androidx.media3, androidx.navigation, androidx.palette, coil3.compose, coil3.imageLoader, coil3.request, coil3.toBitmap, dagger.hilt, kotlinx.coroutines, me.saket |
| `app/src/main/kotlin/com/jtech/zemer/ui/player/MiniPlayer.kt` | 900 | `com.jtech.zemer.ui.player` | yes | 85 | 86 | android.annotation, android.content, androidx.compose, androidx.media3, coil3.compose, kotlin.math, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/ui/player/PlaybackError.kt` | 45 | `com.jtech.zemer.ui.player` | yes | 15 | 1 | androidx.compose, androidx.media3 |
| `app/src/main/kotlin/com/jtech/zemer/ui/player/Player.kt` | 1364 | `com.jtech.zemer.ui.player` | yes | 147 | 106 | android.annotation, android.content, android.widget, androidx.compose, androidx.media3, androidx.navigation, androidx.palette, coil3.compose, coil3.imageLoader, coil3.request, coil3.toBitmap, kotlin.math, kotlinx.coroutines, me.saket |
| `app/src/main/kotlin/com/jtech/zemer/ui/player/Queue.kt` | 1131 | `com.jtech.zemer.ui.player` | yes | 117 | 48 | android.annotation, androidx.activity, androidx.compose, androidx.media3, androidx.navigation, kotlin.math, kotlinx.coroutines, sh.calvin |
| `app/src/main/kotlin/com/jtech/zemer/ui/player/Thumbnail.kt` | 472 | `com.jtech.zemer.ui.player` | yes | 74 | 56 | androidx.compose, androidx.media3, coil3.compose, kotlin.math, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/AccountScreen.kt` | 200 | `com.jtech.zemer.ui.screens` | yes | 39 | 8 | androidx.compose, androidx.hilt, androidx.navigation |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/AlbumScreen.kt` | 723 | `com.jtech.zemer.ui.screens` | yes | 98 | 46 | androidx.activity, androidx.compose, androidx.hilt, androidx.media3, androidx.navigation, coil3.compose, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/BrowseScreen.kt` | 143 | `com.jtech.zemer.ui.screens` | yes | 37 | 7 | androidx.compose, androidx.hilt, androidx.navigation |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/ChartsScreen.kt` | 307 | `com.jtech.zemer.ui.screens` | yes | 74 | 16 | androidx.compose, androidx.hilt, androidx.navigation |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/ExploreScreen.kt` | 400 | `com.jtech.zemer.ui.screens` | yes | 75 | 20 | androidx.compose, androidx.hilt, androidx.navigation |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/HistoryScreen.kt` | 517 | `com.jtech.zemer.ui.screens` | yes | 75 | 31 | androidx.activity, androidx.compose, androidx.hilt, androidx.navigation, java.time |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/HomeScreen.kt` | 1037 | `com.jtech.zemer.ui.screens` | yes | 102 | 63 | androidx.compose, androidx.hilt, androidx.navigation, kotlin.math, kotlin.random, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/KidZoneScreen.kt` | 336 | `com.jtech.zemer.ui.screens` | yes | 63 | 19 | androidx.compose, androidx.hilt, androidx.navigation |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/LoginGateScreen.kt` | 253 | `com.jtech.zemer.ui.screens` | yes | 53 | 23 | android.widget, androidx.compose, androidx.navigation, io.ktor, kotlinx.coroutines, kotlinx.serialization |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/LoginScreen.kt` | 227 | `com.jtech.zemer.ui.screens` | yes | 41 | 20 | android.annotation, android.content, android.webkit, android.widget, androidx.activity, androidx.compose, androidx.navigation, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/MoodAndGenresScreen.kt` | 171 | `com.jtech.zemer.ui.screens` | yes | 40 | 8 | android.content, androidx.compose, androidx.hilt, androidx.navigation |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/NavigationBuilder.kt` | 346 | `com.jtech.zemer.ui.screens` | no | 41 | 4 | androidx.compose, androidx.navigation |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/NewReleaseScreen.kt` | 254 | `com.jtech.zemer.ui.screens` | yes | 45 | 12 | androidx.compose, androidx.hilt, androidx.navigation |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/OnboardingScreen.kt` | 2077 | `com.jtech.zemer.ui.screens` | yes | 112 | 113 | android.Manifest, android.annotation, android.content, android.graphics, android.net, android.os, android.provider, androidx.activity, androidx.compose, androidx.core, androidx.datastore, androidx.hilt, androidx.lifecycle, com.airbnb, com.google, dagger.hilt, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/Screens.kt` | 53 | `com.jtech.zemer.ui.screens` | no | 4 | 11 | androidx.annotation, androidx.compose |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/SplashScreen.kt` | 165 | `com.jtech.zemer.ui.screens` | yes | 42 | 5 | android.graphics, androidx.compose, com.airbnb |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/StatsScreen.kt` | 426 | `com.jtech.zemer.ui.screens` | yes | 58 | 34 | androidx.compose, androidx.hilt, androidx.navigation, java.time |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/VideoNavigation.kt` | 12 | `com.jtech.zemer.ui.screens` | no | 1 | 3 | android.net |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/WhitelistedArtistsScreen.kt` | 404 | `com.jtech.zemer.ui.screens` | yes | 82 | 21 | androidx.compose, androidx.hilt, androidx.navigation, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/YouTubeBrowseScreen.kt` | 284 | `com.jtech.zemer.ui.screens` | yes | 68 | 12 | androidx.compose, androidx.hilt, androidx.navigation |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/artist/ArtistAlbumsScreen.kt` | 157 | `com.jtech.zemer.ui.screens.artist` | yes | 51 | 13 | androidx.activity, androidx.compose, androidx.hilt, androidx.navigation |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/artist/ArtistItemsScreen.kt` | 329 | `com.jtech.zemer.ui.screens.artist` | yes | 60 | 19 | androidx.compose, androidx.hilt, androidx.navigation |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/artist/ArtistScreen.kt` | 858 | `com.jtech.zemer.ui.screens.artist` | yes | 121 | 45 | android.content, android.widget, androidx.compose, androidx.hilt, androidx.navigation, coil3.compose, com.valentinilk |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/artist/ArtistSongsScreen.kt` | 211 | `com.jtech.zemer.ui.screens.artist` | yes | 52 | 14 | androidx.compose, androidx.hilt, androidx.navigation |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/library/LibraryAlbumsScreen.kt` | 324 | `com.jtech.zemer.ui.screens.library` | yes | 67 | 24 | androidx.compose, androidx.hilt, androidx.navigation, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/library/LibraryArtistsScreen.kt` | 302 | `com.jtech.zemer.ui.screens.library` | yes | 65 | 18 | androidx.compose, androidx.hilt, androidx.navigation, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/library/LibraryMixScreen.kt` | 797 | `com.jtech.zemer.ui.screens.library` | yes | 96 | 38 | android.widget, androidx.compose, androidx.hilt, androidx.navigation, java.text, java.time, java.util, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/library/LibraryPlaylistsScreen.kt` | 545 | `com.jtech.zemer.ui.screens.library` | yes | 77 | 32 | androidx.compose, androidx.hilt, androidx.navigation, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/library/LibraryScreen.kt` | 87 | `com.jtech.zemer.ui.screens.library` | yes | 16 | 6 | androidx.compose, androidx.navigation |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/library/LibrarySongsScreen.kt` | 356 | `com.jtech.zemer.ui.screens.library` | yes | 68 | 23 | androidx.activity, androidx.compose, androidx.hilt, androidx.navigation |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/library/LibraryVideosScreen.kt` | 156 | `com.jtech.zemer.ui.screens.library` | yes | 42 | 10 | androidx.compose, androidx.hilt, androidx.navigation |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/player/VideoPlayerScreen.kt` | 1114 | `com.jtech.zemer.ui.screens.player` | yes | 114 | 127 | android.app, android.content, android.net, android.os, android.util, android.view, android.widget, androidx.activity, androidx.compose, androidx.lifecycle, androidx.media3, androidx.navigation, io.sanghun, java.io, java.time, java.util, kotlinx.coroutines, okhttp3.OkHttpClient, okhttp3.Request |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/playlist/AutoPlaylistScreen.kt` | 680 | `com.jtech.zemer.ui.screens.playlist` | yes | 104 | 34 | androidx.activity, androidx.compose, androidx.hilt, androidx.media3, androidx.navigation, coil3.compose, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/playlist/CachePlaylistScreen.kt` | 490 | `com.jtech.zemer.ui.screens.playlist` | yes | 86 | 23 | androidx.activity, androidx.compose, androidx.hilt, androidx.navigation, coil3.compose, java.time |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/playlist/DownloadedContentScreen.kt` | 182 | `com.jtech.zemer.ui.screens.playlist` | yes | 41 | 5 | androidx.compose, androidx.hilt, androidx.navigation |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/playlist/DownloadedVideosScreen.kt` | 510 | `com.jtech.zemer.ui.screens.playlist` | yes | 92 | 27 | androidx.activity, androidx.compose, androidx.hilt, androidx.navigation, coil3.compose |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/playlist/LocalPlaylistScreen.kt` | 1503 | `com.jtech.zemer.ui.screens.playlist` | yes | 157 | 89 | android.annotation, android.content, android.graphics, android.net, androidx.activity, androidx.compose, androidx.core, androidx.hilt, androidx.lifecycle, androidx.media3, androidx.navigation, coil3.compose, coil3.request, com.yalantis, io.ktor, java.time, kotlinx.coroutines, sh.calvin |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/playlist/OnlinePlaylistScreen.kt` | 730 | `com.jtech.zemer.ui.screens.playlist` | yes | 111 | 30 | androidx.activity, androidx.compose, androidx.hilt, androidx.navigation, coil3.compose, coil3.request |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/playlist/TopPlaylistScreen.kt` | 631 | `com.jtech.zemer.ui.screens.playlist` | yes | 97 | 28 | androidx.activity, androidx.compose, androidx.hilt, androidx.media3, androidx.navigation, coil3.compose, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/search/OnlineSearchResult.kt` | 441 | `com.jtech.zemer.ui.screens.search` | yes | 76 | 28 | androidx.compose, androidx.hilt, androidx.navigation, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/search/OnlineSearchScreen.kt` | 458 | `com.jtech.zemer.ui.screens.search` | yes | 83 | 19 | androidx.compose, androidx.hilt, androidx.navigation, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/settings/AboutScreen.kt` | 191 | `com.jtech.zemer.ui.screens.settings` | yes | 47 | 4 | androidx.compose, androidx.navigation |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/settings/AccountSettings.kt` | 429 | `com.jtech.zemer.ui.screens.settings` | yes | 78 | 45 | android.content, android.widget, androidx.compose, androidx.hilt, androidx.navigation, coil3.compose, com.google, io.ktor, kotlinx.coroutines, kotlinx.serialization |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/settings/AndroidAutoSettings.kt` | 268 | `com.jtech.zemer.ui.screens.settings` | yes | 50 | 29 | androidx.compose, androidx.lifecycle, androidx.navigation, kotlinx.coroutines, sh.calvin |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/settings/AppearanceSettings.kt` | 1028 | `com.jtech.zemer.ui.screens.settings` | yes | 110 | 93 | android.annotation, android.content, android.os, androidx.compose, androidx.core, androidx.navigation, java.util, kotlin.math, me.saket |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/settings/BackupAndRestore.kt` | 190 | `com.jtech.zemer.ui.screens.settings` | yes | 41 | 16 | androidx.activity, androidx.compose, androidx.hilt, androidx.navigation, java.time, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/settings/ButtonSetupScreen.kt` | 374 | `com.jtech.zemer.ui.screens.settings` | yes | 56 | 17 | android.view, androidx.activity, androidx.compose, androidx.hilt, androidx.navigation |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/settings/ContentSettings.kt` | 681 | `com.jtech.zemer.ui.screens.settings` | yes | 89 | 66 | android.content, android.os, android.provider, androidx.activity, androidx.compose, androidx.core, androidx.hilt, androidx.navigation, com.google, dagger.hilt, java.text, java.util, javax.inject, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/settings/ContributeScreen.kt` | 388 | `com.jtech.zemer.ui.screens.settings` | yes | 64 | 21 | androidx.compose, androidx.credentials, androidx.hilt, androidx.lifecycle, androidx.navigation, coil3.compose, coil3.request, com.google, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/settings/GeneralSettings.kt` | 88 | `com.jtech.zemer.ui.screens.settings` | yes | 29 | 4 | android.content, android.net, android.os, android.provider, androidx.compose, androidx.navigation |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/settings/PlayerSettings.kt` | 246 | `com.jtech.zemer.ui.screens.settings` | yes | 42 | 27 | androidx.compose, androidx.navigation |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/settings/PrivacySettings.kt` | 208 | `com.jtech.zemer.ui.screens.settings` | yes | 39 | 12 | androidx.compose, androidx.navigation |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/settings/SettingsScreen.kt` | 264 | `com.jtech.zemer.ui.screens.settings` | yes | 36 | 21 | android.os, android.widget, androidx.compose, androidx.navigation, com.google |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/settings/StorageSettings.kt` | 440 | `com.jtech.zemer.ui.screens.settings` | yes | 66 | 35 | android.annotation, android.content, android.net, android.provider, androidx.activity, androidx.compose, androidx.navigation, coil3.annotation, coil3.imageLoader, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/settings/StreamSourceSettings.kt` | 218 | `com.jtech.zemer.ui.screens.settings` | yes | 46 | 20 | androidx.compose, androidx.navigation |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/settings/UpdaterSettings.kt` | 326 | `com.jtech.zemer.ui.screens.settings` | yes | 63 | 23 | android.content, androidx.compose, androidx.navigation, java.io, kotlinx.coroutines, rikka.shizuku, timber.log |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/settings/integrations/IntegrationScreen.kt` | 49 | `com.jtech.zemer.ui.screens.settings.integrations` | yes | 17 | 1 | androidx.compose, androidx.navigation |
| `app/src/main/kotlin/com/jtech/zemer/ui/theme/PlayerColorExtractor.kt` | 159 | `com.jtech.zemer.ui.theme` | no | 5 | 42 | androidx.compose, androidx.palette, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/ui/theme/PlayerSliderColors.kt` | 146 | `com.jtech.zemer.ui.theme` | yes | 6 | 12 | androidx.compose |
| `app/src/main/kotlin/com/jtech/zemer/ui/theme/Theme.kt` | 113 | `com.jtech.zemer.ui.theme` | yes | 22 | 18 | android.graphics, android.os, androidx.compose, androidx.palette, com.materialkolor |
| `app/src/main/kotlin/com/jtech/zemer/ui/theme/Type.kt` | 123 | `com.jtech.zemer.ui.theme` | no | 5 | 1 | androidx.compose |
| `app/src/main/kotlin/com/jtech/zemer/ui/utils/AppBar.kt` | 75 | `com.jtech.zemer.ui.utils` | yes | 14 | 10 | androidx.compose |
| `app/src/main/kotlin/com/jtech/zemer/ui/utils/FadingEdge.kt` | 89 | `com.jtech.zemer.ui.utils` | no | 7 | 2 | androidx.compose |
| `app/src/main/kotlin/com/jtech/zemer/ui/utils/ItemWrapper.kt` | 15 | `com.jtech.zemer.ui.utils` | no | 1 | 4 | androidx.compose |
| `app/src/main/kotlin/com/jtech/zemer/ui/utils/KeyUtils.kt` | 50 | `com.jtech.zemer.ui.utils` | no | 1 | 9 | java.util |
| `app/src/main/kotlin/com/jtech/zemer/ui/utils/LazyGridSnapLayoutInfoProvider.kt` | 66 | `com.jtech.zemer.ui.utils` | no | 6 | 14 | androidx.compose |
| `app/src/main/kotlin/com/jtech/zemer/ui/utils/NavControllerUtils.kt` | 14 | `com.jtech.zemer.ui.utils` | no | 2 | 2 | androidx.navigation |
| `app/src/main/kotlin/com/jtech/zemer/ui/utils/ScrollUtils.kt` | 59 | `com.jtech.zemer.ui.utils` | yes | 9 | 8 | androidx.compose |
| `app/src/main/kotlin/com/jtech/zemer/ui/utils/ShapeUtils.kt` | 8 | `com.jtech.zemer.ui.utils` | no | 3 | 1 | androidx.compose |
| `app/src/main/kotlin/com/jtech/zemer/ui/utils/ShowMediaInfo.kt` | 357 | `com.jtech.zemer.ui.utils` | yes | 49 | 16 | android.content, android.text, android.widget, androidx.compose, com.zemer |
| `app/src/main/kotlin/com/jtech/zemer/ui/utils/StringUtils.kt` | 36 | `com.jtech.zemer.ui.utils` | no | 2 | 5 | java.text, kotlin.math |
| `app/src/main/kotlin/com/jtech/zemer/ui/utils/YouTubeUtils.kt` | 23 | `com.jtech.zemer.ui.utils` | no | 0 | 5 |  |
| `app/src/main/kotlin/com/jtech/zemer/utils/AccessibilityUtils.kt` | 91 | `com.jtech.zemer.utils` | yes | 19 | 18 | android.content, android.database, android.net, android.os, android.provider, android.text, androidx.compose, androidx.lifecycle |
| `app/src/main/kotlin/com/jtech/zemer/utils/ButtonInputCapture.kt` | 40 | `com.jtech.zemer.utils` | no | 5 | 10 | android.view, java.util, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/utils/ButtonMapperBridge.kt` | 35 | `com.jtech.zemer.utils` | no | 5 | 8 | android.view, java.util |
| `app/src/main/kotlin/com/jtech/zemer/utils/CoilBitmapLoader.kt` | 54 | `com.jtech.zemer.utils` | no | 15 | 8 | android.content, android.graphics, android.net, androidx.core, androidx.media3, coil3.imageLoader, coil3.request, coil3.toBitmap, com.google, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/utils/ComposeDebugUtils.kt` | 99 | `com.jtech.zemer.utils` | no | 18 | 15 | androidx.compose, kotlin.math, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/utils/ComposeToImage.kt` | 263 | `com.jtech.zemer.utils` | no | 32 | 63 | android.annotation, android.content, android.graphics, android.net, android.os, android.provider, android.text, androidx.core, coil3.imageLoader, coil3.request, coil3.toBitmap, java.io, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/utils/ConnectivityManager.kt` | 75 | `com.jtech.zemer.utils` | no | 10 | 18 | android.content, android.net, android.os, javax.inject, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/utils/ContentFilterConfig.kt` | 108 | `com.jtech.zemer.utils` | no | 0 | 20 |  |
| `app/src/main/kotlin/com/jtech/zemer/utils/CoverArtEmbedder.kt` | 170 | `com.jtech.zemer.utils` | no | 9 | 22 | android.content, android.util, java.io, kotlinx.coroutines, okhttp3.OkHttpClient, okhttp3.Request |
| `app/src/main/kotlin/com/jtech/zemer/utils/CoverArtNative.kt` | 47 | `com.jtech.zemer.utils` | no | 0 | 3 |  |
| `app/src/main/kotlin/com/jtech/zemer/utils/CrashReportingTree.kt` | 35 | `com.jtech.zemer.utils` | no | 2 | 6 | android.util, timber.log |
| `app/src/main/kotlin/com/jtech/zemer/utils/DataStore.kt` | 193 | `com.jtech.zemer.utils` | yes | 21 | 13 | android.content, androidx.compose, androidx.datastore, kotlin.properties, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/utils/DeviceIdGenerator.kt` | 198 | `com.jtech.zemer.utils` | no | 15 | 30 | android.content, android.os, android.provider, android.util, androidx.datastore, dagger.hilt, java.util, javax.inject, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/utils/EnvironmentPaths.kt` | 25 | `com.jtech.zemer.utils` | no | 4 | 6 | android.net, android.os, android.provider, java.io |
| `app/src/main/kotlin/com/jtech/zemer/utils/FirestoreUtils.kt` | 41 | `com.jtech.zemer.utils` | no | 0 | 2 |  |
| `app/src/main/kotlin/com/jtech/zemer/utils/FutureUtils.kt` | 43 | `com.jtech.zemer.utils` | no | 8 | 3 | androidx.concurrent, com.google, java.util, kotlin.coroutines, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/utils/IsraeliArtistRegistry.kt` | 51 | `com.jtech.zemer.utils` | no | 5 | 7 | com.google, kotlinx.coroutines, timber.log |
| `app/src/main/kotlin/com/jtech/zemer/utils/MediaStoreHelper.kt` | 669 | `com.jtech.zemer.utils` | no | 16 | 82 | android.content, android.net, android.os, android.provider, androidx.documentfile, java.io, kotlinx.coroutines, timber.log |
| `app/src/main/kotlin/com/jtech/zemer/utils/NetworkConnectivityObserver.kt` | 74 | `com.jtech.zemer.utils` | no | 7 | 15 | android.content, android.net, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/utils/NetworkUtils.kt` | 20 | `com.jtech.zemer.utils` | no | 4 | 4 | android.content, android.net, androidx.core |
| `app/src/main/kotlin/com/jtech/zemer/utils/NotificationUtils.kt` | 35 | `com.jtech.zemer.utils` | no | 6 | 2 | android.Manifest, android.content, android.os, androidx.core |
| `app/src/main/kotlin/com/jtech/zemer/utils/PermissionHelper.kt` | 247 | `com.jtech.zemer.utils` | no | 10 | 19 | android.Manifest, android.app, android.content, android.os, androidx.activity, androidx.core, timber.log |
| `app/src/main/kotlin/com/jtech/zemer/utils/StringUtils.kt` | 31 | `com.jtech.zemer.utils` | no | 2 | 8 | java.math, java.security |
| `app/src/main/kotlin/com/jtech/zemer/utils/SyncUtils.kt` | 724 | `com.jtech.zemer.utils` | no | 33 | 103 | android.content, android.util, androidx.datastore, dagger.hilt, java.time, javax.inject, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/utils/UpdateChecker.kt` | 155 | `com.jtech.zemer.utils` | no | 18 | 53 | android.content, android.net, io.ktor, java.io, kotlinx.coroutines, kotlinx.serialization |
| `app/src/main/kotlin/com/jtech/zemer/utils/Updater.kt` | 55 | `com.jtech.zemer.utils` | no | 5 | 21 | io.ktor, org.json |
| `app/src/main/kotlin/com/jtech/zemer/utils/UrlValidator.kt` | 109 | `com.jtech.zemer.utils` | no | 2 | 12 | okhttp3.HttpUrl |
| `app/src/main/kotlin/com/jtech/zemer/utils/Utils.kt` | 30 | `com.jtech.zemer.utils` | no | 4 | 3 | android.content, java.util, timber.log |
| `app/src/main/kotlin/com/jtech/zemer/utils/VideoLinkBuilder.kt` | 10 | `com.jtech.zemer.utils` | no | 0 | 3 |  |
| `app/src/main/kotlin/com/jtech/zemer/utils/WhitelistCache.kt` | 40 | `com.jtech.zemer.utils` | no | 2 | 10 | java.util |
| `app/src/main/kotlin/com/jtech/zemer/utils/WhitelistFetcher.kt` | 72 | `com.jtech.zemer.utils` | no | 6 | 21 | com.google, java.time, kotlinx.coroutines, timber.log |
| `app/src/main/kotlin/com/jtech/zemer/utils/WhitelistFilter.kt` | 262 | `com.jtech.zemer.utils` | no | 9 | 38 | java.util, timber.log |
| `app/src/main/kotlin/com/jtech/zemer/utils/YTPlayerUtils.kt` | 562 | `com.jtech.zemer.utils` | no | 34 | 69 | android.net, androidx.core, androidx.media3, com.zemer, kotlinx.coroutines, okhttp3.OkHttpClient, timber.log |
| `app/src/main/kotlin/com/jtech/zemer/utils/ZemerLinkBuilder.kt` | 16 | `com.jtech.zemer.utils` | no | 0 | 6 |  |
| `app/src/main/kotlin/com/jtech/zemer/utils/sabr/EjsNTransformSolver.kt` | 307 | `com.jtech.zemer.utils.sabr` | no | 17 | 37 | android.content, android.net, android.webkit, com.zemer, java.io, kotlin.coroutines, kotlinx.coroutines, timber.log |
| `app/src/main/kotlin/com/jtech/zemer/utils/sabr/SabrException.kt` | 3 | `com.jtech.zemer.utils.sabr` | no | 0 | 1 |  |
| `app/src/main/kotlin/com/jtech/zemer/utils/updater/ApkInstallController.kt` | 102 | `com.jtech.zemer.utils.updater` | yes | 15 | 15 | androidx.activity, androidx.compose, java.io, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/utils/updater/AppInstaller.kt` | 267 | `com.jtech.zemer.utils.updater` | no | 28 | 41 | android.app, android.content, android.net, android.os, android.provider, androidx.core, com.topjohnwu, dev.rikka, java.io, kotlinx.coroutines, org.lsposed, rikka.shizuku, timber.log |
| `app/src/main/kotlin/com/jtech/zemer/utils/updater/AppRestarter.kt` | 30 | `com.jtech.zemer.utils.updater` | no | 1 | 4 | android.content |
| `app/src/main/kotlin/com/jtech/zemer/utils/updater/InstallReceiver.kt` | 70 | `com.jtech.zemer.utils.updater` | no | 10 | 7 | android.content, android.os, android.widget, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/utils/updater/Installer.kt` | 24 | `com.jtech.zemer.utils.updater` | no | 2 | 4 | androidx.annotation |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/AccountSettingsViewModel.kt` | 42 | `com.jtech.zemer.viewmodels` | no | 9 | 4 | android.content, androidx.lifecycle, dagger.hilt, javax.inject, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/AccountViewModel.kt` | 81 | `com.jtech.zemer.viewmodels` | no | 15 | 11 | androidx.lifecycle, dagger.hilt, javax.inject, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/AlbumViewModel.kt` | 58 | `com.jtech.zemer.viewmodels` | no | 14 | 5 | androidx.lifecycle, dagger.hilt, javax.inject, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/ArtistAlbumsViewModel.kt` | 25 | `com.jtech.zemer.viewmodels` | no | 8 | 4 | androidx.lifecycle, dagger.hilt, javax.inject, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/ArtistItemsViewModel.kt` | 98 | `com.jtech.zemer.viewmodels` | no | 20 | 12 | android.content, androidx.lifecycle, dagger.hilt, javax.inject, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/ArtistViewModel.kt` | 134 | `com.jtech.zemer.viewmodels` | no | 29 | 19 | android.content, androidx.compose, androidx.lifecycle, dagger.hilt, javax.inject, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/AutoPlaylistViewModel.kt` | 76 | `com.jtech.zemer.viewmodels` | no | 23 | 9 | android.content, androidx.lifecycle, dagger.hilt, javax.inject, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/BackupRestoreViewModel.kt` | 197 | `com.jtech.zemer.viewmodels` | no | 29 | 22 | android.content, android.net, android.widget, androidx.lifecycle, dagger.hilt, java.io, java.util, javax.inject, kotlin.system, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/BrowseViewModel.kt` | 55 | `com.jtech.zemer.viewmodels` | no | 22 | 7 | android.content, androidx.lifecycle, dagger.hilt, javax.inject, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/ButtonSetupViewModel.kt` | 159 | `com.jtech.zemer.viewmodels` | no | 22 | 29 | android.content, android.view, androidx.datastore, androidx.lifecycle, dagger.hilt, javax.inject, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/CachePlaylistViewModel.kt` | 26 | `com.jtech.zemer.viewmodels` | no | 7 | 4 | androidx.lifecycle, dagger.hilt, javax.inject, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/ChartsViewModel.kt` | 87 | `com.jtech.zemer.viewmodels` | no | 17 | 14 | android.content, androidx.lifecycle, dagger.hilt, javax.inject, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/ContributeViewModel.kt` | 296 | `com.jtech.zemer.viewmodels` | no | 17 | 61 | androidx.lifecycle, com.google, dagger.hilt, javax.inject, kotlin.random, kotlinx.coroutines, timber.log |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/DownloadedContentViewModel.kt` | 24 | `com.jtech.zemer.viewmodels` | no | 8 | 3 | androidx.lifecycle, dagger.hilt, javax.inject, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/DownloadedVideosViewModel.kt` | 46 | `com.jtech.zemer.viewmodels` | no | 20 | 5 | android.content, androidx.lifecycle, dagger.hilt, javax.inject, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/ExploreViewModel.kt` | 78 | `com.jtech.zemer.viewmodels` | no | 19 | 10 | android.content, androidx.lifecycle, dagger.hilt, javax.inject, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/HistoryViewModel.kt` | 108 | `com.jtech.zemer.viewmodels` | no | 20 | 22 | androidx.lifecycle, dagger.hilt, java.time, javax.inject, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/HomeViewModel.kt` | 1537 | `com.jtech.zemer.viewmodels` | no | 55 | 328 | android.content, androidx.datastore, androidx.lifecycle, com.google, dagger.hilt, javax.inject, kotlin.random, kotlinx.coroutines, timber.log |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/KidZoneViewModel.kt` | 73 | `com.jtech.zemer.viewmodels` | no | 14 | 13 | androidx.lifecycle, dagger.hilt, javax.inject, kotlinx.coroutines, timber.log |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/LibraryVideosViewModel.kt` | 47 | `com.jtech.zemer.viewmodels` | no | 17 | 9 | android.content, androidx.lifecycle, dagger.hilt, javax.inject, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/LibraryViewModels.kt` | 462 | `com.jtech.zemer.viewmodels` | no | 61 | 64 | android.content, androidx.compose, androidx.lifecycle, dagger.hilt, java.time, javax.inject, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/LocalPlaylistViewModel.kt` | 93 | `com.jtech.zemer.viewmodels` | no | 25 | 7 | android.content, androidx.lifecycle, dagger.hilt, java.text, java.util, javax.inject, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/LocalSearchViewModel.kt` | 93 | `com.jtech.zemer.viewmodels` | no | 18 | 10 | androidx.lifecycle, dagger.hilt, javax.inject, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/LyricsMenuViewModel.kt` | 100 | `com.jtech.zemer.viewmodels` | no | 22 | 16 | android.content, androidx.compose, androidx.lifecycle, dagger.hilt, javax.inject, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/MoodAndGenresViewModel.kt` | 44 | `com.jtech.zemer.viewmodels` | no | 9 | 6 | androidx.lifecycle, dagger.hilt, javax.inject, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/NewReleaseViewModel.kt` | 110 | `com.jtech.zemer.viewmodels` | no | 20 | 19 | android.content, androidx.lifecycle, dagger.hilt, javax.inject, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/OnboardingViewModel.kt` | 205 | `com.jtech.zemer.viewmodels` | no | 19 | 31 | android.content, androidx.lifecycle, dagger.hilt, javax.inject, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/OnlinePlaylistViewModel.kt` | 167 | `com.jtech.zemer.viewmodels` | no | 25 | 29 | android.content, androidx.lifecycle, dagger.hilt, javax.inject, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/OnlineSearchSuggestionViewModel.kt` | 129 | `com.jtech.zemer.viewmodels` | no | 23 | 18 | android.content, androidx.lifecycle, dagger.hilt, javax.inject, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/OnlineSearchViewModel.kt` | 286 | `com.jtech.zemer.viewmodels` | no | 33 | 36 | android.content, android.net, androidx.compose, androidx.lifecycle, dagger.hilt, javax.inject, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/StatsViewModel.kt` | 175 | `com.jtech.zemer.viewmodels` | no | 20 | 9 | androidx.lifecycle, dagger.hilt, java.time, javax.inject, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/TopPlaylistViewModel.kt` | 38 | `com.jtech.zemer.viewmodels` | no | 14 | 4 | android.content, androidx.lifecycle, dagger.hilt, javax.inject, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/WhitelistedArtistsViewModel.kt` | 82 | `com.jtech.zemer.viewmodels` | no | 16 | 15 | androidx.lifecycle, dagger.hilt, javax.inject, kotlinx.coroutines, timber.log |
| `app/src/main/kotlin/com/jtech/zemer/viewmodels/YouTubeBrowseViewModel.kt` | 52 | `com.jtech.zemer.viewmodels` | no | 17 | 7 | android.content, androidx.lifecycle, dagger.hilt, javax.inject, kotlinx.coroutines |
| `app/src/main/kotlin/com/jtech/zemer/widget/MusicWidget.kt` | 426 | `com.jtech.zemer.widget` | yes | 56 | 44 | android.content, android.graphics, androidx.compose, androidx.datastore, androidx.glance, coil3.ImageLoader, coil3.request, coil3.toBitmap, kotlinx.coroutines |
| `app/src/test/kotlin/com/jtech/zemer/utils/CrashReportingTreeTest.kt` | 64 | `com.jtech.zemer.utils` | no | 6 | 6 | org.junit, timber.log |
| `app/src/test/kotlin/com/jtech/zemer/utils/updater/InstallerTest.kt` | 43 | `com.jtech.zemer.utils.updater` | no | 3 | 1 | org.junit |

## `innertube` Kotlin files (96)

| File | Lines | Package | Compose | Imports | Decls | External import roots |
| --- | ---: | --- | --- | ---: | ---: | --- |
| `innertube/src/main/kotlin/com/metrolist/innertube/InnerTube.kt` | 705 | `com.metrolist.innertube` | no | 49 | 49 | io.ktor, java.net, java.util, kotlinx.serialization, okhttp3.ConnectionPool, okhttp3.Dispatcher |
| `innertube/src/main/kotlin/com/metrolist/innertube/YouTube.kt` | 1246 | `com.metrolist.innertube` | no | 65 | 191 | io.ktor, java.net, kotlin.random, kotlinx.coroutines, kotlinx.serialization |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/AccountInfo.kt` | 8 | `com.metrolist.innertube.models` | no | 0 | 5 |  |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/AutomixPreviewVideoRenderer.kt` | 18 | `com.metrolist.innertube.models` | no | 1 | 6 | kotlinx.serialization |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/Badges.kt` | 13 | `com.metrolist.innertube.models` | no | 1 | 4 | kotlinx.serialization |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/Button.kt` | 16 | `com.metrolist.innertube.models` | no | 1 | 7 | kotlinx.serialization |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/Context.kt` | 60 | `com.metrolist.innertube.models` | no | 1 | 27 | kotlinx.serialization |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/Continuation.kt` | 20 | `com.metrolist.innertube.models` | no | 3 | 5 | kotlinx.serialization |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/ContinuationItemRenderer.kt` | 18 | `com.metrolist.innertube.models` | no | 1 | 6 | kotlinx.serialization |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/Endpoint.kt` | 118 | `com.metrolist.innertube.models` | no | 5 | 55 | kotlinx.serialization |
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
| `innertube/src/main/kotlin/com/metrolist/innertube/models/MusicResponsiveListItemRenderer.kt` | 98 | `com.metrolist.innertube.models` | no | 8 | 30 | kotlinx.serialization |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/MusicShelfRenderer.kt` | 28 | `com.metrolist.innertube.models` | no | 1 | 11 | kotlinx.serialization |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/MusicTwoRowItemRenderer.kt` | 52 | `com.metrolist.innertube.models` | no | 5 | 12 | kotlinx.serialization |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/NavigationEndpoint.kt` | 27 | `com.metrolist.innertube.models` | no | 1 | 10 | kotlinx.serialization |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/PlaylistDeleteBody.kt` | 10 | `com.metrolist.innertube.models.body` | no | 2 | 3 | kotlinx.serialization |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/PlaylistPanelRenderer.kt` | 21 | `com.metrolist.innertube.models` | no | 1 | 12 | kotlinx.serialization |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/PlaylistPanelVideoRenderer.kt` | 19 | `com.metrolist.innertube.models` | no | 1 | 13 | kotlinx.serialization |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/ResponseContext.kt` | 21 | `com.metrolist.innertube.models` | no | 1 | 9 | kotlinx.serialization |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/ReturnYouTubeDislikeResponse.kt` | 14 | `com.metrolist.innertube.models` | no | 1 | 8 | kotlinx.serialization |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/Runs.kt` | 43 | `com.metrolist.innertube.models` | no | 1 | 10 | kotlinx.serialization |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/SearchSuggestions.kt` | 6 | `com.metrolist.innertube.models` | no | 0 | 3 |  |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/SearchSuggestionsSectionRenderer.kt` | 20 | `com.metrolist.innertube.models` | no | 1 | 8 | kotlinx.serialization |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/SectionListRenderer.kt` | 73 | `com.metrolist.innertube.models` | no | 3 | 35 | kotlinx.serialization |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/SubscriptionButton.kt` | 14 | `com.metrolist.innertube.models` | no | 1 | 5 | kotlinx.serialization |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/Tabs.kt` | 26 | `com.metrolist.innertube.models` | no | 1 | 11 | kotlinx.serialization |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/ThumbnailRenderer.kt` | 29 | `com.metrolist.innertube.models` | no | 3 | 12 | kotlinx.serialization |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/Thumbnails.kt` | 15 | `com.metrolist.innertube.models` | no | 1 | 6 | kotlinx.serialization |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/TwoColumnBrowseResultsRenderer.kt` | 26 | `com.metrolist.innertube.models` | no | 1 | 11 | kotlinx.serialization |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/YTItem.kt` | 92 | `com.metrolist.innertube.models` | no | 0 | 60 |  |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/YouTubeClient.kt` | 261 | `com.metrolist.innertube.models` | no | 1 | 38 | kotlinx.serialization |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/YouTubeDataPage.kt` | 183 | `com.metrolist.innertube.models` | no | 2 | 63 | kotlinx.serialization |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/YouTubeLocale.kt` | 9 | `com.metrolist.innertube.models` | no | 1 | 3 | kotlinx.serialization |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/body/AccountMenuBody.kt` | 11 | `com.metrolist.innertube.models.body` | no | 2 | 4 | kotlinx.serialization |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/body/BrowseBody.kt` | 13 | `com.metrolist.innertube.models.body` | no | 3 | 5 | kotlinx.serialization |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/body/CreatePlaylistBody.kt` | 18 | `com.metrolist.innertube.models.body` | no | 2 | 9 | kotlinx.serialization |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/body/EditPlaylistBody.kt` | 79 | `com.metrolist.innertube.models.body` | no | 2 | 37 | kotlinx.serialization |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/body/FeedbackBody.kt` | 12 | `com.metrolist.innertube.models.body` | no | 2 | 5 | kotlinx.serialization |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/body/GetQueueBody.kt` | 11 | `com.metrolist.innertube.models.body` | no | 2 | 4 | kotlinx.serialization |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/body/GetSearchSuggestionsBody.kt` | 10 | `com.metrolist.innertube.models.body` | no | 2 | 3 | kotlinx.serialization |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/body/GetTranscriptBody.kt` | 10 | `com.metrolist.innertube.models.body` | no | 2 | 3 | kotlinx.serialization |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/body/LikeBody.kt` | 18 | `com.metrolist.innertube.models.body` | no | 2 | 8 | kotlinx.serialization |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/body/NextBody.kt` | 15 | `com.metrolist.innertube.models.body` | no | 2 | 8 | kotlinx.serialization |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/body/PlayerBody.kt` | 30 | `com.metrolist.innertube.models.body` | no | 2 | 14 | kotlinx.serialization |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/body/SearchBody.kt` | 11 | `com.metrolist.innertube.models.body` | no | 2 | 4 | kotlinx.serialization |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/body/SubscribeBody.kt` | 10 | `com.metrolist.innertube.models.body` | no | 2 | 3 | kotlinx.serialization |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/response/AccountMenuResponse.kt` | 53 | `com.metrolist.innertube.models.response` | no | 5 | 18 | kotlinx.serialization |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/response/AddItemYouTubePlaylistResponse.kt` | 20 | `com.metrolist.innertube.models.response` | no | 1 | 8 | kotlinx.serialization |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/response/BrowseResponse.kt` | 142 | `com.metrolist.innertube.models.response` | no | 14 | 72 | kotlinx.serialization |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/response/ContinuationResponse.kt` | 20 | `com.metrolist.innertube.models.response` | no | 2 | 6 | kotlinx.serialization |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/response/CreatePlaylistResponse.kt` | 8 | `com.metrolist.innertube.models.response` | no | 1 | 2 | kotlinx.serialization |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/response/EditPlaylistResponse.kt` | 8 | `com.metrolist.innertube.models.response` | no | 1 | 2 | kotlinx.serialization |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/response/FeedbackResponse.kt` | 13 | `com.metrolist.innertube.models.response` | no | 1 | 4 | kotlinx.serialization |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/response/GetQueueResponse.kt` | 14 | `com.metrolist.innertube.models.response` | no | 2 | 4 | kotlinx.serialization |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/response/GetSearchSuggestionsResponse.kt` | 14 | `com.metrolist.innertube.models.response` | no | 2 | 4 | kotlinx.serialization |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/response/GetTranscriptResponse.kt` | 65 | `com.metrolist.innertube.models.response` | no | 1 | 26 | kotlinx.serialization |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/response/ImageUploadResponse.kt` | 8 | `com.metrolist.innertube.models.response` | no | 1 | 2 | kotlinx.serialization |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/response/NextResponse.kt` | 40 | `com.metrolist.innertube.models.response` | no | 5 | 15 | kotlinx.serialization |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/response/PlayerResponse.kt` | 117 | `com.metrolist.innertube.models.response` | no | 4 | 64 | kotlinx.serialization |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/response/SearchResponse.kt` | 33 | `com.metrolist.innertube.models.response` | no | 4 | 12 | kotlinx.serialization |
| `innertube/src/main/kotlin/com/metrolist/innertube/pages/AlbumPage.kt` | 127 | `com.metrolist.innertube.pages` | no | 11 | 21 |  |
| `innertube/src/main/kotlin/com/metrolist/innertube/pages/ArtistItemsContinuationPage.kt` | 8 | `com.metrolist.innertube.pages` | no | 1 | 3 |  |
| `innertube/src/main/kotlin/com/metrolist/innertube/pages/ArtistItemsPage.kt` | 122 | `com.metrolist.innertube.pages` | no | 11 | 6 |  |
| `innertube/src/main/kotlin/com/metrolist/innertube/pages/ArtistPage.kt` | 187 | `com.metrolist.innertube.pages` | no | 16 | 13 |  |
| `innertube/src/main/kotlin/com/metrolist/innertube/pages/BrowseResult.kt` | 31 | `com.metrolist.innertube.pages` | no | 2 | 7 |  |
| `innertube/src/main/kotlin/com/metrolist/innertube/pages/ChartsPage.kt` | 18 | `com.metrolist.innertube.pages` | no | 1 | 8 |  |
| `innertube/src/main/kotlin/com/metrolist/innertube/pages/ExplorePage.kt` | 8 | `com.metrolist.innertube.pages` | no | 1 | 3 |  |
| `innertube/src/main/kotlin/com/metrolist/innertube/pages/HistoryPage.kt` | 68 | `com.metrolist.innertube.pages` | no | 8 | 7 |  |
| `innertube/src/main/kotlin/com/metrolist/innertube/pages/HomePage.kt` | 166 | `com.metrolist.innertube.pages` | no | 13 | 23 |  |
| `innertube/src/main/kotlin/com/metrolist/innertube/pages/LibraryAlbumsPage.kt` | 36 | `com.metrolist.innertube.pages` | no | 11 | 4 |  |
| `innertube/src/main/kotlin/com/metrolist/innertube/pages/LibraryContinuationPage.kt` | 8 | `com.metrolist.innertube.pages` | no | 1 | 3 |  |
| `innertube/src/main/kotlin/com/metrolist/innertube/pages/LibraryPage.kt` | 148 | `com.metrolist.innertube.pages` | no | 12 | 7 |  |
| `innertube/src/main/kotlin/com/metrolist/innertube/pages/MoodAndGenres.kt` | 47 | `com.metrolist.innertube.pages` | no | 4 | 9 |  |
| `innertube/src/main/kotlin/com/metrolist/innertube/pages/NewPipe.kt` | 133 | `com.metrolist.innertube` | no | 16 | 23 | io.ktor, java.io, java.net, okhttp3.OkHttpClient, okhttp3.RequestBody, org.schabi |
| `innertube/src/main/kotlin/com/metrolist/innertube/pages/NewReleaseAlbumPage.kt` | 44 | `com.metrolist.innertube.pages` | no | 5 | 2 |  |
| `innertube/src/main/kotlin/com/metrolist/innertube/pages/NextPage.kt` | 76 | `com.metrolist.innertube.pages` | no | 9 | 11 |  |
| `innertube/src/main/kotlin/com/metrolist/innertube/pages/PageHelper.kt` | 38 | `com.metrolist.innertube.pages` | no | 3 | 8 |  |
| `innertube/src/main/kotlin/com/metrolist/innertube/pages/PlaylistContinuationPage.kt` | 8 | `com.metrolist.innertube.pages` | no | 1 | 3 |  |
| `innertube/src/main/kotlin/com/metrolist/innertube/pages/PlaylistPage.kt` | 52 | `com.metrolist.innertube.pages` | no | 7 | 6 |  |
| `innertube/src/main/kotlin/com/metrolist/innertube/pages/RelatedPage.kt` | 163 | `com.metrolist.innertube.pages` | no | 10 | 7 |  |
| `innertube/src/main/kotlin/com/metrolist/innertube/pages/SearchPage.kt` | 210 | `com.metrolist.innertube.pages` | no | 11 | 6 |  |
| `innertube/src/main/kotlin/com/metrolist/innertube/pages/SearchSuggestionPage.kt` | 146 | `com.metrolist.innertube.pages` | no | 9 | 3 |  |
| `innertube/src/main/kotlin/com/metrolist/innertube/pages/SearchSummaryPage.kt` | 377 | `com.metrolist.innertube.pages` | no | 17 | 12 |  |
| `innertube/src/main/kotlin/com/metrolist/innertube/utils/ResilientDns.kt` | 84 | `com.metrolist.innertube.utils` | no | 5 | 10 | java.net, okhttp3.Dns, okhttp3.HttpUrl, okhttp3.OkHttpClient, okhttp3.dnsoverhttps |
| `innertube/src/main/kotlin/com/metrolist/innertube/utils/Utils.kt` | 95 | `com.metrolist.innertube.utils` | no | 4 | 23 | java.security |

## `lrclib` Kotlin files (2)

| File | Lines | Package | Compose | Imports | Decls | External import roots |
| --- | ---: | --- | --- | ---: | ---: | --- |
| `lrclib/src/main/kotlin/com/metrolist/lrclib/LrcLib.kt` | 293 | `com.metrolist.lrclib` | no | 15 | 42 | io.ktor, kotlin.math, kotlinx.coroutines, kotlinx.serialization |
| `lrclib/src/main/kotlin/com/metrolist/lrclib/models/Track.kt` | 137 | `com.metrolist.lrclib.models` | no | 2 | 29 | kotlin.math, kotlinx.serialization |

## `simpmusic` Kotlin files (2)

| File | Lines | Package | Compose | Imports | Decls | External import roots |
| --- | ---: | --- | --- | ---: | ---: | --- |
| `simpmusic/src/main/kotlin/com/metrolist/simpmusic/SimpMusicLyrics.kt` | 119 | `com.metrolist.simpmusic` | no | 15 | 15 | io.ktor, kotlin.math, kotlinx.serialization |
| `simpmusic/src/main/kotlin/com/metrolist/simpmusic/models/LyricsResponse.kt` | 32 | `com.metrolist.simpmusic.models` | no | 2 | 15 | kotlinx.serialization |
