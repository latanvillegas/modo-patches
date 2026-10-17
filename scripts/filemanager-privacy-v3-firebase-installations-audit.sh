#!/usr/bin/env bash
# File Manager Plus 3.8.2 — Firebase Installations source audit (read-only)
set -euo pipefail
ROOT="${1:-$HOME/filemanager-amoled-audit/privacy-v2/jadx/sources}"
OUT="$HOME/filemanager-amoled-audit/privacy-v2/privacy-v3-firebase-installations-audit.txt"
[[ -d "$ROOT" ]] || { echo "Missing JADX sources: $ROOT" >&2; exit 1; }
mkdir -p "$(dirname "$OUT")"
{
  echo "=== FIREBASE INSTALLATIONS AUDIT — READ ONLY ==="
  echo "JADX_ROOT=$ROOT"
  echo "=== INSTALLATIONS HOSTNAMES AND API REFERENCES ==="
  grep -RInE --include='*.java' 'firebaseinstallations\.googleapis\.com|FirebaseInstallations|getId\(|getToken\(|FirebaseInstallationsApi|FirebaseInstallationsRegistrar|FirebaseInstallationsException' "$ROOT/com/alphainventor/filemanager" "$ROOT/ax" 2>/dev/null | head -160 || true
  echo "=== FIREBASE INITIALIZATION / CONFIGURATION ==="
  grep -RInE --include='*.java' 'FirebaseApp\.initializeApp|FirebaseApp\.getInstance|FirebaseCrashlytics\.getInstance|FirebaseMessaging\.getInstance|FirebaseRemoteConfig\.getInstance|FirebaseAnalytics\.getInstance|FirebaseInstallations\.getInstance|FirebaseSessions' "$ROOT/com/alphainventor/filemanager" "$ROOT/ax" 2>/dev/null | head -160 || true
  echo "=== FIREBASE INSTALLATIONS IMPLEMENTATIONS ==="
  find "$ROOT" -type f \( -iname '*FirebaseInstallations*.java' -o -iname '*InstallationsRegistrar*.java' -o -iname '*FirebaseSessions*.java' \) | head -70
  echo "=== FIREBASE MANIFEST REFERENCES ==="
  MANIFEST="$HOME/filemanager-amoled-audit/privacy-v2/manifest.txt"
  if [[ -f "$MANIFEST" ]]; then grep -nEi 'firebase|crashlytics|session|messaging|analytics' "$MANIFEST" | head -100 || true; else echo "Manifest text unavailable"; fi
  echo "=== END — No files or APK modified ==="
} > "$OUT"
echo "Saved $OUT"
wc -l "$OUT"
