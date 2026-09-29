# 03 - After a silent update: restart and heads-up

A silent install (root or Shizuku) replaces our package while the app runs, and the OS **kills our
process**. The app therefore cannot relaunch itself inline, and to the user it simply vanishes.

| Method | Auto-restart? | Why |
|---|---|---|
| ROOT | **yes** | the root shell is an independent process, so a relaunch chained onto the commit survives our death |
| SHIZUKU | **no** | its remote process is bound to ours and reaped with it |
| NATIVE | n/a | the system installer offers its own "Open" button |

## The root relaunch

**Never relaunch through an activity start from our own process** (e.g. an `AlarmManager`
`PendingIntent.getActivity`): background activity launches are blocked on Android 10+, so it silently
never fires. `am start` issued as **root** is exempt. `AppRestarter.relaunchCommand(context)` returns
`am start -n <launcher component>` (or null if the launch activity cannot be resolved), and
`installRoot` runs it **in the same shell command** as the commit:

```
pm install-commit <sid> && sleep 1 && am start -n <component>
```

The root shell runs the whole `&&` chain after we are killed; `sleep 1` lets PackageManager register the
new activity. Issuing the relaunch as a second `Shell.cmd(...)` from Kotlin would race the kill.

## Shizuku: no auto-restart

There is nothing to chain onto (the result is an async broadcast), and both alternatives fail: an
`AlarmManager` activity start is blocked as above, and `Shizuku.newProcess(["sh","-c","am start …"])` from
`InstallReceiver` is reaped with our process before `am start` lands. `InstallReceiver` just toasts on
success and the user reopens the app. A reliable Shizuku restart would need a Shizuku `UserService` that
outlives the app.

## The heads-up

So the silent kill is not abrupt, the install dialog (the shared `ui/component/UpdateDownloadDialog.kt`,
used by both entry points) shows "Installing…" with the method's `InstallerType.installingNote` (root:
will restart automatically; Shizuku: will close, reopen it). `rememberApkInstallController` waits
`SILENT_INSTALL_HEADS_UP_MS` (1.2 s) before any non-`NATIVE` install so the note actually renders;
Standard has no delay.
