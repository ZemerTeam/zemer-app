# 03 — Discovery and connection

Everything in this page lives in `playback/FCastDiscoveryHandler.kt`. The handler
is the only object that touches the SDK; it implements
`DeviceDiscovererEventHandler` (discovery callbacks) and owns a `DevEventHandler`
(per-connection device callbacks).

## Discovery (NSD / mDNS)

`MusicService.startDiscovery()` lazily creates an `NsdDeviceDiscoverer` (an SDK
class) pointed at the handler:

```kotlin
fun startDiscovery() {
    if (deviceDiscoverer == null && castLibLoader.isReady) {
        deviceDiscoverer = NsdDeviceDiscoverer(this, discoveryHandler)
    }
}
```

The discoverer calls back `deviceAvailable` / `deviceChanged` / `deviceRemoved`
on the SDK's NSD threads. Those threads are **not contractually serialised**, so
every mutate-then-snapshot of the device map is guarded:

```kotlin
private val devicesLock = Any()
val discoveredDevices = mutableMapOf<String, DeviceInfo>()   // keyed by deviceInfo.name

override fun deviceAvailable(deviceInfo: DeviceInfo) {
    discoveredDevicesFlow.value = synchronized(devicesLock) {
        discoveredDevices[deviceInfo.name] = deviceInfo
        discoveredDevices.values.toList()
    }
}
```

`discoveredDevicesFlow: StateFlow<List<DeviceInfo>>` is what the picker renders.

> **No stop API.** sender-sdk 0.4.0's `NsdDeviceDiscoverer` has no way to stop.
> Once `startDiscovery()` runs, mDNS discovery continues until the process dies.
> `startDiscovery()` is therefore idempotent (guarded by `deviceDiscoverer ==
> null`) and only ever started when the picker is actually opened — never from the
> Settings toggle. This is a known SDK limitation, noted in the KDoc.

## State the handler publishes

| Field | Type | Written by | Read by |
| --- | --- | --- | --- |
| `discoveredDevicesFlow` | `StateFlow<List<DeviceInfo>>` | discovery callbacks | picker |
| `connectedDeviceFlow` | `StateFlow<CastingDevice?>` | connect / disconnect | cast button icon, picker |
| `remoteConnectionState` | `StateFlow<DeviceConnectionState>` | `connectionStateChanged` | `isConnected`, `isCasting` |
| `remotePlaybackState` | `StateFlow<PlaybackState?>` | `playbackStateChanged` | `isPlaying`, auto-advance |
| `remoteTime` | `StateFlow<Double>` (seconds) | `timeChanged` | seek bar, stall detector |
| `remoteDuration` | `StateFlow<Double>` (seconds) | `durationChanged` | seek bar, near-end test |

Cross-thread scalar intent/connection fields are `@Volatile`:
`connectedDevice`, `onDisconnect`, `shouldPlay`, `currentStreamUrl`,
`currentContentType`, `currentMetadata`, `initialResumePosition`. They are
written on SDK callback threads and read on the main thread; `@Volatile`
publishes the write. (`remoteConnectionState.value` — read by `isConnected` —
gets the same cross-thread visibility for free as a `StateFlow`, which is why
it, not the bare `connectedDevice`, is the routing predicate.)

## The address gap: devices are listed before they are resolvable

sender-sdk 0.4.0's discoverer publishes a device (`deviceAvailable`) the moment
mDNS *finds* the service — before Android has resolved its host, i.e. with an
**empty address list and port 0**. The real addresses arrive later via
`deviceChanged` *if* resolution succeeds. On API < 34 the legacy single-flight
`NsdManager.resolveService` loses the race whenever several services resolve at
once (`FAILURE_ALREADY_ACTIVE`), the SDK never retries, and the entry stays
address-less forever. Connecting to such an entry throws the SDK's
`MissingAddresses` — historically swallowed, which read as "tap connect and
nothing happens".

Three app-side pieces close the gap:

