#!/usr/bin/env bash
# Read-only preflight for File Manager Plus Privacy v3 (no APK compilation).
set -euo pipefail
ROOT="${1:-$HOME/filemanager-amoled-audit/privacy-v2/jadx/sources}"
ADS="$ROOT/com/alphainventor/filemanager/ads/a.java"
OPEN="$ROOT/com/alphainventor/filemanager/ads/AppOpenManager.java"
CRASH="$ROOT/ax/p265q8/C2287x.java"
OUT="$HOME/filemanager-amoled-audit/privacy-v2/privacy-v3-preflight.txt"
mkdir -p "$(dirname "$OUT")"
{
  echo '=== PRIVACY V3 READ-ONLY PREFLIGHT ==='
  echo 'Expected APK: File Manager Plus 3.8.2'
  for f in "$ADS" "$OPEN" "$CRASH"; do
    if [[ -f "$f" ]]; then echo "FOUND $f"; else echo "MISSING $f"; fi
  done
  echo '=== HOST ADS METHOD SIGNATURES ==='
  if [[ -f "$ADS" ]]; then
    grep -nE 'public static (void|View) (A|B|C|D|E|x|y|z)\(' "$ADS" || true
    echo '=== HOST ADS CALLBACK CONTRACTS ==='
    grep -nE 'public interface [ik]|void [abc]\(' "$ADS" || true
  fi
  echo '=== APP OPEN SIGNATURES ==='
  if [[ -f "$OPEN" ]]; then grep -nE 'public static void n\(|public void q\(' "$OPEN" || true; fi
  echo '=== CRASHLYTICS GATE ==='
  if [[ -f "$CRASH" ]]; then grep -nE 'synchronized boolean d\(' "$CRASH" || true; fi
  echo '=== HOST CALLERS ==='
  if [[ -f "$ADS" ]]; then
    grep -RInE 'com\.alphainventor\.filemanager\.ads\.a\.(A|B|C|D|E|x|y|z)\('       "$ROOT/com/alphainventor/filemanager" "$ROOT/ax/p100g3" 2>/dev/null | head -80 || true
  fi
  echo '=== RESULT ==='
  echo 'Manual fingerprint validation required; this does not compile or patch an APK.'
} > "$OUT"
echo "Saved: $OUT"
wc -l "$OUT"
