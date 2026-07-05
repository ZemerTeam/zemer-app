# 05 — End-of-track auto-advance

The FCast SDK does not advance our queue when a track finishes — it just plays the
one URL we loaded. So `CastController` (owned by the process-scoped `MusicService`)
detects end-of-track on the receiver and drives the next load. Because no single
SDK signal is reliable across receivers, **three independent detectors** feed one
debounced advance.

All timing thresholds are pure and unit-tested in `CastAutoAdvance`:

```kotlin
object CastAutoAdvance {
    const val STALL_END_EPSILON_SEC  = 3.0     // a stalled clock this close to the end == finished
    const val STALL_SILENCE_MS       = 4000L   // remote clock silent at least this long == stalled
    const val ADVANCE_DEBOUNCE_MS    = 8000L   // detectors + a real transition can't double-advance
    const val IDLE_END_WINDOW_SEC    = 10.0    // IDLE-from-PLAYING within this window of the end == finished
    const val IDLE_END_TAIL_FRACTION = 0.1     // …or within this proportional tail (whichever is larger)
    const val PAUSED_END_EPSILON_SEC = 2.0     // PAUSED-from-PLAYING this close to the end == finished (tight!)

    fun nearEnd(durationSec, lastPositionSec, epsilonSec) =
        durationSec > 0.0 && lastPositionSec >= durationSec - epsilonSec
    // Generous on purpose — a coarse FCast clock can stop reporting several seconds before the real end.
    fun finishedNearEnd(durationSec, lastPositionSec) = durationSec > 0.0 &&
        lastPositionSec >= durationSec - maxOf(IDLE_END_WINDOW_SEC, durationSec * IDLE_END_TAIL_FRACTION)
    // Chromecast zeroes the clock just BEFORE IDLE — judge the IDLE edge by the last real (> 0) report.
    fun endEdgePositionSec(reportedSec, lastProgressSec) =
        if (reportedSec > 0.0) reportedSec else lastProgressSec
    fun debouncePassed(nowMs, lastTransitionMs) = nowMs - lastTransitionMs > ADVANCE_DEBOUNCE_MS
    fun stalled(stalledForMs) = stalledForMs > STALL_SILENCE_MS
}
```

> **The remote clock is coarse.** FCast receivers report position only ~1 Hz and
> sometimes stop a few seconds before the real end, which both makes the seek bar
> choppy and starves the end detectors. `FCastDiscoveryHandler.interpolatedRemoteTimeSec()`
> extrapolates the last report by the elapsed wall-clock while PLAYING (capped at
> the duration). The seek bar and the **stall** detector read the interpolated clock
> so playback looks smooth and a clock that stopped short still reaches the end; the
> **IDLE** detector instead uses the generous `finishedNearEnd` window.

## The detectors

All live in `CastController` and call the shared `advanceRemoteAfterEnd()`. No single
signal is reliable across receivers — verified on real hardware, the **FCast Receiver
Android** app ends a track by going `PLAYING → PAUSED` at `pos == duration`, sending
*no* `END` event and *never* `IDLE` — so end-of-track is caught three ways:

1. **SDK `END` event** — `DevEventHandler.mediaEvent(END)` → `onTrackEnded` →
   `advanceRemoteAfterEnd()`. The cleanest signal when the receiver sends it.

2. **End state from PLAYING** — a collector on `remotePlaybackState`. Two end signals:
   - `PLAYING → IDLE` while `finishedNearEnd(dur, pos)` — a **generous** window (a
     coarse clock stops reporting early). IDLE far from the end is a stop/error.
     The position judged is `endEdgePositionSec(pos, handler.lastProgressSec)`:
     **Chromecast-protocol receivers reset the reported clock to 0 a few ms
     BEFORE reporting IDLE** at end-of-track, so on the IDLE edge the current
     report already reads 0 and only the last real (> 0) progress report still
     holds the true end position — without the fallback the queue froze at track
     end on Chromecast (and the stall detector can't recover: once IDLE the
     interpolated clock stays at 0). FCast receivers go IDLE with the clock still
     near the end, so for them the fallback is the identity.
     `FCastDiscoveryHandler.lastProgressSec` is reset on every content (re)load,
     connect, and disconnect, so it can never carry a previous track's near-end
     position into a new one; a mid-track stop on the TV still does not advance.
   - `PLAYING → PAUSED` while `nearEnd(dur, pos, PAUSED_END_EPSILON_SEC)` — a **tight**
     window (2 s). Some receivers auto-pause at `pos == duration` to signal the end;
     the tight epsilon distinguishes that from a deliberate mid-track pause (which must
     not advance). A PAUSED report at the very end also must **not** flip the play
     intent off (`FCastDiscoveryHandler.playbackStateChanged`), or the next track would
     load paused; and `advanceRemoteAfterEnd` re-asserts `shouldPlay = true`.

