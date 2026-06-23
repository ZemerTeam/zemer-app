# 04 — Playback and transport

While casting, the receiver is the source of truth for *playback* and the *clock*,
and the phone is a remote control + a mirror. This page covers the three seams
that route transport, the clock conversion, the play-intent model, and the reload
de-dup.

## Clock units (`CastPlayback`)

The FCast SDK reports position/duration in **seconds** (`Double`); the app's
player works in **milliseconds** (`Long`). `CastPlayback` is the single place the
conversion (and the remote-state mapping) lives — extracted and unit-tested
because a single dropped `* 1000` silently desyncs the seek bar:

```kotlin
object CastPlayback {
    fun isPlaying(state: PlaybackState?) = state == PlaybackState.PLAYING
    fun isPaused(state: PlaybackState?)  = state == PlaybackState.PAUSED
    fun playIntentForState(state): Boolean? = when (state) {        // PLAYING→true, PAUSED→false,
        PLAYING -> true; PAUSED -> false; else -> null }            // transient/unknown→no change
    fun remoteSecondsToMs(s: Double): Long = (s * 1000).toLong()
    fun msToRemoteSeconds(ms: Long): Double = ms / 1000.0
}
```

## Seam 1 — the media session (`CastAwarePlayer`)

`CastAwarePlayer` is a `ForwardingPlayer` wrapping the real ExoPlayer; it is what
`MusicService` registers with the `MediaLibrarySession`. So transport from the
**media notification, lock screen, Android Auto, and headset buttons** flows
through it. While `casting` (= `discoveryHandler.isConnected`) it routes to the
receiver; otherwise it forwards to ExoPlayer untouched:

```kotlin
private val casting get() = discoveryHandler.isConnected
override fun play()            { if (casting) discoveryHandler.play()  else super.play() }
override fun pause()           { if (casting) discoveryHandler.pause() else super.pause() }
override fun seekTo(pos)       { if (casting) discoveryHandler.seek(msToRemoteSeconds(pos)) else super.seekTo(pos) }
override fun getContentPosition() = if (casting) remotePositionMs else super.getContentPosition()
override fun getContentDuration() = if (casting) remoteDurationMs else super.getContentDuration()
// …getCurrentPosition/Duration, getBufferedPosition, seekBack/Forward, seekTo(index,pos) likewise
```

The media-style notification builds its **scrubber from the *content* position /
duration**, not `getCurrentPosition`, which is why those are overridden too —
otherwise the scrubber would sit at the paused local player's position while the
TV advances.

> **Known gap:** `CastAwarePlayer` does **not** mirror the remote PLAYING/PAUSED
> state back into the session, so the notification play/pause *icon* still
> reflects the (paused) local player while casting. Fully mirroring it would
> require synthesising `Player.Event`s from the remote callbacks, deliberately
> avoided to keep the core notification behaviour untouched. The scrubber and
> seek/skip *are* correct. See [07](07-testing-and-troubleshooting.md).

## Seam 2 — the in-app player UI (`PlayerConnection`)

`PlayerConnection` exposes cast-aware transport for the Compose player surfaces:

```kotlin
val isCasting = discoveryHandler.remoteConnectionState
    .map { it is Connected }.stateIn(scope, SharingStarted.Lazily, false)

fun playPause() {
    if (isCasting.value) {
        if (CastPlayback.isPlaying(remotePlaybackState.value)) discoveryHandler.pause()
        else discoveryHandler.play()
    } else player.togglePlayPause()
}
fun seekTo(ms)            { if (isCasting.value) discoveryHandler.seek(msToRemoteSeconds(ms)) else player.seekTo(ms) }
fun currentPositionMs()   = if (isCasting.value) remoteSecondsToMs(interpolatedRemoteTimeSec()) else player.currentPosition
fun currentDurationMs()   = if (isCasting.value) remoteSecondsToMs(remoteDuration.value)        else player.duration
```

`Player.kt`, `MiniPlayer.kt`, `Lyrics.kt`, and `Thumbnail.kt` all read
`currentPositionMs()` / `currentDurationMs()` and call `playPause()` / `seekTo()`
— never the remote flows directly — so the seek bar can't drift between surfaces.
The position is the **interpolated** remote clock (`interpolatedRemoteTimeSec()`),
which extrapolates between the receiver's coarse ~1 Hz reports so the bar moves
smoothly — see [05](05-auto-advance.md).

