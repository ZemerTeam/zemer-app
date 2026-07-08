#!/usr/bin/env bash
# Whole-app ratchet that no dead resource re-enters the tree.
#
# Background: the 2026-07 dead-code sweep (#183) removed ~90 strings, 32 drawables and a raw asset
# that had accumulated unreferenced since the Metrolist fork. This script holds the line at ZERO:
# every string in the default res/values/*.xml, every drawable, and every raw asset must have at
# least one `R.<type>.<name>` or `@<type>/<name>` reference somewhere in the source tree, or CI
# fails. Debug builds never shrink, so dead resources otherwise sit invisible until the next audit.
#
# Soundness guard: name-based reference analysis is only valid while the app has NO dynamic resource
# lookup. The script therefore also fails if `getIdentifier` appears in any module — if dynamic
# lookup is ever introduced, this check must be redesigned, not silenced.
#
# Scope notes:
#  - Only the DEFAULT values/ files are checked for strings; translated locales (values-iw, ...) are
#    managed separately and only ever mirror the default set.
#  - Launcher-icon layers are allowlisted: adaptive-icon indirection keeps some layers unreferenced
#    by name while still shipped intentionally.
#  - Kotlin-level dead code is out of scope here (not reliably greppable); this guards resources.
set -u

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

# Intentionally-kept resources with no textual reference (launcher icon layers).
ALLOWLIST='^(ic_launcher_foreground|ic_launcher_foreground_v31|ic_launcher_background_v31)$'

# Ratchet baseline: known-dead resources grandfathered in (one "type/name" per line, sorted).
# Currently: dead strings in values/strings.xml, which agents must not edit (CLAUDE.md rule 3) —
# they await a human-approved cleanup. The baseline may only SHRINK: entries that become referenced
# or get deleted should be pruned; any dead resource NOT listed here fails CI.
BASELINE="scripts/dead-resources-baseline.txt"

fail=0

# 0. Soundness: no dynamic resource lookup anywhere.
hits="$(grep -rn "getIdentifier" app/src innertube/src lrclib/src simpmusic --include=*.kt 2>/dev/null)"
if [ -n "$hits" ]; then
  echo "FAIL: dynamic resource lookup (getIdentifier) found — name-based dead-resource analysis is no longer sound:"
  echo "$hits" | sed 's/^/  /'
  exit 1
fi

# One pass: every R.<type>.<name> / @<type>/<name> reference in source, manifests, res XML and build files.
used="$(grep -rhoE 'R\.(string|drawable|raw)\.[A-Za-z0-9_]+|@(string|drawable|raw)/[A-Za-z0-9_]+' \
          app/src innertube/src lrclib/src simpmusic \
          --include=*.kt --include=*.xml --include=*.kts 2>/dev/null \
        | sed -E 's#^R\.(string|drawable|raw)\.#\1/#; s#^@##' | sort -u)"

is_used() { # <type>/<name>
  printf '%s\n' "$used" | grep -qx "$1"
}

baselined() { # <type>/<name>
  [ -f "$BASELINE" ] && grep -qx "$1" "$BASELINE"
}

baseline_hits=0

check() { # <type> <name> <where>
  local type="$1" name="$2" where="$3"
  [[ "$type" != string ]] && [[ "$name" =~ $ALLOWLIST ]] && return
  if ! is_used "$type/$name"; then
    if baselined "$type/$name"; then
      baseline_hits=$((baseline_hits + 1))
      return
    fi
    echo "FAIL: dead $type resource '$name' ($where) — unreferenced by any R.$type.$name / @$type/$name"
    fail=1
  fi
}

# 1. Strings declared in the default values/ directory.
while IFS= read -r name; do
  check string "$name" "app/src/main/res/values"
done < <(grep -hoE '<string name="[A-Za-z0-9_]+"' app/src/main/res/values/*.xml | sed -E 's/.*name="([^"]+)".*/\1/' | sort -u)

# 2. Drawables (all drawable* qualifiers, deduped by name).
while IFS= read -r name; do
  check drawable "$name" "app/src/main/res/drawable*"
done < <(find app/src/main/res/drawable* -type f 2>/dev/null | sed -E 's#.*/##; s#\.[a-z0-9.]+$##' | sort -u)

# 3. Raw assets.
while IFS= read -r name; do
  check raw "$name" "app/src/main/res/raw"
done < <(find app/src/main/res/raw -type f 2>/dev/null | sed -E 's#.*/##; s#\.[a-z0-9.]+$##' | sort -u)

if [ "$fail" -eq 0 ]; then
  echo "dead-resource check passed — no dead resources beyond the baseline ($baseline_hits grandfathered, may only shrink)."
  exit 0
fi

echo "Remove the dead resource(s) or reference them; see the dead-code sweep (#183) for methodology."
exit 1
