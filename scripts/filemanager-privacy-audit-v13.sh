#!/usr/bin/env bash
# Read-only inspection of File Manager Plus 3.8.2 decompiled with JADX.
set -euo pipefail

DIR="${1:-$HOME/filemanager-amoled-audit/privacy-v2}"
SRC="$DIR/jadx/sources"
MAN="$DIR/manifest.txt"
OUT="$DIR/privacy-v13-crashlytics-sessions.txt"

[[ -d "$SRC" && -f "$MAN" ]] || {
  echo "Missing JADX sources or manifest in: $DIR" >&2
  exit 1
}

{
  echo "=== FIREBASE MANIFEST FLAGS (including absent flags) ==="
  for flag in firebase_data_collection_default_enabled firebase_analytics_collection_enabled firebase_crashlytics_collection_enabled firebase_sessions_enabled firebase_performance_collection_enabled google_analytics_adid_collection_enabled; do
    echo "--- $flag ---"
    grep -n -A 5 -B 2 -F "$flag" "$MAN" || echo "Not explicitly declared"
  done

  echo
  echo "=== HOST-SIDE CRASHLYTICS / SESSIONS REFERENCES ==="
  grep -RInE 'FirebaseCrashlytics|getInstance\(|recordException\(|setCrashlyticsCollectionEnabled\(|FirebaseSessions|SessionLifecycleService' \
    "$SRC/com/alphainventor/filemanager" "$SRC/ax/U2" \
    2>/dev/null | head -90 || true

  echo
  echo "=== CRASHLYTICS / SESSIONS REGISTRARS AND PROVIDERS ==="
  grep -n -A 5 -B 2 -E 'CrashlyticsRegistrar|FirebaseSessionsRegistrar|SessionLifecycleService|FirebaseInitProvider|ComponentDiscoveryService' "$MAN" || true

  echo
  echo "=== APP TELEMETRY CONTROLS ==="
  sed -n '150,165p' "$SRC/ax/p387y3/k.java"
  sed -n '1,55p' "$SRC/ax/A3/a.java"
  sed -n '315,335p' "$SRC/ax/p100g3/q0.java"

  echo
  echo "=== ANALYTICS CONFIGURATION ==="
  grep -n -A 5 -B 2 -F 'firebase_analytics_collection_enabled' "$MAN" || true
} > "$OUT"

cat "$OUT"
printf '\nSaved: %s\n' "$OUT"
