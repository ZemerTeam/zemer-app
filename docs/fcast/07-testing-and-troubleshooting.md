# 07 - Testing, limitations, troubleshooting

## Unit tests (pure JVM, no SDK/Android runtime)

The load-bearing decisions are extracted into pure objects so they can be tested without a player, the
SDK or Android (all under `app/src/test/kotlin/com/jtech/zemer/playback/`):

| Test | Covers |
| --- | --- |
| `CastPlaybackTest` | state mapping, `isRemotePlaying`, s↔ms conversion, `steppedVolume`, `shouldStartLocalPlayback`, the video-viewer and task-clear guards |
| `CastAutoAdvanceTest` | `nearEnd` / `finishedNearEnd` / `debouncePassed` / `stalled`, the stale-position reset, `endEdgePositionSec` with positions from a captured Chromecast log (zero-clock-before-IDLE) |
| `CastErrorRecoveryTest` | the ladder, the abandoned-tracks cap, give-up with nowhere to go, burst dedupe, the progress reset |
| `CastNativeLibLoaderTest` | `cacheIsValid`, `pickAbi`, `downloadProgress` |
| `CastConnectTest` | terminal-result mapping; only `Failed` prunes the tapped device |
| `CastDeviceCatalogTest` | refresh-burst merge and the FCast instance-name vs Chromecast TXT-`fn` naming |
| `CastVolumeKeysTest` | key routing: Ignore when not casting / non-volume / local video, Adjust on DOWN, Consume on UP |
| `CastIdleWatchdogTest` | the paused / stalled timeouts and their boundaries |
| `CastRelayProtocolTest` | request-head parsing, `/stream/<token>` paths, Range/Content-Range math, URL hosts |
| `CastStreamRelayTest` | the relay end to end: `urlFor`/`servesUrl`, full + 206 Range serving with CORS, HEAD→GET, preflight, 404/405, forced re-mint on a rejected upstream, byte-exact resume after a mid-body drop, 502 on a null resolve, `stop` |
| `RemoteVolumeTrackerTest` | steps refused until the level is known; clamping; reset per connection |
| `SeekMathTest` | `forwardSeekTarget` never clamps to an unknown (0/unset) duration |

```bash
./gradlew :app:testDebugUnitTest \
  --tests "com.jtech.zemer.playback.Cast*" \
  --tests "com.jtech.zemer.playback.RemoteVolumeTracker*" \
  --tests "com.jtech.zemer.playback.SeekMath*"
```

**Not unit-tested:** the stateful wiring (`FCastDiscoveryHandler`, `CastController`, `PlayerConnection`,
the `MusicService` cast paths) depends on Media3, coroutines, SDK threads and Android, and the project
has no Robolectric. Its decisions live in the pure objects above; the wiring is covered by building both
APKs (`./gradlew :app:assembleDebug :app:assembleRelease`, `bash scripts/ui-audit.sh`) and the manual
checklist.

## Manual checklist (a real receiver on the same Wi-Fi)

1. **First-run download** - enable casting → consent → `Ready`; killing mid-download re-downloads.
2. **Connect** - local pauses, the receiver plays from the current position; in-app and notification
   scrubbers track it.
3. **Transport parity** - play/pause/seek from the in-app button, notification, lock screen, widget and a
   list screen's active-row tap all act on the receiver; a pause tap right after connecting pauses.
4. **Skip** - next/previous from every surface advance the receiver; previous never restarts in-item.
5. **Auto-advance** - a track end loads the next exactly once. **Pause near the end** does not skip.
   **Repeat-one** replays on the receiver.
6. **New queue = current song** - it reloads on the receiver.
7. **Device switch** - connect A then B: A stops, B plays and is not spuriously auto-skipped.
8. **Disconnect** - "Stop casting" or the device leaving Wi-Fi → local resumes paused at the last remote
   position.
9. **Sleep timer** (incl. end-of-song mode) pauses the receiver; the next track loads paused.
10. **Widget** icon and seek bar mirror the receiver; **fullscreen lyrics** follow the remote clock.
11. **Background mid-connect** - the picker is not stuck on "Connecting…" on return.
12. **Volume** on both an FCast receiver and a Chromecast - keys and the slider move the receiver, the TV
    remote moves the slider, other apps keep local volume; keys stay inert until the receiver reports a
    level or the slider is used.
13. **Status viewer** while casting - the receiver pauses, volume keys drive the video, and it resumes on
    close only if it was playing.

## Known limitations (by design)

- **Discovery cannot be stopped** (sender-sdk 0.4.0 has no stop API).
- **ABIs:** only `arm64-v8a` / `armeabi-v7a`; others get `Failed(UNSUPPORTED_DEVICE)`.
- **Volume keys are inert until the receiver's level is known** - the SDK has no volume getter, and
  stepping the `1.0` placeholder could set a quiet receiver near max.
