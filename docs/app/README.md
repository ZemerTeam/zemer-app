# App module documentation

## Module facts from Gradle and tracked files

| Fact | Value |
| --- | --- |
| Gradle module | `:app` |
| Namespace / application ID | `com.jtech.zemer` / `com.jtech.zemer` |
| Compile / min / target SDK | `36` / `26` / `36` |
| Version code/name | `25` / `25` |
| Kotlin/JVM target | JVM 21 |
| Compose | Enabled |
| BuildConfig fields visible in Gradle | `ARCHITECTURE`, `GOOGLE_TOKEN_EXCHANGE_URL` |
| Room schema directory | `app/schemas` |
| Native build file | `app/src/main/cpp/CMakeLists.txt` |
| Tracked app paths | `539` |
| Tracked app Kotlin files | `294` |
| Tracked app resource paths | `199` |
| Tracked app asset paths | `3` |
| Tracked app Room schema files | `32` |

## Android components from manifest

| Type | `android:name` | `android:exported` |
| --- | --- | --- |
| `activity` | `.MainActivity` | `true` |
| `activity` | `com.yalantis.ucrop.UCropActivity` | `false` |
| `service` | `.playback.MusicService` | `true` |
| `service` | `.playback.ExoDownloadService` | `false` |
| `service` | `.playback.MediaStoreDownloadService` | `false` |
| `service` | `.accessibility.ButtonMapperAccessibilityService` | `false` |
| `provider` | `androidx.core.content.FileProvider` | `false` |
| `provider` | `com.dpi.DensityScaler` | `false` |
| `receiver` | `androidx.media3.session.MediaButtonReceiver` | `true` |
| `receiver` | `.widget.MusicWidgetReceiver` | `true` |

## Kotlin package inventory

| Package | File count |
| --- | ---: |
| `com.dpi` | 4 |
| `com.jtech.zemer` | 2 |
| `com.jtech.zemer.accessibility` | 1 |
| `com.jtech.zemer.auth` | 3 |
| `com.jtech.zemer.constants` | 6 |
| `com.jtech.zemer.db` | 3 |
| `com.jtech.zemer.db.entities` | 28 |
| `com.jtech.zemer.di` | 6 |
| `com.jtech.zemer.extensions` | 9 |
| `com.jtech.zemer.lyrics` | 8 |
| `com.jtech.zemer.lyrics.model` | 1 |
| `com.jtech.zemer.models` | 5 |
| `com.jtech.zemer.playback` | 9 |
| `com.jtech.zemer.playback.queues` | 6 |
| `com.jtech.zemer.repositories` | 1 |
| `com.jtech.zemer.sync` | 2 |
| `com.jtech.zemer.sync.models` | 2 |
| `com.jtech.zemer.ui.component` | 31 |
| `com.jtech.zemer.ui.component.shimmer` | 5 |
| `com.jtech.zemer.ui.menu` | 16 |
| `com.jtech.zemer.ui.player` | 6 |
| `com.jtech.zemer.ui.screens` | 20 |
| `com.jtech.zemer.ui.screens.artist` | 4 |
| `com.jtech.zemer.ui.screens.library` | 7 |
| `com.jtech.zemer.ui.screens.player` | 1 |
| `com.jtech.zemer.ui.screens.playlist` | 7 |
| `com.jtech.zemer.ui.screens.search` | 2 |
| `com.jtech.zemer.ui.screens.settings` | 13 |
| `com.jtech.zemer.ui.screens.settings.integrations` | 1 |
| `com.jtech.zemer.ui.theme` | 4 |
| `com.jtech.zemer.ui.utils` | 11 |
| `com.jtech.zemer.utils` | 33 |
| `com.jtech.zemer.utils.sabr` | 2 |
| `com.jtech.zemer.viewmodels` | 34 |
| `com.jtech.zemer.widget` | 1 |

## Kotlin directory inventory under `app/src/main/kotlin`

| Directory | Kotlin files |
| --- | ---: |
| `com/dpi` | 4 |
| `com/jtech/zemer` | 2 |
| `com/jtech/zemer/accessibility` | 1 |
| `com/jtech/zemer/auth` | 3 |
| `com/jtech/zemer/constants` | 6 |
| `com/jtech/zemer/db` | 3 |
| `com/jtech/zemer/db/entities` | 28 |
| `com/jtech/zemer/di` | 6 |
| `com/jtech/zemer/extensions` | 9 |
| `com/jtech/zemer/lyrics` | 8 |
| `com/jtech/zemer/lyrics/model` | 1 |
| `com/jtech/zemer/models` | 5 |
| `com/jtech/zemer/playback` | 9 |
| `com/jtech/zemer/playback/queues` | 6 |
| `com/jtech/zemer/repositories` | 1 |
| `com/jtech/zemer/sync` | 2 |
| `com/jtech/zemer/sync/models` | 2 |
| `com/jtech/zemer/ui/component` | 31 |
| `com/jtech/zemer/ui/component/shimmer` | 5 |
| `com/jtech/zemer/ui/menu` | 16 |
| `com/jtech/zemer/ui/player` | 6 |
| `com/jtech/zemer/ui/screens` | 20 |
| `com/jtech/zemer/ui/screens/artist` | 4 |
| `com/jtech/zemer/ui/screens/library` | 7 |
| `com/jtech/zemer/ui/screens/player` | 1 |
| `com/jtech/zemer/ui/screens/playlist` | 7 |
| `com/jtech/zemer/ui/screens/search` | 2 |
| `com/jtech/zemer/ui/screens/settings` | 13 |
| `com/jtech/zemer/ui/screens/settings/integrations` | 1 |
| `com/jtech/zemer/ui/theme` | 4 |
| `com/jtech/zemer/ui/utils` | 11 |
| `com/jtech/zemer/utils` | 33 |
| `com/jtech/zemer/utils/sabr` | 2 |
| `com/jtech/zemer/viewmodels` | 34 |
| `com/jtech/zemer/widget` | 1 |

## Contributor study map

| Area | Hard-data entry points |
| --- | --- |
| Application startup | `App.kt`, `MainActivity.kt`, `AndroidManifest.xml` |
| Dependency injection | `di/AppModule.kt`, `di/NetworkModule.kt`, `di/SyncModule.kt`, qualifier files, entry points |
| Database | `db/MusicDatabase.kt`, `db/DatabaseDao.kt`, `db/entities/*.kt`, `app/schemas/com.jtech.zemer.db.InternalDatabase/*.json` |
| Whitelist/content filters | `utils/WhitelistFetcher.kt`, `utils/WhitelistCache.kt`, `utils/WhitelistFilter.kt`, `utils/ContentFilterConfig.kt`, `utils/IsraeliArtistRegistry.kt` |
| Playback | `playback/*.kt`, `playback/queues/*.kt`, `constants/MediaSessionConstants.kt`, `ui/player/*.kt` |
| UI/navigation | `ui/screens/Screens.kt`, `ui/screens/NavigationBuilder.kt`, `ui/screens/**`, `ui/component/**`, `ui/menu/**`, `ui/theme/**` |
| Preferences and sync | `constants/PreferenceKeys.kt`, `utils/DataStore.kt`, `sync/*.kt`, `sync/models/*.kt`, `utils/SyncUtils.kt` |
| Auth | `auth/*.kt`, auth-related settings/onboarding screens |
| Lyrics | `lyrics/*.kt`, `lyrics/model/*.kt`, `lrclib`, `simpmusic` modules |
| Native/assets | `src/main/cpp/CMakeLists.txt`, `src/main/assets/solver/*.js`, app resources |

See the `reference/` docs for per-file metadata.
