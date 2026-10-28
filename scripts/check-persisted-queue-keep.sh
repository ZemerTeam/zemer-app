#!/usr/bin/env bash
# Checks that R8 kept the persisted-queue classes whole in a release APK.
#
# The saved queue is Java-serialized. If R8 strips a class's pinned serialVersionUID (or readResolve, or the
# class itself), the next build computes a different UID and every updating user's saved queue fails to
# restore. The JVM compat tests cannot see R8, so this reads the APK's dex.
#
#   bash scripts/check-persisted-queue-keep.sh app/build/outputs/apk/release/app-release.apk
set -u

APK="${1:?usage: $0 <release apk>}"
SDK="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Android/Sdk}}"
DEXDUMP="$(ls "$SDK"/build-tools/*/dexdump 2>/dev/null | sort -V | tail -1)"
[ -x "$DEXDUMP" ] || { echo "dexdump not found under $SDK/build-tools" >&2; exit 2; }

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
unzip -q -o "$APK" 'classes*.dex' -d "$TMP"
for dex in "$TMP"/classes*.dex; do "$DEXDUMP" "$dex" 2>/dev/null; done > "$TMP/dump.txt"

# class -> the members it must keep
EXPECTED=(
    "PersistQueue serialVersionUID"
    "PersistPlayerState serialVersionUID"
    "MediaMetadata serialVersionUID"
    "MediaMetadata\$Artist serialVersionUID"
    "MediaMetadata\$Album serialVersionUID"
    "QueueType serialVersionUID"
    "QueueType\$LIST serialVersionUID readResolve"
    "QueueType\$YOUTUBE serialVersionUID readResolve"
    "QueueType\$YOUTUBE_ALBUM_RADIO serialVersionUID readResolve"
    "QueueType\$LOCAL_ALBUM_RADIO serialVersionUID readResolve"
    "QueueData serialVersionUID"
    "QueueData\$YouTubeData serialVersionUID"
    "QueueData\$YouTubeAlbumRadioData serialVersionUID"
    "QueueData\$LocalAlbumRadioData serialVersionUID"
)

fail=0
for entry in "${EXPECTED[@]}"; do
    read -r cls members <<< "$entry"
    desc="Lcom/jtech/zemer/models/$cls;"
    names="$(awk -v d="'$desc'" '$1 == "Class" && $2 == "descriptor" { inside = ($4 == d) } inside && $1 == "name" { print $3 }' "$TMP/dump.txt")"
    if [ -z "$names" ]; then
        echo "MISSING class $cls"
        fail=1
        continue
    fi
    for m in $members; do
        if ! grep -qx "'$m'" <<< "$names"; then
            echo "MISSING $cls.$m"
            fail=1
        fi
    done
done

if [ "$fail" -ne 0 ]; then
    echo "R8 stripped persisted-queue members: keep every Serializable class under models/ (proguard-rules.pro)." >&2
    exit 1
fi
echo "Persisted-queue classes kept (${#EXPECTED[@]} classes)."
