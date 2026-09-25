# App module documentation

## Module facts from Gradle and tracked files

| Fact | Value |
| --- | --- |
| Gradle module | `:app` |
| Namespace / application ID | `com.jtech.zemer` / `com.jtech.zemer` |
| Compile / min / target SDK | `36` / `26` / `36` |
| Version code/name | `38` / `38` |
| Kotlin/JVM target | JVM 21 |
| Compose | Enabled |
| BuildConfig fields visible in Gradle | `ARCHITECTURE`, `COMMIT_HASH`, `RUN_NUMBER`, `GOOGLE_TOKEN_EXCHANGE_URL`, `CONTENT_MIRROR_URL`, `ZEMER_LYRICS_BASE_URL` |
| Room schema directory | `app/schemas` |
| Native build | None (no `app/src/main/cpp`, no `externalNativeBuild`) |
| Tracked app paths | `1078` |
| Tracked app Kotlin files | `799` (`608` in `src/main`, `190` in `src/test`, `1` in `src/androidTest`) |
| Tracked app resource paths | `210` |
| Tracked app asset paths | `0` in `src/main` (`3` test fixtures under `src/androidTest/assets`) |
| Tracked app Room schema files | `36` |

## Android components from manifest

| Type | `android:name` | `android:exported` |
| --- | --- | --- |
| `activity` | `.MainActivity` | `true` |
| `activity` | `com.yalantis.ucrop.UCropActivity` | `false` |
| `activity` | `.ResumePlaybackActivity` | `true` |
| `activity` | `.ui.screens.recognition.RecognizeMusicDialogActivity` | `false` |
| `service` | `.playback.MusicService` | `true` |
| `service` | `.playback.MediaStoreDownloadService` | `false` |
| `service` | `.accessibility.ButtonMapperAccessibilityService` | `false` |
| `provider` | `androidx.core.content.FileProvider` | `false` |
| `provider` | `rikka.shizuku.ShizukuProvider` | `true` |
| `provider` | `com.dpi.DensityScaler` | `false` |
| `receiver` | `.utils.updater.InstallReceiver` | `false` |
| `receiver` | `androidx.media3.session.MediaButtonReceiver` | `true` |
| `receiver` | `.widget.MusicWidgetReceiver` | `true` |

## Kotlin package inventory

| Package | File count |
| --- | ---: |
| `com.dpi` | 5 |
| `com.jtech.zemer` | 3 |
| `com.jtech.zemer.accessibility` | 1 |
| `com.jtech.zemer.auth` | 3 |
| `com.jtech.zemer.constants` | 6 |
| `com.jtech.zemer.db` | 4 |
| `com.jtech.zemer.db.entities` | 32 |
| `com.jtech.zemer.di` | 7 |
| `com.jtech.zemer.extensions` | 10 |
| `com.jtech.zemer.latestreleases` | 8 |
| `com.jtech.zemer.lyrics` | 13 |
| `com.jtech.zemer.lyrics.lrclib` | 3 |
| `com.jtech.zemer.lyrics.simpmusic` | 3 |
| `com.jtech.zemer.lyrics.youtube` | 2 |
| `com.jtech.zemer.lyrics.zemer` | 14 |
| `com.jtech.zemer.models` | 5 |
| `com.jtech.zemer.offline` | 16 |
| `com.jtech.zemer.playback` | 50 |
| `com.jtech.zemer.playback.queues` | 7 |
| `com.jtech.zemer.playback.relay` | 4 |
| `com.jtech.zemer.playback.sabr` | 14 |
| `com.jtech.zemer.recognition` | 9 |
| `com.jtech.zemer.recognition.shazam` | 2 |
| `com.jtech.zemer.repositories` | 1 |
| `com.jtech.zemer.search` | 10 |
| `com.jtech.zemer.statuses` | 14 |
| `com.jtech.zemer.sync` | 4 |
| `com.jtech.zemer.sync.models` | 1 |
| `com.jtech.zemer.tracking` | 10 |
| `com.jtech.zemer.ui.component` | 82 |
| `com.jtech.zemer.ui.component.lyrics` | 1 |
| `com.jtech.zemer.ui.component.shimmer` | 6 |
| `com.jtech.zemer.ui.menu` | 23 |
| `com.jtech.zemer.ui.player` | 18 |
| `com.jtech.zemer.ui.screens` | 29 |
| `com.jtech.zemer.ui.screens.artist` | 2 |
| `com.jtech.zemer.ui.screens.library` | 8 |
| `com.jtech.zemer.ui.screens.onboarding` | 8 |
| `com.jtech.zemer.ui.screens.playlist` | 10 |
| `com.jtech.zemer.ui.screens.podcast` | 1 |
| `com.jtech.zemer.ui.screens.recognition` | 2 |
| `com.jtech.zemer.ui.screens.search` | 3 |
| `com.jtech.zemer.ui.screens.settings` | 18 |
| `com.jtech.zemer.ui.screens.statuses` | 4 |
| `com.jtech.zemer.ui.theme` | 7 |
| `com.jtech.zemer.ui.utils` | 20 |
| `com.jtech.zemer.utils` | 41 |
| `com.jtech.zemer.utils.markdown` | 1 |
| `com.jtech.zemer.utils.mp4` | 2 |
| `com.jtech.zemer.utils.ogg` | 1 |
| `com.jtech.zemer.utils.updater` | 7 |
| `com.jtech.zemer.viewmodels` | 51 |
| `com.jtech.zemer.widget` | 2 |

