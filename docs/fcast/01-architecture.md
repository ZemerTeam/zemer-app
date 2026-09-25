# 01 - Architecture

## One owner, three seams

```
                 MusicService (process-scoped)
                   discoveryHandler  - owns the SDK
                   castLibLoader     - owns the .so
                   castController    - the control plane
                   sessionPlayer = CastAwarePlayer(player, discoveryHandler, scope)
                              |
      +-----------------------+-----------------------+
      |                       |                       |
 CastAwarePlayer        PlayerConnection        onStartCommand
 (session, notif.,      (in-app player UI)      (home-screen widget)
  Auto, headset)
      |    while connected: transport goes to FCastDiscoveryHandler
      +-----------------------+-----------------------+
```

Exactly **one** object talks to the SDK (`FCastDiscoveryHandler`), and three seams decide "local player
or receiver?". Everything else delegates to ExoPlayer untouched, so non-casting playback is unchanged by
construction. The seams are small explicit branches at each transport site rather than one universal
`ForwardingPlayer` behind `playerConnection.player`: that rewrite could only be validated on a live cast
device and risked the non-casting path. `CastAwarePlayer` is the one wrapper, because the media session
needs a `Player` and has no in-app call site to edit.

## The single casting predicate

```kotlin
val isConnected: Boolean get() = remoteConnectionState.value is DeviceConnectionState.Connected
```

`CastAwarePlayer` and the widget read `discoveryHandler.isConnected`; `PlayerConnection.isCasting` maps
the same `remoteConnectionState`. **Never gate transport on `connectedDevice != null`**: `connectTo()`
assigns it synchronously, before the device reports `Connected` (only so the stale-callback guard can
recognise the new device), so it would route presses to a not-yet-ready device while the in-app UI still
drives the local player - a split-brain ([04](04-playback-and-transport.md)).

## Ownership and lifecycle

| Object | Lifetime | Created in |
| --- | --- | --- |
| `FCastDiscoveryHandler` | process (`MusicService`) | `MusicService` field |
| `castContext` (`CastContext()`) | first touched in `connectTo()` | `FCastDiscoveryHandler` `by lazy` |
| `CastNativeLibLoader`, `CastController`, `CastConnector`, `CastDeviceRefresher` | process | `MusicService` `by lazy` |
| `CastStreamRelay` | server bound on first relay URL; stopped by `stopCastRelay()` | `MusicService` field |
| `CastSessionLocks` | acquired when a relay URL is issued, released with the relay | `MusicService` `by lazy` |
| `NsdDeviceDiscoverer` | first `startDiscovery()` until process death | `MusicService.startDiscovery()` |
| `PlayerConnection` | the bound Activity | `MainActivity.onServiceConnected` |

The handler and the session outlive any `PlayerConnection`, so the control plane lives in
`CastController`, never in `PlayerConnection` - which keeps only the UI seam and delegates its one cast
hook (`onPlayQueueWhileCasting`) to the controller (`markRemoteLoaded` / `advanceRemoteAfterEnd` are
called by `CastConnector`).

### Connect / disconnect

- **Connect:** the picker calls `CastConnector.connect`, which pauses local, resolves the stream, swaps
  in the relay URL and calls `handler.connectTo(...)`; the SDK's `connectionStateChanged(Connected)` then
  loads the URL onto the receiver ([03](03-discovery-and-connection.md)).
- **Disconnect** comes from "Stop casting", the SDK reporting `Disconnected` (its own heartbeat), the
  idle watchdog, the error ladder's GIVE_UP, or "stop music on task clear" (`shouldEndCastOnTaskClear`:
  a local pause alone would leave the receiver playing). It never comes from `deviceRemoved` - an NSD
  "service lost" is a transient flap and must not kill the TCP session. All paths funnel through
  `onConnectionDisconnected()`, whose `onDisconnect` callback lets `CastController` seek the **local**
  player to the last remote position and leave it paused.
