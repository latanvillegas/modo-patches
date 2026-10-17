#!/usr/bin/env bash
# Read-only preflight for File Manager Plus 3.8.2 Premium gate patch.
set -euo pipefail
ROOT="${1:-$HOME/filemanager-amoled-audit/privacy-v2/jadx/sources}"
APK="/sdcard/Download/com.alphainventor.filemanager_3.8.2-2103082_minAPI21(nodpi)_apkmirror.com.apk"
OUT="$HOME/filemanager-amoled-audit/privacy-v2/premium-lifetime-preflight.txt"
mkdir -p "$(dirname "$OUT")"
{
  echo "=== PREMIUM GATES PREFLIGHT (READ ONLY) ==="
  echo "APK: $APK"
  if [[ -f "$APK" ]]; then sha256sum "$APK"; else echo APK_MISSING; fi
  echo "=== JAVA: h.g(), h.h(), h.i(), h.j(), h.k() ==="
  f="$ROOT/ax/p387y3/h.java"
  if [[ -f "$f" ]]; then nl -ba "$f" | head -220; else echo JAVA_MISSING; fi
  echo "=== CALLERS (APP CODE) ==="
  if [[ -d "$ROOT" ]]; then
    grep -RInE --include='*.java' 'ax\.p387y3\.h\.(g|h|i|j|k)\(' "$ROOT/com/alphainventor" "$ROOT/ax/p100g3" 2>/dev/null | head -150 || true
  fi
  echo "=== SMALI: h.g(), h.i(), h.j() AND REGISTERS ==="
  if [[ -f "$APK" ]] && command -v apktool >/dev/null 2>&1; then
    tmp="$(mktemp -d)"; trap 'rm -rf "$tmp"' EXIT
    if apktool d -f -r "$APK" -o "$tmp/out" >/dev/null 2>"$tmp/apktool.err"; then
      smali="$(find "$tmp/out" -path '*/ax/y3/h.smali' -print -quit)"
      if [[ -n "$smali" ]]; then
        echo "SMALI=$smali"
        for method in g h i j k; do
          echo "--- $method()Z"
          awk -v m="$method" '$0 ~ "^\\.method " && index($0, " "m"()Z") {p=1} p {print} p && /^\\.end method/ {exit}' "$smali" | head -90
        done
      else echo SMALI_MISSING; fi
    else echo APKTOOL_FAILED; tail -20 "$tmp/apktool.err"; fi
  else echo APK_OR_APKTOOL_MISSING; fi
  echo "=== NO PATCHES OR BUILDS PERFORMED ==="
} > "$OUT"
echo "Saved: $OUT"
wc -l "$OUT"
