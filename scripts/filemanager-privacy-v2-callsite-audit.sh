#!/usr/bin/env bash
# Read-only targeted Firebase caller inspection for File Manager Plus 3.8.2.
set -euo pipefail
BASE="${1:-$HOME/filemanager-amoled-audit/privacy-v2}"
SRC="$BASE/jadx/sources"
OUT="$BASE/privacy-v2-callsite-details.txt"
[[ -d "$SRC/ax" ]] || { echo "Missing $SRC/ax" >&2; exit 1; }
{
 echo "=== HOST / OBFUSCATED CRASHLYTICS AND SESSIONS CALL SITES ==="
 for f in ax/U8/m.java ax/U8/E.java ax/U8/H.java ax/U8/l.java ax/U8/y.java ax/V8/a.java ax/p217n8/g.java ax/p265q8/r.java; do
   p="$SRC/$f"
   echo
   echo "=== $f ==="
   if [[ -f "$p" ]]; then
     grep -n -C 9 -E 'FirebaseCrashlytics|FirebaseSessions|setCrashlyticsCollectionEnabled|recordException|SessionLifecycleService|CrashlyticsRegistrar' "$p" | head -140 || true
   else
     echo "MISSING"
   fi
 done
 echo
 echo "=== SDK CRASHLYTICS COLLECTION CONTROLS ==="
 grep -RIn -C 4 -E 'crashlytics_collection_enabled|isAutomaticDataCollectionEnabled|setCrashlyticsCollectionEnabled|setCrashlyticsCollection|dataCollectionDefaultEnabled' "$SRC/com/google/firebase/crashlytics" 2>/dev/null | head -180 || true
 echo
 echo "=== SDK SESSIONS COLLECTION CONTROLS ==="
 grep -RIn -C 4 -E 'sessions_enabled|isEnabled\(|getEnabled\(|session.*[Ee]nabled|isDataCollectionEnabled' "$SRC/com/google/firebase/sessions" 2>/dev/null | head -180 || true
 echo
 echo "=== FIREBASE REGISTRARS ==="
 for f in com/google/firebase/crashlytics/CrashlyticsRegistrar.java com/google/firebase/sessions/FirebaseSessionsRegistrar.java; do
   p="$SRC/$f"
   echo "--- $f ---"
   if [[ -f "$p" ]]; then sed -n '1,200p' "$p" | head -200; else echo MISSING; fi
 done
} > "$OUT"
echo "Saved: $OUT"
wc -l "$OUT"
echo "Show full report with: cat '$OUT'"
