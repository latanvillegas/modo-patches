#!/usr/bin/env bash
# Read-only targeted Firebase Installations call-path inspection for File Manager Plus 3.8.2
set -euo pipefail
ROOT="${1:-$HOME/filemanager-amoled-audit/privacy-v2/jadx/sources}"
OUT="$HOME/filemanager-amoled-audit/privacy-v2/privacy-v3-firebase-callpath-detail.txt"
[[ -d "$ROOT" ]] || { echo "Missing JADX sources: $ROOT" >&2; exit 1; }
mkdir -p "$(dirname "$OUT")"
{
 echo '=== FIREBASE INSTALLATIONS CALLPATH DETAIL — READ ONLY ==='
 for rel in ax/U8/t.java ax/U8/l.java ax/L8/c.java ax/p073e8/f.java ax/A3/a.java com/google/firebase/installations/FirebaseInstallationsRegistrar.java com/google/firebase/sessions/FirebaseSessionsRegistrar.java; do
   f="$ROOT/$rel"
   echo "=== FILE: $rel ==="
   if [[ -f "$f" ]]; then sed -n '1,230p' "$f"; else echo 'MISSING'; fi
 done
 echo '=== REMOTE CONFIG SDK REFERENCES ==='
 grep -RInE --include='*.java' 'FirebaseRemoteConfig|getInstance\(\).*RemoteConfig|fetchAndActivate|fetch\(' "$ROOT/com/alphainventor/filemanager" "$ROOT/ax" 2>/dev/null | head -85 || true
 echo '=== FIREBASE INSTALLATIONS TOKEN CALLERS ==='
 grep -RInE --include='*.java' 'firebaseInstallations\.getToken|FirebaseInstallations|getToken\(false\)|getId\(\)' "$ROOT/ax/U8" "$ROOT/ax/L8" "$ROOT/com/google/firebase" 2>/dev/null | head -95 || true
 echo '=== END ==='
} > "$OUT"
echo "Saved $OUT"
wc -l "$OUT"
