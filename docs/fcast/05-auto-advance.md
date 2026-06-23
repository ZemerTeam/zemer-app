# 05 — End-of-track auto-advance

The FCast SDK does not advance our queue when a track finishes — it just plays the
one URL we loaded. So `CastController` (owned by the process-scoped `MusicService`)
detects end-of-track on the receiver and drives the next load. Because no single
SDK signal is reliable across receivers, **three independent detectors** feed one
debounced advance.

All timing thresholds are pure and unit-tested in `CastAutoAdvance`:

```kotlin
object CastAutoAdvance {
    const val IDLE_END_EPSILON_SEC  = 2.0     // IDLE-from-PLAYING this close to the end == finished
    const val STALL_END_EPSILON_SEC = 3.0     // a stalled clock this close to the end == finished
    const val STALL_SILENCE_MS      = 4000L   // remote clock silent at least this long == stalled
    const val ADVANCE_DEBOUNCE_MS   = 8000L   // detectors + a real transition can't double-advance

    fun nearEnd(durationSec, lastPositionSec, epsilonSec) =
        durationSec > 0.0 && lastPositionSec >= durationSec - epsilonSec
    fun debouncePassed(nowMs, lastTransitionMs) = nowMs - lastTransitionMs > ADVANCE_DEBOUNCE_MS
    fun stalled(stalledForMs) = stalledForMs > STALL_SILENCE_MS
}
```

## The three detectors

All live in `CastController` and call the shared `advanceRemoteAfterEnd()`.

1. **SDK `END` event** — `DevEventHandler.mediaEvent(END)` → `onTrackEnded` →
   `advanceRemoteAfterEnd()`. The cleanest signal when the receiver sends it.

2. **IDLE-after-PLAYING** — a collector on `remotePlaybackState`: if the state
   goes `PLAYING → IDLE` while `nearEnd(dur, lastPos, IDLE_END_EPSILON_SEC)`, the
   track finished. (IDLE far from the end is a stop/error, not an end.)

3. **Stall poll** — a 1 Hz loop (only while casting) that fires when the remote
   clock has been silent past `STALL_SILENCE_MS` and `nearEnd(…,
   STALL_END_EPSILON_SEC)`, **and** the receiver is not deliberately paused
   (`!CastPlayback.isPaused(...)`). The paused carve-out is essential: pausing
   freezes the clock exactly like a stall, and without it pausing near the end
   would silently auto-skip the track.

```kotlin
fun advanceRemoteAfterEnd() = scope.launch {
    if (!CastAutoAdvance.debouncePassed(now(), lastTransitionTime)) return@launch
    lastTransitionTime = now()
    if (player.repeatMode == REPEAT_MODE_ONE) { player.seekTo(currentIndex, 0); triggerRemoteLoad(currentItem) }
    else if (canSkipNext.value) seekToNext()     // advances the queue → onMediaItemTransition → reload
}
```

## Why a debounce, on one thread

The three detectors can fire near-simultaneously, and a real media-item
transition also bumps `lastTransitionTime`. `advanceRemoteAfterEnd` runs on the
connection scope (main thread) and does the debounce check + the timestamp stamp
there — serialised on one thread — so the detectors (and a genuine transition)
can't double-advance and skip a track. The **repeat-one** path matters here: it
replays the same index, which fires *no* media-item transition of its own, so the
debounce stamp must happen inside `advanceRemoteAfterEnd` (it does), or the
window would never refresh.

The `END` callback arrives on a native SDK thread; `advanceRemoteAfterEnd`
marshals onto the main thread because Media3's player must be touched on its
application thread.

## The stall trackers and the position reset

The stall + IDLE detectors compare against `lastRemotePosition` /
`lastRemoteTimeUpdateAt`, maintained by a collector on `remoteTime`:

```kotlin
service.discoveryHandler.remoteTime.collect { time ->
    lastRemotePosition = time                 // unconditional — see below
    lastRemoteTimeUpdateAt = System.currentTimeMillis()
}
```

`lastRemotePosition` is recorded **unconditionally** (not only when `time > 0`).
`connectTo()` / `load()` reset `remoteTime` to `0` for a new track, and that `0`
*must* clear the previous track's near-end position. Otherwise a fresh connect —
or a **device switch**, whose old-device `Disconnected` is intentionally ignored
so the `onDisconnect` reset never runs — leaves `lastRemotePosition` stale near
the end, and the stall detector compares it against the *new* track's duration and
spuriously auto-skips it. Recording `0` is safe because `nearEnd(dur, 0, eps)` is
false for any real-length track. This property is pinned by a regression test in
`CastAutoAdvanceTest` ("resetting last position to zero clears a stale near-end").

On disconnect, `CastController`'s `onDisconnect` handler also resets
`lastRemotePosition`, `lastRemoteTimeUpdateAt`, `lastTransitionTime`, and
`remoteLoadedMediaId` — so a later reconnect/new track doesn't auto-skip on stale
near-end state.

## Advance survives the Activity being destroyed

All three detectors and the reload live in `CastController`, owned by the
process-scoped `MusicService` (not the Activity-scoped `PlayerConnection`). So a
cast session keeps advancing through its queue even when the Activity is destroyed
mid-cast. `MusicService.onMediaItemTransition` drives the single reload owner
(`CastController.onMediaItemTransition`), so there is exactly one reload per track
change — no double-load. (Earlier the control plane lived in `PlayerConnection`,
which made auto-advance stop once the Activity went away; that limitation is gone.)
