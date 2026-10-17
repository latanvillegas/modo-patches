#!/usr/bin/env bash
# File Manager Plus Privacy v3: read-only Crashlytics and advertising inventory.
set -euo pipefail
BASE="${1:-$HOME/filemanager-amoled-audit/privacy-v2}"
SRC="$BASE/jadx/sources"
OUT="$BASE/privacy-v3-crashlytics-ads-audit.txt"
[[ -d "$SRC" ]] || { echo "Missing JADX sources: $SRC" >&2; exit 1; }
{
  echo "=== TARGET APK / AUDIT ==="
  date -u '+UTC %Y-%m-%dT%H:%M:%SZ'
  echo "=== CRASHLYTICS COLLECTION GATE: C2287x ==="
  if [[ -f "$SRC/ax/p265q8/C2287x.java" ]]; then
    sed -n '1,300p' "$SRC/ax/p265q8/C2287x.java"
  else echo "MISSING C2287x"; fi
  echo "=== CRASHLYTICS API / CALLERS ==="
  if [[ -f "$SRC/com/google/firebase/crashlytics/a.java" ]]; then
    sed -n '365,430p' "$SRC/com/google/firebase/crashlytics/a.java"
  fi
  grep -RIn -m 4 -E 'firebase_crashlytics_collection_enabled|firebase_data_collection_default_enabled' "$SRC/com/alphainventor" "$SRC/ax" "$SRC/com/google/firebase" 2>/dev/null | head -100 || true
  echo "=== AD SDK PACKAGE DIRECTORY INVENTORY ==="
  for path in 'com/google/android/gms/ads' 'com/google/android/ump' 'com/facebook/ads' 'com/applovin' 'com/unity3d/ads' 'com/ironsource' 'com/bytedance/sdk/openadsdk' 'com/inmobi' 'com/mopub' 'com/startapp' 'com/vungle' 'com/chartboost' 'com/google/ads'; do
    if [[ -d "$SRC/$path" ]]; then
      echo "PRESENT $path"
      find "$SRC/$path" -type f | head -12
    else echo "ABSENT $path"; fi
  done
  echo "=== AD RELATED CLASSES / STRINGS (BOUNDED) ==="
  grep -RIn -m 2 -E 'AdView|AdRequest|InterstitialAd|RewardedAd|MobileAds|AdMob|admob|doubleclick|googleads.g.doubleclick|applovin|unityads|facebook.ads|startapp|ironSource|ad_unit_id|ca-app-pub-' "$SRC/com/alphainventor" "$SRC/ax" "$SRC/com/google" 2>/dev/null | head -180 || true
  echo "=== MANIFEST AD COMPONENT REFERENCES ==="
  if [[ -f "$BASE/manifest.txt" ]]; then
    grep -in -C 2 -E 'ads|admob|applovin|unity|facebook|crashlytics|firebase' "$BASE/manifest.txt" | head -140 || true
  else echo "manifest.txt not found"; fi
  echo "=== RECOMMENDED NEXT STEP ==="
  echo "Inspect report; no APK or SDK modification performed."
} > "$OUT"
echo "Saved: $OUT"
wc -l "$OUT"
