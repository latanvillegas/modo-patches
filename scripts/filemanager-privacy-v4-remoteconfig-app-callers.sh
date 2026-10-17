#!/usr/bin/env bash
# Read-only: trace app-side Remote Config wrapper and callers.
set -euo pipefail
ROOT="${1:-$HOME/filemanager-amoled-audit/privacy-v2/jadx/sources}"
OUT="$HOME/filemanager-amoled-audit/privacy-v2/privacy-v4-remoteconfig-app-callers.txt"
[[ -d "$ROOT" ]] || { echo "Missing JADX sources: $ROOT" >&2; exit 1; }
mkdir -p "$(dirname "$OUT")"
{
 echo '=== REMOTE CONFIG APP CALLERS — READ ONLY ==='
 for rel in ax/A3/b.java ax/A3/a.java com/google/firebase/remoteconfig/a.java com/google/firebase/remoteconfig/c.java; do
  f="$ROOT/$rel"
  echo "=== $rel ==="
  if [[ -f "$f" ]]; then wc -l "$f"; sed -n '1,290p' "$f"; else echo MISSING; fi
 done
 echo '=== AX/A3 REFERENCES AND CALL SITES ==='
 grep -RInE --include='*.java' 'ax\.A3\.b|ax\.A3\.a|new b\(|\.h\(\)' "$ROOT/com/alphainventor/filemanager" "$ROOT/ax/A3" 2>/dev/null | head -160 || true
 echo '=== REMOTE CONFIG API REFERENCES IN APP AND OBFUSCATED PACKAGES ==='
 grep -RInE --include='*.java' 'com\.google\.firebase\.remoteconfig\.[ac]|FirebaseRemoteConfig|fetchAndActivate|remoteConfig' "$ROOT/com/alphainventor/filemanager" "$ROOT/ax" 2>/dev/null | head -160 || true
 echo '=== END — no modifications ==='
} > "$OUT"
echo "Saved $OUT"
wc -l "$OUT"
