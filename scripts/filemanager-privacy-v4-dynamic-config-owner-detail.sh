#!/usr/bin/env bash
# Read-only focused dump of File Manager dynamic configuration owner and consumers.
set -euo pipefail
ROOT="${1:-$HOME/filemanager-amoled-audit/privacy-v2/jadx/sources}"
OUT="$HOME/filemanager-amoled-audit/privacy-v2/privacy-v4-dynamic-config-owner-detail.txt"
[[ -d "$ROOT" ]] || { echo "Missing JADX sources: $ROOT" >&2; exit 1; }
mkdir -p "$(dirname "$OUT")"
{
 echo '=== DYNAMIC CONFIG OWNER DETAIL — READ ONLY ==='
 for rel in ax/p387y3/d.java ax/A3/b.java; do
  f="$ROOT/$rel"; echo "=== $rel ==="
  if [[ -f "$f" ]]; then wc -l "$f"; cat "$f"; else echo MISSING; fi
 done
 echo '=== REFERENCES TO OWNER TYPE (FULLY QUALIFIED) ==='
 grep -RInF --include='*.java' 'ax.p387y3.d' "$ROOT/ax" "$ROOT/com/alphainventor" 2>/dev/null | head -120 || true
 echo '=== CONFIG KEYS AND FEATURE USE IN OWNER PACKAGE ==='
 grep -RInE --include='*.java' 'premium|cloud|remote|firebase|config|feature|ads|license|subscription' "$ROOT/ax/p387y3" 2>/dev/null | head -160 || true
 echo '=== END — no modifications ==='
} > "$OUT"
echo "Saved $OUT"; wc -l "$OUT"
