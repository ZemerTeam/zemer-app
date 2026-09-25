# 05 - Auto-advance, error recovery, relay, idle watchdog

The SDK plays the one URL it was given and never advances our queue, so `CastController` (process-scoped,
owned by `MusicService`) detects end-of-track on the receiver and drives the next load. No single signal
is reliable across receivers (the FCast Receiver Android app, for one, ends a track with
`PLAYING → PAUSED` at `pos == duration` and sends neither `END` nor `IDLE`), so **three detectors** feed
one debounced `advanceRemoteAfterEnd()`.

## Thresholds (`CastAutoAdvance`, pure, `CastAutoAdvanceTest`)

| Constant | Value | Meaning |
| --- | --- | --- |
| `STALL_END_EPSILON_SEC` | 3.0 | a stalled clock this close to the end = finished |
| `STALL_SILENCE_MS` | 4000 | clock silent this long = stalled |
| `ADVANCE_DEBOUNCE_MS` | 8000 | detectors + a real transition cannot double-advance |
| `IDLE_END_WINDOW_SEC` / `IDLE_END_TAIL_FRACTION` | 10.0 / 0.1 | IDLE within max(window, tail × duration) of the end = finished |
| `PAUSED_END_EPSILON_SEC` | 2.0 | PAUSED this close to the end = finished (tight) |

Helpers: `nearEnd`, `finishedNearEnd` (the generous IDLE window), `endEdgePositionSec`, `debouncePassed`,
`stalled`.

