#!/usr/bin/env bash
# UI standards audit (docs/ui/standards.md, sections 5, 7-8: strings, dialogs, theme & color).
#
# Ratcheting check: it FAILS only on NEW Rule 5/7/8 violations beyond the committed baseline
# (scripts/ui-audit-baseline.tsv). The current known violations are allowlisted, so CI is green
# today and the count can only shrink — fix some, then run --update to tighten the baseline.
#
# Section 11 (grouped lists / menus) is PARTLY checked: raw `ListItem(` inside ui/menu/ is ratcheted
# (R11 below) — a grouped action menu must be built from Material3MenuGroup, not raw ListItem rows.
# Scoping to ui/menu/ avoids the false positive the rest of the app would cause (a raw `ListItem(` is
# correct for a plain song list, wrong for a grouped menu); the ratchet allowlists the legit dialog /
# data-list ListItems that legitimately remain in menu files, so only NEW ones fail.
#
# Componentization / reuse (standards.md section 1) is PARTLY checked: the two highest-frequency
# shared widgets are ratcheted here — the top-bar back button (R14, BackNavigationIcon / BackTopAppBar)
# and the row 3-dot overflow menu (R15, MoreVertMenuButton). A screen that re-rolls either from a raw
# drawable fails once the baseline is set. Both are broad drawable matches, so the genuinely-different
# sites (a state-branching nav icon that flips to `close` in selection mode; a non-nav glyph) are
# baselined and only NEW hand-rolls fail — record a legit new one with --update.
#
# NOT mechanically checked here (enforced by code review, see standards.md): the rest of the reuse
# rules (section 1) — TopAppBarActionButton, PlaylistPlayShuffleButtons, the shimmer placeholders,
# ArtistBrowseComponents — plus the settings grouped-list component (Material3SettingsGroup) and the
# .focusable() D-pad requirement on any new row component — reuse/structure judgments, not greppable
# without heavy false positives.
#
#   bash scripts/ui-audit.sh            # check; exit 1 if a file gained violations
#   bash scripts/ui-audit.sh --update   # rewrite the baseline to the current state
#
# Rules enforced over UI code (app/.../ui/, excluding ui/theme/):
#   R8-fontsize    raw `fontSize = N.sp`   -> use MaterialTheme.typography (Type.kt)
#   R8-hex         hardcoded `Color(0x..)` -> use MaterialTheme.colorScheme
#   R7-alertdialog raw `AlertDialog(` / `BasicAlertDialog(` outside component/Dialog.kt
#                  -> use the Dialog.kt helpers (DefaultDialog etc.); baseline is zero
#   R5-hardcoded   hardcoded user-facing text (Text("..."), text = "...",
#                  contentDescription = "...", Toast literals) -> stringResource /
#                  context.getString with metrolist_strings.xml; baseline is zero.
#                  Pure-interpolation strings ("${...}%") are not matched.
#   R11-menu       raw `ListItem(` inside ui/menu/ -> build grouped action menus from
#                  Material3MenuGroup / Material3MenuItemData (section 11). Ratcheted: the
#                  ListItems left in dialog/data-list rows are baselined; only new ones fail.
#   R12-blur       raw `Modifier.blur(` under ui/ -> player blur must go through the effective
#                  background (PlayerBackgroundStyle.effective(), no-op below API 31; see
#                  ui/player/PlayerBackground.kt and standards.md section 8). Ratcheted.
#   R14-backbtn    raw `R.drawable.arrow_back` in a screen -> use the shared BackNavigationIcon /
#                  BackTopAppBar (component/IconButton.kt, component/BackTopAppBar.kt) instead of
#                  re-rolling the top-bar back button. Ratcheted (state-branching nav icons that
#                  legitimately can't use the shared component are baselined).
#   R15-morevert   raw `R.drawable.more_vert` in a screen -> use the shared MoreVertMenuButton
#                  (component/IconButton.kt) instead of a hand-rolled 3-dot overflow button.
#                  Ratcheted.
#   R16-navroute   hand-built id route navigate("artist/$id") / navigate("album/$id") / navigate("online_podcast/$id")
#                  -> use the null-safe navigateToArtist/navigateToAlbum helpers
#                  (ui/utils/AppNavigation.kt); a blank id would otherwise crash on "artist/". Baseline 0.
#   R17-entrypoint raw `EntryPointAccessors.fromApplication(..., ZemerSearchRepositoryEntryPoint::class.java)`
#                  in a composable -> use the Context.zemerSearchRepository() extension
#                  (di/ZemerSearchRepositoryEntryPoint.kt). UI-scoped. Baseline 0.
#   R18-runblocking `runBlocking(` / `runBlocking {` in a UI file -> blocks the main thread (ANR). Use a
#                  suspend fn + LaunchedEffect/rememberCoroutineScope or a Flow (collectAsState).
#                  UI-scoped. Baseline 0.
#   R19-share      hand-rolled `Intent.ACTION_SEND` text/plain share -> use Context.shareText()
#                  (extensions/ContextExt.kt). Excludes component/Lyrics.kt (shares a lyric IMAGE via
#                  EXTRA_STREAM, a different intent). Baseline 0.
#   R20-clipboard  hand-rolled `ClipboardManager.setPrimaryClip(...)` -> use Context.copyToClipboard()
#                  (extensions/ContextExt.kt), which also shows the confirmation toast. Baseline 0.
#   R21-toast      hand-rolled `Toast.makeText(...).show()` -> use Context.toast(text|resId, long)
#                  (extensions/ContextExt.kt). Baseline 0.
#   R22-home-shimmer (positive assertion, not a shrink-count) the Home loading skeleton is shaped like
#                  the MUSIC home (title + card row) and is driven by music-VM state, so it MUST be
#                  scoped to `homeTab == HomeContentTab.MUSIC` — else it paints an inaccurate skeleton
#                  on Radio/Podcasts/Videos that never resolves. Loading skeletons must match the real
#                  content that replaces them; a per-tab skeleton never renders on another tab.
#
# Genuine fixed-value exceptions (AMOLED pure-black, the lyric-image *export*, color-picker
# swatches) are allowed: they live in the baseline. Keep them minimal; --update records them.
set -u

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
UI="app/src/main/kotlin/com/jtech/zemer/ui"
BASELINE="scripts/ui-audit-baseline.tsv"

