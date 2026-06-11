# Multi-method self-update — install updates without the Play Store

Hand-authored docset for Zemer's in-app updater: how a downloaded APK gets installed
through one of several methods (Standard / Root / Shizuku), and how the app relaunches
itself after a silent update. Everything here is derived from the code as of
`feat/multi-update` `213b0a8`; every claim cites the file and symbol that proves it.

## TL;DR

Zemer ships outside the Play Store, so it updates itself: it checks a remote endpoint for a
newer version, downloads the APK, and installs it. The install step is the interesting part.
A plain `ACTION_VIEW` hand-off to the system installer ("Standard") always works but makes
the user tap through the OS installer UI. For users who have **root** or **Shizuku**, the
app can instead install **silently** and then **restart itself**.

The install layer is a small package adapted from
[APK-MultiUpdate](https://github.com/alltechdev/APK-MultiUpdate) (GPL-3.0), whose silent
paths come from Aurora Store:

```
app/src/main/kotlin/com/jtech/zemer/utils/updater/
├── Installer.kt              # InstallerType enum (NATIVE / ROOT / SHIZUKU)
├── AppInstaller.kt           # the install methods + availability checks
├── InstallReceiver.kt        # PackageInstaller session callback (Shizuku)
├── AppRestarter.kt           # relaunch the app after a silent update
└── ApkInstallController.kt   # Compose hook shared by both install entry points
```

The chosen method is one DataStore preference (`InstallerTypeKey`, an `InstallerType`
ordinal). Two screens trigger an install — the Updater settings screen and the startup
update dialog — and both go through the same `rememberApkInstallController`, so behaviour is
identical at both entry points.

> Dhizuku (a fourth method in upstream APK-MultiUpdate) was deliberately **left out**: it
> requires `android:testOnly="true"` on the manifest, which blocks normal installs of a
> distributed APK. Dropping it also removed that constraint entirely.

## The pages

1. **[01-architecture.md](01-architecture.md)** — the pieces and the data flow from "check"
   to "installed", where this sits relative to the two update-source checkers, and the
   single shared install path.
2. **[02-install-methods.md](02-install-methods.md)** — the three methods in
   `AppInstaller`: how each installs, what it requires, the availability/permission checks,
   and the sync-vs-async result models that matter for the restart.
3. **[03-restart.md](03-restart.md)** — `AppRestarter`: why a silent self-update kills our
   process, why the relaunch is scheduled through `AlarmManager`, and the two success
   signals that trigger it.
4. **[04-wiring.md](04-wiring.md)** — everything outside the `updater/` package: Gradle
   dependencies, the manifest (receiver, ShizukuProvider, `overrideLibrary`), the
   `HiddenApiBypass` call in `App.kt`, ProGuard keep rules, and the FileProvider.
5. **[05-runbook.md](05-runbook.md)** — testing the flow on-device, the version-downgrade
   trick, known edge cases, and where to look when an install fails.

## One-paragraph mental model

Updating is two stages: **acquire** (check + download the APK to cache) and **install**.
This feature owns the install stage. `InstallerType` names how to install; `AppInstaller`
does it; `rememberApkInstallController` is the one place that calls `AppInstaller`, gating
the Standard method behind the "install unknown apps" permission and, on a silent success,
asking `AppRestarter` to relaunch the app. Root finishes synchronously (its `install()`
returns `InstallResult.Success`); Shizuku finishes asynchronously through `InstallReceiver`
(its `install()` returns `RequiresUserAction` and the real result arrives as a
`PackageInstaller` broadcast) — so the restart is triggered from two different places, one
per model. Everything degrades safely: a missing/denied privilege surfaces an inline error
and the user can fall back to Standard.

## Implementation history (the actual commits)

On branch `feat/multi-update` (not yet merged to `main`):

| Commit | What |
|---|---|
| `4880591` | the feature: `updater/` package (Native/Root/Shizuku), `InstallerTypeKey`, the Updater-screen install-method picker, manifest + Gradle + ProGuard wiring; replaced `UpdateChecker.installApk` |
| `213b0a8` | auto-restart after silent updates (`AppRestarter`); extracted `rememberApkInstallController` so the startup dialog and Updater screen share one install path; fixed the download progress bar (dropped the unreliable standalone HEAD); `pm install-write` by path instead of a `cat` pipe; removed dead strings |
