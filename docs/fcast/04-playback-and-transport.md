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
        if (discoveryHandler.isRemotePlaying()) discoveryHandler.pause()   // NOT raw isPlaying — see below
        else discoveryHandler.play()
    } else player.togglePlayPause()
}
fun seekTo(ms)            { if (isCasting.value) discoveryHandler.seek(msToRemoteSeconds(ms)) else player.seekTo(ms) }
fun seekToPrevious()      { if (isCasting.value) player.seekToPreviousMediaItem() else { player.seekToPrevious(); … } }
fun currentPositionMs()   = if (isCasting.value) remoteSecondsToMs(interpolatedRemoteTimeSec()) else player.currentPosition
fun currentDurationMs()   = if (isCasting.value) remoteSecondsToMs(remoteDuration.value)        else player.duration
```

Toggles decide from `isRemotePlaying()` (`CastPlayback.isRemotePlaying(state,
shouldPlay)`, pinned by `CastPlaybackTest`), **not** the raw reported state: in
the window after `connectTo` but before the receiver's first
`playbackStateChanged`, the state is still `null` while the button already
shows "pause" (from the `shouldPlay` intent — the same fallback the `isPlaying`
flow uses). Deciding from the raw state there would turn a pause tap into
`play()` and re-arm the intent, discarding the user's pause until the first
`PLAYING` report.

`seekToPrevious()` uses `seekToPreviousMediaItem()` while casting for the same
reason as the widget's `PREV` (below): the paused local clock is meaningless
remotely, and a within-item restart fires no media-item transition, so the
receiver would never be reloaded.

`Player.kt`, `MiniPlayer.kt`, `Lyrics.kt`, `Thumbnail.kt`, and the fullscreen
`LyricsScreen.kt` all read `currentPositionMs()` / `currentDurationMs()` and call
`playPause()` / `seekTo()` — never the remote flows directly — so the seek bar
(and synced lyrics) can't drift between surfaces. Every list screen's
tap-the-active-row toggle likewise calls `playerConnection.playPause()`, never
`player.togglePlayPause()` — the raw toggle would resume the deliberately-paused
local player **on top of** the cast stream.
The position is the **interpolated** remote clock (`interpolatedRemoteTimeSec()`),
which extrapolates between the receiver's coarse ~1 Hz reports so the bar moves
smoothly — see [05](05-auto-advance.md).

`isCasting` is a `stateIn(Lazily)` flow; its upstream is started by an in-class
collector in `PlayerConnection.init` (the stall poll) and by the UI, so
`isCasting.value` is reliable once the connection exists.

## Seam 3 — the home-screen widget (`MusicService.onStartCommand`)

```kotlin
MusicWidget.ACTION_PLAY_PAUSE -> if (discoveryHandler.isConnected) {
        if (discoveryHandler.isRemotePlaying()) discoveryHandler.pause() else discoveryHandler.play()
    } else { /* local toggle */ }
MusicWidget.ACTION_NEXT -> player.seekToNext()    // local skip advances the queue → reload (below)
MusicWidget.ACTION_PREV -> if (discoveryHandler.isConnected) player.seekToPreviousMediaItem()
                           else player.seekToPrevious()
```

`PREV` uses `seekToPreviousMediaItem()` while casting because the local clock is
meaningless remotely, so `seekToPrevious()`'s "restart current track if >3 s in"
would misfire.

The widget's **rendering** is cast-aware too, not just its actions:
`updateWidget()` / `widgetIsPlaying()` render the receiver's state and the
interpolated remote clock while connected (the local player is paused and its
clock frozen — rendering it would show a "play" icon whose tap *pauses* the
audibly-playing receiver). Local-player callbacks are silent while casting, so a
`combine(remoteConnectionState, remotePlaybackState)` collector in
`MusicService.onCreate` repaints the widget on remote connect/play/pause edges
and runs the 1 Hz seek-bar ticker off the remote clock (`.drop(1)` skips the
initial not-casting emission so service start doesn't flash an empty widget).

## Seam 3b — everything else that pauses: the sleep timer

`SleepTimer` routes its pause through the same branch
(`pausePlayback()`: `discoveryHandler.pause()` when connected, else
`player.pause()`). The local player is already paused while casting, so a raw
`player.pause()` would be an audible no-op — the receiver would play all night.
Routing through `discoveryHandler.pause()` also sets `shouldPlay = false`, which
keeps the *pause-when-song-ends* mode correct across the end-of-track
auto-advance: the advance reloads the next track on the receiver, and `load()`
re-pauses it because the intent is off (see [play intent](#play-intent-shouldplay)).

## Why one predicate everywhere

All three seams gate on the **same** `Connected` signal (`isConnected` /
`isCasting`). If `CastAwarePlayer`/widget gated on `connectedDevice != null`
(set early, at `connectTo`) while `PlayerConnection` gated on `Connected`, then in
the connect window the notification/widget would route to a not-yet-ready device
while the in-app UI still drove the local player — a split-brain where a press
goes to the wrong target. Unifying on `Connected` removes that class of bug.

## Volume control

While casting, the phone's volume becomes a remote for the **receiver's** volume.
`CastingDevice.changeVolume(Double)` (0.0–1.0) drives it, and because Chromecast is
just another FCast `ProtocolType`, one call covers **both** protocols. The handler
holds the tracked receiver volume as the single source of truth:

```kotlin
val volumeTracker = RemoteVolumeTracker()            // volume: StateFlow the UI observes + the known flag
override fun volumeChanged(volume: Double) {         // receiver's own reports (e.g. TV remote)
    if (handler.connectedDevice !== device) return   // stale-device guard, as in connectionStateChanged
    handler.volumeTracker.onReceiverReport(volume)   // coerces to [0,1], marks the level known
}
fun setVolume(volume: Double)    { castCall { connectedDevice?.changeVolume(volumeTracker.setAbsolute(volume)) } }
fun adjustVolume(direction: Int) { val v = volumeTracker.step(direction) ?: return
                                   castCall { connectedDevice?.changeVolume(v) } }   // direction = +1 / -1
