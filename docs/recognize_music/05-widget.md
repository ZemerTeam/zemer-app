# 5 · The combined widget (player + recognize + seek bar)

There is **one** Zemer home-screen widget: the now-playing player widget, with the recognize mic
folded in. (An earlier standalone recognizer widget was removed — one widget, not two.) It is built
with **Jetpack Glance** (`widget/MusicWidget.kt`, `MusicWidgetReceiver`), matching Zemer's existing
widget idiom.

## Layout — one adaptive design

```
┌───────────────────────────────────────────────┐
│ [art]  Title (1 line, ellipsized)   ⏮ ⏯ ⏭ 🎤  │   main row (fills remaining height)
│        Artist (1 line)                          │
│ 0:42  ▓▓▓▓▓▓▓░░░░░░░░░░░░░░░░░  3:15             │   seek row (elapsed · bar · total)
└───────────────────────────────────────────────┘
```

- **Main row**: album art (`fillMaxHeight`, fixed width — fills the widget height so there's no
  vertical whitespace), a weighted/ellipsized title+artist column, then transport: prev / play-pause
  / next / **mic** (`RecognizeButton` → opens the popup). The play button uses `widget_accent`.
- **Seek row**: elapsed time · `LinearProgressIndicator` (`androidx.glance.appwidget`) · total time.

### Why it fits at every size

`override val sizeMode = SizeMode.Exact` — Glance lays the widget out for the **actual** width and
height every time. (The previous `SizeMode.Responsive` rendered fixed 280×56 / 280×100 buckets and
clipped whenever the real widget was narrower/shorter than a bucket — that was the "all cut off"
bug.) With Exact + `fillMaxHeight` art + a weighted text column, content adapts to any size with no
clipping and no whitespace.

`res/xml/music_widget_info.xml`: `minHeight=84dp`, `minResizeHeight=76dp`, `targetCellWidth=4`,
`targetCellHeight=1` — short by default, but tall enough that the transport + seek row always fit.

## The live seek bar (~1 s)

The widget state carries `position_ms` / `duration_ms` (Glance `Preferences`), formatted to
`m:ss` and a 0..1 progress.

`MusicService` (`playback/MusicService.kt`) feeds it:

- `updateWidget()` reads `player.currentPosition` / `player.duration` and calls
  `MusicWidget.updateWidget(... positionMs, durationMs)`. It early-returns if no widget is placed.
- A **1-second ticker** runs while playing:

  ```kotlin
  override fun onIsPlayingChanged(isPlaying: Boolean) {
      if (isPlaying) startWidgetTicker() else updateWidget()
  }
  private fun startWidgetTicker() {           // self-stops when paused
      if (widgetTickerJob?.isActive == true) return
      widgetTickerJob = scope.launch {
          while (isActive && player.isPlaying) { updateWidget(); delay(1000) }
      }
  }
  ```

  On pause it pushes one final update so the bar freezes at the paused position.

> **Not draggable.** A home-screen widget (Glance/RemoteViews) can show progress + time but cannot be
> dragged to seek — only the system media player gets a scrubber. The bar is read-only; tapping the
> art/title opens the app.

## Transport actions

`prev` / `play-pause` / `next` use `actionStartService` to `MusicService`, handled in
`MusicService.onStartCommand` (`MusicWidget.ACTION_PREV/PLAY_PAUSE/NEXT`). The mic uses
`actionStartActivity` to the popup. (The old `ACTION_LIKE` was removed with the like button.)

## Album art

`MusicWidget.updateWidget` loads the art bitmap via coil only when the URL changes (cached in a
companion field), copying off `HARDWARE` config, then pushes it with `BitmapImageProvider`.
