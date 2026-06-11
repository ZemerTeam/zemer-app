# 05 — Runbook

## Testing the update flow on-device

The updater only offers an update when the remote version is newer than the installed one.
To exercise download + install without cutting a real release, **temporarily lower the local
version** below what `https://ghtrack.zemer.io/api` reports:

```kotlin
// app/build.gradle.kts — TEMPORARY, restore before any release
versionCode = 30
versionName = "30"
```

Then build, install, and open **Settings -> Updater**:

```
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

- "Automatically check for updates" toggles the startup check (`CheckForUpdatesKey`).
- "Installation method" opens the Standard / Root / Shizuku picker.
- "Check for updates now" runs `UpdateChecker.checkForUpdates()`; if newer, the dialog offers
  Download & Install, which downloads to cache and installs via the chosen method.

> **Restore the version before releasing.** A `versionCode` that goes *down* is a downgrade
> the OS / store rejects, and per CLAUDE.md the version is a release-team decision. The `30`
> value only exists to make the installed build look older than the published one during
> testing.

### Per-method expectations

| Method | What you should see |
|---|---|
| Standard | system installer UI opens; if "install unknown apps" is off, the app sends you to grant it, then retries; finishes with the OS "Open" button |
| Root | Magisk/SuperSU grant prompt the first time Root is *selected*; "Installing…" heads-up; install is silent; **app relaunches itself** shortly after success |
| Shizuku | needs Shizuku installed + running; permission prompt if not yet granted; "Installing…" heads-up; install is silent; **app closes** with a success toast — reopen it manually |

A silent install closes the app mid-update — that is expected ([03](03-restart.md)), not a
crash. The "Installing…" note warns the user first; root then comes back on its own, Shizuku
is reopened by hand.

## Verifying

- Unit tests (no device): `./gradlew :app:testDebugUnitTest --tests "com.jtech.zemer.utils.updater.*"`
  — covers `InstallerType.fromOrdinal` fallback, ordinal stability, and
  `AppInstaller.parseSessionId`.
- **Build release too**: `./gradlew :app:assembleRelease`. R8 is the only thing that
  exercises the ProGuard keep rules for the hidden/Shizuku/libsu classes ([04](04-wiring.md)).
- Logs: install failures go through `reportException` (Timber + Crashlytics non-fatal); look
  for tags around `AppInstaller`/`Native install`/`Root install`/`Shizuku install`.

## When an install fails

| Symptom | Likely cause | Where to look |
|---|---|---|
| Standard does nothing, no prompt | "install unknown apps" denied and not re-requested | `ApkInstallController` gate; `AppInstaller.canInstallPackages` |
| Root: "Root access not available" | `Shell.getShell().isRoot` false (denied / no su) | `AppInstaller.installRoot` |
| Shizuku: "not running" / "permission required" | service down or grant missing | `isShizukuAlive` / `hasShizukuPermission`; the `DisposableEffect` listener |
| Shizuku: "not supported on this Android version" | hidden-constructor signature changed (Android 16+) | the `NoSuchMethodError` catch in `installShizuku` |
| Root install works but app doesn't relaunch | launcher activity unresolved, or `am start` failed | `AppRestarter.relaunchCommand`; root chains it onto the commit |
| Shizuku install works but app doesn't relaunch | expected — Shizuku has no auto-restart | reopen manually; success toast confirms ([03](03-restart.md)) |
| Crash reaching Shizuku hidden APIs on release only | a missing ProGuard keep rule | `app/proguard-rules.pro` |

## Known edge cases (not bugs to "fix" blindly)

- **Shizuku grant lost on navigation.** The grant listener lives in the Updater screen's
  `DisposableEffect`; leaving the screen before answering the Shizuku prompt disposes it and
  the selection is not persisted. Rare (the prompt is usually an overlay over the same
  activity). Fixing properly means an activity-scoped listener; left as-is intentionally.
- **Two install entry points, one path.** If you add install behaviour, add it to
  `rememberApkInstallController`, not to a call site — `MainActivity` and `UpdaterSettings`
  share it on purpose (see [01](01-architecture.md)).

## Download progress accuracy (related fix)

The progress bar reads its total from the **GET response** content length after redirects,
not a separate HEAD. A standalone HEAD to `https://ghtrack.zemer.io/download` is unreliable:
the path redirects through a worker + CDN and a HEAD can be answered by a different hop (e.g.
a Cloudflare challenge page) whose `Content-Length` is not the APK's, which scaled the bar to
the wrong total. gzip-encoded responses are treated as unknown size (the header would be the
compressed length, not the bytes counted). See `UpdateChecker.downloadUpdate` (`213b0a8`).
