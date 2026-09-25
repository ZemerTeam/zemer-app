# 03 - Discovery and connection

`playback/FCastDiscoveryHandler.kt` is the only object that touches the SDK. It implements
`DeviceDiscovererEventHandler` (discovery callbacks) and creates one `DevEventHandler` per connection
(device callbacks).

## Discovery (NSD / mDNS)

`MusicService.startDiscovery()` creates one `NsdDeviceDiscoverer` (SDK class) pointed at the handler,
guarded by `deviceDiscoverer == null && castLibLoader.isReady`. Its `deviceAvailable` / `deviceChanged`
upsert into `discoveredDevices` (keyed by `DeviceInfo.name`) and `deviceRemoved` drops the entry; the
callbacks arrive on SDK NSD threads that are **not contractually serialised**, so every
mutate-then-snapshot runs under `devicesLock` and republishes `discoveredDevicesFlow`.

**No stop API:** sender-sdk 0.4.0's discoverer cannot be stopped, so discovery runs from the first
`startDiscovery()` until process death. It is therefore started only when the picker opens, never from
the Settings toggle.

## State the handler publishes

| Field | Type | Written by | Read by |
| --- | --- | --- | --- |
| `discoveredDevicesFlow` | `List<DeviceInfo>` | discovery callbacks, refresh merge, prune | picker |
| `connectedDeviceFlow` | `CastingDevice?` | `Connected` / teardown | cast buttons, picker |
| `remoteConnectionState` | `DeviceConnectionState` | `connectTo` preset, `connectionStateChanged`, teardown | `isConnected`, `isCasting`, `awaitOutcome` |
| `remotePlaybackState` | `PlaybackState?` | `playbackStateChanged` | `isPlaying`, end detectors, notification |
| `remoteTime` / `remoteDuration` | `Double` seconds | `timeChanged` / `durationChanged`, loads | seek bar, end detectors |
| `remoteVolume` | `Double` 0..1 | `RemoteVolumeTracker` | volume slider |

Cross-thread scalars are `@Volatile` (`connectedDevice`, `onDisconnect`,
`shouldPlay`, `currentStreamUrl`/`ContentType`/`Metadata`, `initialResumePosition`, `remoteTimeUpdatedAt`,
`lastProgressSec`, `videoPlaybackActive`).

## The address gap

The SDK lists a device the moment mDNS finds it - before Android resolved its host (no addresses, port
0). On API < 34 the single-flight `NsdManager.resolveService` loses the race when several services
resolve at once (`FAILURE_ALREADY_ACTIVE`) and the SDK never retries, so the entry can stay address-less
and connecting throws the SDK's `MissingAddresses`. Three pieces close the gap:

- **`CastDeviceAddressResolver.refreshAddresses`** (run by `CastConnector` on every tap) re-resolves when
  `CastConnect.shouldReResolve(protocol, hasAddresses)`: always for FCast (picks up a new DHCP lease;
  `ADDRESS_REFRESH_TIMEOUT_MS`, falling back to the cached addresses), and for address-less entries of
  any protocol (`ADDRESS_RESOLVE_TIMEOUT_MS`, no fallback). Already-resolved **Chromecast** entries are
  skipped - they are named by TXT `fn`, which is not a resolvable instance name. It retries while the
  resolver is busy and writes back into the mutable `DeviceInfo` held in the map, so later taps benefit.
- **`CastConnect.awaitOutcome(remoteConnectionState)`** turns the attempt into `CONNECTED` / `FAILED` /
  `TIMED_OUT` (`CONNECT_TIMEOUT_MS`; the SDK otherwise retries a dead address forever). On timeout the
  connector calls `disconnect()` so the attempt cannot surprise-connect later. Every `Failed` result
  (including a timeout) **prunes the tapped entry** (`shouldPruneDevice` → `pruneDevice`): a force-closed
  receiver sends no mDNS goodbye and lingers in caches, and a failed connect is the only definitive
  unreachability signal. `NoStream` never prunes - that failure is ours, not the device's. A live device
  pruned by mistake is re-added by the SDK or the next refresh.
