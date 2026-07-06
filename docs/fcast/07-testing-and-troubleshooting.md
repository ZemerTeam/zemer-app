# 07 — Testing, limitations, troubleshooting

## Unit tests (pure logic, no SDK/Android)

The end-of-track and clock/state logic is extracted into pure objects precisely so
it is unit-testable on the JVM without a player, the FCast SDK, or an Android
runtime:

| Test | Covers |
| --- | --- |
| `CastPlaybackTest` (11) | `isPlaying`/`isPaused`/`playIntentForState` state mapping; seconds↔ms conversion + round-trip; `steppedVolume` step/clamp math; `shouldStartLocalPlayback` (the async-queue dual-playback guard). |
| `CastAutoAdvanceTest` (16) | `nearEnd` boundary/zero-duration; `debouncePassed`/`stalled` strict windows; combined idle/stall scenarios; the stale-position-reset regression for the device-switch auto-skip; `endEdgePositionSec` with the exact positions from a captured Chromecast sender log (zero-clock-before-IDLE). |
| `CastErrorRecoveryTest` (7) | The receiver-playback-error ladder: reload → fresh resolve → advance escalation; the consecutive-abandoned-tracks cap; give-up when the queue can't advance (repeat-one / last track); error-burst dedupe; the progress threshold that resets the counters. |
| `CastNativeLibLoaderTest` (6) | `cacheIsValid` (exists + SHA match, stale/missing/partial rejection), `pickAbi`, and `downloadProgress` (fraction / null-when-unknown). |
| `CastConnectTest` (8) | The connect-flow decisions: terminal-result mapping and which results prune the tapped device from the picker (only `Failed`; `NoStream` never proved the device dead). |
| `CastDeviceCatalogTest` (10) | Rebuilding the picker list from a refresh burst: the FCast instance-name vs. Chromecast TXT-`fn` naming rules that merge refreshed entries onto the SDK's map keys. |
| `CastVolumeKeysTest` (5) | The hardware-volume-key routing rule (`decide`): app-scoped Ignore when not casting / non-volume key; Adjust on ACTION_DOWN; ACTION_UP consumed so the system volume UI doesn't flash. |
| `RemoteVolumeTrackerTest` (5) | The unknown-until-reported stepping rule: steps refused (and the placeholder undisturbed) until the receiver reports or the slider sets; clamping; reset on a fresh connection. |
| `SeekMathTest` (3) | `forwardSeekTarget`: clamp to a known duration, and the no-clamp rule for an unknown (0 / unset) duration — a cast track before the receiver reports its duration must not snap a forward double-tap to 0. |

