#!/usr/bin/env bash
# Read-only Firebase Sessions / Installations consumer audit; no APK modifications.
set -euo pipefail
ROOT="${1:-$HOME/filemanager-amoled-audit/privacy-v2/jadx/sources}"
OUT="$HOME/filemanager-amoled-audit/privacy-v2/privacy-v3-firebase-consumers-audit.txt"
[[ -d "$ROOT" ]] || { echo "JADX sources missing: $ROOT" >&2; exit 1; }
mkdir -p "$(dirname "$OUT")"
{
 echo '=== FIREBASE SESSIONS AND INSTALLATIONS CONSUMERS ==='
 for rel in ax/U8/C.java ax/W8/f.java ax/I8/e.java ax/U8/t.java com/google/firebase/remoteconfig/RemoteConfigRegistrar.java; do
   echo "=== $rel ==="
   f="$ROOT/$rel"
   if [[ -f "$f" ]]; then
     wc -l "$f"
     sed -n '1,320p' "$f"
   else echo 'MISSING'; fi
 done
 echo '=== INSTALLATIONS API CONSUMERS (STATIC TYPE REFERENCES) ==='
 grep -RInF --include='*.java' 'ax.I8.e' "$ROOT/ax" "$ROOT/com/google/firebase" 2>/dev/null | head -110 || true
 echo '=== INSTALLATIONS METHOD CALLS ==='
 grep -RInE --include='*.java' 'firebaseInstallations|getToken\(false\)|FirebaseInstallationsApi|installationId|\.b\(false\)' "$ROOT/ax/U8" "$ROOT/ax/W8" "$ROOT/ax/Q8" "$ROOT/com/google/firebase/remoteconfig" 2>/dev/null | head -120 || true
 echo '=== REMOTE CONFIG COMPONENT DEPENDENCIES ==='
 grep -RInE --include='*.java' 'Installations|ax\.I8\.e|RemoteConfigRegistrar|fetchAndActivate' "$ROOT/com/google/firebase/remoteconfig" "$ROOT/ax/Q8" 2>/dev/null | head -90 || true
 echo '=== END — READ ONLY ==='
} > "$OUT"
echo "Saved $OUT"
wc -l "$OUT"
