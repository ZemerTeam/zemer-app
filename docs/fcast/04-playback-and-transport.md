# 04 - Playback and transport

While casting, the receiver owns playback and the clock; the phone is a remote control and a mirror.

## Clock units (`CastPlayback`)

The SDK reports position/duration in **seconds** (`Double`); the app works in **milliseconds** (`Long`).
Convert only through `CastPlayback.remoteSecondsToMs` / `msToRemoteSeconds` - a dropped `* 1000`
silently desyncs the seek bar. `CastPlayback` also holds the state mapping (`isPlaying`, `isPaused`,
`playIntentForState`, `isRemotePlaying`), all JVM-tested in `CastPlaybackTest`.

## Seam 1 - the media session (`CastAwarePlayer`)

The `ForwardingPlayer` `MusicService` registers with the `MediaLibrarySession`, so notification, lock
screen, Android Auto and headset transport flow through it. While `casting` (= `isConnected`) `play` /
`pause` / `seekTo` / `seekBack` / `seekForward` go to the receiver; otherwise it forwards untouched.

- The position/duration getters **including `getContentPosition`/`getContentDuration` and the buffered
  getters** report the remote clock: the media notification builds its scrubber from the *content*
  position, so overriding only `getCurrentPosition` would leave it on the paused local position.
- `getPlayWhenReady` / `isPlaying` report the receiver's state (`getPlaybackState` reports `READY`), and
  a collector on `remotePlaybackState` re-notifies the session's listeners, so the notification icon
  follows the receiver (including a pause from the TV remote).

## Seam 2 - the in-app UI (`PlayerConnection`)

`isCasting` is `remoteConnectionState` mapped to `is Connected` (`stateIn(Lazily)`; its upstream is
started by the `isPlaying` combine, which folds it in). While casting:

- `playPause()` decides from `discoveryHandler.isRemotePlaying()` - the reported state once known, else
  the `shouldPlay` intent - **never the raw state**: before the receiver's first report the state is null
  while the button already shows "pause", and deciding from null would turn a pause tap into `play()`.
- `seekTo(ms)` → `discoveryHandler.seek(seconds)`.
- `seekToPrevious()` uses `seekToPreviousMediaItem()`: the frozen local clock makes "restart if > 3 s
  in" misfire, and a within-item restart fires no media-item transition, so the receiver never reloads.
- `seekToNext()` skips locally without resuming local audio; the transition reloads the receiver.
- `currentPositionMs()` = the **interpolated** remote clock (`interpolatedRemoteTimeSec()`, which
  extrapolates the receiver's coarse reports - see [05](05-auto-advance.md)); `currentDurationMs()` =
  `remoteDuration`.

`Player.kt`, `Thumbnail.kt`, `PlayerVideoFullscreen.kt`, `component/Lyrics.kt` and `LyricsScreen.kt` read
`currentPositionMs()` / `currentDurationMs()`, never the remote flows, so the seek bar and synced lyrics
cannot drift between surfaces. **A tap-the-active-row toggle must call `playerConnection.playPause()`,
never `player.togglePlayPause()`** - the raw toggle resumes the paused local player on top of the cast.
(Known violations to fix: `LibraryPodcastsScreen` and `OnlinePodcastScreen` still call
`player.togglePlayPause()`.)

## Seam 3 - the home-screen widget (`MusicService.onStartCommand`)

- `ACTION_PLAY_PAUSE` while connected toggles via `isRemotePlaying()`.
- `ACTION_NEXT` skips locally (the transition reloads the receiver).
- `ACTION_PREV` uses `seekToPreviousMediaItem()` while connected (same reason as above).

The widget's **rendering** is cast-aware too: `widgetIsPlaying()` / `updateWidget()` render the
receiver's state and interpolated clock while connected (the frozen local state would show a "play" icon
whose tap pauses the audible receiver). Local-player callbacks are silent while casting, so a
`combine(remoteConnectionState, remotePlaybackState)` collector in `MusicService` repaints on remote edges
and runs the widget ticker off the remote clock; its `.drop(1)` skips the initial not-casting emission so
service start does not flash an empty widget.

## Other pausers

- **Sleep timer:** `SleepTimer.pausePlayback()` calls `discoveryHandler.pause()` when connected (a raw
  `player.pause()` is a no-op on the receiver). Because that clears `shouldPlay`, the end-of-song mode
  stays correct across auto-advance: the next track loads paused.
- **Full-screen status viewers** (`PauseMusicWhileActive` in `ui/utils/MediaViewerEffects.kt`): pause
  the receiver while a status video/image is open (`pauseCastForVideo`, only if it was playing), set
  `videoPlaybackActive` so volume keys drive the local video, and on close resume the receiver only if
  this viewer paused it and the session is still connected (`shouldResumeCastAfterVideo`).
- **Stop music on task clear:** `shouldEndCastOnTaskClear` disconnects the session, since a local pause
  would leave the receiver playing.

## Why one predicate

All seams gate on the same `Connected` signal. Mixing it with `connectedDevice != null` (set early, in
`connectTo`) would, during the connect window, route the notification/widget to a not-yet-ready device
while the in-app UI still drove the local player.

