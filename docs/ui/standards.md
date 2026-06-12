# UI standards and rules

How to build UI in this app so new screens look and behave like the existing ones. Match these
conventions rather than inventing parallel patterns. All UI is Jetpack Compose + Material 3.
Most of the codebase follows them; some older screens predate parts of this doc (section 8 in
particular), so `scripts/ui-audit.sh` ratchets the known gaps down without blocking you.

## 1. Reuse before building

- Look in `app/src/main/kotlin/com/jtech/zemer/ui/component/` first. There are ready components for
  settings rows (`Preference.kt`), dialogs (`Dialog.kt`, `*Dialog.kt`), bottom sheets
  (`BottomSheet*.kt`), menus (`GridMenu.kt`, `NewMenuComponents.kt`), list items (`Items.kt`),
  icon buttons (`IconButton.kt`), chips (`ChipsRow.kt`), placeholders (`EmptyPlaceholder.kt`,
  `AppStateViews.kt`), and more.
- Do not introduce a second component that duplicates one of these. (For example, settings rows use
  the `Preference.kt` widgets below — do not add a parallel "settings group" widget set.)

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
- Enforcement: `scripts/ui-audit.sh` (ratcheting) fails CI on *new* raw `fontSize = N.sp` or
  `Color(0x..)` under `ui/` (outside `theme/`); the current known cases are baselined in
  `scripts/ui-audit-baseline.tsv` and can only shrink (run `--update` after fixing some). A few
  fixed values are genuinely required and stay baselined: AMOLED pure-black (`0xFF0A0A0A`), the
  lyric-image *export* (it renders a shareable bitmap, not themed UI), and color-picker swatches.
  Keep such cases minimal.

## 9. Icons

- Vector drawables in `app/src/main/res/drawable/`, referenced with `painterResource(R.drawable.x)`.
- Switch thumbs use `check`/`close`; the back arrow is `arrow_back`. Reuse existing drawables before
  adding new ones.

## 10. Documentation

- No emojis or decorative symbols anywhere in `docs/` — ASCII only. (Arrows like `->` over a glyph.)
- Keep this file in sync when a shared UI convention changes.
