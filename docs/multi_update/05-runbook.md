# 05 - Runbook

## Testing the flow on-device

An update is offered only when the remote build is newer than the installed one. To exercise download +
install without a real release, **temporarily** lower `versionName` in `app/build.gradle.kts` below
the `latestVersion` that `https://ghtrack.zemer.io/api` reports (the stable check compares only
versionName, `isNewerVersion`), build and install
(`./gradlew :app:assembleDebug`, `adb install -r app/build/outputs/apk/debug/app-debug.apk`), then open
**Settings → Updater**. **Never commit that change** - version bumps are a release-team decision.

- "Automatically check for updates" toggles the startup check (`CheckForUpdatesKey`).
- "Installation method" opens the Standard / Root / Shizuku picker.
- "Check for updates now" runs `UpdateChecker.checkForUpdates()`; the dialog then downloads and installs
  via the chosen method.

| Method | Expect |
|---|---|
| Standard | the system installer opens (after sending you to grant "install unknown apps" if needed, then retrying); finishes with the OS "Open" button |
| Root | the Magisk/SuperSU prompt when Root is first *selected*; "Installing…"; silent install; **the app relaunches itself** |
| Shizuku | needs Shizuku installed + running and the permission; "Installing…"; silent install; **the app closes** with a success toast - reopen it |

A silent install closing the app is expected ([03](03-restart.md)), not a crash.

## Verifying

- Unit tests: `./gradlew :app:testDebugUnitTest --tests "com.jtech.zemer.utils.updater.*"` (includes
  `InstallerTest`: `fromOrdinal` fallback, ordinal stability, `parseSessionId`).
- **Build release too** (`./gradlew :app:assembleRelease`): only R8 exercises the keep rules
  ([04](04-wiring.md)).
- Install failures go through `reportException` with the contexts `Native install`, `Root install`,
  `Shizuku install` and `Shizuku install: hidden API mismatch`.

## When an install fails

| Symptom | Likely cause | Where to look |
|---|---|---|
| Standard does nothing | "install unknown apps" denied | the controller's `NATIVE` gate; `AppInstaller.canInstallPackages` |
| Root: "Root access not available" | `Shell.getShell().isRoot` false (denied / no su) | `installRoot` |
| Shizuku: "not running" / "permission required" | service down or grant missing | `isShizukuAlive` / `hasShizukuPermission`; the `DisposableEffect` listener |
| Shizuku: "not supported on this Android version" | the hidden constructor signature changed | the `NoSuchMethodError` catch in `installShizuku` |
| Root installs but does not relaunch | launcher activity unresolved, or `am start` failed | `AppRestarter.relaunchCommand` |
| Shizuku installs but does not relaunch | expected - no auto-restart | reopen manually |
| Crash in Shizuku hidden APIs on release only | a missing keep rule | `app/proguard-rules.pro` |

## Download progress (related rule)

`UpdateChecker.downloadUpdate` sizes a stable download's progress bar from the **GET response's** content
length after redirects (a nightly uses the mirror's declared `size`), never a separate HEAD: `/download` redirects through a worker + CDN, and a HEAD can be answered
by a different hop (e.g. a challenge page) with the wrong length. A gzip-encoded body is treated as
unknown size (the header would be the compressed length).
