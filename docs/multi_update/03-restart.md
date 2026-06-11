# 03 — After a silent update: restart and heads-up

A silent install (root or Shizuku) replaces our own package while the app is running. As
part of that swap the OS **kills our process**. Two consequences follow: the app cannot
relaunch itself inline (the code that would do it is in the dying process), and from the
user's view the app simply vanishes. This page covers how each is handled.

## Root auto-restarts; Shizuku does not

| Method | Auto-restart? | Why |
|---|---|---|
| ROOT | **yes** | the root shell is an independent process, so the relaunch chained onto the commit survives our death |
| SHIZUKU | **no** | its privileged process is bound to ours and is reaped together with us — the relaunch can't be made to fire reliably |
| NATIVE | n/a | the system installer UI offers its own "Open" button |

### Why the relaunch must run through a privileged shell

Starting an activity from the background — e.g. via an `AlarmManager`
`PendingIntent.getActivity` — is blocked on Android 10+ (background-activity-launch
restrictions). That was the first implementation, and it never fired: the alarm triggered
but the system silently refused the launch, leaving the user to reopen by hand. `am start`
issued as **root**, by contrast, is exempt. `AppRestarter.relaunchCommand` builds it:

```kotlin
fun relaunchCommand(context: Context): String? {
    val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
    val component = launchIntent.component?.flattenToShortString() ?: return null
    return "am start -n $component"   // caller adds any settle delay
}
```

### Root: chain it onto the commit

`installRoot` runs the relaunch as part of the same root-shell command:

```
pm install-commit <sid> && sleep 1 && am start -n <component>
```

`pm install-commit` kills our process, but the root shell is a *separate* process that runs
the whole `&&` sequence to completion, so the trailing `am start` still fires after we are
gone (the `sleep 1` lets PackageManager register the freshly installed activity first).
Splitting it into a second `Shell.cmd(...)` call from Kotlin would race the kill.

### Shizuku: no auto-restart (and why the attempts failed)

Shizuku's result is an async `PackageInstaller` broadcast, so there is nothing to chain onto.
Two approaches were tried and removed:

- An `AlarmManager` activity start — blocked by the background-launch restriction (above).
- `Shizuku.newProcess(["sh","-c","am start …"])` from `InstallReceiver` — the
  `ShizukuRemoteProcess` is bound to our process, so it is reaped the instant the replace
  kills us, before `am start` lands. Even with no `sleep` the race was unreliable.

So Shizuku does not auto-restart: `InstallReceiver` on `STATUS_SUCCESS` shows the success
toast and the user reopens the app. (If a future fix wants a reliable Shizuku restart it
needs a Shizuku `UserService` that outlives the app, not a one-shot remote process.)

## Making the silent kill less abrupt

Because a silent install kills the app with no system UI in front of it, the install flow
warns the user first instead of just disappearing:

- The install dialog shows an "Installing…" indicator with a per-method note
  (`UpdaterSettings.kt`): root -> *"The app will restart automatically once the update is
  installed."*; Shizuku -> *"The app will close to finish installing. Reopen it to use the
  new version."*
- `rememberApkInstallController` waits `SILENT_INSTALL_HEADS_UP_MS` (1.2 s) before launching
  a non-`NATIVE` install, so that note actually renders before the process is killed. The
  Standard method has no delay (nothing kills the UI).
