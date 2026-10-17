#!/usr/bin/env bash
# File Manager Plus Privacy v3: targeted read-only host ad callsite audit.
set -euo pipefail
BASE="${1:-$HOME/filemanager-amoled-audit/privacy-v2}"
SRC="$BASE/jadx/sources"
OUT="$BASE/privacy-v3-ad-callsite-details.txt"
[[ -d "$SRC/com/alphainventor/filemanager/ads" ]] || { echo "Missing host ads sources" >&2; exit 1; }
{
 echo "=== HOST ADS CLASSES ==="
 find "$SRC/com/alphainventor/filemanager/ads" -maxdepth 2 -type f -name '*.java' | sort
 echo "=== APP OPEN MANAGER ==="
 sed -n '1,280p' "$SRC/com/alphainventor/filemanager/ads/AppOpenManager.java"
 echo "=== HOST ADS CONTROLLER ==="
 sed -n '1,320p' "$SRC/com/alphainventor/filemanager/ads/a.java"
 echo "=== HOST ADS CALL SITES (EXCLUDING R.java) ==="
 grep -RIn -m 8 -E 'AppOpenManager|com\\.alphainventor\\.filemanager\\.ads|MobileAds\\.|InterstitialAd\\.|RewardedAd\\.|AdLoader|NativeAdView|loadAd\\(' "$SRC/com/alphainventor/filemanager" "$SRC/ax/p387y3" 2>/dev/null | grep -v '/R.java:' | head -180 || true
 echo "=== HOST AD RESOURCE / PREFERENCE KEYS ==="
 grep -RIn -m 5 -E 'remove_ads|no_ads|ad_free|premium|purchase|subscription|isPro|isPremium|ads_enabled|show_ads' "$SRC/com/alphainventor/filemanager/ads" "$SRC/ax/p387y3" 2>/dev/null | head -120 || true
 echo "=== CRASHLYTICS GATE SUMMARY ==="
 grep -n -A 12 'public synchronized boolean d()' "$SRC/ax/p265q8/C2287x.java" || true
} > "$OUT"
echo "Saved: $OUT"
wc -l "$OUT"
