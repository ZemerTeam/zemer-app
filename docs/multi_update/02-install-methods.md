# 02 - The install methods

All live in `AppInstaller` (`utils/updater/AppInstaller.kt`) behind
`suspend fun install(context, apkFile, installerType): InstallResult` (runs on `Dispatchers.IO`).

| `InstallResult` | Meaning |
|---|---|
| `Success` | installed silently and synchronously (root) |
| `RequiresUserAction` | something else finishes it: the system installer UI (Standard) or the session broadcast (Shizuku) |
| `Error(message)` | failed; `message` is user-facing |

`InstallerType` (`Installer.kt`) is `NATIVE`, `ROOT`, `SHIZUKU`, each with a `title` and an optional
`installingNote` heads-up (`installing_note_restart` for root, `installing_note_reopen` for Shizuku,
none for Standard). **The ordinal is persisted, so constants are append-only - never reorder or remove**
(`InstallerTest` pins the ordinals).

## NATIVE (Standard) - `installNative`

`Intent.ACTION_VIEW` with a `FileProvider` URI (authority `${packageName}.FileProvider`) and
`FLAG_GRANT_READ_URI_PERMISSION`; returns `RequiresUserAction`. Works on every device, but needs the
"install unknown apps" permission, which the controller gates (`canInstallPackages` /
`getInstallPermissionIntent`). The default and the fallback.

## ROOT - `installRoot`

`pm` over a libsu root shell (`com.topjohnwu.superuser.Shell`):

```
pm install-create -i <pkg> --user 0 -r -S <size>          → session id (parseSessionId)
pm install-write -S <size> <sid> base.apk "<apk-path>"    → pm reads the file by path
pm install-commit <sid> && sleep 1 && am start -n <component>
```

- Pass the APK **path** to `install-write`, never pipe it (`cat apk | pm install-write` copies the whole
  APK through a shell pipe); the split name is a fixed `base.apk`, so no file name is interpolated into
  the shell command.
- The relaunch is chained onto the commit ([03](03-restart.md)); a successful commit returns `Success`.
- `parseSessionId(output)` (first integer in the first output line) is pure and covered by `InstallerTest`.
- `hasRootAccess()` (`Shell.getShell().isRoot`) **opens the root shell and shows the Magisk/SuperSU grant
  prompt**, so it is called only when the user selects Root, and always off the main thread.

## SHIZUKU - `installShizuku`

The hidden `PackageInstaller` APIs through a Shizuku-wrapped binder, with `Refine.unsafeCast` bridging the
hidden `*Hidden` types:

1. Fail fast unless `isShizukuAlive()` (`Shizuku.pingBinder()`) and `hasShizukuPermission()`; apply the
   hidden-API exemption lazily ([04](04-wiring.md)).
2. `IPackageManager` from `SystemServiceHelper.getSystemService("package")` in a `ShizukuBinderWrapper`;
   build a `PackageInstallerHidden` (constructor differs from API 31).
3. Create a `MODE_FULL_INSTALL` session with `INSTALL_REPLACE_EXISTING`, write + fsync the APK,
   `commit()` with a `PendingIntent` to `InstallReceiver`.

Returns `RequiresUserAction` immediately; the real outcome is the broadcast. A `NoSuchMethodError` (the
hidden constructors changed on newer Android) is caught and surfaced as `shizuku_not_supported_version`,
not a crash. Selection also checks `hasShizukuOrSui(context)` (the Shizuku package is installed).

### The Shizuku permission grant (`UpdaterSettings.kt`)

The grant is asynchronous. The screen registers a `Shizuku.OnRequestPermissionResultListener` in a
`DisposableEffect` (grant → persist, denial → `shizuku_permission_required`). `selectInstaller` checks
installed → alive, **persists the selection**, then calls `Shizuku.requestPermission(0)` only if the
permission is missing - so leaving the screen mid-prompt cannot lose the choice, and `installShizuku`
re-validates the permission at install time.

## `InstallReceiver` - the Shizuku session callback

Action `InstallReceiver.ACTION_INSTALL_STATUS` (`com.jtech.zemer.INSTALL_STATUS`), manifest-registered;
only the Shizuku path uses it:

- `STATUS_PENDING_USER_ACTION` → launch the confirm intent.
- `STATUS_SUCCESS` → emit `Success` on `events` + success toast (no auto-restart).
- `STATUS_FAILURE*` → emit `Error(message)` on `events` + failure toast.
