#!/usr/bin/env bash
# Read-only: inspect ad callbacks and UI state transitions before disabling native/banner requests.
set -euo pipefail
BASE="${1:-$HOME/filemanager-amoled-audit/privacy-v2}"
SRC="$BASE/jadx/sources"
OUT="$BASE/privacy-v3-ad-callback-state-audit.txt"
declare -a TARGETS=(
  "com/alphainventor/filemanager/activity/MainActivity.java:1750:1840"
  "com/alphainventor/filemanager/activity/MainActivity.java:1920:1970"
  "com/alphainventor/filemanager/viewer/VideoPlayerActivity.java:615:685"
  "com/alphainventor/filemanager/viewer/VideoPlayerActivity.java:1450:1505"
  "ax/p100g3/AbstractC2064n.java:110:215"
  "ax/p100g3/AbstractC2064n.java:825:915"
  "ax/p100g3/AbstractC2064n.java:1030:1070"
  "ax/p100g3/C2057h0.java:55:95"
  "ax/p100g3/C2057h0.java:160:195"
  "ax/p100g3/C2044b.java:175:215"
  "ax/p100g3/C2044b.java:525:565"
  "ax/p387y3/a.java:90:120"
)
{
  echo '=== AD CALLBACK AND STATE TRANSITIONS ==='
  for entry in "${TARGETS[@]}"; do
    IFS=: read -r path first last <<< "$entry"
    file="$SRC/$path"
    echo "=== $path lines $first-$last ==="
    if [[ -f "$file" ]]; then
      sed -n "${first},${last}p" "$file" | nl -ba -v "$first"
    else
      echo "MISSING: $file"
    fi
  done
  echo '=== ADS INIT CALLER CONTEXT ==='
  file="$SRC/ax/p100g3/AbstractC2064n.java"
  if [[ -f "$file" ]]; then sed -n '630,680p' "$file" | nl -ba -v 630; fi
} > "$OUT"
echo "Saved: $OUT"
wc -l "$OUT"
