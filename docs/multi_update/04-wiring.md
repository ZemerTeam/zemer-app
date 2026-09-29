# 04 - Wiring (everything outside `updater/`)

## Gradle (`gradle/libs.versions.toml`, `build.gradle.kts`, `app/build.gradle.kts`)

| Library | Coordinate | Role |
|---|---|---|
| Shizuku API / provider | `dev.rikka.shizuku:api`, `dev.rikka.shizuku:provider` | the Shizuku service + `ShizukuProvider` |
| libsu | `com.github.topjohnwu.libsu:core` | root shell (JitPack) |
| refine runtime | `dev.rikka.tools.refine:runtime` | `Refine.unsafeCast` for hidden types |
| hidden stub | `dev.rikka.hidden:stub` (`compileOnly`) | compile-time stubs of the hidden APIs; the real classes are on-device |
| HiddenApiBypass | `org.lsposed.hiddenapibypass:hiddenapibypass` | lifts the hidden-API denylist at runtime |

The `dev.rikka.tools.refine` Gradle plugin is declared `apply false` in the root `build.gradle.kts` and
applied in `app/build.gradle.kts`, so the refine casts are rewritten.

## Manifest (`app/src/main/AndroidManifest.xml`)

- `REQUEST_INSTALL_PACKAGES` permission.
- `<uses-sdk tools:overrideLibrary="rikka.shizuku.api, rikka.shizuku.provider, rikka.shizuku.shared,
  rikka.shizuku.aidl" />` - the manifest-merger override for the Shizuku libraries (keep the list in
  sync with the Shizuku modules pulled in).
- The non-exported `.utils.updater.InstallReceiver` with action `com.jtech.zemer.INSTALL_STATUS`. It is
  wired **by string** to `InstallReceiver.ACTION_INSTALL_STATUS`, so a rename must change both. It is the
  hardcoded package, not `${applicationId}` - they coincide only because there is no
  `applicationIdSuffix`.
- `rikka.shizuku.ShizukuProvider` (authority `${applicationId}.shizuku`, exported, guarded by
  `INTERACT_ACROSS_USERS_FULL`).
- **No `android:testOnly`** (upstream needs it only for the dropped Dhizuku method). Do not add it.

The existing `androidx.core.content.FileProvider` (authority `${applicationId}.FileProvider`,
`res/xml/provider_paths.xml`) already covers `cache-path`, where the APK is written; no new provider.

## The hidden-API exemption (lazy)

The Shizuku path reaches hidden `PackageInstaller` constructors, which Android 9+ blocks.
`AppInstaller.ensureHiddenApiBypass()` calls `HiddenApiBypass.addHiddenApiExemptions("I", "L")` (JVM
signature prefixes covering the classes used) on the **first Shizuku install**, not at startup, so users
who never pick Shizuku don't pay for it. A failure is logged and non-fatal - only Shizuku degrades.

## ProGuard (`app/proguard-rules.pro`)

R8 runs on release, so the reflectively/hidden-accessed classes are kept: `rikka.shizuku.**`,
`moe.shizuku.**`, `dev.rikka.tools.refine.**`, `android.content.pm.IPackageManager`,
`IPackageInstaller`, `IPackageInstallerSession` (each with `$Stub`), `PackageInstallerHidden` (+ `$*`),
`PackageManagerHidden`, and `com.topjohnwu.superuser.**`. **Only the release build exercises these
rules** - a working debug build proves nothing about them.
