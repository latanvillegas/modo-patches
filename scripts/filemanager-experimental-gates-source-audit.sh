#!/usr/bin/env bash
# Read-only source audit for File Manager Plus experimental gates.
set -euo pipefail
SRC="${1:-$HOME/filemanager-amoled-audit/privacy-v2/jadx/sources}"
OUT="$HOME/filemanager-amoled-audit/privacy-v2/experimental-gates-source-audit.txt"
mkdir -p "$(dirname "$OUT")"
{
 echo '=== PREMIUM CALL SITES (h.g/i/j/k) ==='
 if [[ -d "$SRC" ]]; then
  grep -RnE --include='*.java' 'h\.(g|i|j|k)\(\)' "$SRC" | head -160 || true
 else
  echo "SOURCE_DIRECTORY_MISSING $SRC"
 fi
 echo '=== PREMIUM LICENSE IMPLEMENTATION ==='
 f="$SRC/ax/p164k3/c.java"
 if [[ -f "$f" ]]; then
  grep -nE -A 18 -B 3 'boolean (F|H|I|J)\(' "$f" || true
 else
  echo "LICENSE_SOURCE_MISSING $f"
 fi
 echo '=== AD CONFIG CALL SITES ==='
 if [[ -d "$SRC" ]]; then
  grep -RnE --include='*.java' '\.(F|G|H|I|K|N|O|P)\(\)' "$SRC/ax" | grep -E 'y3|Ads|Ad|advert|MainActivity' | head -160 || true
 fi
 echo '=== END READ ONLY ==='
} > "$OUT"
echo "$OUT"
wc -l "$OUT"
