# 03 — Auto-restart after a silent update

## Why a restart is needed, and why it must be scheduled

A silent install (root or Shizuku) replaces our own package while the app is running. As
part of that swap the OS **kills our process**. So the app cannot "install and then relaunch
itself" inline — the code that would do the relaunching is in the process that is about to
die. The relaunch has to be handed to something that **survives the kill**.

`AppRestarter` (`utils/updater/AppRestarter.kt`) does exactly that: it schedules the
relaunch through `AlarmManager` with a `PendingIntent` to the launcher activity, then lets
the process die:

```kotlin
fun scheduleRestart(context: Context) {
    val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        ?.apply { addFlags(FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_CLEAR_TASK) } ?: return
    val pendingIntent = PendingIntent.getActivity(
        context, RESTART_REQUEST_CODE, launchIntent,
        PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    (context.getSystemService(ALARM_SERVICE) as AlarmManager)
        .set(AlarmManager.RTC, System.currentTimeMillis() + RESTART_DELAY_MS, pendingIntent)  // 600 ms
}
```

It is `set` (inexact) on purpose — we only need the app back *shortly* after the swap, and
inexact alarms avoid the `SCHEDULE_EXACT_ALARM` permission. The whole body is wrapped in
`runCatching { … }.onFailure { reportException(...) }`, so a scheduling failure is reported,
never fatal.

## The two success signals (one per install model)

The two silent methods finish on different timelines, so the restart is triggered from two
places — each at the point that method actually knows it succeeded:

| Method | Completion model | Where `scheduleRestart` is called |
|---|---|---|
| ROOT | synchronous — `installRoot` returns `InstallResult.Success` | `rememberApkInstallController`, in the `runInstall` coroutine, on `Success` |
| SHIZUKU | asynchronous — `installShizuku` returns `RequiresUserAction`; the real result is a `PackageInstaller` broadcast | `InstallReceiver`, on `STATUS_SUCCESS` |

This is why the controller restarts **only** on `Success` and not on `RequiresUserAction`:
restarting on `RequiresUserAction` would relaunch during a Shizuku install that has not
finished (and would also wrongly fire for the Standard installer, whose `ACTION_VIEW`
hand-off also returns `RequiresUserAction`).

## NATIVE deliberately does not auto-restart

The Standard installer hands off to the system package-installer UI, which shows its own
**"Open"** button when the install completes — the OS already offers the relaunch, and our
process is not in control of that flow. So `installNative` returns `RequiresUserAction`,
which never reaches either restart trigger. Auto-restart is a silent-method-only behaviour.

## Caveat

A self-replace does not *always* kill the process instantly on every OEM. If it does not,
the scheduled alarm still brings the launcher activity forward (with
`FLAG_ACTIVITY_CLEAR_TASK`), so the user lands on a fresh start either way; the old process,
if still alive, is running pre-swap code and will be reaped normally. We intentionally do
**not** force-kill (`exitProcess`/`Process.killProcess`) to keep the success toast and any
in-flight UI from being torn down mid-frame.
