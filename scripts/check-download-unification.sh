#!/usr/bin/env bash
# Whole-app guard that the download/progress UI stays unified on ONE path.
#
# Background: the app downloads exclusively through MediaStore (MediaStoreDownloadManager) and the
# durable truth is SongEntity.isDownloaded. The legacy ExoPlayer download map
# (DownloadUtil.downloads / DownloadUtil.getDownload) is never written for those downloads, so any UI
# that reads it silently reports "not downloaded". Likewise, download/progress state must be rendered
# through the shared helpers, never a per-surface re-implementation. This script fails CI the moment a
# banned pattern reappears ANYWHERE under app/src/main (ui-audit's R13 only covers ui/).
#
# Allowed homes for the legacy infrastructure (these legitimately reference the ExoPlayer download
# manager + Download.STATE_* for the still-present download cache): DownloadUtil.kt, ExoDownloadService.kt.
#
# The ONE path:
#   - state:    com.jtech.zemer.playback.DownloadStateResolver (pure) + ui/component/DownloadStatusUi.kt
#   - menu row: ui/menu/DownloadMenuItems.kt (downloadMenuItem) decided by DownloadMenuLogic
#   - header:   ui/component/DownloadStatusUi.kt (AggregateDownloadButton)
set -u

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
SRC="app/src/main/kotlin/com/jtech/zemer"

# Files allowed to touch the legacy ExoPlayer download manager / Download.STATE_* (the infra itself).
INFRA='playback/DownloadUtil.kt|playback/ExoDownloadService.kt'

fail=0

report() { # <pattern-description> <grep-output>
  if [ -n "$2" ]; then
    echo "FAIL: $1"
    echo "$2" | sed 's/^/  /'
    echo
    fail=1
  fi
}

# 1. The dead legacy download map / flow must not be read anywhere.
hits="$(grep -rnE "downloadUtil\.downloads|\.getDownload\(" "$SRC" --include=*.kt 2>/dev/null \
  | grep -vE "$INFRA")"
report "legacy download map read (use DownloadStateResolver / getMediaStoreDownload / getAllMediaStoreDownloads)" "$hits"

# 2. Download.STATE_* (the ExoPlayer enum) only belongs in the legacy infra, not in UI/playback logic.
hits="$(grep -rnE "Download\.STATE_" "$SRC" --include=*.kt 2>/dev/null \
  | grep -vE "$INFRA")"
report "Download.STATE_* outside the legacy download infra (compute status via DownloadStateResolver)" "$hits"

# 3. The download badge has exactly one renderer; no per-surface Icon.Download.
hits="$(grep -rnE "Icon\.Download\(" "$SRC" --include=*.kt 2>/dev/null)"
report "per-surface Icon.Download( (use DownloadStatusIcon / SongDownloadBadge in DownloadStatusUi.kt)" "$hits"

if [ "$fail" -eq 0 ]; then
  echo "download-unification check passed — one path, no legacy reads, one badge."
  exit 0
fi

echo "Download/progress UI must go through the unified path (see docs/ui/standards.md §12)."
exit 1