## Kotlin directory inventory under `app/src/main/kotlin`

| Directory | Kotlin files |
| --- | ---: |
| `com/dpi` | 5 |
| `com/jtech/zemer` | 3 |
| `com/jtech/zemer/accessibility` | 1 |
| `com/jtech/zemer/auth` | 3 |
| `com/jtech/zemer/constants` | 6 |
| `com/jtech/zemer/db` | 4 |
| `com/jtech/zemer/db/entities` | 32 |
| `com/jtech/zemer/di` | 7 |
| `com/jtech/zemer/extensions` | 10 |
| `com/jtech/zemer/latestreleases` | 8 |
| `com/jtech/zemer/lyrics` | 13 |
| `com/jtech/zemer/lyrics/lrclib` | 3 |
| `com/jtech/zemer/lyrics/simpmusic` | 3 |
| `com/jtech/zemer/lyrics/youtube` | 2 |
| `com/jtech/zemer/lyrics/zemer` | 14 |
| `com/jtech/zemer/models` | 5 |
| `com/jtech/zemer/offline` | 16 |
| `com/jtech/zemer/playback` | 50 |
| `com/jtech/zemer/playback/queues` | 7 |
| `com/jtech/zemer/playback/relay` | 4 |
| `com/jtech/zemer/playback/sabr` | 14 |
| `com/jtech/zemer/recognition` | 9 |
| `com/jtech/zemer/recognition/shazam` | 2 |
| `com/jtech/zemer/repositories` | 1 |
| `com/jtech/zemer/search` | 10 |
| `com/jtech/zemer/statuses` | 14 |
| `com/jtech/zemer/sync` | 4 |
| `com/jtech/zemer/sync/models` | 1 |
| `com/jtech/zemer/tracking` | 10 |
| `com/jtech/zemer/ui/component` | 82 |
| `com/jtech/zemer/ui/component/lyrics` | 1 |
| `com/jtech/zemer/ui/component/shimmer` | 6 |
| `com/jtech/zemer/ui/menu` | 23 |
| `com/jtech/zemer/ui/player` | 18 |
| `com/jtech/zemer/ui/screens` | 29 |
| `com/jtech/zemer/ui/screens/artist` | 2 |
| `com/jtech/zemer/ui/screens/library` | 8 |
| `com/jtech/zemer/ui/screens/onboarding` | 8 |
| `com/jtech/zemer/ui/screens/playlist` | 10 |
| `com/jtech/zemer/ui/screens/podcast` | 1 |
| `com/jtech/zemer/ui/screens/recognition` | 2 |
| `com/jtech/zemer/ui/screens/search` | 3 |
| `com/jtech/zemer/ui/screens/settings` | 18 |
| `com/jtech/zemer/ui/screens/statuses` | 4 |
| `com/jtech/zemer/ui/theme` | 7 |
| `com/jtech/zemer/ui/utils` | 20 |
| `com/jtech/zemer/utils` | 41 |
| `com/jtech/zemer/utils/markdown` | 1 |
| `com/jtech/zemer/utils/mp4` | 2 |
| `com/jtech/zemer/utils/ogg` | 1 |
| `com/jtech/zemer/utils/updater` | 7 |
| `com/jtech/zemer/viewmodels` | 51 |
| `com/jtech/zemer/widget` | 2 |

## Contributor study map

| Area | Hard-data entry points |
| --- | --- |
| Application startup | `App.kt`, `MainActivity.kt`, `AndroidManifest.xml` |
| Dependency injection | `di/AppModule.kt`, `di/NetworkModule.kt`, `di/SyncModule.kt`, `di/Qualifiers.kt`, `di/DataStoreQualifiers.kt`, entry points (`di/LyricsStoreEntryPoint.kt`, `di/ZemerSearchRepositoryEntryPoint.kt`) |
| Database | `db/MusicDatabase.kt`, `db/DatabaseDao.kt`, `db/entities/*.kt`, `app/schemas/com.jtech.zemer.db.InternalDatabase/*.json` |
| Whitelist/content filters | `utils/WhitelistFetcher.kt`, `utils/WhitelistCache.kt`, `utils/WhitelistFilter.kt`, `utils/ContentFilterConfig.kt`, `utils/IsraeliArtistRegistry.kt` |
| Playback | `playback/*.kt`, `playback/queues/*.kt`, `playback/relay/*.kt`, `playback/sabr/*.kt`, `constants/MediaSessionConstants.kt`, `constants/PlaybackMode.kt`, `ui/player/*.kt` |
| UI/navigation | `ui/screens/Screens.kt`, `ui/screens/NavigationBuilder.kt`, `ui/screens/**`, `ui/component/**`, `ui/menu/**`, `ui/theme/**` |
| Preferences and sync | `constants/PreferenceKeys.kt`, `utils/DataStore.kt`, `sync/*.kt`, `sync/models/*.kt`, `utils/SyncUtils.kt` |
| Auth | `auth/*.kt`, auth-related settings/onboarding screens |
| Lyrics | `lyrics/*.kt` (chain core), `lyrics/zemer/*.kt` (Zemer resolver + parser ports), `lyrics/lrclib/*.kt`, `lyrics/simpmusic/*.kt`, `lyrics/youtube/*.kt`, `ui/component/lyrics/*.kt` |
| Resources | `src/main/res/**` (no native code, no `src/main/assets`) |

See the `reference/` docs for per-file metadata.