- **`CastDeviceAddressResolver.refreshAddresses(deviceInfo)`** (driven by
  `CastConnector.connect`, the picker's single connect entry point) re-runs the
  NSD resolve at click time — always for FCast entries (a receiver may hold a
  new DHCP lease; the cached addresses are kept as a fallback if the re-resolve
  fails), and for address-less entries of any protocol (full
  `ADDRESS_RESOLVE_TIMEOUT_MS` budget — no fallback exists). It retries while
  the single-flight resolver is busy and writes the result back into the
  **mutable** `DeviceInfo` — the same instance held in the device map, so later
  taps are fixed too. Already-resolved *Chromecast* entries are not re-resolved:
  they are named by TXT `fn`, which is not a resolvable mDNS instance name
  (`CastConnect.shouldReResolve`).
- **`CastConnect.awaitOutcome(remoteConnectionState)`** turns the attempt into a
  terminal `CONNECTED` / `FAILED` / `TIMED_OUT` the UI can surface. On timeout
  the connector calls `disconnect()` so a zombie attempt can't surprise-connect
  after the user was told it failed. A `Failed` result also **prunes the tapped
  entry** (`pruneDevice`) — the one definitive unreachability signal. This covers
  the case discovery can never prune: a **force-closed** receiver sends no mDNS
  goodbye, its advertisement lingers in caches, the refresh burst still "finds"
  it, and the failed resolve deliberately blocks pruning (non-authoritative). A
  `NoStream` result never prunes — that failure is ours, not the device's. If a
  pruned device is actually alive, the SDK's events or the next refresh re-add
  it within seconds.
- **`CastDeviceRefresher.refresh()`** ("reload devices") rebuilds the list on
  demand — the SDK's discoverer never re-checks a device once found, and can't
  even be restarted. A refresh runs a short discovery **burst** with our own NSD
  listeners (a fresh listener is immediately told about every service currently
  advertised), resolves each found service, **TCP-probes** the resolved port
  (mDNS caches keep answering resolves for a force-closed receiver until the
  records' TTL runs out — only an actual connection attempt separates alive from
  lingering; an unreachable service contributes no entry and is pruned), and
  merges via `CastDeviceCatalog.merge`: fresh addresses win, vanished entries
  are pruned — but only when that protocol's burst is **authoritative**
  (discovery started and every found service resolved), so a flaky resolve can
  only fail to prune, never hide a live device. Chromecast naming (SDK keys those entries by TXT
  `fn`, not the instance name) is mirrored in `CastDeviceCatalog.displayName`;
  an unresolved Chromecast contributes no entry for exactly that reason. The
  picker triggers one refresh automatically when opened and offers a manual
  refresh button; merge rules are pinned by `CastDeviceCatalogTest`.

## Connecting

```kotlin
fun connectTo(deviceInfo, streamUrl, contentType, metadata, resumePosition, onTrackEnded): Boolean {
    connectedDevice?.let { d ->                       // silence + drop any previous device:
        castCall { d.stopPlayback() }                 // a bare disconnect leaves its loaded stream
        castCall { d.disconnect() }                   // playing (the relay keeps serving it) — a
    }                                                 // device switch must not leave both playing
    shouldPlay = true                                 // explicit play intent
    currentStreamUrl = streamUrl; currentContentType = contentType
    currentMetadata = metadata; initialResumePosition = resumePosition
    remoteTime.value = resumePosition; remoteDuration.value = 0.0   // failed attempt recovers HERE, not 0

    val newDevice = runCatching { castContext.createDeviceFromInfo(deviceInfo) } … ?: return false
    connectedDevice = newDevice                       // synchronous — see below
    remoteConnectionState.value = Connecting          // preset for awaitOutcome — see below
    val issued = runCatching { newDevice.connect(null, DevEventHandler(this, newDevice, onTrackEnded), 1000u) } …
    if (!issued) onConnectionDisconnected()           // single teardown path; returns false
    return issued
}
```

`connectTo` returns **false** when the attempt could not even be issued (device
creation or the connect call threw — e.g. `MissingAddresses`); the async outcome
of an issued attempt lands on `remoteConnectionState`, which is **preset to
`Connecting`** so `CastConnect.awaitOutcome` can never read the *previous*
session's `Disconnected` (the `StateFlow`'s current value) as this attempt
failing.

`connectedDevice` is assigned **synchronously**, before the device reports
`Connected`. That is intentional: `DevEventHandler.connectionStateChanged`
ignores every callback from a device that is no longer `connectedDevice` (one we
replaced or tore down), so a stale async `Disconnected` can neither null out the
new connection nor overwrite the new attempt's state in
`remoteConnectionState`. **But** that early assignment is exactly why
`connectedDevice != null` is the wrong predicate for routing transport — use
`isConnected` (= `remoteConnectionState` is `Connected`). See
[04](04-playback-and-transport.md).

## The per-connection callback sink: `DevEventHandler`

`DevEventHandler : DeviceEventHandler` receives everything the receiver reports:

```kotlin
override fun connectionStateChanged(state: DeviceConnectionState) {
    if (handler.connectedDevice !== device) return   // stale device (replaced / torn down) — ignore entirely
    handler.remoteConnectionState.value = state
    if (state is Connected) {
        handler.connectedDeviceFlow.value = device
        val url = handler.currentStreamUrl; val type = handler.currentContentType
        if (url != null && type != null) {
            val pos = if (isReconnect && handler.remoteTime.value > 0) handler.remoteTime.value
                      else handler.initialResumePosition
            castCall { device.load(urlLoadRequest(url, type, pos, handler.currentMetadata)) }
            if (!handler.shouldPlay) castCall { device.pausePlayback() }   // honour paused intent
        }
    } else if (state is Disconnected) {
        handler.onConnectionDisconnected()
    }
}

override fun playbackStateChanged(state) {
    if (handler.connectedDevice !== device) return   // stale-device guard — see below
    handler.remotePlaybackState.value = state
    CastPlayback.playIntentForState(state)?.let { handler.shouldPlay = it }  // mirror TV remote
}
override fun timeChanged(t)     { /* stale guard */ handler.remoteTime.value = t }
override fun durationChanged(d) { /* stale guard */ handler.remoteDuration.value = d }
override fun mediaEvent(e)      { /* stale guard */ if (e.type == END) onTrackEnded?.invoke() }   // → auto-advance
override fun playbackError(m)   { reportException(…); /* stale guard */ onPlaybackError?.invoke(m) }
```

**Every callback carries the stale-device guard** (`handler.connectedDevice !==
device`), not just `connectionStateChanged`. The `stopPlayback` that `connectTo`
sends to the outgoing device *solicits* final reports from it — a `PAUSED`
state, a clock reset to `0` (Chromecast does this on stop), an `END` media
event — and its `DevEventHandler` stays live until the SDK's reader thread
dies. Unguarded, those reports would race the new connection's freshly-reset
state: a stale `PAUSED` flips `shouldPlay` off (the switched-to device loads
and immediately re-pauses — a silent device switch), a stale `timeChanged(0)`
clobbers the resume position a failed connect recovers the local player to,
and a stale `END` auto-advances the queue mid-switch.

Note the **reconnect** branch: if the device drops and re-establishes mid-track
(`wasConnected` was already true), it resumes from the last known `remoteTime`
rather than the original resume position — so a flaky network reconnect doesn't
restart the track.

## Loading a (new) track onto the receiver

`load()` is called by `CastController.triggerRemoteLoad` on every track change
while casting:

```kotlin
fun load(streamUrl, contentType, metadata, resumePosition) {
    currentStreamUrl = streamUrl; currentContentType = contentType
    currentMetadata = metadata; initialResumePosition = resumePosition
    remoteTime.value = resumePosition; remoteDuration.value = 0.0   // reset clock for new track
    connectedDevice?.let { d ->
        castCall { d.load(urlLoadRequest(streamUrl, contentType, resumePosition, metadata)) }
        if (!shouldPlay) castCall { d.pausePlayback() }   // preserve play intent (see 04)
    }
}
```

`load()` deliberately does **not** force `shouldPlay = true` — see
[04 — play intent](04-playback-and-transport.md#play-intent-shouldplay).

## Transport + teardown

```kotlin
fun play()  { shouldPlay = true;  castCall { connectedDevice?.resumePlayback() } }
fun pause() { shouldPlay = false; castCall { connectedDevice?.pausePlayback() } }
fun seek(p) { castCall { connectedDevice?.seek(p) } }                 // p in remote seconds

fun disconnect() {                       // explicit "Stop casting"
    connectedDevice?.let { castCall { it.stopPlayback() }; castCall { it.disconnect() } }
    onConnectionDisconnected()
}
fun onConnectionDisconnected() {         // the single teardown path
    val lastPos = CastPlayback.remoteSecondsToMs(remoteTime.value)
    connectedDevice = null; connectedDeviceFlow.value = null
    remotePlaybackState.value = null
    remoteConnectionState.value = Disconnected
    onDisconnect?.invoke(lastPos)        // CastController resumes local at lastPos, paused
}

override fun deviceRemoved(deviceName) {            // service vanished from NSD discovery
    discoveredDevicesFlow.value = synchronized(devicesLock) { discoveredDevices.remove(deviceName); … }
    // NB: only drops it from the picker list — does NOT disconnect an active session. NSD "Service lost"
    // flaps transiently; the cast connection has its own heartbeat, so a real drop arrives as Disconnected.
}
```

Every SDK call goes through `castCall { … }`, which `runCatching`s the throwing
SDK call and routes failures to `reportException` instead of crashing — receivers
drop mid-call routinely.
