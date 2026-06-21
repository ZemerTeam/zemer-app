# UI standards and rules

How to build UI in this app so new screens look and behave like the existing ones. Match these
conventions rather than inventing parallel patterns. All UI is Jetpack Compose + Material 3.
Most of the codebase follows them; some older screens predate parts of this doc (section 8 in
particular), so `scripts/ui-audit.sh` ratchets the known gaps down without blocking you.

## 1. Reuse before building

- Look in `app/src/main/kotlin/com/jtech/zemer/ui/component/` first. There are ready components for
  settings rows (`Preference.kt`), grouped settings cards (`Material3SettingsGroup.kt`), grouped
  menu / detail rows (`Material3MenuItem.kt`), dialogs (`Dialog.kt`, `*Dialog.kt`), bottom sheets
  (`BottomSheet*.kt`), menus (`GridMenu.kt`, `NewMenuComponents.kt`), list items (`Items.kt`),
  icon buttons (`IconButton.kt`), chips (`ChipsRow.kt`), placeholders (`EmptyPlaceholder.kt`,
  `AppStateViews.kt`), and more.
- Do not introduce a second component that duplicates one of these. (For example, settings rows use
  the `Preference.kt` widgets below — do not add a parallel "settings group" widget set; grouped/
  separated rows use the components in section 11 — do not hand-roll cards per row.)

## 2. Settings screens

Every settings screen is a `@Composable fun XxxSettings(navController: NavController,
scrollBehavior: TopAppBarScrollBehavior)` annotated `@OptIn(ExperimentalMaterial3Api::class)`.

