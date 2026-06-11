# 01 — Architecture and data flow

## Where the install layer sits

Updating has two stages. This feature owns the second one:

```
  ACQUIRE (pre-existing)                      INSTALL (this feature)

  check for a newer version  ->  download  ->  pick a method  ->  install  ->  (restart)

  UpdateChecker.checkForUpdates               ApkInstallController
  UpdateChecker.downloadUpdate                  -> AppInstaller.install
  (emits DownloadState.Downloaded)              (root chains its own am-start relaunch)
```

The acquire stage already existed. The install stage used to be a single
`UpdateChecker.installApk()` that did an `ACTION_VIEW` hand-off; this feature replaced it
with the `updater/` package and a chooser.

## The two update-source checkers (context, not part of this feature)

Zemer has **two** independent "is there a newer version?" surfaces. Neither is changed by
this feature; both ultimately feed an APK file into the install stage:

| Checker | Source | Used by | File |
|---|---|---|---|
| `UpdateChecker` | `https://ghtrack.zemer.io` (`/api`, `/changelog`, `/download`) | the Updater settings screen, the startup update dialog | `utils/UpdateChecker.kt` |
| `Updater` | Firestore doc `appUpdates/latest` (per-arch URLs) | "new version available" row in Account settings (opens the URL) | `utils/Updater.kt` |

The **download-and-install** path is `UpdateChecker.downloadUpdate(context)`, which streams
the APK to `context.cacheDir/zemer-update.apk` and emits `DownloadState.Downloaded(apkFile)`
(`UpdateChecker.kt`). That file is what the installer consumes. The Firestore `Updater` is a
separate browser-hand-off surface and does not flow through the installer.

## The single shared install path

Two composables start an install, and both call the same controller so they cannot drift:

| Entry point | File | Trigger |
|---|---|---|
| Updater settings screen | `ui/screens/settings/UpdaterSettings.kt` | user taps "Install" / download completes in the dialog |
| Startup update dialog | `MainActivity.kt` (`LaunchedEffect(downloadState)`) | a queued update auto-installs when its download completes |

Both obtain a controller from `rememberApkInstallController(installerType, onResult)`
(`ApkInstallController.kt`) and call `controller.install(apkFile)`. The controller:

1. If the method is `NATIVE` and `canInstallPackages()` is false, launches the
   "install unknown apps" settings intent and retries once it returns
   (`rememberLauncherForActivityResult`).
2. For a silent method (root/Shizuku), waits `SILENT_INSTALL_HEADS_UP_MS` so the
   "installing…" heads-up renders before the install kills the process ([03](03-restart.md)).
3. Calls `AppInstaller.install(context, apkFile, installerType)` on a coroutine. Root
   relaunches itself by chaining `am start` onto its commit; Shizuku does not auto-restart.
4. Hands the `InstallResult` back to the caller via `onResult` so each screen maps it to its
   own UI state (`Success` → reset, `RequiresUserAction` → let the system UI take over,
   `Error` → show the message).

Before this was extracted (commit `213b0a8`), `MainActivity` called `AppInstaller.install`
directly: it skipped the `NATIVE` permission gate and only handled `Error`, so a Standard
install with the permission off failed silently and the dialog never reset. The shared
controller removed that divergence.

## State and the selected method

The chosen method persists as one DataStore preference:

```
val InstallerTypeKey = intPreferencesKey("installerType")   // constants/PreferenceKeys.kt
```

It stores an `InstallerType` **ordinal**. Read it back through
`InstallerType.fromOrdinal(ordinal)`, which falls back to `NATIVE` for any out-of-range
value (`Installer.kt`). Because the ordinal is persisted, the enum constants are
append-only — see [02](02-install-methods.md).

The Updater screen renders the picker with the shared `ListPreference` component (a radio
dialog), reading each option's label from `InstallerType.title`. Selecting Root or Shizuku
runs that method's availability/permission check before persisting the choice; failures show
inline under the row (`UpdaterSettings.kt`, `selectInstaller`).
