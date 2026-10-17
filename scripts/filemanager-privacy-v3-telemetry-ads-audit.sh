#!/usr/bin/env bash
# File Manager Plus 3.8.2 Privacy v3: read-only telemetry/ads audit.
set -euo pipefail
BASE="${1:-$HOME/filemanager-amoled-audit/privacy-v2}"
SRC="$BASE/jadx/sources"
OUT="$BASE/privacy-v3-telemetry-ads-audit.txt"
[[ -d "$SRC" ]] || { echo "Missing JADX sources: $SRC" >&2; exit 1; }
{
  echo "=== CRASHLYTICS COLLECTION GATE (full class) ==="
  F="$SRC/ax/p265q8/C2287x.java"
  if [[ -f "$F" ]]; then sed -n '1,300p' "$F"; else echo "MISSING $F"; fi
  echo
  echo "=== CRASHLYTICS INITIALIZATION AND OVERRIDES ==="
  F="$SRC/com/google/firebase/crashlytics/a.java"
  if [[ -f "$F" ]]; then sed -n '365,435p' "$F"; else echo "MISSING $F"; fi
  echo
  echo "=== ADS SDK CLASSES AND API REFERENCES ==="
  for p in 'com/google/android/gms/ads' 'com/google/android/ump' 'com/applovin' 'com/unity3d/ads' 'com/facebook/ads' 'com/ironsource' 'com/mopub' 'com/bytedance/sdk/openadsdk'; do
    if [[ -d "$SRC/$p" ]]; then echo "FOUND SDK PATH $p"; find "$SRC/$p" -type f | head -20; else echo "NOT FOUND SDK PATH $p"; fi
  done
  echo
  echo "=== ADS / TELEMETRY STRINGS (limited sample) ==="
  grep -RIn -E 'AdView|AdRequest|InterstitialAd|RewardedAd|MobileAds|AdMob|google_mobile_ads_app_id|com.google.android.gms.ads|firebase_crashlytics_collection_enabled|firebase_analytics_collection_enabled|FirebaseAnalytics|AppMeasurement|Adjust|AppsFlyer|Amplitude|Mixpanel|Sentry' "$SRC/com/alphainventor" "$SRC/ax" "$SRC/com/google/firebase" 2>/dev/null | head -220 || true
  echo
  echo "=== DOMAIN LITERALS (candidate endpoints; not proof of traffic) ==="
  grep -RInE 'crashlytics\\.com|firebaseinstallations\\.googleapis\\.com|app-measurement\\.com|googleads\\.g\\.doubleclick\\.net|doubleclick\\.net|googlesyndication\\.com|admob\\.com|appsflyer\\.com|adjust\\.com' "$SRC" 2>/dev/null | head -140 || true
  echo
  echo "=== MANIFEST DUMP: ADS / TELEMETRY ==="
  if [[ -f "$BASE/manifest.txt" ]]; then
    grep -in -C 3 -E 'ads|admob|firebase|crashlytics|analytics|measurement|internet|installations' "$BASE/manifest.txt" | head -180 || true
  else echo "Manifest dump unavailable"; fi
} > "$OUT"
echo "Saved: $OUT"
wc -l "$OUT"