**The remote clock is coarse** (~1 Hz reports, sometimes stopping seconds before the end).
`FCastDiscoveryHandler.interpolatedRemoteTimeSec()` extrapolates the last report by elapsed wall-clock
while PLAYING, capped at the duration, and returns the raw value until a real duration arrives (so a
just-loaded track's bar does not creep up from 0). The seek bar and the stall detector use it.

## The detectors (all in `CastController`)

1. **SDK `END` event** → `onTrackEnded` → `advanceRemoteAfterEnd()`.
2. **End state after PLAYING** (a `remotePlaybackState` collector):
   - `IDLE` with `finishedNearEnd(duration, endEdgePositionSec(remoteTime, lastProgressSec))`. Chromecast
     resets the reported clock to 0 just **before** reporting IDLE at end-of-track, so the edge is judged
     by `lastProgressSec` (the last report > 0) when the current one is 0 - judging the raw 0 froze the
     queue at track end. `lastProgressSec` is reset on every load, connect and disconnect, so it never
     carries a previous track's near-end position.
   - `PAUSED` with `nearEnd(…, PAUSED_END_EPSILON_SEC)`: the tight window separates a receiver's
     end-of-track auto-pause from a real mid-track pause. That PAUSED must also not clear the play intent
     ([03](03-discovery-and-connection.md)), and `advanceRemoteAfterEnd` re-asserts `shouldPlay = true`.
3. **Stall poll** - a 1 Hz loop, running only while connected (`collectLatest` on the connection state):
   fires when the clock has been silent past `STALL_SILENCE_MS`, the **interpolated** clock is `nearEnd(…,
   STALL_END_EPSILON_SEC)`, **and** the receiver is not PAUSED. The paused carve-out is essential: a pause
   freezes the clock exactly like a stall, and pausing near the end would otherwise auto-skip.

`advanceRemoteAfterEnd()` runs on the controller's scope (the service Main scope): if the debounce has
passed it sets `shouldPlay = true`, then either replays the current item for repeat-one (seek to 0 +
`triggerRemoteLoad`) or `seekToNext()`s locally (the transition reloads the receiver). The debounce
timestamp is stamped **only when it actually advances** (a no-op report on the last track must not burn
the window) and inside this function for repeat-one, which fires no media-item transition of its own.
Everything is serialised on one thread, and SDK-thread callbacks hop onto it before touching Media3.

## Tracker resets

- The `remoteTime` collector records `lastRemotePosition` **unconditionally** (a load resets the clock
  backward to the resume position, which must replace the previous track's near-end value) and stamps
  `lastRemoteTimeUpdateAt` (stall silence) and, on a forward move > `PROGRESS_EPSILON_SEC`,
  `lastForwardProgressAt` (idle watchdog).
- `triggerRemoteLoad` resets the trackers **and the visible clock** (`remoteTime`/`remoteDuration`)
  synchronously - `handler.load()` only runs after the async stream resolve, and until then the new song
  would show the old track's full bar. It also cancels any in-flight resolve for a previous track, so a
  slow earlier resolve cannot land on the receiver after a faster later one; a failed resolve clears
  `remoteLoadedMediaId` and reports `FCast: could not resolve a stream URL for <id>`.
- `onDisconnect` resets every tracker, the debounce, `remoteLoadedMediaId` and the error counters, so a
  later connect does not auto-skip on stale state.

## Errors are not ends: the recovery ladder

The detectors only advance near the end, so a receiver whose stream fetch fails mid-track would sit
silent. `DevEventHandler.playbackError` → `CastController.onRemotePlaybackError` escalates per the pure
`CastErrorRecovery.actionForAttempt` (`CastErrorRecoveryTest`):

1. **RELOAD** - re-send the receiver's URL, resuming at `lastProgressSec` (a fresh connection re-rolls
   its network path).
2. **RESOLVE_FRESH** - `MusicService.invalidateStreamCache`, re-resolve, reload (still resuming).
3. **DIRECT_URL** - only when the failing URL is a relay URL (`castStreamRelay.servesUrl`): load the raw
   googlevideo URL, in case the receiver cannot reach the relay at all. Per-track, non-sticky.
4. **ADVANCE** - skip the track, capped at `MAX_CONSECUTIVE_ERROR_ADVANCES` consecutive abandoned tracks
   so a dead network cannot machine-gun the queue.
5. **GIVE_UP** - when the cap is hit or there is nowhere to go (repeat-one / no next item): report,
   toast `cast_playback_failed`, disconnect.

Bookkeeping: callbacks within `ERROR_BURST_WINDOW_MS` count as one failure; each media-item transition
resets the per-track attempt count but **not** the abandoned-tracks streak; `PROGRESS_RESET_SEC` of real
playback resets both; connect (`markRemoteLoaded`) and disconnect reset everything.

## The phone-side stream relay (`CastStreamRelay`)

googlevideo binds a stream URL to the network identity that minted it and 403s other identities past
the first free MiB, so a receiver behind CGNAT IPv4 or on another IPv6 prefix cannot fetch the phone's
URLs directly. The relay is a minimal LAN HTTP server that proxies the stream, so the fetching identity
is the minting one by construction.

- `MusicService.relayedStreamUrl(mediaId, rawUrl)` returns `http://<phone>:<port>/stream/<token>` (random
  port, a random 128-bit token per media id), or `rawUrl` when the relay cannot serve (no receiver
  address, no route, server failed to start; logged as "Relay unavailable", and a thrown error is
  reported as `Cast relay URL`). `CastConnector` and `CastController` both use it.
- The server binds the wildcard address; the URL host is re-derived per URL by a UDP route probe toward
  `receiverAddress`, which `CastConnector` sets from `CastConnect.relayTargetAddress` (IPv4 preferred -
  mDNS IPv6 entries are often link-local and cannot be a URL host).
- A client `HEAD` becomes an upstream `GET` (googlevideo HEAD false-negatives). An upstream 403/expiry or
  a mid-body drop is re-resolved (forced fresh on the last attempt) and spliced at the exact byte offset
  the receiver already has. The pure HTTP/Range math is `CastRelayProtocol`.
- While a relay URL is out, `CastSessionLocks` holds a `WIFI_MODE_FULL_HIGH_PERF` lock + a partial wake
  lock (a screen-off phone would otherwise starve the receiver).
- `onDisconnect` stops the relay and locks via `MusicService.stopCastRelay()` after `RELAY_STOP_GRACE_MS`
  and only if still disconnected: a device switch's deferred `Disconnected` lands after the new connect
  has handed out a relay URL.

## The idle watchdog

A session can hang with nothing playing and never tear down (a receiver paused and abandoned, or cut off
while its socket lingers), holding the relay, locks and foreground service. The stall poll also asks
`CastIdleWatchdog.shouldEndIdleSession(state, idleForMs)`, where `idleForMs` is time since
`lastForwardProgressAt` (reset by real progress, a fresh load/connect, and a resume to PLAYING):
`PAUSED_IDLE_TIMEOUT_MS` (20 min) while paused, `STALLED_IDLE_TIMEOUT_MS` (3 min) otherwise. A hit runs
`endIdleSession()`: toast `cast_session_ended_idle` and `disconnect()`, which recovers the local player
and stops the relay.
