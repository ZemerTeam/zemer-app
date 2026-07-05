# 01 — Architecture

## The shape: one owner, three seams

```
                         ┌─────────────────────────────────────────┐
                         │  MusicService  (singleton, long-lived)   │
                         │                                          │
   resolveStreamUrl ─────┤  val discoveryHandler  ◄── owns the SDK  │
   streamContentType     │  val castLibLoader     ◄── owns the .so  │
                         │  player = CastAwarePlayer(exo, handler)  │
                         └───────────────┬──────────────────────────┘
                                         │
         ┌───────────────────────────────┼───────────────────────────────┐
         │                               │                               │
 ┌───────▼────────┐            ┌─────────▼─────────┐            ┌────────▼─────────┐
 │ CastAwarePlayer │            │  PlayerConnection │            │ onStartCommand   │
 │ (media session, │            │  (in-app player   │            │ (home-screen     │
 │  notification,  │            │   UI: play/seek,  │            │  widget play/    │
 │  Auto, headset) │            │   auto-advance)   │            │  next/prev)      │
 └───────┬────────┘            └─────────┬─────────┘            └────────┬─────────┘
         │  while connected: route transport to the receiver,            │
         └───────────────────────► FCastDiscoveryHandler ◄───────────────┘
                                   play/pause/seek/load
                                   + remote-state StateFlows
```

There is exactly **one** object talking to the SDK — `FCastDiscoveryHandler` —
and **three** call sites ("seams") that decide *"is this transport command for
the local player or the receiver?"*. Everything else delegates to the local
ExoPlayer untouched, so **non-casting playback is identical to upstream** by
construction.

### Why a seam at each surface (not one universal wrapper)

An earlier design considered making `playerConnection.player` itself a cast-aware
`ForwardingPlayer` so *every* call site routed automatically. That is cleaner on
paper but it is a branch-wide rewrite of working playback integration that can
only be validated by builds + unit tests, **not** a live cast device — too risky
for the non-casting guarantee. The team chose the **surgical** approach: small,
explicit `isCasting`/`isConnected` branches only at the handful of transport
sites, each individually reviewable. `CastAwarePlayer` is the one place a
forwarding wrapper *is* used, because the media session needs ExoPlayer to *be* a
`Player` and there is no in-app call site to edit there.

## The single casting predicate

Every transport-routing decision asks the same question through the same
property:

```kotlin
// FCastDiscoveryHandler.kt
val isConnected: Boolean get() = remoteConnectionState.value is DeviceConnectionState.Connected
```

- `CastAwarePlayer.casting` → `discoveryHandler.isConnected`
- `MusicService.onStartCommand` (widget) → `discoveryHandler.isConnected`
- `PlayerConnection.isCasting` → a `StateFlow` mapped from the same
  `remoteConnectionState` being `Connected`.

**Do not** gate transport on `connectedDevice != null`. `connectedDevice` is
assigned *synchronously* in `connectTo()` — before the device has actually
reported `Connected` — purely so the stale-disconnect guard can recognise the
new device. Using it to route transport would, in the connect window, send a
play/pause to a not-yet-ready device while the UI still thinks it's local (a
split-brain). The `Connected`-based predicate is the source of truth; see
[04-playback-and-transport](04-playback-and-transport.md).

## Ownership and lifecycle

| Object | Lifetime | Created in |
| --- | --- | --- |
| `FCastDiscoveryHandler` | == `MusicService` (process) | `MusicService` field |
| `castContext` (`CastContext()`) | lazy, first touched in `connectTo()` | `FCastDiscoveryHandler` `by lazy` |
| `CastNativeLibLoader` | == `MusicService` | `MusicService` `by lazy` |
| `CastController` | == `MusicService` (process) | `MusicService` `by lazy` |
| `NsdDeviceDiscoverer` | from first `startDiscovery()` to process death | `MusicService.startDiscovery()` |
| `PlayerConnection` | == bound Activity (disposed on unbind/destroy) | `MainActivity.onServiceConnected` |

The **lifetime mismatch matters**: the handler and the cast session outlive any
single `PlayerConnection`. So the cast **control plane** — the auto-advance
detectors, the track-change reload, and disconnect recovery — lives in
`CastController`, owned by the (process-scoped) `MusicService`, **not** in the
Activity-scoped `PlayerConnection`. A cast session therefore keeps advancing
through its queue even after the Activity is destroyed. `PlayerConnection` holds
only the UI-facing seam (play/seek routing + the observable state flows) and
delegates its few cast hooks to `CastController`. See
[05-auto-advance](05-auto-advance.md).

### Connect / disconnect lifecycle

- **Connect** is initiated from the picker (`CastPicker.connect`) → it pauses
  local, resolves the stream URL, and calls `handler.connectTo(...)`. The SDK's
  `connectionStateChanged(Connected)` callback then loads the URL onto the
  receiver.
- **Disconnect** comes from the user ("Stop casting") or the SDK reporting
  `Disconnected` (its own heartbeat). It does **not** come from `deviceRemoved` —
  NSD discovery losing the service is a transient flap and must not kill the active
  TCP session. Both funnel through `onConnectionDisconnected()`, which clears the
  remote state and
  invokes `CastController`'s `onDisconnect` callback to seek the **local**
  player to the last remote position and leave it paused (so the user can resume
  on the phone).

## What lives where (quick index)

- **"How is the receiver discovered/connected?"** → `FCastDiscoveryHandler`,
  [03](03-discovery-and-connection.md).
- **"Where does a play/pause from the notification go?"** → `CastAwarePlayer`,
  [04](04-playback-and-transport.md).
- **"Where does a play/pause from the in-app button go?"** →
  `PlayerConnection.playPause`, [04](04-playback-and-transport.md).
- **"Who decides a cast track ended?"** → `CastController` detectors +
  `CastAutoAdvance`, [05](05-auto-advance.md).
- **"Where does the `.so` come from?"** → `CastNativeLibLoader`,
  [02](02-on-demand-native-lib.md).
