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

## Connecting

```kotlin
fun connectTo(deviceInfo, streamUrl, contentType, metadata, resumePosition, onTrackEnded) {
    castCall { connectedDevice?.disconnect() }       // drop any previous device
    shouldPlay = true                                 // explicit play intent
    currentStreamUrl = streamUrl; currentContentType = contentType
    currentMetadata = metadata; initialResumePosition = resumePosition
    remoteTime.value = 0.0; remoteDuration.value = 0.0

    val newDevice = castContext.createDeviceFromInfo(deviceInfo)
    connectedDevice = newDevice                       // synchronous — see below
    connectedDeviceFlow.value = newDevice
    castCall { newDevice.connect(null, DevEventHandler(this, newDevice, onTrackEnded), 1000u) }
}
```

`connectedDevice` is assigned **synchronously**, before the device reports
`Connected`. That is intentional: when the *old* device's asynchronous
`Disconnected` callback later lands, the guard `connectedDevice === device`
recognises it as stale (we already replaced it) and ignores it, so it can't null
out the new connection. **But** that early assignment is exactly why
`connectedDevice != null` is the wrong predicate for routing transport — use
`isConnected` (= `remoteConnectionState` is `Connected`). See
[04](04-playback-and-transport.md).

## The per-connection callback sink: `DevEventHandler`

`DevEventHandler : DeviceEventHandler` receives everything the receiver reports:

```kotlin
override fun connectionStateChanged(state: DeviceConnectionState) {
    handler.remoteConnectionState.value = state
    if (state is Connected) {
        handler.connectedDevice = device
        handler.connectedDeviceFlow.value = device
        val url = handler.currentStreamUrl; val type = handler.currentContentType
        if (url != null && type != null) {
            val pos = if (isReconnect && handler.remoteTime.value > 0) handler.remoteTime.value
                      else handler.initialResumePosition
            castCall { device.load(urlLoadRequest(url, type, pos, handler.currentMetadata)) }
            if (!handler.shouldPlay) castCall { device.pausePlayback() }   // honour paused intent
        }
    } else if (state is Disconnected) {
        if (handler.connectedDevice === device) handler.onConnectionDisconnected()  // ignore stale
    }
}

override fun playbackStateChanged(state) {
    handler.remotePlaybackState.value = state
    CastPlayback.playIntentForState(state)?.let { handler.shouldPlay = it }  // mirror TV remote
}
override fun timeChanged(t)     { handler.remoteTime.value = t }
override fun durationChanged(d) { handler.remoteDuration.value = d }
override fun mediaEvent(e)      { if (e.type == END) onTrackEnded?.invoke() }   // → auto-advance
override fun playbackError(m)   { reportException(IllegalStateException("FCast playback error: $m")) }
```

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

override fun deviceRemoved(deviceName) {            // device vanished from discovery
    discoveredDevicesFlow.value = synchronized(devicesLock) { discoveredDevices.remove(deviceName); … }
    if (connectedDevice?.name() == deviceName) disconnect()
}
```

Every SDK call goes through `castCall { … }`, which `runCatching`s the throwing
SDK call and routes failures to `reportException` instead of crashing — receivers
drop mid-call routinely.
