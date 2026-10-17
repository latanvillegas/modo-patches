#!/usr/bin/env bash
# File Manager Plus Privacy v2 — inspect candidate collection gates (read-only).
set -euo pipefail
BASE="${1:-$HOME/filemanager-amoled-audit/privacy-v2}"
SRC="$BASE/jadx/sources"
OUT="$BASE/privacy-v2-collection-gates.txt"
[[ -d "$SRC" ]] || { echo "Missing JADX source directory: $SRC" >&2; exit 1; }
{
 echo "=== SESSIONS SETTINGS GATE: ax/W8/f.java ==="
 if [[ -f "$SRC/ax/W8/f.java" ]]; then
   sed -n '1,260p' "$SRC/ax/W8/f.java"
 else echo MISSING; fi
 echo
 echo "=== SESSIONS CALLER: ax/U8/l.java ==="
 if [[ -f "$SRC/ax/U8/l.java" ]]; then
   sed -n '35,125p' "$SRC/ax/U8/l.java"
 else echo MISSING; fi
 echo
 echo "=== CRASHLYTICS API: com/google/firebase/crashlytics/a.java ==="
 if [[ -f "$SRC/com/google/firebase/crashlytics/a.java" ]]; then
   sed -n '1,240p' "$SRC/com/google/firebase/crashlytics/a.java"
 else echo MISSING; fi
 echo
 echo "=== CRASHLYTICS DATA COLLECTION CLASSES ==="
 for d in "$SRC/ax/p265q8" "$SRC/ax/p217n8" "$SRC/ax/I8"; do
   [[ -d "$d" ]] || continue
   grep -RIn -C 3 -E 'firebase_crashlytics_collection_enabled|firebase_data_collection_default_enabled|isAutomaticDataCollectionEnabled|collect.*report|automatic.*collection|DataCollection|dataCollection' "$d" 2>/dev/null | head -180 || true
 done
 echo
 echo "=== SDK SETTINGS CALL SITES ==="
 grep -RIn -C 3 -E 'Sessions SDK disabled|Sessions SDK enabled|Crashlytics automatic|automatic data collection|Crashlytics collection' "$SRC/ax" "$SRC/com/google/firebase" 2>/dev/null | head -160 || true
} > "$OUT"
echo "Saved: $OUT"
wc -l "$OUT"