- **`CastDeviceRefresher.refresh()`** (automatic when the picker opens, plus a manual button) rebuilds
  the list, since the SDK never re-checks a found device. It runs a `DISCOVERY_BURST_MS` burst per
  protocol with fresh NSD listeners, resolves each service, and **TCP-probes** the port
  (`REACHABILITY_PROBE_TIMEOUT_MS`) because mDNS caches keep answering resolves for a dead receiver until
  TTL; an unreachable service contributes no entry. `CastDeviceCatalog.merge` then lets fresh addresses
  win and prunes vanished entries **only for an authoritative protocol** (discovery started and every
  found service resolved), so a flaky resolve can fail to prune but never hide a live device. Chromecast
  naming (TXT `fn`) is mirrored in `CastDeviceCatalog.displayName`. Pinned by `CastDeviceCatalogTest`.

## Connecting (`connectTo`)

1. Silence the previous device: `stopPlayback()` **then** `disconnect()` - a bare disconnect leaves its
   stream playing (the relay keeps serving it), so a device switch would play on both.
2. Reset intent/content: `shouldPlay = true`, the stream URL/type/metadata/resume position, and the
   clock (`remoteTime` = resume position, not 0, so a failed attempt recovers the local player to where
   the user was), `lastProgressSec`, and the volume tracker.
3. `createDeviceFromInfo`; assign `connectedDevice` **synchronously**; preset `remoteConnectionState` to
   `Connecting` so `awaitOutcome` never reads the previous session's `Disconnected` as this attempt's
   failure; `connect(...)` with a new `DevEventHandler`.
4. Returns false (after the single teardown) when device creation or `connect` threw; otherwise the
   outcome lands asynchronously on `remoteConnectionState`.

`CastConnector` clamps the resume position to 0 when the local track is within 1.5 s of its end, so a
connect at a track boundary does not cast the outgoing item at `pos == duration` and trip auto-advance.

## `DevEventHandler` - the per-connection callback sink

**Every callback starts with the stale-device guard** (`handler.connectedDevice !== device` → ignore).
The `stopPlayback` sent to an outgoing device solicits final reports (a `PAUSED`, a clock reset to 0 -
Chromecast does this, an `END`) and its handler stays live until the SDK reader thread dies; unguarded,
a stale `PAUSED` would flip `shouldPlay` off (the new device loads then re-pauses), a stale 0 would
clobber the resume position, and a stale `END` would auto-advance mid-switch.

- `connectionStateChanged`: publishes the state. On `Connected` it publishes `connectedDeviceFlow`, loads
  the current URL - from the last `remoteTime` on a **reconnect** of the same device (a flaky network does
  not restart the track), else from `initialResumePosition` - and re-pauses if `!shouldPlay`. On
  `Disconnected` it runs `onConnectionDisconnected()`.
- `playbackStateChanged`: mirrors the state into `shouldPlay` via `playIntentForState` (a pause from the
  TV remote sticks) - **except** a `PAUSED` within `PAUSED_END_EPSILON_SEC` of the end, which some
  receivers send instead of an end event.
- `timeChanged`: updates `remoteTime` + `remoteTimeUpdatedAt`, and `lastProgressSec` only for `time > 0`
  ([05](05-auto-advance.md)). `durationChanged`, `volumeChanged` update their flows.
- `mediaEvent(END)` → `onTrackEnded` (auto-advance). `playbackError` always reports `FCast playback
  error: …`, then (if not stale) → `onPlaybackError` (the recovery ladder).

## Loading, transport, teardown

- `load(url, type, metadata, resume)` (called by `CastController` on track changes and error recovery)
  resets the content and clock like `connectTo`, loads, and re-pauses if `!shouldPlay`. It deliberately
  does **not** set `shouldPlay = true` ([04](04-playback-and-transport.md#play-intent-shouldplay)).
- `play()` / `pause()` set `shouldPlay` then resume/pause; `seek(seconds)`.
- `disconnect()` ("Stop casting") = `stopPlayback` + `disconnect` + `onConnectionDisconnected()`.
- `onConnectionDisconnected()` is the single teardown: captures the last position, clears
  `connectedDevice`/flows, sets `Disconnected`, resets `lastProgressSec`, invokes `onDisconnect(lastPosMs)`.
- `deviceRemoved` only drops the picker entry; it **never disconnects** an active session (NSD flaps
  transiently; a real drop arrives as `Disconnected` via the session heartbeat).

Every SDK call goes through `castCall { … }`, which reports failures (`FCast SDK call`) instead of
crashing - receivers drop mid-call routinely.
