# Multi-method self-update - in-app APK installation

How the in-app updater installs a downloaded APK through one of three methods (Standard / Root /
Shizuku), and how the app comes back after a silent update. Hand-authored; the update *check* and
*download* (stable `ghtrack.zemer.io`, nightly `nightly.zemer.io`) are covered in the `AGENTS.md`
"in-app updater" section.

## Mental model

Updating is **acquire** (check + download the APK to cache) then **install**; this docset covers
install. `InstallerType` names the method, `AppInstaller` performs it, and
`rememberApkInstallController` is the one place that calls `AppInstaller` - it gates Standard behind the
"install unknown apps" permission and shows an "installing…" heads-up before a silent install kills the
process. **Standard** (`ACTION_VIEW` to the system installer) always works but needs the user's taps.
**Root** installs silently and relaunches itself by chaining `am start` onto its commit. **Shizuku**
installs silently, finishes asynchronously through `InstallReceiver`, and does **not** auto-restart (its
privileged process is reaped with ours), so the user reopens the app. A missing or denied privilege
surfaces an inline error, and the user can fall back to Standard.

```
app/src/main/kotlin/com/jtech/zemer/utils/updater/
├── Installer.kt              # InstallerType (NATIVE / ROOT / SHIZUKU)
├── AppInstaller.kt           # InstallResult + the install methods + availability checks
├── InstallReceiver.kt        # PackageInstaller session callback (Shizuku only)
├── AppRestarter.kt           # the am-start relaunch command (root)
└── ApkInstallController.kt   # Compose hook shared by both install entry points
```

The install layer is adapted from [APK-MultiUpdate](https://github.com/alltechdev/APK-MultiUpdate)
(GPL-3.0), whose silent paths come from Aurora Store. Upstream's fourth method, **Dhizuku, is
deliberately left out**: it requires `android:testOnly="true"`, which blocks normal installs of a
distributed APK. Do not add it (or the flag) back.

## Pages

1. [01-architecture](01-architecture.md) - where install sits, the two update checkers, the shared
   install path, the persisted method.
2. [02-install-methods](02-install-methods.md) - each method, its requirements and result model.
3. [03-restart](03-restart.md) - why a silent update kills us, the root relaunch, the heads-up.
4. [04-wiring](04-wiring.md) - Gradle, manifest, the lazy hidden-API exemption, ProGuard, FileProvider.
5. [05-runbook](05-runbook.md) - on-device testing, verification, failure table.
