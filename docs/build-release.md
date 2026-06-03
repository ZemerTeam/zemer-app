# Build, CI, native, and auxiliary modules documentation

## Root Gradle and settings facts

| File | Hard facts visible in file |
| --- | --- |
| `settings.gradle.kts` | Root project is `Zemer`; includes `:app`, `:innertube`, `:lrclib`, `:simpmusic`; includes composite build `cipher` and substitutes `com.zemer:cipher` with `:library`; repositories include `mavenLocal`, Google, Gradle Plugin Portal, Maven Central, and JitPack. |
| `build.gradle.kts` | Applies Hilt, KSP, and Google services plugins with `apply(false)`; buildscript adds Android Gradle plugin, Kotlin Gradle plugin, and Google services classpath; registers `clean`; configures Kotlin compile options for optional Compose compiler reports/metrics. |
| `gradle.properties` | JVM args use 4096M heap and ParallelGC; AndroidX enabled; Jetifier disabled; Gradle configuration cache enabled; non-transitive R disabled; parallel and daemon enabled; Gradle build cache disabled; KSP incremental and intermodule incremental disabled; HTTP timeouts set to 180000 ms. |
| `gradle/libs.versions.toml` | Central version catalog for plugins and dependencies. See `reference/non-kotlin-files.md` for tracked metadata. |

## GitHub Actions release workflow

`.github/workflows/release-build.yml` defines workflow `Android Release Build`.

| Workflow fact | Value |
| --- | --- |
| Triggers | `workflow_dispatch`, push to `main`, pull request to `main` |
| Environment | `USE_PREBUILT_NATIVE: true` |
| Job | `assemble-release` on `ubuntu-latest` |
| Permissions | `contents: write` |
| Checkout | `actions/checkout@v4` with `submodules: recursive` |
| Java setup | `actions/setup-java@v4`, Temurin JDK 21 |
| Gradle setup | `gradle/actions/setup-gradle@v4` |
| Android SDK setup | `android-actions/setup-android@v3` |
| Native cache path | `app/src/main/jniLibs` |
| Native cache key | `bento4-libs-v1.0.0` |
| Native fallback download | `gh release download v1.0.0 --repo ZemerTeam/zemer-bento4 --pattern 'bento4-libs.zip'` then unzip to `app/src/main` |
| Firebase config | Decodes `secrets.GOOGLE_SERVICES_JSON_BASE64` into `app/google-services.json` |
| Release keystore | Decodes `secrets.RELEASE_KEYSTORE_BASE64` into `app/keystore/release.keystore`; writes store/key env vars to `$GITHUB_ENV` |
| Build command | `./gradlew assembleRelease` |
| Artifact upload | `actions/upload-artifact@v4`, name `release-apk`, path `app/build/outputs/apk/release/*.apk` |
| Notification | Sends Telegram document or message using bot/chat secrets and build status data |

## Native code and submodules

| Path | Hard facts |
| --- | --- |
| `.gitmodules` | Tracks submodule `app/src/main/cpp/bento4` from `https://github.com/ZemerTeam/zemer-bento4.git` and submodule `cipher` from `https://github.com/zemerteam/cipher.git`. |
| `app/src/main/cpp/CMakeLists.txt` | Minimum CMake `3.18`; project name `coverart-wrapper`; calls `add_subdirectory(bento4)`. |
| `app/build.gradle.kts` | If `USE_PREBUILT_NATIVE` is not `true`, configures CMake file `src/main/cpp/CMakeLists.txt`, CMake version `3.22.1`, NDK version `27.0.12077973`, and C++17 flags. |
| `app/src/main/cpp/bento4` | Tracked as a gitlink/submodule path in this checkout, not a regular source file. |

## Auxiliary JVM modules

| Module | Package | Gradle facts | Tracked Kotlin files |
| --- | --- | --- | --- |
| `:innertube` | `com.metrolist.innertube` | Kotlin JVM, serialization plugin, JDK 21; Ktor core/OkHttp/content-negotiation/JSON/encoding, OkHttp DNS-over-HTTPS, NewPipe extractor with protobuf-javalite excluded, JUnit tests. | See `docs/innertube/README.md` and `docs/reference/kotlin-files.md`. |
| `:lrclib` | `com.metrolist.lrclib` | Kotlin JVM, serialization plugin, JDK 21; Ktor core/CIO/content-negotiation/JSON; JUnit tests. | `LrcLib.kt`, `models/Track.kt`. |
| `:simpmusic` | `com.metrolist.simpmusic` | Kotlin JVM, serialization plugin, JDK 21; Ktor core/CIO/content-negotiation/JSON. | `SimpMusicLyrics.kt`, `models/LyricsResponse.kt`. |

## Solver assets

Tracked solver assets under `app/src/main/assets/solver`:

| File | Hard fact |
| --- | --- |
| `astring.js` | JavaScript asset included in app assets. |
| `meriyah.js` | JavaScript asset included in app assets. |
| `yt.solver.core.js` | JavaScript asset included in app assets. |

The repository docs do not infer solver runtime behavior beyond the fact that these JavaScript assets are tracked under Android assets.
