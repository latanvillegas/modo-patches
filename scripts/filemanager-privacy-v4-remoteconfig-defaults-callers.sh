#!/usr/bin/env bash
# Read-only targeted JADX audit: dynamic config initialization and defaults.
set -euo pipefail
ROOT="${1:-$HOME/filemanager-amoled-audit/privacy-v2/jadx/sources}"
OUT="$HOME/filemanager-amoled-audit/privacy-v2/privacy-v4-remoteconfig-defaults-callers.txt"
[[ -d "$ROOT" ]] || { echo "Missing JADX sources: $ROOT" >&2; exit 1; }
mkdir -p "$(dirname "$OUT")"
{
 echo '=== REMOTE CONFIG DEFAULTS AND CALLERS — READ ONLY ==='
 echo '=== EXPLICIT TYPE REFERENCES ==='
 grep -RInE --include='*.java' 'ax\.A3\.b|import ax\.A3\.\*|new ax\.A3\.b\(' "$ROOT/ax" "$ROOT/com/alphainventor" 2>/dev/null | head -120 || true
 echo '=== A3 PACKAGE ==='
 find "$ROOT/ax/A3" -maxdepth 1 -type f -name '*.java' -print 2>/dev/null | sort
 echo '=== REMOTE CONFIG LITERALS ==='
 grep -RInE --include='*.java' 'Dynamic config fetch|FIREBASE INIT ERROR|GoogleApiHandler|usage_diagnostics|firebase_remote_config|remote_config' "$ROOT/ax" "$ROOT/com/alphainventor" 2>/dev/null | head -130 || true
 echo '=== CONFIG CLASS REFERENCES ==='
 grep -RInE --include='*.java' 'ax\.A3\.|import ax\.A3\.' "$ROOT" 2>/dev/null | head -200 || true
 echo '=== CLASS A3/b METHOD CALLS (candidate field types and constructor) ==='
 grep -RInE --include='*.java' 'new (ax\.A3\.)?b\(\)|new HashMap<String, Object>|HashMap<String, Object>' "$ROOT/ax/A3" "$ROOT/ax/U2" "$ROOT/ax/p387y3" 2>/dev/null | head -140 || true
 echo '=== END — no APK, patches, or workflows modified ==='
} > "$OUT"
echo "Saved $OUT"; wc -l "$OUT"