# One "<path>\t<rule>" line per violating source line (theme/ excluded).
violations() {
  grep -rnE "fontSize[[:space:]]*=[[:space:]]*[0-9]" "$UI" --include=*.kt 2>/dev/null \
    | grep -v "/theme/" | sed -E 's/:.*//' | sed 's/$/\tR8-fontsize/'
  grep -rnE "Color\(0x" "$UI" --include=*.kt 2>/dev/null \
    | grep -v "/theme/" | sed -E 's/:.*//' | sed 's/$/\tR8-hex/'
  grep -rnE "(^|[^.A-Za-z])(Basic)?AlertDialog\(" "$UI" --include=*.kt 2>/dev/null \
    | grep -v "/theme/" | grep -v "component/Dialog.kt" | sed -E 's/:.*//' | sed 's/$/\tR7-alertdialog/'
  # R5 is multi-line-aware (a Python scanner), since a grep can't see a literal that sits on a
  # different line from its Text(/Toast call — the exact shape that has slipped through before.
  python3 "$ROOT/scripts/ui-strings-scan.py" 2>/dev/null
  # R11: raw `ListItem(` in menus. The `[^.A-Za-z]` guard skips method calls (`.ListItem(`) and
  # composite components whose name ends in ListItem (SongListItem, AlbumListItem, …).
  grep -rnE "(^|[^.A-Za-z])ListItem\(" "$UI/menu" --include=*.kt 2>/dev/null \
    | sed -E 's/:.*//' | sed 's/$/\tR11-menu/'
  # R12: raw Modifier.blur( in UI. Player blur must go through PlayerBackgroundStyle.effective()
  # (the RenderEffect blur is a no-op below API 31). Ratcheted: existing blurs are baselined.
  grep -rnE "\.blur\(" "$UI" --include=*.kt 2>/dev/null \
    | grep -v "/theme/" | sed -E 's/:.*//' | sed 's/$/\tR12-blur/'
  # R13: dead/legacy download-state reads in UI. Download/progress state is ONE path —
  # DownloadStateResolver + the DownloadStatusUi helpers (persisted isDownloaded + live MediaStore).
  # The legacy ExoPlayer `downloadUtil.downloads` / `getDownload()` map is never written, and a
  # per-surface `Icon.Download(` re-implements the unified badge. Baselined at zero.
  grep -rnE "downloadUtil\.downloads|\.getDownload\(|Icon\.Download\(" "$UI" --include=*.kt 2>/dev/null \
    | grep -v "/theme/" | sed -E 's/:.*//' | sed 's/$/\tR13-download/'
  # R14: hand-rolled top-bar back button. The shared BackNavigationIcon / BackTopAppBar own the
  # arrow_back nav icon; a raw R.drawable.arrow_back in a screen re-rolls it. Exclude the two files
  # that DEFINE / wrap the shared component. Ratcheted (state-branching close/back icons baselined).
  grep -rnE "R\.drawable\.arrow_back" "$UI" --include=*.kt 2>/dev/null \
    | grep -v "/theme/" | grep -v "component/IconButton.kt" | grep -v "component/BackTopAppBar.kt" \
    | sed -E 's/:.*//' | sed 's/$/\tR14-backbtn/'
  # R15: hand-rolled 3-dot overflow menu. Use MoreVertMenuButton (component/IconButton.kt) rather
  # than a raw R.drawable.more_vert IconButton. Exclude the defining file. Ratcheted.
  grep -rnE "R\.drawable\.more_vert" "$UI" --include=*.kt 2>/dev/null \
    | grep -v "/theme/" | grep -v "component/IconButton.kt" \
    | sed -E 's/:.*//' | sed 's/$/\tR15-morevert/'
  # R16: hand-built single-segment id nav route. navigate("artist/$id") / navigate("album/$id") built
  # from a possibly-blank id is a crash (empty id -> "artist/" matches no destination -> throws).
  # Route through the null-safe navigateToArtist/navigateToAlbum helpers (ui/utils/AppNavigation.kt).
  # The regex matches ONLY the plain single-segment form (no `/sub` route, no `?query`), so the
  # legit `artist/{id}/songs` and zemerAlbumRoute() sites are not flagged. Baselined at zero.
  grep -rnE 'navigate\("(artist|album|online_podcast)/\$[^"/]*"\)' "$UI" --include=*.kt 2>/dev/null \
    | grep -v "/theme/" | grep -v "utils/AppNavigation.kt" | sed -E 's/:.*//' | sed 's/$/\tR16-navroute/'
  # R17: hand-rolled EntryPoint resolution of the Zemer repository in a composable. A leaf composable
  # with no ViewModel resolves the repo via Context.zemerSearchRepository() (di/ZemerSearchRepositoryEntryPoint.kt),
  # NOT a raw EntryPointAccessors.fromApplication(..., ZemerSearchRepositoryEntryPoint::class.java) call.
  # UI-scoped (the playback/queues classes that legitimately hold the boilerplate live outside ui/).
  grep -rnE "ZemerSearchRepositoryEntryPoint::class" "$UI" --include=*.kt 2>/dev/null \
    | grep -v "/theme/" | sed -E 's/:.*//' | sed 's/$/\tR17-entrypoint/'
  # R18: runBlocking in a composable/UI file blocks the main thread -> ANR. Collect a value with a
  # suspend function + LaunchedEffect/rememberCoroutineScope, or a Flow (collectAsState), or read the
  # documented DataStore sync accessors OFF the main thread. Baseline 0 (no runBlocking under ui/).
  grep -rnE "(^|[^A-Za-z])runBlocking(\(|[[:space:]]*\{)" "$UI" --include=*.kt 2>/dev/null \
    | grep -v "/theme/" | sed -E 's/:.*//' | sed 's/$/\tR18-runblocking/'
  # R19: hand-rolled plain-text share intent. A `text/plain` ACTION_SEND share of a URL goes through
  # Context.shareText() (extensions/ContextExt.kt), not a re-rolled Intent + createChooser. Exclude
  # component/Lyrics.kt, whose ACTION_SEND shares a rendered lyric IMAGE (EXTRA_STREAM), a different
  # intent shape that legitimately keeps its own builder. Baseline 0.
  grep -rnE "Intent\.ACTION_SEND" "$UI" --include=*.kt 2>/dev/null \
    | grep -v "/theme/" | grep -v "component/Lyrics.kt" | sed -E 's/:.*//' | sed 's/$/\tR19-share/'
  # R20: hand-rolled clipboard copy. `ClipboardManager.setPrimaryClip(...)` goes through
  # Context.copyToClipboard(label, text) (extensions/ContextExt.kt), which also shows the confirmation
  # toast. Baseline 0.
  grep -rnE "\.setPrimaryClip\(" "$UI" --include=*.kt 2>/dev/null \
    | grep -v "/theme/" | sed -E 's/:.*//' | sed 's/$/\tR20-clipboard/'
  # R21: hand-rolled `Toast.makeText(...).show()` -> use Context.toast(text|resId, long) (extensions/
  # ContextExt.kt). Baseline 0.
  grep -rnE "Toast\.makeText\(" "$UI" --include=*.kt 2>/dev/null \
    | grep -v "/theme/" | sed -E 's/:.*//' | sed 's/$/\tR21-toast/'
  # R23: an UNGATED focus visual. Every focus ring/border/fill condition must AND with
  # focusVisualsEnabled() (FocusBorder.kt) so touch sessions never see it - a bare
  # `if (xFocused...)` painting a color is the stuck-ring bug. Matches single-line focus-driven
  # color/alpha conditions missing the gate. Baseline 0.
  grep -rnE "if \(([a-zA-Z]*[Ff]ocused)(\.value)?\) (MaterialTheme|Color|accentColor|1f)" "$UI" --include=*.kt 2>/dev/null \
    | grep -v "focusVisualsEnabled" | grep -v "/theme/" | sed -E 's/:.*//' | sed 's/$/\tR23-focusgate/'
  # R24: a hand-rolled initial D-pad focus grab. Screen-open `firstFocus.requestFocus()` (and the
  # dpadSession-guarded copies it spawned) go through RequestInitialDpadFocus(requester)
  # (FocusBorder.kt), which skips touch sessions AND re-arms when the input mode flips to keys.
  # Baseline 0.
  grep -rnE "dpadSession|firstFocus\.requestFocus\(\)" "$UI" --include=*.kt 2>/dev/null \
    | grep -v "/theme/" | grep -v "component/FocusBorder.kt" | grep -v "screens/search/OnlineSearchResult.kt" | sed -E 's/:.*//' | sed 's/$/\tR24-initialfocus/'
}

