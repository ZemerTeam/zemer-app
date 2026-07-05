# 02 — The on-demand native library

The FCast sender SDK ships a native library, `libfcast_sender_sdk.so`
(~5.3 MB per ABI). Casting is a minority feature, so bundling it in every APK is
wasteful. Zemer **excludes it from the APK** and downloads it on demand the first
time a user opts into casting.

## Gradle: keep the `.so` out of the APK

`app/build.gradle.kts`:

```kotlin
implementation("org.futo.gitlab.videostreaming.fcast-sdk-jitpack:sender-sdk-minimal:0.4.0") {
    exclude(group = "net.java.dev.jna")
}
implementation("net.java.dev.jna:jna:5.13.0@aar")

packaging {
    jniLibs {
        // The FCast sender-SDK native lib is NOT bundled (~5.3 MB) — downloaded on demand.
        excludes += "**/libfcast_sender_sdk.so"
    }
}
```

The Kotlin/JVM bindings (uniffi-generated, package `org.fcast.sender_sdk`) are
still in the APK — only the native code is excluded. JNA is pinned explicitly
because the SDK's transitive JNA is excluded.

## Hosting: `ZemerTeam/zemer-cast`

The per-ABI `.so` files are CI-built (byte-for-byte extracted from the upstream
aar) and published as GitHub release assets, with their SHA-256 pinned in code:

```kotlin
// CastNativeLib (CastNativeLibLoader.kt) — pure, Android-free, unit-testable metadata
object CastNativeLib {
    const val SDK_VERSION = "0.4.0"
    const val LIB_FILE_NAME = "libfcast_sender_sdk.so"
    const val OVERRIDE_PROPERTY = "uniffi.component.fcast_sender_sdk.libraryOverride"
    private const val BASE = "https://github.com/ZemerTeam/zemer-cast/releases/download/sdk-$SDK_VERSION"

    val ABIS = listOf(
        AbiLib("arm64-v8a",   "$BASE/libfcast_sender_sdk-arm64-v8a.so",   "ef198a26…427e"),
        AbiLib("armeabi-v7a", "$BASE/libfcast_sender_sdk-armeabi-v7a.so", "99cd8119…ec63"),
    )
    fun pickAbi(supportedAbis: List<String>): AbiLib? = ...   // most-preferred matching ABI
}
```

Only `arm64-v8a` and `armeabi-v7a` are supported (matching the app's native ABI
set). `pickAbi` returns `null` on any other device → the loader reports
`Failed(UNSUPPORTED_DEVICE)` and casting is unavailable, never a crash.

## How uniffi finds the downloaded file

uniffi reads the system property `uniffi.component.fcast_sender_sdk.libraryOverride`
and hands its value straight to JNA's `Native.load`. So instead of placing the
`.so` on the normal JNI path, the loader downloads it to app-private storage and
points the property at the absolute path:

```kotlin
private fun applyOverride() {
    System.setProperty(CastNativeLib.OVERRIDE_PROPERTY, libFile.absolutePath)
}
```

This must happen **before** any cast SDK type is touched. Two guards enforce
that ordering:

- `FCastDiscoveryHandler.castContext` is `by lazy { CastContext() }` — merely
  constructing the handler loads no native code.
- `MusicService.startDiscovery()` no-ops unless `castLibLoader.isReady`.

## The download + verify state machine

`CastLibState` is a sealed interface: `Idle` → `Downloading(progress)` → `Ready`,
or `Failed(reason)` where `reason` is `UNSUPPORTED_DEVICE` or `DOWNLOAD_FAILED`.
`Downloading.progress` is the 0..1 downloaded fraction driving the dialog's
progress bar — or `null` (an indeterminate bar) when the server sent no usable
`Content-Length` (`CastNativeLib.downloadProgress`, pure + unit-tested).
The flow is exposed as `MusicService.castLibState` for the UI.

`CastNativeLibLoader.ensure()` (blocking I/O, call off-main):

1. If `cachedLibValid()` → apply the override, set `Ready`, return `true`.
2. Otherwise delete any stale/partial copy, `pickAbi()` (→ `Failed` if none),
   set `Downloading`.
3. Stream the asset to `…/castlib/libfcast_sender_sdk.so.download`, computing the
   SHA-256 of the bytes received and re-emitting `Downloading(progress)` as
   chunks arrive.
4. If the SHA mismatches the pinned value → delete, report, `Failed`.
5. Else rename into place, **then** write the marker file
   (`…/libfcast_sender_sdk.so.sha`), apply the override, set `Ready`.

### Why a marker file, not just `exists()`

`cacheIsValid(libExists, storedSha, expectedSha)` (pure, unit-tested) trusts a
cached lib only if the file exists **and** its recorded SHA matches the SHA
pinned for this device's ABI:

```kotlin
fun cacheIsValid(libExists: Boolean, storedSha: String?, expectedSha: String?): Boolean =
    libExists && expectedSha != null && storedSha != null && storedSha.equals(expectedSha, true)
```

The marker is written **only after** the lib is fully in place, so:

- a crash mid-copy leaves no marker → the partial file is re-downloaded, not
  trusted;
- an `SDK_VERSION` bump changes `expectedSha` → the old lib's marker no longer
  matches → re-download.

### Concurrency

`ensure()` is `@Synchronized`. The call-site guard in
`MusicService.downloadCastLib()` (`isReady || Downloading → return`) is *not*
atomic with the `_state = Downloading` flip inside `ensure()`, so two fast
download/retry taps could both pass the guard. Serialising makes the second
caller block until the first finishes, then see the valid cache and return —
never two concurrent writers into the shared `.download` temp file (which could
otherwise leave a corrupt `libFile` validated by a correct marker, because each
writer's own SHA matches its own stream).

## Entry points that trigger a download

- **Settings → Enable casting** → `CastDownloadDialog` (consent) →
  `MusicService.downloadCastLib()`.
- **The picker**, if opened before the lib exists → consent text + Download
  button → same `downloadCastLib()`.

`downloadCastLib()` is download-only; it does **not** start NSD discovery (so
toggling the setting doesn't kick off background discovery). Discovery is started
separately by `startDiscovery()` when the picker is open and the lib is ready.
