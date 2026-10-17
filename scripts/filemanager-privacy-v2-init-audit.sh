#!/usr/bin/env bash
# File Manager Plus 3.8.2 — Privacy v2 read-only host/SDK audit.
# No APK modification, no Gradle, no GitHub Actions.
set -euo pipefail
BASE="${1:-$HOME/filemanager-amoled-audit/privacy-v2}"
SRC="$BASE/jadx/sources"
MAN="$BASE/manifest.txt"
OUT="$BASE/privacy-v2-init-paths.txt"
[[ -d "$SRC" && -f "$MAN" ]] || { echo "Missing JADX sources or manifest in $BASE" >&2; exit 1; }
mkdir -p "$BASE"
{
  echo "=== FILE MANAGER PRIVACY V2: INITIALIZATION PATHS ==="
  echo "=== MANIFEST SDK COMPONENTS ==="
  grep -n -A 6 -B 3 -E 'CrashlyticsRegistrar|FirebaseSessionsRegistrar|SessionLifecycleService|FirebaseInitProvider|ComponentDiscoveryService|firebase_.*collection|firebase_sessions_enabled|google_analytics_adid_collection_enabled' "$MAN" | head -180 || true
  echo
  echo "=== HOST PACKAGE FIREBASE CALLS (bounded) ==="
  if [[ -d "$SRC/com/alphainventor/filemanager" ]]; then
    grep -RInE 'FirebaseCrashlytics|FirebaseSessions|setCrashlyticsCollectionEnabled|recordException|logException|FirebaseApp|FirebaseAnalytics|usage_diagnostics|setAnalyticsCollectionEnabled' "$SRC/com/alphainventor/filemanager" | head -130 || true
  else
    echo "Host package not found at expected JADX path"
  fi
  echo
  echo "=== OBFUSCATED APP CODE: CRASHLYTICS AND SESSIONS ==="
  if [[ -d "$SRC/ax" ]]; then
    grep -RIlE 'FirebaseCrashlytics|FirebaseSessions|setCrashlyticsCollectionEnabled|recordException|SessionLifecycleService|CrashlyticsRegistrar' "$SRC/ax" 2>/dev/null | head -70 || true
  fi
  echo
  echo "=== SDK API DECLARATIONS ==="
  for symbol in 'setCrashlyticsCollectionEnabled' 'recordException' 'FirebaseSessionsRegistrar' 'CrashlyticsRegistrar' 'firebase_crashlytics_collection_enabled' 'firebase_sessions_enabled'; do
    echo "--- $symbol ---"
    grep -RInF "$symbol" "$SRC/com/google/firebase" 2>/dev/null | head -14 || true
  done
  echo
  echo "=== USAGE DIAGNOSTICS IMPLEMENTATION ==="
  if [[ -f "$SRC/ax/p387y3/k.java" ]]; then
    grep -n -A 12 -B 5 -F '"usage_diagnostics"' "$SRC/ax/p387y3/k.java" | head -45 || true
  fi
  echo
  echo "=== ANALYTICS INITIALIZATION ==="
  if [[ -f "$SRC/ax/A3/a.java" ]]; then
    grep -n -A 12 -B 8 -E 'k\.o\(|firebaseAnalytics|INIT_EVENT' "$SRC/ax/A3/a.java" | head -95 || true
  fi
} > "$OUT"
echo "Saved: $OUT"
echo "=== REPORT SUMMARY ==="
wc -l "$OUT"
sed -n '1,110p' "$OUT"