## Volume control

The phone's volume becomes a remote for the **receiver's** volume via `CastingDevice.changeVolume`
(0.0-1.0; Chromecast is another FCast `ProtocolType`, so one call covers both). `RemoteVolumeTracker`
(held by the handler, exposed as `remoteVolume`) is the single source of truth, fed by three inputs:

1. **The player-menu slider** (`PlayerMenu`): while casting it reads `remoteVolume` and writes
   `setVolume` (an absolute set); otherwise it drives the local player volume.
2. **Hardware keys**: `adjustVolume(±1)` steps by `CastPlayback.VOLUME_STEP` (1/15, clamped 0..1).
3. **The receiver's own reports** (`volumeChanged`), which move the slider.

The tracked value is a `1.0` **placeholder** (reset on every `connectTo`) until the receiver reports or
the slider sets a level; until then `RemoteVolumeTracker.step` returns null and a key press is consumed
but sends nothing - stepping the placeholder would set a receiver sitting at 20% to ~93% on the first
volume-down (`RemoteVolumeTrackerTest`).

Key routing is `CastVolumeKeys.decide(keyCode, action, isCasting, videoPlaybackActive)` →
`AdjustUp` / `AdjustDown` / `Consume` / `Ignore`. Not casting, a non-volume key, or an open local video
is `Ignore` (the OS handles it); a casting `ACTION_UP` is `Consume`d so the system volume UI does not
flash. **Volume is app-scoped by design** - no MediaSession remote `VolumeProvider`, which could hijack
volume in other apps - so the rule must be wired into every window:

- The Activity: `MainActivity.dispatchKeyEvent`.
- Overlay windows (bottom-sheet menus, dialogs) never route keys to the Activity, so
  `castVolumeKeyModifier()` (`ui/component/CastVolumeKeyHandler.kt`) applies the same rule via
  `onPreviewKeyEvent` on the overlay's content root. Compose's pipeline is used because the platform
  `OnUnhandledKeyEventListener` is API 28+ and `minSdk` is 26. Overlays without their own auto-focus
  (`BottomSheetMenu`, `AccountSettingsDialog`, the share-intent song dialog in `MainActivity`) seed
  focus; the `Dialog.kt` dialogs pass `seedFocus = false` so a text field keeps its focus (see the
  limitation in [07](07-testing-and-troubleshooting.md)). **Any new dialog/sheet window must apply
  `castVolumeKeyModifier()`**, or its volume keys hit the system volume while casting.

## Play intent (`shouldPlay`)

`shouldPlay` (a `@Volatile` on the handler) records what the user wants, so a load preserves it:

- `playbackStateChanged` mirrors the receiver's PLAYING/PAUSED into it (TV-remote pauses stick), except
  the end-of-track PAUSED.
- `connectTo`, `play()`, `CastController.onPlayQueueWhileCasting()` (tapping a song / starting a queue)
  and `advanceRemoteAfterEnd()` set it true; `pause()` clears it.
- `load()` never forces it; it re-pauses after loading when it is false.

| While casting | Result |
| --- | --- |
| Auto-advance while playing | next track plays |
| Tap a song / start a queue | plays |
| **Skip while paused** | next track loads **paused** |
| Pause from the TV remote | survives the next reload |

### Local playback stays suppressed (`shouldStartLocalPlayback`)

`onPlayQueueWhileCasting` pauses local up front, but a `ListQueue` (albums, playlists) has no preload and
sets `playWhenReady` only after an **async fetch**, well after that pause. Both `MusicService.playQueue`
sites that set local `playWhenReady` (preload and fetched items) therefore re-check the live state via
`CastPlayback.shouldStartLocalPlayback(playWhenReady, isConnected)` rather than trusting the earlier
pause (the dual-playback bug).

## The reload de-dup (`remoteLoadedMediaId`)

`CastController.onMediaItemTransition` (driven by `MusicService.onMediaItemTransition`) is the **single
reload owner**: exactly one receiver load per track change, whether or not an Activity is bound. It
reloads on `SEEK` / `AUTO` / `REPEAT`, and on `PLAYLIST_CHANGED` only when the item differs from
`remoteLoadedMediaId` (queue edits fire it too). A new queue whose first track is already on the receiver
must still reload, so `onPlayQueueWhileCasting()` clears `remoteLoadedMediaId`; `CastConnector` sets it
via `markRemoteLoaded` so the first `PLAYLIST_CHANGED` after connect does not reload.

## Stream URL + content type

`CastConnector` resolves the stream with `MusicService.resolveStreamUrl(id)` (the normal `YTPlayerUtils`
path; falls back to `currentStreamUrl`), then hands the receiver `relayedStreamUrl(id, url)`;
`CastController`'s reload does the same. `resolveStreamUrl` caches the container MIME in `songMimeCache`,
and `streamContentType(id)` / `currentContentType` return it or `"audio/mp4"`. The receiver needs the
**container** MIME - never `player.audioFormat.sampleMimeType` (the codec MIME, which receivers reject).
`songMimeCache` is cleared in lockstep with `songUrlCache` on auth change and entry-by-entry by
`invalidateStreamCache`.