`isCasting` is a `stateIn(Lazily)` flow; its upstream is started by an in-class
collector in `PlayerConnection.init` (the stall poll) and by the UI, so
`isCasting.value` is reliable once the connection exists.

## Seam 3 — the home-screen widget (`MusicService.onStartCommand`)

```kotlin
MusicWidget.ACTION_PLAY_PAUSE -> if (discoveryHandler.isConnected) {
        if (CastPlayback.isPlaying(remotePlaybackState.value)) discoveryHandler.pause() else discoveryHandler.play()
    } else { /* local toggle */ }
MusicWidget.ACTION_NEXT -> player.seekToNext()    // local skip advances the queue → reload (below)
MusicWidget.ACTION_PREV -> if (discoveryHandler.isConnected) player.seekToPreviousMediaItem()
                           else player.seekToPrevious()
```

`PREV` uses `seekToPreviousMediaItem()` while casting because the local clock is
meaningless remotely, so `seekToPrevious()`'s "restart current track if >3 s in"
would misfire.

## Why one predicate everywhere

All three seams gate on the **same** `Connected` signal (`isConnected` /
`isCasting`). If `CastAwarePlayer`/widget gated on `connectedDevice != null`
(set early, at `connectTo`) while `PlayerConnection` gated on `Connected`, then in
the connect window the notification/widget would route to a not-yet-ready device
while the in-app UI still drove the local player — a split-brain where a press
goes to the wrong target. Unifying on `Connected` removes that class of bug.

## Play intent (`shouldPlay`)

`shouldPlay` (a `@Volatile` on the handler) records *what the user wants*, so a
new track load preserves it instead of always resuming:

- `playbackStateChanged` mirrors the receiver's own state into `shouldPlay` via
  `playIntentForState` — a pause/resume from the **TV's** remote sticks.
- `connectTo` and `PlayerConnection.playQueue` set `shouldPlay = true` — connect
  and tapping a song are explicit play actions.
- `load()` does **not** force `shouldPlay = true`; after loading it re-pauses if
  `shouldPlay` is false.

Net effect:

| Action while casting | Result |
| --- | --- |
| Auto-advance while playing | next track plays (intent stayed `true`) |
| Tap a new song / start a queue | plays (`playQueue` sets intent `true`) |
| **Skip while paused** | next track loads **paused** (intent stayed `false`) |
| Pause from the TV remote | mirrored into intent; survives the next reload |

## The receiver-reload de-dup (`remoteLoadedMediaId`)

`CastController.onMediaItemTransition` (driven from `MusicService.onMediaItemTransition`)
reloads the receiver when the *current item* actually changes, and de-dups against
what's already loaded:

```kotlin
val isCurrentItemChange = reason == SEEK || reason == AUTO || reason == REPEAT ||
    (reason == PLAYLIST_CHANGED && mediaItem?.mediaId != remoteLoadedMediaId)
if (casting && isCurrentItemChange) { player.pause(); triggerRemoteLoad(mediaItem) }
```

`PLAYLIST_CHANGED` also fires on queue *edits* that don't change the current
track, hence the `!= remoteLoadedMediaId` guard. But a genuine **new queue whose
first track equals the id already on the receiver** must still reload — so
`playQueue()` clears `remoteLoadedMediaId` (while casting) to force the upcoming
`PLAYLIST_CHANGED` to reload.

> **Single reload owner.** `CastController` (owned by the process-scoped
> `MusicService`, driven from `MusicService.onMediaItemTransition`) is the sole
> reload owner — `PlayerConnection` no longer reloads — so the receiver is loaded
> exactly once per track change **and** auto-advance survives the Activity being
> destroyed mid-cast ([05](05-auto-advance.md)).

## Stream URL + content type

`CastPicker.connect` resolves the same stream the app would play locally:

```kotlin
val streamUrl = currentId?.let { service.resolveStreamUrl(it) } ?: service.currentStreamUrl
handler.connectTo(streamUrl = streamUrl, contentType = service.currentContentType, …)
```

`resolveStreamUrl` runs the normal `YTPlayerUtils` path and, on success, caches
the real container MIME in `songMimeCache`. `streamContentType(id)` (and
`currentContentType`, which delegates to it) returns `songMimeCache[id] ?:
"audio/mp4"`. The receiver needs the *container* MIME — never fall back to
`player.audioFormat.sampleMimeType` (the codec MIME, wrong granularity, which the
receiver rejects). `songMimeCache` is cleared in lockstep with `songUrlCache` on
auth change.
