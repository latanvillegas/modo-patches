#!/usr/bin/env bash
# Read-only preflight for Premium gate and advertising gate method signatures.
set -euo pipefail
SRC="${1:-$HOME/filemanager-amoled-audit/privacy-v2/jadx/sources}"
OUT="$HOME/filemanager-amoled-audit/privacy-v2/premium-preflight.txt"
mkdir -p "$(dirname "$OUT")"
{
  echo "=== PREMIUM GATES ==="
  for f in "$SRC/ax/p387y3/h.java" "$SRC/ax/p387y3/d.java"; do
    echo "--- $f"
    if [[ -f "$f" ]]; then
      grep -nE -A 16 -B 2 'boolean (g|i|j|F|G|H|K|N|O)\(' "$f" || true
    else
      echo MISSING
    fi
  done
  echo "=== END READ ONLY ==="
} > "$OUT"
echo "$OUT"