Run them (note `RemoteVolumeTracker*`/`SeekMath*` don't match `Cast*`):

```bash
./gradlew :app:testDebugUnitTest \
  --tests "com.jtech.zemer.playback.Cast*" \
  --tests "com.jtech.zemer.playback.RemoteVolumeTracker*" \
  --tests "com.jtech.zemer.playback.SeekMath*"
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
   lock screen, and the home-screen widget all act on the receiver. Include a
   list screen's active-row tap (e.g. a curated playlist) — it must toggle the
   receiver, never resume phone audio on top of it. A pause tap **immediately
   after connecting** (before the receiver's first state report) must pause,
   not silently re-assert play.
4. **Skip** — next/previous (in-app, notification, widget, fullscreen lyrics
   screen) advance the receiver; skip-previous doesn't restart on a >3 s
   position (it must skip to the previous item — a within-item restart never
   reloads the receiver).
5. **Auto-advance** — let a track end → the next loads automatically, exactly once
   (no double-skip).
6. **Pause near end** — pause within ~3 s of the end → it must NOT auto-skip.
7. **Repeat-one** — replays the same track on the receiver.
8. **New queue = current song** — start a playlist whose first track is the one
   already casting → it reloads/restarts on the receiver.
9. **Device switch** — connect A, then connect B → **A stops** (stopPlayback is
   sent before its socket drops — two receivers must never play at once); B
   plays from its start and is not spuriously auto-skipped (A's stop-solicited
   reports are dropped by the stale-device guards).
10. **Disconnect** — "Stop casting" (and: device drops off Wi-Fi) → local resumes
    at the last remote position, **paused**.
11. **Sleep timer** — set a short timer (and the end-of-song mode) while casting
    → the **receiver** pauses when it fires; in end-of-song mode the next track
    loads paused instead of playing on.
12. **Widget state** — while casting, the widget's icon mirrors the receiver
    (pause icon while the TV plays; flips on remote play/pause) and its seek bar
    tracks the remote clock.
13. **Lyrics screen** — open fullscreen lyrics while casting → synced lyrics
    advance with the receiver and the slider moves.
14. **Background mid-connect** — tap a device, immediately background the app,
    return → the picker is not stuck on "Connecting…"; the attempt resolves (or
    times out and aborts) and rows re-enable.
15. **Volume** — while casting: hardware buttons and the 3-dot slider move the
    **receiver's** volume; a volume change from the TV's own remote moves the
    slider; in another app the buttons stay local. Do this on **both** an FCast
    receiver and a Chromecast — if the receiver never sends a `volumeChanged`
    report, button steps stay inert until the slider is used once (by design, see
    below), and that's worth knowing per receiver type.

## Known limitations (by design)

- **Discovery can't be stopped** (sender-sdk 0.4.0 `NsdDeviceDiscoverer` has no
  stop API) — it runs from first `startDiscovery()` until the process dies.
- **ABI** — only `arm64-v8a` / `armeabi-v7a`; other devices report
  `Failed(UNSUPPORTED_DEVICE)`.
- **Volume buttons are inert until the receiver's level is known** (its first
  `volumeChanged` report, or one slider set). The SDK has no volume *getter*, so
  stepping before that would act on a `1.0` placeholder and could set a quiet
  receiver to near-max on the first press (`RemoteVolumeTracker`).
- **Touch-mode dialogs without focused content don't route volume keys.** The
  `Dialog.kt` dialogs use `seedFocus = false` (so text fields keep their
  auto-focus); Compose's key pipeline needs *a* focused node, so with nothing
  focused (touch mode, no text field) volume keys fall through to the system
  volume while such a dialog is open. D-pad use always focuses something, so the
  G1 is unaffected.

## Debugging: log tags & error telemetry

All cast logging goes through **Timber**. Debug builds plant `Timber.DebugTree`,
which tags each line with the **calling class's simple name** — there are no
hand-written cast log tags. The tags worth filtering:

| Logcat tag | Source | What it shows |
| --- | --- | --- |
| `CastDeviceAddressResolver` | app (Timber) | Click-time NSD re-resolves: refreshed addresses/port, resolve failures + error codes. |
| `CastDeviceRefresher` | app (Timber) | The refresh burst: what resolved, what TCP-probed unreachable and got pruned, discovery-start failures. |
| `NsdDeviceDiscoverer` | FCast SDK | The SDK's own discovery: services found vs. resolved. |
| `YTPlayerUtils` | app (streaming) | Stream URL resolution — casting resolves the URL through the same validated path as local playback (`resolveStreamUrl`), so a cast that "loads nothing" often debugs here. |
| `MusicService` | app | General service lifecycle around the cast session. |

One command for a cast session:

```bash
adb logcat -s CastDeviceAddressResolver:V CastDeviceRefresher:V NsdDeviceDiscoverer:V YTPlayerUtils:V MusicService:V
```

**Release builds have no logcat output** (only the `CrashReportingTree` is
planted): every Timber log (DEBUG+) becomes a Crashlytics **breadcrumb**, and
errors are reported as **non-fatal issues** via `reportException`. The cast
non-fatals to look for in Crashlytics, and what each means:

| Non-fatal context | Meaning |
| --- | --- |
| `FCast SDK call` | Any SDK call threw (`castCall` wraps every one — receivers misbehave; we never crash). |
| `FCast connect` / `FCast createDeviceFromInfo` | The connect handshake or device construction failed. |
| `FCast playback error: <msg>` | The receiver itself reported a playback error. Also triggers the recovery ladder (`CastErrorRecovery`): reload → fresh resolve → advance (capped) instead of leaving the session dead. |
| `FCast: cast error recovery gave up …` | The ladder exhausted its options (repeated errors across tracks, or repeat-one/last-track with a dead load); the user got a toast. |
| `FCast: could not resolve a stream URL for <id>` | `CastController` had nothing castable for the current item. |
| `Cast NSD resolve` / `Cast refresh discovery` | The Android NSD layer threw during re-resolve / the refresh burst. |
| `FCast lib checksum mismatch` | The downloaded `.so` failed SHA verification (see [02](02-on-demand-native-lib.md)). |

## Troubleshooting

| Symptom | Likely cause / where to look |
| --- | --- |
| Tap a device, toast "Couldn't connect" (was: nothing happens) | The entry was discovered without addresses and the click-time re-resolve also failed (`CastDeviceAddressResolver`), or the TCP connect was refused / timed out (receiver not actually listening — firewall on port 46899, receiver app closed). `adb logcat -s NsdDeviceDiscoverer:V` shows found vs. resolved services; `MissingAddresses` non-fatals mean the re-resolve path regressed. The failed tap prunes the entry from the picker (it re-appears via refresh if actually alive). |
| A closed receiver stays listed after refresh | Shouldn't happen anymore: a **force-closed** receiver sends no mDNS goodbye and caches keep answering its resolve for up to the records' TTL, but the refresh burst TCP-probes every resolved service and prunes the unreachable (`CastDeviceRefresher.probeReachable`). If one still lingers, its resolve failed outright (non-authoritative burst — pruning deliberately blocked); one failed tap prunes it. |
| "Enable casting" then nothing downloads | `Failed(UNSUPPORTED_DEVICE)` (ABI) or `DOWNLOAD_FAILED` (network / GitHub release reachability). Check `castLibState`; non-fatals via `reportException`. |
| Cast crashes on first connect after an SDK bump | A trusted stale/corrupt `.so`. The marker SHA should prevent this; verify `CastNativeLib.ABIS` SHAs match the `zemer-cast` `sdk-<ver>` release assets. |
| Receiver rejects the stream | Wrong content type. `currentContentType`/`streamContentType` must return the **container** MIME from `songMimeCache` (populated by `resolveStreamUrl`), never the codec MIME. |
| Seek bar frozen / jumping while casting | A surface bypassing `currentPositionMs()`/`currentDurationMs()`, or `remoteTime` not updating (receiver not emitting `timeChanged`). |
| Track auto-skips right after connecting / switching | Stale `lastRemotePosition` — confirm the `remoteTime` collector records position unconditionally (the `0` reset must clear it). Regression-tested in `CastAutoAdvanceTest`. |
| Local audio plays on top of the cast | A transport site routing on `connectedDevice != null` instead of `isConnected`/`isCasting`, or a `player.*` call that bypassed the seam. |
| Double-skip at end of track | Two reload owners or a broken debounce — only `PlayerConnection` may reload; `advanceRemoteAfterEnd` must stamp `lastTransitionTime`. |
| Receiver errors `Not authorized to access resource.` (instant) or `Could not read from resource.` (mid-track) | GStreamer-speak for **googlevideo refusing the receiver's HTTP fetch** (403) — the stream URL is network-identity-bound and the *receiver* fetches it from its own address. Measured on T-Mobile home internet (2026-07): every IPv4/CGNAT fetch is 403 (CGNAT egress IP is per-flow, the binding never matches) while any IPv6 fetch in the home /64 succeeds — so the receiver's per-connection IPv4-vs-IPv6 pick makes it intermittent: an unlucky first connection dies instantly, an unlucky buffer-refill reconnect dies minutes in. It looks exactly like "auto-advance broke" but the advance logic is fine. The recovery ladder (`CastErrorRecovery`) now reloads / re-resolves / advances instead of dying silently; the root fix (receiver fetches via a relay on the phone) is future work. Diagnose with the `tests/` harness: mint a URL and `curl` it with `-4` vs `-6`. |

## When you bump the FCast SDK version

1. Update `CastNativeLib.SDK_VERSION` and the per-ABI **SHA-256** values to the
   new `zemer-cast` `sdk-<ver>` release assets (the marker check forces a
   re-download for existing installs automatically).
2. Bump the Gradle dependency version in `app/build.gradle.kts` (keep the
   `libfcast_sender_sdk.so` packaging exclusion).
3. If the SDK API shape changed (not just the lib), update
   `FCastDiscoveryHandler` / `CastAwarePlayer` accordingly and rebuild both APKs.
4. Re-run the manual checklist against a real receiver.
