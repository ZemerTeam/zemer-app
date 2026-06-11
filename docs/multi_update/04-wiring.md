# 04 — Wiring (everything outside `updater/`)

The install package needs build, manifest, runtime, and shrinker support. All of it.

## Gradle (`gradle/libs.versions.toml`, `build.gradle.kts`, `app/build.gradle.kts`)

Versions and libraries added:

| Library | Coordinate | Role |
|---|---|---|
| Shizuku API | `dev.rikka.shizuku:api` | talk to the Shizuku service |
| Shizuku provider | `dev.rikka.shizuku:provider` | the `ShizukuProvider` for the manifest |
| libsu | `com.github.topjohnwu.libsu:core` | root shell for the Root method (JitPack) |
| refine runtime | `dev.rikka.tools.refine:runtime` | `Refine.unsafeCast` for hidden types |
| hidden stub | `dev.rikka.hidden:stub` (`compileOnly`) | compile-time stubs of hidden `*Hidden` APIs |
| HiddenApiBypass | `org.lsposed.hiddenapibypass:hiddenapibypass` | lift the hidden-API denylist at runtime |

The hidden stub is `compileOnly` — it provides the hidden classes (`PackageInstallerHidden`,
`PackageManagerHidden`, `IPackageInstaller`, …) to compile against; the real implementations
are on-device. The `dev.rikka.tools.refine` Gradle **plugin** is applied (`apply false` in
the root `build.gradle.kts`, applied in `app/build.gradle.kts`) so the refine cast rewrites
work. libsu resolves from JitPack, already a configured repo (`settings.gradle.kts`).

## Manifest (`app/src/main/AndroidManifest.xml`)

```xml
<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />

<!-- Shizuku libs declare lower minSdk variants; override on our minSdk 26 -->
<uses-sdk tools:overrideLibrary="rikka.shizuku.api, rikka.shizuku.provider, rikka.shizuku.shared, rikka.shizuku.aidl" />

<!-- Shizuku session-status callback (Shizuku path only) -->
<receiver android:name=".utils.updater.InstallReceiver" android:exported="false">
    <intent-filter><action android:name="com.jtech.zemer.INSTALL_STATUS" /></intent-filter>
</receiver>

<!-- Shizuku provider -->
<provider
    android:name="rikka.shizuku.ShizukuProvider"
    android:authorities="${applicationId}.shizuku"
    android:enabled="true" android:exported="true" android:multiprocess="false"
    android:permission="android.permission.INTERACT_ACROSS_USERS_FULL" />
```

The receiver's `<action>` string must match `InstallReceiver.ACTION_INSTALL_STATUS`
(`com.jtech.zemer.INSTALL_STATUS`) exactly — they are wired by string, so a rename has to
change both. Note this is the **hardcoded package**, not `${applicationId}.INSTALL_STATUS`;
there is no `applicationIdSuffix`, so they coincide, but keep that in mind if a suffix is
ever added.

> **No `android:testOnly`.** Upstream APK-MultiUpdate sets it for Dhizuku; Zemer dropped
> Dhizuku, so the flag is absent and normal installs are unaffected. Do not add it.

### FileProvider (pre-existing, reused)

The Standard method and the download both rely on the existing FileProvider —
authority `${applicationId}.FileProvider`, paths in `res/xml/provider_paths.xml`, which
already covers `cache-path`/`external-cache-path` where `UpdateChecker` writes the APK. No
new provider was added.

## `App.kt` — lift the hidden-API denylist

The Shizuku path reaches hidden `PackageInstaller` constructors, which Android 9+ blocks by
default. `App.onCreate` exempts them once, early:

```kotlin
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
    runCatching { HiddenApiBypass.addHiddenApiExemptions("I", "L") }
        .onFailure { Timber.w(it, "Hidden API bypass unavailable; Shizuku install method will not work") }
}
```

The `"I"`/`"L"` prefixes are JVM type-signature prefixes (object and array-of-object), which
cover the hidden classes used. Failure is logged and non-fatal — only the Shizuku method
degrades.

## ProGuard (`app/proguard-rules.pro`)

R8 runs on release (`isMinifyEnabled = true`), so the reflectively/hidden-accessed classes
are kept:

```
-keep class rikka.shizuku.** { *; }
-keep class moe.shizuku.** { *; }
-keep class dev.rikka.tools.refine.** { *; }
-keep class android.content.pm.IPackageManager { *; }          # + $Stub
-keep class android.content.pm.IPackageInstaller { *; }        # + $Stub
-keep class android.content.pm.IPackageInstallerSession { *; } # + $Stub
-keep class android.content.pm.PackageInstallerHidden { *; }   # + $*
-keep class android.content.pm.PackageManagerHidden { *; }
-keep class com.topjohnwu.superuser.** { *; }
```

Because these only matter under R8, **the release build is the real test of this wiring** —
a debug build that runs proves nothing about the keep rules (per CLAUDE.md, build both).