```

`CastPlayback.steppedVolume(current, dir, step = VOLUME_STEP)` is the pure,
unit-tested step math (`VOLUME_STEP = 1/15`, matching Android's default music
granularity), clamped to `[0,1]`. The tracked value starts as (and is reset on
`connectTo` to) a `1.0` **placeholder**; the receiver's real level is *unknown*
until its first `volumeChanged` report or an absolute slider set. Until then,
`RemoteVolumeTracker.step` refuses relative hardware-key steps (`null` — the key is
still consumed, the receiver untouched): stepping the placeholder would **set** a
receiver that never reported — say a TV sitting at 20% — to ~93% on the first
volume-*down* press. The slider (an absolute set) works immediately and also
unlocks stepping; the rule is pinned by `RemoteVolumeTrackerTest`. Three inputs
feed the one flow, so they can never disagree:

1. **The player-menu slider** (`PlayerMenu` `BigSeekBar`) is cast-aware: while
   `isCasting` it reads `remoteVolume` and writes `setVolume`, otherwise it drives
   the local ExoPlayer gain (`MusicService.playerVolume`) as before.
2. **Hardware volume buttons**, decided by the pure `CastVolumeKeys.decide(keyCode,
   action, isCasting)` → `AdjustUp / AdjustDown / Consume / Ignore`. `Ignore` (not
   casting, or a non-volume key) lets the OS handle it — so **volume stays app-scoped:
   in another app the buttons control that app's local audio**, never the receiver.
   Casting `ACTION_UP` is `Consume`d so the system volume UI doesn't flash.
3. The **receiver itself** (its own remote) via `volumeChanged`, which moves the
   slider.

The key-button path is app-scoped by design — a MediaSession remote
`VolumeProvider` is deliberately **not** used, as it could hijack volume even while
the user is in another app. That means it has to be wired into every window the app
can present:

- **The Activity window** — `MainActivity.dispatchKeyEvent` delegates to
  `CastVolumeKeys.decide` and calls `adjustVolume`.
- **Overlay windows** (Compose `ModalBottomSheet` menus, `Dialog`s) live in their
  **own** window and never route key events to the Activity, so `dispatchKeyEvent`
  can't reach them. `castVolumeKeyModifier()` (in `ui/component`) handles those via
  Compose's focus-based `onPreviewKeyEvent`, applied to the overlay's **content
  root** (an ancestor of the menu/dialog items, so it keeps catching keys after
  D-pad focus moves to a child). It reuses the same `CastVolumeKeys.decide` rule.
  Compose's pipeline is used rather than the platform `OnUnhandledKeyEventListener`
  because that API is **28+** and the app supports `minSdk 26` (the G1 test device
  is API 27, where the listener never fires). Menus and dialogs without their own
  auto-focus (the 3-dot menu, the share-intent song dialog, `AccountSettingsDialog`)
  seed focus so keys flow immediately even in touch mode; the `Dialog.kt` dialogs
  pass `seedFocus = false` so a text field keeps its own auto-focus — which means
  keys route there only while *something* inside is focused (see the known
  limitation in [07](07-testing-and-troubleshooting.md)). Any **new** dialog/sheet
  window must apply `castVolumeKeyModifier()` too, or volume keys inside it fall
  through to the system volume while casting.

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

### Local playback must stay suppressed while casting (`shouldStartLocalPlayback`)

Starting a queue while casting pauses the local player up front
(`onPlayQueueWhileCasting`), but that pause is **not enough**: `ListQueue`
(community/online playlists, local playlists, albums) has no `preloadItem`, so
`playQueue()` sets `playWhenReady` on the raw local ExoPlayer only after an
**async fetch** completes — well after the initial pause ran. Without a guard,
local audio started on top of the receiver (the dual-playback bug). Every site
that would flip local `playWhenReady` on must therefore re-check the **live**
connection state at that moment via
`CastPlayback.shouldStartLocalPlayback(playWhenReady, isCasting)` (pure,
unit-tested) rather than trusting an earlier pause to still hold.

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