Skeleton:

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExampleSettings(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val (enabled, onEnabledChange) = rememberPreference(ExampleKey, defaultValue = true)

    Column(
        Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
            .verticalScroll(rememberScrollState()),
    ) {
        PreferenceGroupTitle(title = stringResource(R.string.example_group))

        SwitchPreference(
            title = { Text(stringResource(R.string.example_toggle)) },
            description = stringResource(R.string.example_toggle_desc),
            icon = { Icon(painterResource(R.drawable.example), null) },
            checked = enabled,
            onCheckedChange = onEnabledChange,
        )
    }

    TopAppBar(
        title = { Text(stringResource(R.string.example_title)) },
        navigationIcon = {
            IconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain,
            ) {
                Icon(painterResource(R.drawable.arrow_back), contentDescription = null)
            }
        },
        scrollBehavior = scrollBehavior,
    )
}
```

Rules:

- Body is a scrollable `Column` (`verticalScroll(rememberScrollState())`) padded with
  `windowInsetsPadding(LocalPlayerAwareWindowInsets.current)`. Use a `LazyColumn` instead only when
  the screen contains a dynamic or reorderable list (see section 6) — never nest a `LazyColumn`
  inside a `verticalScroll` `Column`.
- The `TopAppBar` is emitted after the body (it draws over the top) and is given the passed
  `scrollBehavior`. Its back button is the app's `com.jtech.zemer.ui.component.IconButton` with
  `onClick = navController::navigateUp` and `onLongClick = navController::backToMain`.
- Group separation comes from `PreferenceGroupTitle` (it has its own 16dp padding). Do not insert
  arbitrary `Spacer` heights between groups.

## 3. Settings widgets (`ui/component/Preference.kt`)

Use these; do not hand-roll equivalents.

| Component | Use for |
| --- | --- |
| `PreferenceGroupTitle(title)` | Section header. Renders an uppercase `labelLarge` in `primary`. |
| `PreferenceEntry(title, description?, icon?, trailingContent?, onClick?, isEnabled?)` | Generic clickable row; the base for everything below. Use directly when you need a custom trailing control (e.g. a drag handle + switch) or a row that opens a dialog. |
| `SwitchPreference(title, description?, icon?, checked, onCheckedChange, isEnabled?)` | Boolean toggle row. The thumb shows `check`/`close` icons automatically. |
| `EditTextPreference(...)` | Inline text field preference. |
| `SliderPreference(...)` | Numeric slider preference. |

- `title` is `@Composable () -> Unit` (usually `{ Text(stringResource(...)) }`); `description` is a
  plain `String?`; `icon` is `{ Icon(painterResource(R.drawable.x), null) }`.
- A row that opens a chooser is a `PreferenceEntry` whose `description` shows the current value and
  whose `onClick` sets a `showDialog` state (see section 7).

## 4. Preferences and state

- Read/write DataStore preferences with `rememberPreference(key, defaultValue)`; it returns a
  `(value, setter)` pair.
- Declare keys in `com.jtech.zemer.constants.PreferenceKeys` (`booleanPreferencesKey`,
  `stringPreferencesKey`, etc.).
- If a default-off feature must behave as off when the key is unset, gate the consumer on
  `!= true`, not `== false` (an unset key reads as null).
- Persist on discrete actions (a toggle) or at the end of a continuous gesture (a drag), not on
  every intermediate frame. Keep a local working copy for in-progress gestures and write once when
  it settles.

## 5. Strings (localization)

- Add every new user-facing string to `app/src/main/res/values/metrolist_strings.xml`.
- Never add strings to `app/src/main/res/values/strings.xml` — it is upstream InnerTune strings and
  is headed `Do not add new features here`.
- No hardcoded user-facing text in Kotlin; always `stringResource(R.string.x)` (or
  `context.getString(...)` in non-composable contexts: toasts, notifications, clipboard labels,
  queue titles built in click handlers — hoist a `val` from `stringResource` when the value is
  needed inside a lambda). Technical identifiers shown verbatim (client names, animation labels,
  URL parameters, pure-format strings like `"${'$'}{progress}%"`) may be literals.
- To localize a display label that lives on an enum/constant (e.g. `DensityScale`), give it a
  `@StringRes labelRes: Int` and resolve `stringResource(it.labelRes)` at the call site, rather
  than holding the English text on the enum.
- Enforcement: `scripts/ui-audit.sh` fails CI on any new hardcoded user-facing string under `ui/`
  (R5-hardcoded, baseline zero). The check is a multi-line-aware scanner
  (`scripts/ui-strings-scan.py`), not a line grep, so it also catches a literal that sits on a
  different line from its `Text(`/`text =`/`Toast`/`section =` — the shape that used to slip
  through.

## 6. Lists and reordering

- Static content: a `Column` (see section 2).
- Dynamic or long content: a `LazyColumn` that is the screen's single scroll container. Put the
  non-list parts in `item { }` blocks and the list in `items(...) { }` so there is exactly one
  scrollable. Do not give a `LazyColumn` a hardcoded pixel height to embed it in a `Column`.
- Reordering uses `sh.calvin.reorderable` (`rememberReorderableLazyListState`, `ReorderableItem`,
  `longPressDraggableHandle`). Map moves by stable item `key`, not lazy index, and persist the new
  order in the handle's `onDragStopped`.

## 7. Dialogs

- Use the app's `Dialog.kt` helpers (`DefaultDialog`, `ListDialog`, `TextFieldDialog`,
  `ActionPromptDialog`) — never raw Material 3 `AlertDialog`/`BasicAlertDialog`. The helpers
  derive the AMOLED pure-black surface themselves (`rememberPureBlack()` in `ui/theme/Theme.kt`:
  preference AND dark theme active) — do not thread a pureBlack parameter through callers.
- The bare compose `Dialog` primitive is reserved for non-modal custom containers; the only
  current case is `AccountSettingsDialog` (full-screen scrim with a top-anchored panel). Anything
  shaped like a modal dialog goes through the helpers.
- In `buttons`, Cancel comes first and the affirmative action last (matching M3's
  dismiss-then-confirm order). A pick-and-close list puts only Cancel there.
- `DefaultDialog` content defaults to `bodyMedium` (matching M3 `AlertDialog`); only set a style
  when you want something else. Use `horizontalAlignment = Alignment.Start` for prose dialogs.
  (`TextFieldDialog` overrides its inputs back to `bodyLarge`, the M3 text-field size.)
- `DefaultDialog` caps its height and gives the body a bounded weight, so tall content can't push
  the buttons off-screen and a scrollable/lazy child (e.g. a `LazyColumn` of options) is measured
  with a finite height instead of crashing. Content that can overflow should still bring its own
  `verticalScroll`/`LazyColumn` — the cap bounds it, it does not scroll for you.
- Enforcement: `scripts/ui-audit.sh` fails CI on any new raw `AlertDialog(`/`BasicAlertDialog(`
  under `ui/` outside `component/Dialog.kt` (ratcheted like Rule 8, baseline currently zero).

## 8. Theme and color

- Colors come from `MaterialTheme.colorScheme` roles; never hardcode hex. Common usage in this app:
  - `primary` — group titles, emphasis.
  - `onSurfaceVariant` — secondary/hint text, inactive icons.
  - `secondaryContainer` / `onSecondaryContainer` — chips and soft pills.
  - `surface` / `surfaceVariant` — backgrounds and subtle containers.
- Typography comes from `MaterialTheme.typography` (`Type.kt`); do not set raw font sizes. Hints and
  secondary text are `bodyMedium`/`bodySmall` in `onSurfaceVariant`.
- Theme setup lives in `ui/theme/` (`Theme.kt`, `Type.kt`); dynamic/player colors in
  `PlayerColorExtractor.kt`.
- Player background (blur / gradient): the full player and the mini player share one source of truth
  in `ui/player/PlayerBackground.kt`. Read **`PlayerBackgroundStyle.effective()`** (not the raw
  preference) for every render decision — it downgrades BLUR to DEFAULT below Android 12, where the
  `RenderEffect` blur is a no-op (a raw BLUR there shows bright artwork under light text). Hide BLUR
  from the settings list when `isBlurSupported` is false. Extract gradient colors only through
  **`rememberPlayerGradient(...)`** (one shared, bounded, deduped cache) and build the gradient stops
  only through **`playerGradientStops(colors)`** — never hand-roll a Palette extraction or a stop
  array per surface. Use **light (white) content only when the dark backdrop is actually painted**
  (blur needs a `thumbnailUrl`, gradient needs non-empty colors); otherwise keep the solid
  `surfaceContainer` with theme-colored text, or white text lands on the light screen behind a
  not-yet-painted backdrop. The status-bar `DisposableEffect` must key on **expansion** too and force
  light icons only while the sheet is expanded — a collapsed/floating player follows the theme.
- Enforcement: `scripts/ui-audit.sh` (ratcheting) fails CI on *new* raw `fontSize = N.sp`,
  `Color(0x..)`, or `Modifier.blur(` (R12 — route player blur through the effective style) under
  `ui/` (outside `theme/`); the current known cases are baselined in `scripts/ui-audit-baseline.tsv`
  and can only shrink (run `--update` after fixing some). A few fixed values are genuinely required
  and stay baselined: AMOLED pure-black (`0xFF0A0A0A`), the lyric-image *export* (it renders a
  shareable bitmap, not themed UI), and color-picker swatches. Keep such cases minimal.

## 9. Icons

- Vector drawables in `app/src/main/res/drawable/`, referenced with `painterResource(R.drawable.x)`.
- Switch thumbs use `check`/`close`; the back arrow is `arrow_back`. Reuse existing drawables before
  adding new ones.

## 10. Documentation

- No emojis or decorative symbols anywhere in `docs/` — ASCII only. (Arrows like `->` over a glyph.)
- Keep this file in sync when a shared UI convention changes.

## 11. Grouped lists, menus and detail sheets

For label/value or action rows grouped into sections, reuse one of these — do not hand-roll a card
per row or a parallel group widget:

- `Material3SettingsGroup(title, items)` + `Material3SettingsItem` (`Material3SettingsGroup.kt`) —
  one rounded card with the items stacked inside, separated by hairline dividers. The icon is a
  `Painter` and is rendered inside a tinted-primary chip by the component. This is the **settings**
  idiom (the settings hub uses it).
- `Material3MenuGroup(items)` + `Material3MenuItemData` (`Material3MenuItem.kt`) — each item is its
  own card, 4dp apart, with adaptive corner radii (rounded outer ends, lightly-rounded middles):
  the "expressive" separated-list look, built from standard M3 `Card`s (not the Material 3
  Expressive library). The icon is a `@Composable` slot, so the caller controls it: render it bare
  for a flat menu (e.g. `PlayerMenu`), or wrap it in a 40dp `RoundedCornerShape(12.dp)` box tinted
  `primary @ 0.1` with the icon tinted `primary @ 0.9` for the richer chipped look (e.g. the
  song-details sheet, `ShowMediaInfo`). Per-item `cardColors` carry a destructive/emphasis color.

Rules for these and any new row component:

- **D-pad (non-negotiable):** the row must be `.focusable()` with an animated focus background +
  border. Metrolist's upstream rows omit this; ours must not. For a bespoke clickable row/card
  outside the shared group components, apply `Modifier.focusBorder()` (`FocusBorder.kt`) — the single
  source of truth for that treatment — placed **before** `.clickable {}` in the chain so the ripple
  is clipped (`Material3MenuItemRow` / `Material3SettingsItemRow` inline the same effect).
- **Never add `.focusable()` to a `TextField`/`BasicTextField`.** It is already focusable; a stray
  `.focusable()` after `.focusRequester(fr)` binds `fr` to that wrapper node instead of the text
  editor, so `fr.requestFocus()` (e.g. auto-focusing the search field) gains focus but never starts
  text input and the soft keyboard stays hidden. Attach `.focusRequester(fr)` directly to the field.
- The row provides `titleMedium` for the title and `bodyMedium`/`onSurfaceVariant` for the
  description. To shrink text (e.g. dense detail rows), pass an explicit `style` on your `Text` —
  it overrides the row default — rather than editing the component.
- Localize every label and format numbers with `numberFormatter` (locale grouping separator — do
  not force a separator).
- Enforcement: `scripts/ui-audit.sh` (ratcheting, rule `R11-menu`) fails CI on any *new* raw
  `ListItem(` under `ui/menu/` — a grouped action menu must be built from `Material3MenuGroup` /
  `Material3MenuItemData`, not raw `ListItem` rows. The check is scoped to `ui/menu/` (a raw
  `ListItem` is fine for a plain song list elsewhere) and the legit dialog / data-list / header
  `ListItem`s that remain in menu files are baselined in `scripts/ui-audit-baseline.tsv`; the count
  can only shrink. If a genuine dialog/data-list `ListItem` is added, record it with `--update`.
- Common menu dialogs are shared, not re-implemented per menu: `SelectArtistDialog` (the
  multi-artist picker) and `AlreadyInPlaylistDialog` (`MenuDialogs.kt`). Reuse them rather than
  hand-rolling a `ListDialog` copy.
- **Bottom-sheet menus must stay scrollable.** Every menu shown via `LocalMenuState.show { … }` is
  hosted in the full-height `ModalBottomSheet` of `BottomSheetMenu.kt`, with the menu's own
  `LazyColumn` as the single scroll container. Leave that `LazyColumn` user-scrollable (the default)
  and let its `contentPadding` carry the bottom `WindowInsets.systemBars` inset — do **not** gate
  scrolling on orientation/screen (e.g. `userScrollEnabled = !isPortrait`). Tall menus fit on the
  developer's device but overflow on shorter screens, larger font/display scale, or with a gesture
  nav bar; disabling scroll there makes the bottom items unreachable.

## 12. Download state (one source of truth)

Download/progress state is computed in exactly one place and read everywhere — never re-derived per
surface. This is what keeps a downloaded album/song/playlist showing "Remove download" (and a live
progress ring) identically in the library, Home, search rows, every menu and every header.

- The rule lives in `playback/DownloadStateResolver.kt` (pure, unit-tested): a song is **DOWNLOADED**
  when the persisted `SongEntity.isDownloaded` flag is set **OR** the live MediaStore state is
  `COMPLETED`, **DOWNLOADING** when the live state is `QUEUED`/`DOWNLOADING`, else **NOT_DOWNLOADED**;
  `aggregateSongs` / `aggregateProgress` extend that to a collection. The persisted flag is the only
  thing that survives a process restart — the live `MediaStoreDownloadManager.downloadStates` map is
  in-memory — so reading the live map *alone* is the bug that makes downloads "vanish" after relaunch.
- The UI reads it through `ui/component/DownloadStatusUi.kt`: `rememberSongDownloadStatus`,
  `rememberAggregateDownloadStatus`/`…Progress`, the `DownloadStatusIcon` badge, `SongDownloadBadge`
  (the default song-row badge) and `AggregateDownloadButton` (the album/playlist header control, with
  the D-pad focus border + determinate progress).
- Menu rows are built by one decision (`playback/DownloadMenuLogic.kt`, unit-tested) + one builder
  (`ui/menu/DownloadMenuItems.kt` `downloadMenuItem(...)`). Tapping a download row **never dismisses
  the menu** — it animates Download → progress ring + "%" → Remove in place. Videos are handled by the
  same path (`DownloadRowKind.DOWNLOAD_VIDEO`, hidden when videos are blocked).
- **A collection never gets a FAILED/retry row.** `collectionRow` returns only REMOVE / DOWNLOADING /
  DOWNLOAD — a failed member leaves the aggregate NOT_DOWNLOADED, so the collection offers DOWNLOAD
  (which re-enqueues just the missing members = retry) and is removable once complete. A collection
  "retry" row was a dead end that hid Download *and* Remove and re-failed the dead track forever. Only
  single songs get a FAILED row (`songRow`).
- **Async/online collection menus (album / playlist / multi-select) read ONE resolved list for every
  action.** Resolve/fetch the tracks at click time (fetch-if-empty), then Download, Remove **and** the
  aggregate status all iterate that *same* resolved list — never the original (possibly-empty) `songs`
  prop. A Remove that loops the empty prop while Download loops the fetched list removes nothing (real
  bug on the Home long-press playlist menu). For online items: aggregate via `aggregateByIds` (+ a
  persisted-downloaded id set) so progress shows without Room entities, and on Download **persist each
  item then download** (`database.insert`/`transaction { insert }` then `database.song(id).first()`) —
  a bare lookup of a not-yet-persisted id no-ops, so the first tap appears to do nothing.
- **Manager invariants** (`MediaStoreDownloadManager`, no Robolectric so verified by code + on-device):
  `markSongAsDownloaded` bases the row on the **existing** DB row and overwrites only download columns
  (never clobber `liked`/`inLibrary` with a caller's stale `Song`); `performDownload` backfills
  `duration` **and** `thumbnailUrl` from the playback response; the per-download video bitrate is
  cleared only on success/cancel/delete (never on a failed attempt, so retry keeps the chosen quality).
- The legacy ExoPlayer `DownloadUtil.downloads` / `getDownload()` map is dead (nothing writes it for
  MediaStore downloads). **Do not read it, and do not hand-roll an `Icon.Download(`.** Enforcement:
  `scripts/ui-audit.sh` rule `R13-download` (baselined at zero) fails CI on any new
  `downloadUtil.downloads`, `.getDownload(` or `Icon.Download(` under `ui/`.