3. **Stall poll** — a 1 Hz loop (only while casting) that fires when the remote
   clock has been silent past `STALL_SILENCE_MS` and `nearEnd(…,
   STALL_END_EPSILON_SEC)` **of the interpolated clock**, **and** the receiver is
   not deliberately paused (`!CastPlayback.isPaused(...)`). The paused carve-out is
   essential: pausing freezes the clock exactly like a stall, and without it pausing
   near the end would silently auto-skip the track.

```kotlin
fun advanceRemoteAfterEnd() = scope.launch {
    if (!CastAutoAdvance.debouncePassed(now(), lastTransitionTime)) return@launch
    // Stamp the debounce only when we actually advance (a no-op end report on the last track must not
    // burn the window against a later real event).
    if (player.repeatMode == REPEAT_MODE_ONE) { lastTransitionTime = now(); player.seekTo(currentIndex, 0); triggerRemoteLoad(currentItem) }
    else if (canSkipNext()) { lastTransitionTime = now(); player.seekToNext() }  // → onMediaItemTransition → reload
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

The IDLE detector compares against `lastRemotePosition`; the stall detector against
`lastRemoteTimeUpdateAt` (silence) plus the interpolated clock. Both trackers are
maintained by a collector on `remoteTime`:

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

`triggerRemoteLoad` also resets the **visible** remote clock (`remoteTime` /
`remoteDuration` → 0) *synchronously*, not just in `handler.load()` which runs after
the async stream resolve. Without it, when the queue advances the UI switches to the
new song immediately while the remote clock still reads the previous track's near-end
position/duration — a full progress bar that then drops to 0. And
`interpolatedRemoteTimeSec()` returns the raw value (no extrapolation) until a real
duration arrives, so the bar doesn't creep up from 0 on the just-loaded track.

## Errors are not ends: the receiver-error recovery ladder

The three detectors deliberately advance only *near the end* — a mid-track stop or error must
never skip a track the user is listening to. That leaves a gap: when the **receiver's own fetch
of the stream URL fails** (googlevideo refuses its connection — see the symptom table in
[07](07-testing-and-troubleshooting.md)), the track dies at an arbitrary position and no end
detector can ever fire. Historically the error was swallowed (report-only) and the session sat
silent — indistinguishable from auto-advance breaking.

`DevEventHandler.playbackError` now drives `CastController.onRemotePlaybackError`, which
escalates per the pure, unit-tested `CastErrorRecovery` ladder:

1. **RELOAD** — re-send the URL the receiver already had, resuming from `lastProgressSec`
   (a fresh receiver connection re-rolls its network path; a track that died minutes in
   picks up where it stopped, not from 0).
2. **RESOLVE_FRESH** — drop the cached URL (`MusicService.invalidateStreamCache`), re-resolve,
   reload (still resuming).
3. **ADVANCE** — abandon the track and let the queue continue, **capped** at
   `MAX_CONSECUTIVE_ERROR_ADVANCES` consecutively abandoned tracks so a dead network can't
   machine-gun the whole queue. With repeat-one or no next item there is nowhere to go, so the
   ladder **gives up** instead (a toast + non-fatal; never an endless replay loop).

Bookkeeping that keeps the ladder honest: error callbacks within `ERROR_BURST_WINDOW_MS` count
as one failure (a broken pipeline can emit several); each media-item transition resets the
per-track attempt count (every track gets a fresh ladder) but **not** the abandoned-tracks
streak; real playback progress (`PROGRESS_RESET_SEC` of remote clock) resets both; and
connect/disconnect reset everything.

## Advance survives the Activity being destroyed

All three detectors and the reload live in `CastController`, owned by the
process-scoped `MusicService` (not the Activity-scoped `PlayerConnection`). So a
cast session keeps advancing through its queue even when the Activity is destroyed
mid-cast. `MusicService.onMediaItemTransition` drives the single reload owner
(`CastController.onMediaItemTransition`), so there is exactly one reload per track
change — no double-load. (Earlier the control plane lived in `PlayerConnection`,
which made auto-advance stop once the Activity went away; that limitation is gone.)
