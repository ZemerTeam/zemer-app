# 07 — Testing, limitations, troubleshooting

## Unit tests (pure logic, no SDK/Android)

The end-of-track and clock/state logic is extracted into pure objects precisely so
it is unit-testable on the JVM without a player, the FCast SDK, or an Android
runtime:

| Test | Covers |
| --- | --- |
| `CastPlaybackTest` (6) | `isPlaying`/`isPaused`/`playIntentForState` state mapping; seconds↔ms conversion + round-trip. |
| `CastAutoAdvanceTest` (9) | `nearEnd` boundary/zero-duration; `debouncePassed`/`stalled` strict windows; combined idle/stall scenarios; the stale-position-reset regression for the device-switch auto-skip. |
| `CastNativeLibLoaderTest` (5) | `cacheIsValid` (exists + SHA match, stale/missing/partial rejection) and `pickAbi`. |

Run them:

```bash
./gradlew :app:testDebugUnitTest --tests "com.jtech.zemer.playback.Cast*"
```

## What is NOT unit-tested (and why)

The stateful wiring — `FCastDiscoveryHandler`, `PlayerConnection`, and the
`MusicService` cast paths — depends on Media3, coroutines, SDK callback threads,
and Android. The project has **no Robolectric**, so these layers can't be unit
tested without heavy new infrastructure. Per the engineering rules this is called
out explicitly rather than skipped: the load-bearing *decisions* inside them are
pushed down into the pure objects above (which are tested), and the wiring itself
is verified by builds + the manual checklist below.

## Build verification

Always build both — release runs R8 and catches shrink/keep-rule breakage debug
never will:

```bash
./gradlew :app:assembleDebug :app:assembleRelease
bash scripts/ui-audit.sh
```

## Manual test checklist (needs a real receiver)

Casting can only be fully validated on a device + an FCast receiver on the same
Wi-Fi. The high-value paths:

1. **First-run download** — fresh install → Settings → Enable casting → consent →
   download → `Ready`. Kill mid-download → relaunch re-downloads (no trusted
   partial).
2. **Connect & play** — open picker, pick a device → local pauses, receiver plays
   from the current position; the in-app + notification scrubbers track the TV.
3. **Transport parity** — play/pause and seek from: in-app button, notification,
   lock screen, and the home-screen widget all act on the receiver.
4. **Skip** — next/previous (in-app, notification, widget) advance the receiver;
   skip-previous doesn't restart on a >3 s position.
5. **Auto-advance** — let a track end → the next loads automatically, exactly once
   (no double-skip).
6. **Pause near end** — pause within ~3 s of the end → it must NOT auto-skip.
7. **Repeat-one** — replays the same track on the receiver.
8. **New queue = current song** — start a playlist whose first track is the one
   already casting → it reloads/restarts on the receiver.
9. **Device switch** — connect A, then connect B → B plays from its start; B is
   not spuriously auto-skipped.
10. **Disconnect** — "Stop casting" (and: device drops off Wi-Fi) → local resumes
    at the last remote position, **paused**.

## Known limitations (by design)

- **Notification play/pause icon while casting** reflects the paused local
  player, not the receiver. Scrubber + seek/skip are correct. Mirroring the icon
  needs synthesised `Player.Event`s — deferred. (`CastAwarePlayer` KDoc.)
- **Auto-advance needs a bound `PlayerConnection`.** If the Activity is destroyed
  while the service keeps casting, the queue won't auto-advance until a
  `PlayerConnection` rebinds. The deeper fix (own the advance loop in
  `MusicService`) is deferred because the obvious second-owner implementation
  double-loaded the receiver. (`MusicService.onMediaItemTransition` note,
  [05](05-auto-advance.md).)
- **Discovery can't be stopped** (sender-sdk 0.4.0 `NsdDeviceDiscoverer` has no
  stop API) — it runs from first `startDiscovery()` until the process dies.
- **ABI** — only `arm64-v8a` / `armeabi-v7a`; other devices report
  `Failed(UNSUPPORTED_DEVICE)`.

## Troubleshooting

| Symptom | Likely cause / where to look |
| --- | --- |
| "Enable casting" then nothing downloads | `Failed(UNSUPPORTED_DEVICE)` (ABI) or `DOWNLOAD_FAILED` (network / GitHub release reachability). Check `castLibState`; non-fatals via `reportException`. |
| Cast crashes on first connect after an SDK bump | A trusted stale/corrupt `.so`. The marker SHA should prevent this; verify `CastNativeLib.ABIS` SHAs match the `zemer-cast` `sdk-<ver>` release assets. |
| Receiver rejects the stream | Wrong content type. `currentContentType`/`streamContentType` must return the **container** MIME from `songMimeCache` (populated by `resolveStreamUrl`), never the codec MIME. |
| Seek bar frozen / jumping while casting | A surface bypassing `currentPositionMs()`/`currentDurationMs()`, or `remoteTime` not updating (receiver not emitting `timeChanged`). |
| Track auto-skips right after connecting / switching | Stale `lastRemotePosition` — confirm the `remoteTime` collector records position unconditionally (the `0` reset must clear it). Regression-tested in `CastAutoAdvanceTest`. |
| Local audio plays on top of the cast | A transport site routing on `connectedDevice != null` instead of `isConnected`/`isCasting`, or a `player.*` call that bypassed the seam. |
| Double-skip at end of track | Two reload owners or a broken debounce — only `PlayerConnection` may reload; `advanceRemoteAfterEnd` must stamp `lastTransitionTime`. |

## When you bump the FCast SDK version

1. Update `CastNativeLib.SDK_VERSION` and the per-ABI **SHA-256** values to the
   new `zemer-cast` `sdk-<ver>` release assets (the marker check forces a
   re-download for existing installs automatically).
2. Bump the Gradle dependency version in `app/build.gradle.kts` (keep the
   `libfcast_sender_sdk.so` packaging exclusion).
3. If the SDK API shape changed (not just the lib), update
   `FCastDiscoveryHandler` / `CastAwarePlayer` accordingly and rebuild both APKs.
4. Re-run the manual checklist against a real receiver.
