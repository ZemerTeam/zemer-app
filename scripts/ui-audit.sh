#!/usr/bin/env bash
# UI standards audit (docs/ui/standards.md, sections 5, 7-8: strings, dialogs, theme & color).
#
# Ratcheting check: it FAILS only on NEW Rule 5/7/8 violations beyond the committed baseline
# (scripts/ui-audit-baseline.tsv). The current known violations are allowlisted, so CI is green
# today and the count can only shrink — fix some, then run --update to tighten the baseline.
#
# NOT mechanically checked here (enforced by code review, see standards.md): the reuse rules
# (section 1) and the grouped-list/menu components (section 11 — Material3SettingsGroup /
# Material3MenuItem, plus the .focusable() D-pad requirement on any new row component). These are
# reuse/structure judgments, not greppable patterns: e.g. a raw `ListItem(` is correct for a plain
# song list but wrong for a grouped menu, so it cannot be ratcheted without false positives.
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
  echo "UI audit FAILED — new Rule 5/7/8 violations (docs/ui/standards.md sections 5, 7-8):"
  echo "$new"
  echo
  echo "Route font sizes through MaterialTheme.typography (Type.kt), colors through"
  echo "MaterialTheme.colorScheme, dialogs through the Dialog.kt helpers (DefaultDialog etc.),"
  echo "and user-facing text through stringResource() with metrolist_strings.xml."
  echo "If a fixed value is genuinely required, keep it minimal and record it with:"
  echo "  bash scripts/ui-audit.sh --update"
  exit 1
fi

total="$(violations | grep -c .)"
echo "UI audit passed — no new Rule 5/7/8 violations (baseline: $total known, only allowed to shrink)."
if [ -n "$improved" ]; then
  echo "Burned down since the baseline — tighten it with \`bash scripts/ui-audit.sh --update\`:"
  echo "$improved"
fi
exit 0