- **Touch-mode dialogs with nothing focused don't route volume keys**: the `Dialog.kt` dialogs use
  `seedFocus = false` (so text fields keep focus) and Compose's key pipeline needs a focused node, so
  keys fall through to the system volume. D-pad use always focuses something.

## Logs and telemetry

Debug builds plant `Timber.DebugTree` (tag = calling class name unless set explicitly); the Crashlytics
tree runs in every build, turning logs into breadcrumbs and `reportException` into non-fatals.

| Logcat tag | Shows |
| --- | --- |
| `CastDeviceAddressResolver` | click-time re-resolves and their failures |
| `CastDeviceRefresher` | refresh bursts: resolved, unreachable-and-pruned, discovery-start failures |
| `NsdDeviceDiscoverer` (SDK) | services found vs resolved |
| `CastRelay` | relay port, start/stop, no route to receiver, "Relay unavailable" (direct-URL fallback) |
| `CastController` | receiver errors with ladder attempt/action; idle-session auto-end |
| `YTPlayerUtils` | stream resolution (casting uses the same `resolveStreamUrl` path) |

```bash
adb logcat -s CastDeviceAddressResolver:V CastDeviceRefresher:V NsdDeviceDiscoverer:V CastRelay:V CastController:V YTPlayerUtils:V
```

| Non-fatal context | Meaning |
| --- | --- |
| `FCast SDK call` | an SDK call threw (`castCall`) |
| `FCast connect` / `FCast createDeviceFromInfo` | the handshake or device construction failed |
| `FCast playback error: <msg>` | the receiver reported an error; the recovery ladder runs |
| `FCast: cast error recovery gave up …` | the ladder exhausted its options; the user got a toast |
| `Cast relay URL` | minting a relay URL threw; the receiver got the direct URL |
| `FCast: could not resolve a stream URL for <id>` | nothing castable for the current item |
| `Cast NSD resolve` / `Cast refresh discovery` | Android NSD threw during a re-resolve / a burst |
| `FCast lib checksum mismatch` | the downloaded `.so` failed SHA verification |

## Troubleshooting

| Symptom | Where to look |
| --- | --- |
| Tap a device → "Couldn't connect" | the re-resolve failed (`CastDeviceAddressResolver`) or the TCP connect was refused / timed out (receiver closed, firewall). The failed tap prunes the entry; a refresh re-adds a live one. `MissingAddresses` non-fatals mean the re-resolve path regressed. |
| A closed receiver stays listed after refresh | its resolve failed outright, making the burst non-authoritative (pruning deliberately blocked); one failed tap prunes it. |
| "Enable casting", nothing downloads | `Failed(UNSUPPORTED_DEVICE)` or `DOWNLOAD_FAILED` (network / GitHub reachability) in `castLibState`. |
| Crash on first connect after an SDK bump | the `CastNativeLib.ABIS` SHAs don't match the `zemer-cast` `sdk-<ver>` assets. |
| Receiver rejects the stream | wrong content type - must be the container MIME from `songMimeCache`, never the codec MIME. |
| Seek bar frozen / jumping | a surface bypassing `currentPositionMs()`/`currentDurationMs()`, or the receiver not emitting `timeChanged`. |
| Auto-skip right after connecting / switching | stale near-end state reaching a detector: `lastProgressSec` must reset on every load/connect/disconnect; the stall detector reads the interpolated clock + `lastRemoteTimeUpdateAt`. |
| Local audio on top of the cast | a transport site gated on `connectedDevice != null`, a raw `player.*` call (e.g. `togglePlayPause()`), or a `playWhenReady` site skipping `shouldStartLocalPlayback`. |
| Double skip at track end | a second reload owner or a broken debounce - only `CastController.onMediaItemTransition` reloads. |
| Receiver errors `Not authorized to access resource.` / `Could not read from resource.` | GStreamer wording for googlevideo 403ing the **receiver's** fetch (the URL is bound to the minting network identity; CGNAT IPv4 vs same-prefix IPv6 makes it intermittent). The relay is the fix and the ladder the backstop; check `CastRelay` for "Relay unavailable". Reproduce by minting a URL with the `tests/` harness and `curl -4` vs `-6`. |

## Bumping the FCast SDK

1. Update `CastNativeLib.SDK_VERSION` and the per-ABI SHA-256s to the new `zemer-cast` `sdk-<ver>`
   assets (the marker check re-downloads for existing installs).
2. Bump the dependency in `app/build.gradle.kts`, keeping the `libfcast_sender_sdk.so` exclusion.
3. If the SDK API changed, update `FCastDiscoveryHandler` / `CastAwarePlayer`, and build both APKs.
4. Re-run the manual checklist on a real receiver.
