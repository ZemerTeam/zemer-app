# 01 - Architecture and data flow

```
  ACQUIRE                                      INSTALL (this docset)

  check  ->  download  ->  pick a method  ->  install  ->  (root relaunches itself)

  UpdateChecker.checkForUpdates                ApkInstallController
  UpdateChecker.downloadUpdate                   -> AppInstaller.install
  (emits DownloadState.Downloaded)
```

## The two update checkers (context)

Both feed an APK into the world; only `UpdateChecker` feeds the installer.

| Checker | Source | Used by | File |
|---|---|---|---|
| `UpdateChecker` | stable `https://ghtrack.zemer.io` (`/api`, `/changelog`, `/download`); nightly `https://nightly.zemer.io/api` (SHA-pinned `downloadUrl`, size + SHA-256 verified before install) | the Updater settings screen, the startup update dialog | `utils/UpdateChecker.kt`, `utils/updater/NightlyUpdates.kt` |
| `Updater` | Firestore `appUpdates/latest` | the daily update notification in `MainActivity` and the Account settings "new version" row - both just open the download URL | `utils/Updater.kt` |

`UpdateChecker.downloadUpdate(context)` downloads to a `.part` file in `context.cacheDir`, promotes it to
`zemer-update.apk`, and emits `DownloadState.Downloaded(apkFile)` - the file the installer consumes.

## The single shared install path

| Entry point | File | Trigger |
|---|---|---|
| Updater settings screen | `ui/screens/settings/UpdaterSettings.kt` | the download completing in its dialog |
| Startup update dialog | `MainActivity.kt` (`LaunchedEffect(downloadState)`) | the download completing |

Both get a controller from `rememberApkInstallController(installerType, onResult)` and call
`controller.install(apkFile)`. **Add install behaviour to the controller, never to a call site** - the two
entry points drifted when `MainActivity` called `AppInstaller` directly (a Standard install with the
permission off failed silently and the dialog never reset). The controller:

1. For `NATIVE` without `canInstallPackages()`, launches the "install unknown apps" settings intent and
   retries once it returns.
2. For a silent method, waits `SILENT_INSTALL_HEADS_UP_MS` so the "installing…" note renders before the
   process dies ([03](03-restart.md)).
3. Runs `AppInstaller.install(context, apkFile, installerType)` (exposing `isInstalling` for the dialog).
4. Passes the `InstallResult` to `onResult`; each screen maps it (`Success` → reset, `RequiresUserAction`
   → the system UI takes over, `Error` → show the message). It also forwards Shizuku's real outcome from
   `InstallReceiver.events`, so a Shizuku failure shows in the dialog, not only as a toast.

## The selected method

One DataStore preference, `InstallerTypeKey = intPreferencesKey("installerType")`
(`constants/PreferenceKeys.kt`), storing an `InstallerType` **ordinal**. Read it through
`InstallerType.fromOrdinal`, which falls back to `NATIVE` for unknown values. Because the ordinal is
persisted, the enum is append-only ([02](02-install-methods.md)).

The Updater screen renders the picker with the shared `ListPreference`, labelled by
`InstallerType.title`. `selectInstaller` runs the chosen method's availability checks (off the main
thread) before persisting; for Shizuku it persists **before** the async permission request. Failures show
inline under the row.
