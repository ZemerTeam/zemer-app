# 02 — The install methods

All three live in `AppInstaller` (`utils/updater/AppInstaller.kt`), behind one entry point:

```kotlin
suspend fun install(context, apkFile, installerType): InstallResult   // on Dispatchers.IO
```

`InstallResult` (same file) is a three-state sealed class:

| Result | Meaning |
|---|---|
| `Success` | installed silently, in-process, right now (root) |
| `RequiresUserAction` | the OS / a session callback will finish it (Standard launches the system installer; Shizuku commits a session and waits for the broadcast) |
| `Error(message)` | failed; `message` is a localized, user-facing string |

`InstallerType` (`Installer.kt`) is the enum of methods. Its ordinal is persisted in
DataStore, so **constants are append-only — never reorder or remove** (an
`InstallerTest` pins the ordinals):

```kotlin
enum class InstallerType(@StringRes val title: Int) {
    NATIVE(R.string.installer_native_title),    // ordinal 0
    ROOT(R.string.installer_root_title),        // ordinal 1
    SHIZUKU(R.string.installer_shizuku_title);  // ordinal 2
    companion object { fun fromOrdinal(ordinal: Int): InstallerType = entries.getOrElse(ordinal) { NATIVE } }
}
```

## NATIVE (Standard) — `installNative`

Hands the APK to the system package installer via `Intent.ACTION_VIEW` with a
`FileProvider` content URI (authority `${packageName}.FileProvider`) and
`FLAG_GRANT_READ_URI_PERMISSION`. Returns `RequiresUserAction` — the user confirms in the OS
installer UI. **Requirements: none** (every device has it), but needs the
"install unknown apps" permission, which the controller gates and requests
(`canInstallPackages` / `getInstallPermissionIntent`). This is the default and the fallback.

## ROOT — `installRoot`

Drives `pm` over a root shell (libsu, `com.topjohnwu.superuser.Shell`):

```
pm install-create -i <pkg> --user 0 -r -S <size>     → parse the session id
pm install-write  -S <size> <sid> <name> <apk-path>  → pm reads the file by path
pm install-commit <sid>
```

Synchronous: on a successful commit it returns `Success` (which is what triggers the restart
from the controller). The session id is parsed by `parseSessionId(output)` — a small pure
helper (first integer in the first output line) that `InstallerTest` covers.
**Requirements: a rooted device.** `hasRootAccess()` calls `Shell.getShell().isRoot`, which
**opens the root shell and shows the Magisk/SuperSU grant prompt** — so it is only ever
called when the user actively selects Root, never to populate UI, and always off the main
thread (`Dispatchers.IO`).

> The `install-write` step passes the APK **path** to `pm` rather than piping it
> (`cat apk | pm install-write`). Piping copies the whole APK through a shell pipe; the path
> form lets `pm` read the file directly (fixed in `213b0a8`).

## SHIZUKU — `installShizuku`

Uses the hidden `PackageInstaller` APIs through a Shizuku-wrapped binder, with
`rikka.tools.refine` (`Refine.unsafeCast`) bridging the hidden `*Hidden` types:

1. `IPackageManager` via `SystemServiceHelper.getSystemService("package")` wrapped in a
   `ShizukuBinderWrapper`.
2. Create a `MODE_FULL_INSTALL` session with `INSTALL_REPLACE_EXISTING`.
3. Write the APK into the session, `commit()` with a `PendingIntent` targeting
   `InstallReceiver`.

Returns `RequiresUserAction` immediately — the **real** outcome arrives asynchronously as a
`PackageInstaller` status broadcast to `InstallReceiver` (see below and
[03](03-restart.md)). **Requirements: Shizuku installed, running, and permission granted.**
Three guards back this: `hasShizukuOrSui(context)` (package present),
`isShizukuAlive()` (`Shizuku.pingBinder()`), `hasShizukuPermission()`
(`Shizuku.checkSelfPermission()`). The hidden-constructor signatures changed in Android 16+,
so a `NoSuchMethodError` is caught specifically and surfaced as
`shizuku_not_supported_version` rather than a crash.

### The Shizuku permission dance (`UpdaterSettings.kt`)

Selecting Shizuku may need a permission grant, which is asynchronous. The screen registers a
`Shizuku.OnRequestPermissionResultListener` in a `DisposableEffect`; on grant it persists the
choice, on denial it shows `shizuku_permission_required`. `selectInstaller` checks
installed → alive → permission and calls `Shizuku.requestPermission(0)` only when needed.
Known edge case: if the user leaves the Updater screen before answering the grant prompt, the
listener is disposed and the selection is lost (rare — see [05](05-runbook.md)).

## InstallReceiver — the Shizuku session callback

`InstallReceiver` (`utils/updater/InstallReceiver.kt`, action
`com.jtech.zemer.INSTALL_STATUS`, registered in the manifest) handles the
`PackageInstaller` session status — **only the Shizuku path routes through it**:

- `STATUS_PENDING_USER_ACTION` → launch the confirm intent.
- `STATUS_SUCCESS` → success toast (Shizuku does **not** auto-restart — see [03](03-restart.md)).
- any `STATUS_FAILURE*` → localized failure toast.