# Aggregate to "<path>\t<rule>\t<count>", sorted.
current_counts() {
  violations | sort | uniq -c | awk '{print $2"\t"$3"\t"$1}' | sort
}

if [ "${1:-}" = "--update" ]; then
  mkdir -p scripts
  current_counts > "$BASELINE"
  echo "Baseline updated: $(grep -c . "$BASELINE") (path, rule) entries, $(violations | grep -c .) total violations."
  exit 0
fi

if [ ! -f "$BASELINE" ]; then
  echo "No baseline at $BASELINE. Create it with: bash scripts/ui-audit.sh --update"
  exit 2
fi

# R22-home-shimmer: positive assertion (not a shrink-count). The Home loading skeleton matches the
# MUSIC home layout and is driven by music-VM state, so `shouldShowShimmer` MUST be gated on
# HomeContentTab.MUSIC (kept on one line so this stays greppable) — otherwise it paints a music-shaped
# skeleton on the Radio/Podcasts/Videos tabs that never resolves. See docs/ui/standards.md + AGENTS.md.
HOME_SCREEN="$UI/screens/HomeScreen.kt"
if [ -f "$HOME_SCREEN" ] && grep -q 'shouldShowShimmer' "$HOME_SCREEN" \
    && ! grep -Eq 'shouldShowShimmer[[:space:]]*=.*HomeContentTab\.MUSIC' "$HOME_SCREEN"; then
  echo "UI audit FAILED — R22-home-shimmer: the Home loading skeleton is not scoped to the Music tab."
  echo "  $HOME_SCREEN"
  echo "  'val shouldShowShimmer = ...' must include 'homeTab == HomeContentTab.MUSIC &&' (on one line)"
  echo "  so the music-shaped skeleton never renders on the Radio / Podcasts / Videos tabs. A loading"
  echo "  skeleton must match the content that replaces it; a per-tab skeleton never shows on another tab."
  exit 1
