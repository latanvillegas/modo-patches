#!/usr/bin/env bash
# File Manager Plus Privacy v3: complete host ad method and callsite inventory, read-only.
set -euo pipefail
BASE="${1:-$HOME/filemanager-amoled-audit/privacy-v2}"
SRC="$BASE/jadx/sources"
OUT="$BASE/privacy-v3-host-ads-complete.txt"
HOST="$SRC/com/alphainventor/filemanager/ads"
[[ -f "$HOST/a.java" && -f "$HOST/AppOpenManager.java" ]] || { echo "Missing JADX host ad sources" >&2; exit 1; }
{
 echo '=== HOST AD CONTROLLER COMPLETE ==='
 cat "$HOST/a.java"
 echo '=== APP OPEN COMPLETE ==='
 cat "$HOST/AppOpenManager.java"
 echo '=== CALLERS OF HOST AD CONTROLLER ==='
 grep -RInF -- 'com.alphainventor.filemanager.ads.a' "$SRC" 2>/dev/null | head -100 || true
 echo '=== APP OPEN CALLERS ==='
 grep -RInF -- 'AppOpenManager.' "$SRC" 2>/dev/null | head -100 || true
 echo '=== NATIVE ADS / INTERSTITIAL LOAD CALLS IN HOST PACKAGE ==='
 grep -RInE 'ax\\.I5\\.|ax\\.K5\\.|MobileAds\\.|\.loadAd\\(|\.setAdUnitId\\(' "$SRC/com/alphainventor/filemanager" 2>/dev/null | grep -v '/R.java:' | head -180 || true
 echo '=== SOURCE FILE LINE COUNTS ==='
 wc -l "$HOST/a.java" "$HOST/AppOpenManager.java"
} > "$OUT"
echo "Saved: $OUT"
wc -l "$OUT"
