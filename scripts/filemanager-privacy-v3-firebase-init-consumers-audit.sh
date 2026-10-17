#!/usr/bin/env bash
# File Manager Plus 3.8.2 — targeted read-only Remote Config / Crashlytics initialization audit
set -euo pipefail
ROOT="${1:-$HOME/filemanager-amoled-audit/privacy-v2/jadx/sources}"
OUT="$HOME/filemanager-amoled-audit/privacy-v2/privacy-v3-firebase-init-consumers-audit.txt"
[[ -d "$ROOT" ]] || { echo "Missing JADX sources: $ROOT" >&2; exit 1; }
mkdir -p "$(dirname "$OUT")"
{
 echo '=== FIREBASE INITIALIZATION CONSUMERS — READ ONLY ==='
 for rel in com/google/firebase/remoteconfig/internal/d.java com/google/firebase/remoteconfig/internal/g.java com/google/firebase/remoteconfig/c.java com/google/firebase/crashlytics/CrashlyticsRegistrar.java com/google/firebase/crashlytics/a.java ax/p265q8/C.java ax/W8/c.java; do
   f="$ROOT/$rel"
   echo "=== FILE $rel ==="
   if [[ -f "$f" ]]; then
     wc -l "$f"
     grep -nE -C 12 'ax\.I8\.e|\.b\(false\)|\.a\(\)|Firebase|fetch|initialize|register|token|installation' "$f" | head -230 || true
   else echo 'MISSING'; fi
 done
 echo '=== APP CALLS TO REMOTE CONFIG / CRASHLYTICS ==='
 grep -RInE --include='*.java' 'FirebaseRemoteConfig|FirebaseCrashlytics|remoteConfig|fetchAndActivate|setCrashlyticsCollectionEnabled|CrashlyticsRegistrar|RemoteConfigRegistrar' "$ROOT/com/alphainventor/filemanager" "$ROOT/ax" 2>/dev/null | head -180 || true
 echo '=== REMOTE CONFIG CLASS REFERENCES ==='
 grep -RInF --include='*.java' 'com.google.firebase.remoteconfig' "$ROOT/com/alphainventor/filemanager" "$ROOT/ax" 2>/dev/null | head -100 || true
 echo '=== END — no APK, sources, patches, or workflows modified ==='
} > "$OUT"
echo "Saved $OUT"
wc -l "$OUT"
