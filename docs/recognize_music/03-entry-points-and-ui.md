# 3 · Entry points, UI, history & the widget

There is **one** recognition UI - the popup activity - reached from two entry points. There is
intentionally no `recognize_music` nav route.

## The popup - `RecognizeMusicDialogActivity`

`ui/screens/recognition/RecognizeMusicDialogActivity.kt`: a transparent `@AndroidEntryPoint`
`ComponentActivity` (`@style/Theme.Zemer.Transparent`) drawing a centered card over the current screen.

- **Records while visible**, so it has normal while-in-use mic access: **no foreground microphone
  service** and no `FOREGROUND_SERVICE_MICROPHONE` permission. Keep it that way.
- Wrapped in `ZemerAppTheme` (the user's palette / dark mode / pure-black); the card uses theme tokens.
- Header: the launcher icon (`R.mipmap.ic_launcher`, circle-clipped), app name, and a history icon.
- Auto-starts listening once per instance (a `rememberSaveable` flag, so rotation doesn't restart
  capture or re-prompt); requests `RECORD_AUDIO` via `rememberLauncherForActivityResult` if needed.
- **Play** → `MainActivity` with `ACTION_VIEW` `https://music.zemer.io/watch?v=<id>`, then `finish()`.
  **History icon** → `https://music.zemer.io/recognition_history`, then `finish()`.

Both deep links are handled in `MainActivity.handleDeepLinkIntent`: `watch?v=` runs `YouTube.queue(...)`
then `filterWhitelistedWithLocalArtists(...)` before playing (a non-whitelisted id toasts
`R.string.song_not_available`); `recognition_history` navigates to that route
(`ui/screens/NavigationBuilder.kt` → `RecognitionHistoryScreen`).

## Entry point 1 - in-app FAB

`ui/component/RecognizeMusicFab.kt`, placed in `MainActivity` at bottom-end above the nav bar /
mini-player. Shown only when `RecognizeMusicFabKey` is on (default **true**, Settings → Appearance), the
current route is Home **and** the effective Home tab is MUSIC (same `effectiveHomeTab` inputs as the
Home selector), search is inactive, and the player sheet is collapsed/dismissed.

## Entry point 2 - the widget

There is **one** home-screen widget: the Glance player widget (`widget/MusicWidget.kt`,
`MusicWidgetReceiver`) with the mic folded in.

- **Main row**: album art (`fillMaxHeight`), a weighted, ellipsized title/artist column, then
  prev / play-pause (`widget_accent`) / next / mic (`RecognizeButton` → the popup via
  `actionStartActivity`). Transport buttons use `actionStartService` to `MusicService`, handled in
  `onStartCommand` (`MusicWidget.ACTION_PREV` / `ACTION_PLAY_PAUSE` / `ACTION_NEXT`).
- **Seek row**: elapsed · `LinearProgressIndicator` · total, from the Glance prefs `position_ms` /
  `duration_ms`. It is dropped when the widget is shorter than
  `WidgetLayout.COMPACT_SEEK_THRESHOLD_DP` (72 dp) instead of clipping the controls.
- `sizeMode = SizeMode.Exact`: laid out for the actual size every time (fixed `Responsive` buckets
  clipped on smaller placements). `res/xml/music_widget_info.xml` sets `minHeight=84dp`,
  `minResizeHeight=76dp`, 2×2 target cells.
- Read-only progress: RemoteViews can't offer a draggable scrubber.

`MusicService` feeds it: `updateWidget()` pushes title/artist/art/position/duration (the cast
receiver's state and clock while casting). `onIsPlayingChanged(true)` starts a 1 s ticker that runs
only while `widgetIsPlaying()` and only if `MusicWidget.hasPlacedWidget` (checked once per playback
session, so users without a widget pay nothing); pausing pushes one final update.
`hasPlacedWidget` must stay wrapped in try/catch: `getGlanceIds` NPEs on ROMs without an
AppWidgetService, which would kill background playback. `MusicWidget.updateWidget` itself no-ops
when no widget is placed and loads art (coil, copied off `HARDWARE` config, persisted to a file) only
when the URL changes.

## Recognition history

**Table** (`db/entities/RecognitionHistoryEntity.kt`): `recognition_history` with `id`, `songId`,
`title`, `artist` (joined names), `thumbnailUrl`, `artistIds` (comma-joined browse ids, for the
whitelist re-check), `recognizedAt`; indexed on `songId` (de-dup delete) and `recognizedAt` (ordering).
Added by the additive `AutoMigration(from = 32, to = 33)` and unchanged since.

**DAO** (`db/DatabaseDao.kt`): `recognitionHistory()` (newest first), `insertRecognitionHistory`,
`deleteRecognitionHistoryBySong`, `deleteRecognitionHistory`, `clearRecognitionHistory`.

**Write**: only in `RecognitionResolver.recordHistory`, after both gates pass - delete-then-insert by
song (most recent, no repeats), wrapped in `runCatching` so a history failure never breaks
recognition. The whitelist re-check on read is in [02](02-whitelist-guarantee.md).

**UI**: `RecognitionHistoryScreen` - a `LazyColumn` (`LocalPlayerAwareWindowInsets` padding) of
`focusBorder()` rows; tap plays seed-first radio
(`playQueue(ZemerRadioQueue.song(entry.toMediaMetadata(), pc.service))`), per-row remove, and clear-all
behind a `DefaultDialog` confirm. Rows never use the deep link.

`RecognitionHistoryEntity.toMediaMetadata()` (`recognition/RecognitionHistoryPlayback.kt`) holds two
easy-to-regress rules: `duration = -1` (the unknown sentinel; `0` makes `MusicService.recoverSong`
skip its repair fetch and show "0:00" forever), and names paired with `artistIds` only when counts
match (a lone name takes the first id; otherwise null ids rather than mis-attributing a channel).
