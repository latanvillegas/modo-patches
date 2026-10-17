#!/usr/bin/env bash
# File Manager Plus 3.8.2: read-only DEX/smali preflight for Premium and ad gates.
set -euo pipefail
APK="${1:-/sdcard/Download/com.alphainventor.filemanager_3.8.2-2103082_minAPI21(nodpi)_apkmirror.com.apk}"
OUT="$HOME/filemanager-amoled-audit/privacy-v2/premium-smali-preflight.txt"
[[ -f "$APK" ]] || { echo "APK not found: $APK" >&2; exit 1; }
command -v apktool >/dev/null || { echo 'apktool required' >&2; exit 1; }
mkdir -p "$(dirname "$OUT")"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
apktool d -f -r "$APK" -o "$TMP/decoded" >/dev/null
{
  echo '=== SOURCE APK SHA256 ==='
  sha256sum "$APK"
  for cls in h d; do
    f="$(find "$TMP/decoded" -type f -path "*/ax/y3/$cls.smali" -print -quit)"
    echo "=== CLASS ax/y3/$cls ==="
    if [[ -z "$f" ]]; then echo CLASS_NOT_FOUND; continue; fi
    echo "PATH $f"
    if [[ "$cls" == h ]]; then
      awk '/^\.method / {keep=($0 ~ / (g|h|i|j|k)\(\)Z$/)} keep {print} keep && /^\.end method/ {keep=0}' "$f"
    else
      awk '/^\.method / {keep=($0 ~ / (F|G|H|I|K|L|N|O|P)\(\)Z$/)} keep {print} keep && /^\.end method/ {keep=0}' "$f"
    fi
  done
  echo '=== END READ ONLY ==='
} > "$OUT"
echo "Saved $OUT"
wc -l "$OUT"
