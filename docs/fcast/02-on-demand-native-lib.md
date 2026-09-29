# 02 - The on-demand native library

The FCast sender SDK ships a native `libfcast_sender_sdk.so`. Casting is a minority feature, so the `.so`
is **excluded from the APK** and downloaded the first time a user opts in.

## Gradle

`app/build.gradle.kts` depends on `sender-sdk-minimal:0.4.0` with `exclude(group = "net.java.dev.jna")`,
pins `net.java.dev.jna:jna:5.13.0@aar` explicitly (the SDK's transitive JNA is excluded), and excludes
`**/libfcast_sender_sdk.so` under `packaging.jniLibs`. The uniffi Kotlin bindings stay in the APK; only
the native code is removed.

## Hosting and pinning

`CastNativeLib` (in `CastNativeLibLoader.kt`, pure) holds `SDK_VERSION`, the release base
`https://github.com/ZemerTeam/zemer-cast/releases/download/sdk-$SDK_VERSION`, and `ABIS` - one `AbiLib`
(abi, url, sha256) each for `arm64-v8a` and `armeabi-v7a`, the assets CI-extracted byte-for-byte from the
upstream aar. `pickAbi` returns the device's most-preferred matching ABI, or null → the loader reports
`Failed(UNSUPPORTED_DEVICE)` and casting is unavailable (never a crash).

## How uniffi finds the file

uniffi hands the system property `uniffi.component.fcast_sender_sdk.libraryOverride`
(`CastNativeLib.OVERRIDE_PROPERTY`) to JNA's `Native.load`, so the loader downloads the `.so` to
`filesDir/castlib/` and sets the property to its absolute path. This must happen **before any SDK type
is touched**, enforced by two guards:

- `FCastDiscoveryHandler.castContext` is `by lazy` - constructing the handler loads no native code.
- `MusicService.startDiscovery()` no-ops unless `castLibLoader.isReady`.

## Download + verify (`CastNativeLibLoader.ensure()`, blocking, off-main)

`CastLibState`: `Idle` → `Downloading(progress)` → `Ready`, or `Failed(UNSUPPORTED_DEVICE |
DOWNLOAD_FAILED)`. `progress` is the 0..1 fraction, or null (indeterminate bar) without a usable
`Content-Length` (`CastNativeLib.downloadProgress`). Exposed as `MusicService.castLibState`.

1. A valid cache → apply the override, `Ready`.
2. Else delete any stale copy + marker, `pickAbi()` (→ `Failed` if none), `Downloading`.
3. Stream to `libfcast_sender_sdk.so.download`, hashing SHA-256 and emitting progress per chunk.
4. SHA mismatch → delete, report `FCast lib checksum mismatch`, `Failed(DOWNLOAD_FAILED)`.
5. Else move into place, **then** write the marker `libfcast_sender_sdk.so.sha`, apply the override,
   `Ready`.

**Why a marker, not `exists()`:** `cacheIsValid(libExists, storedSha, expectedSha)` trusts a cached lib
only if it exists and its recorded SHA equals the one pinned for this ABI. The marker is written only
after the lib is fully in place, so a crash mid-copy re-downloads instead of trusting a partial file, and
an `SDK_VERSION` bump (new expected SHA) forces a re-download.

**Why `@Synchronized`:** the call-site guard in `MusicService.downloadCastLib()` (`isReady ||
Downloading`) is not atomic with the state flip, so two fast taps could both enter. Serialising prevents
two writers in the shared `.download` file, which could leave a corrupt lib validated by a correct marker.

## Entry points

- **Settings → Enable casting** → `CastDownloadDialog` (consent) → `MusicService.downloadCastLib()`.
- **The picker**, opened before the lib exists → consent + Download → the same call.

`downloadCastLib()` only downloads; NSD discovery starts separately via `startDiscovery()` when the
picker opens with the lib ready, so toggling the setting never starts background discovery.
