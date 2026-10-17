#!/usr/bin/env bash
# File Manager Plus 3.8.2: read-only Firebase initialization and callsite audit
set -euo pipefail
ROOT="${1:-$HOME/filemanager-amoled-audit/privacy-v2/jadx/sources}"
OUT="$HOME/filemanager-amoled-audit/privacy-v2/privacy-v3-firebase-init-audit.txt"
[[ -d "$ROOT" ]] || { echo "JADX source directory not found: $ROOT" >&2; exit 1; }
mkdir -p "$(dirname "$OUT")"
{
 echo "=== FIREBASE INITIALIZATION / REMOTE CONFIG / CRASHLYTICS ==="
 echo "=== APP-SIDE FIREBASE REFERENCES ==="
 grep -RInE --include='*.java' 'FirebaseRemoteConfig|FirebaseCrashlytics|firebase\.remoteconfig|firebase\.crashlytics|ax\.p265q8\.C|ax\.I8\.e|ax\.A3\.a' "$ROOT/com/alphainventor/filemanager" "$ROOT/ax/A3" 2>/dev/null | head -140 || true
 echo "=== REMOTE CONFIG AND CRASHLYTICS REGISTRARS ==="
 for rel in com/google/firebase/remoteconfig/RemoteConfigRegistrar.java com/google/firebase/crashlytics/CrashlyticsRegistrar.java com/google/firebase/crashlytics/a.java ax/p265q8/C.java; do
   echo "=== FILE $rel ==="
   if [[ -f "$ROOT/$rel" ]]; then nl -ba "$ROOT/$rel" | head -175; else echo "MISSING"; fi
 done
 echo "=== REMOTE CONFIG NETWORK AND TOKEN ENTRY POINTS ==="
 for rel in com/google/firebase/remoteconfig/internal/d.java com/google/firebase/remoteconfig/internal/g.java; do
   echo "=== FILE $rel: selected contexts ==="
   f="$ROOT/$rel"
   if [[ -f "$f" ]]; then
     grep -nE 'getToken|\.b\(false\)|\.a\(\)|fetch|connect|installation' "$f" | head -55 || true
   fi
 done
 echo "=== FIREBASE INIT PROVIDER AND MANIFEST ==="
 f="$HOME/filemanager-amoled-audit/privacy-v2/manifest.txt"
 if [[ -f "$f" ]]; then grep -nEi 'firebase|crashlytics|remoteconfig|session' "$f" | head -90 || true; fi
 echo "=== END — NO APK OR PATCH MODIFIED ==="
} > "$OUT"
echo "Saved $OUT"
wc -l "$OUT"