# 3 · Entry points, UI & navigation

There is **one** recognition UI — the Zemer-branded popup — reached from two places. There is no
separate full-screen recognition page (an earlier full screen was removed in favor of the popup).

## The popup — `RecognizeMusicDialogActivity`

`ui/screens/recognition/RecognizeMusicDialogActivity.kt` is a **transparent** `ComponentActivity`
(`@AndroidEntryPoint`, theme `@style/Theme.Zemer.Transparent` in `res/values/styles.xml`) that draws
a small centered card over whatever's on screen — like Google's Sound Search.

- **Records while visible** → it has normal "while-in-use" microphone access, so there is **no
  foreground microphone service** and no `FOREGROUND_SERVICE_MICROPHONE` permission anywhere.
- Wraps content in `ZemerTheme`; the card is **pure black** to match the widget — it uses the widget
  palette (`R.color.widget_background` / `widget_text_primary` / `widget_text_secondary` /
  `widget_accent`), not the M3 theme surface, so it looks identical regardless of light/dark theme.
- Header: the **actual launcher icon** (`R.mipmap.ic_launcher`, round) + "Zemer" + a **history**
  icon (top-right) that opens recognition history.
- Auto-starts listening on open (guarded so it fires once per instance); requests `RECORD_AUDIO`
  via `rememberLauncherForActivityResult` if not yet granted.
- States mirror `RecognizeUiState`: pulsing mic (Listening) → spinner (Identifying/Searching) →
  result (cover + title + artist + **Play** / **Try again**) → or "No match" / error.
- **Play** → starts `MainActivity` with `ACTION_VIEW` data `https://music.zemer.io/watch?v=<id>` and
  finishes; that deep link plays the song through the existing whitelist-guarded path.
- **History icon** → starts `MainActivity` with data `https://music.zemer.io/recognition_history`
  and finishes.

The VM (`RecognizeMusicViewModel`) is shared; the popup and any future surface get identical behavior.

## Entry point 1 — in-app FAB

`ui/component/RecognizeMusicFab.kt` is a Material 3 FAB with the mic icon. It's placed in
`MainActivity` over the nav content (bottom-end, above the nav bar / mini-player), shown on main
screens when not searching and the player sheet isn't expanded. Tapping it launches the popup:

```kotlin
RecognizeMusicFab(onClick = { context.startActivity(Intent(context, RecognizeMusicDialogActivity::class.java)) }, …)
```

It is **toggleable**: `RecognizeMusicFabKey` (default **on**), exposed in **Settings → Appearance**
("Recognize music button"). `MainActivity` reads the preference to decide whether to show the FAB.

## Entry point 2 — the home-screen widget

The combined player widget (`widget/MusicWidget.kt`) has a mic button (`RecognizeButton`) that opens
the same popup via `actionStartActivity(Intent(context, RecognizeMusicDialogActivity::class.java))`.
Full widget details in [05-widget.md](05-widget.md).

## Deep links (`MainActivity.handleDeepLinkIntent`)

Two paths matter to this feature (both are whitelist-safe):

| URI path | Effect | Notes |
|---|---|---|
| `…/watch?v=<id>` | Plays the song | Runs `YouTube.queue(...).filterWhitelisted(...)` before playing — non-whitelisted ids silently no-op. Used by the popup's Play and history rows. |
| `…/recognition_history` | Navigates to the history screen | `navController.navigate("recognition_history")`. Used by the popup's history icon. |

## Navigation routes

| Route | Screen |
|---|---|
| `recognition_history` | `RecognitionHistoryScreen` (`ui/screens/NavigationBuilder.kt`) |

There is intentionally **no** `recognize_music` route anymore — recognition is the popup activity,
not a nav destination.

## Permissions (`AndroidManifest.xml`)

- `android.permission.RECORD_AUDIO` — required to listen.
- `<uses-feature android:name="android.hardware.microphone" android:required="false">` — so the app
  still installs on mic-less devices.
- **No** foreground-service permissions for recognition (the popup records while visible).