fi

cur="$(current_counts)"

# NEW violations: a (path, rule) whose current count exceeds its allowed baseline count.
new="$(awk -F'\t' '
  NR==FNR { base[$1 FS $2]=$3; next }
  { key=$1 FS $2; if ($3+0 > base[key]+0) printf "  %-12s %s  now %d, allowed %d\n", $2, $1, $3, base[key]+0 }
' "$BASELINE" <(printf "%s\n" "$cur"))"

# Improvements: a (path, rule) now below its baseline — nudge to tighten.
improved="$(awk -F'\t' '
  NR==FNR { c[$1 FS $2]=$3; next }
  { key=$1 FS $2; if (c[key]+0 < $3+0) printf "  %-12s %s  now %d, was %d\n", $2, $1, c[key]+0, $3 }
' <(printf "%s\n" "$cur") "$BASELINE")"

if [ -n "$new" ]; then
  echo "UI audit FAILED — new Rule 5/7/8/11/12/13/14/15/16/17/18/19/20/21 violations (docs/ui/standards.md sections 1, 5, 7-8, 11, 13):"
  echo "$new"
  echo
  echo "Route font sizes through MaterialTheme.typography (Type.kt), colors through"
  echo "MaterialTheme.colorScheme, dialogs through the Dialog.kt helpers (DefaultDialog etc.),"
  echo "user-facing text through stringResource() with metrolist_strings.xml, grouped action"
  echo "menus through Material3MenuGroup / Material3MenuItemData (not raw ListItem rows),"
  echo "player blur through PlayerBackgroundStyle.effective() (ui/player/PlayerBackground.kt),"
  echo "the top-bar back button through BackNavigationIcon / BackTopAppBar, and the row 3-dot"
  echo "overflow through MoreVertMenuButton (both in ui/component/)."
  echo "If a fixed value or a genuine dialog/data-list ListItem is required, keep it minimal and"
  echo "record it with:"
  echo "  bash scripts/ui-audit.sh --update"
  exit 1
fi

total="$(violations | grep -c .)"
echo "UI audit passed — no new Rule 5/7/8/11/12/13/14/15/16/17/18/19/20/21 violations (baseline: $total known, only allowed to shrink)."
if [ -n "$improved" ]; then
  echo "Burned down since the baseline — tighten it with \`bash scripts/ui-audit.sh --update\`:"
  echo "$improved"
fi
exit 0
