# 03 — Auto-restart after a silent update

## Why the relaunch must run through a privileged shell

A silent install (root or Shizuku) replaces our own package while the app is running. As
part of that swap the OS **kills our process**. So the app cannot "install and then relaunch
itself" inline — the code that would do the relaunching is in the process that is about to
die.

The obvious approach — schedule an `AlarmManager` `PendingIntent.getActivity` and let the
process die — **does not work**, and was the first (broken) implementation. Starting an
activity from the background is blocked on Android 10+ (background-activity-launch
restrictions); the alarm fires but the system silently refuses to launch the activity, so
the user has to reopen the app manually.

The working approach uses the **privileged shell we already hold** for the silent methods.
`am start` issued as root or as the Shizuku shell user is exempt from the background-launch
restriction. `AppRestarter` (`utils/updater/AppRestarter.kt`) builds that command:

```kotlin
fun relaunchCommand(context: Context): String? {
    val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
    val component = launchIntent.component?.flattenToShortString() ?: return null
    return "sleep 1 && am start -n $component"   // short settle, then relaunch
}
```

## The two success signals (one per install model)

The two silent methods finish on different timelines and hold different shells, so each
relaunches itself at the point it knows it succeeded:

| Method | Completion model | How it relaunches |
|---|---|---|
| ROOT | synchronous — `installRoot` returns `InstallResult.Success` | the relaunch is **chained onto the commit**: `pm install-commit <sid> && sleep 1 && am start -n <component>`, run as one root-shell command |
| SHIZUKU | asynchronous — `installShizuku` returns `RequiresUserAction`; the real result is a `PackageInstaller` broadcast | `InstallReceiver`, on `STATUS_SUCCESS`, calls `AppRestarter.relaunchViaShizuku` |

**Root chains the relaunch into one shell command** on purpose: `pm install-commit` kills our
process, but the root shell is a *separate* process that runs the whole `&&` sequence to
completion, so the trailing `am start` still fires after we are gone. Splitting it into a
second `Shell.cmd(...)` call from Kotlin would race the kill.

**Shizuku** can't chain (its result is a broadcast), so it relaunches from `InstallReceiver`
via `Shizuku.newProcess(["sh","-c", cmd], …)` — run in the Shizuku server process, so it
also survives our death. `newProcess` is hidden in the Shizuku API, so it is called
reflectively; failure is caught and reported, never fatal.

The Standard (`NATIVE`) method never reaches either path — it returns `RequiresUserAction`
and the system installer UI offers its own "Open" button.

## Caveat

If the launcher activity can't be resolved (`relaunchCommand` returns null), the silent
install still completes — it just doesn't relaunch, falling back to the manual-reopen
behaviour. The `sleep 1` gives PackageManager a moment to register the freshly installed
activity before `am start` resolves it.
