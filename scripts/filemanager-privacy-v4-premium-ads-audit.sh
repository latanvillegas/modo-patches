#!/usr/bin/env bash
# File Manager Plus 3.8.2: targeted, read-only Premium/theme/ads audit.
set -euo pipefail
ROOT="${1:-$HOME/filemanager-amoled-audit/privacy-v2/jadx/sources}"
OUT="$HOME/filemanager-amoled-audit/privacy-v2/privacy-v4-premium-ads-audit.txt"
APK="/sdcard/Download/com.alphainventor.filemanager_3.8.2-2103082_minAPI21(nodpi)_apkmirror.com.apk"
[[ -d "$ROOT" ]] || { echo "Missing JADX sources: $ROOT" >&2; exit 1; }
mkdir -p "$(dirname "$OUT")"
{
 echo '=== PREMIUM/THEMES/ADS READ-ONLY AUDIT ==='
 echo '=== LICENSE STATE GATES ==='
 for rel in ax/p387y3/h.java ax/p164k3/c.java com/alphainventor/filemanager/license/datatypes/ProductCatalogImpl.java; do
  f="$ROOT/$rel"; echo "--- $rel"
  if [[ -f "$f" ]]; then
   if [[ "$rel" == "ax/p387y3/h.java" ]]; then nl -ba "$f" | head -260
   else grep -nEi -C 5 'premium|license|entitle|purchas|billing|onetime|subscription|trial|coupon|\.P\(' "$f" | head -230 || true; fi
  else echo MISSING; fi
 done
 echo '=== PREMIUM CALLERS IN APPLICATION CODE ==='
 grep -RInE --include='*.java' 'ax\.p387y3\.h\.(g|i|j)\(' "$ROOT/ax" "$ROOT/com/alphainventor" 2>/dev/null | head -150 || true
 echo '=== THEME GATES AND DEPENDENCIES ==='
 grep -RInEi --include='*.java' 'premium.theme|theme.*premium|unlock.*theme|theme.*unlock|night_mode|settings_theme' "$ROOT/com/alphainventor/filemanager" "$ROOT/ax/p100g3" 2>/dev/null | head -130 || true
 echo '=== AD REWARDED AND SDK INITIALIZATION ==='
 f="$ROOT/com/alphainventor/filemanager/ads/a.java"
 if [[ -f "$f" ]]; then grep -nEi -C 6 'reward|initialize|load|show|public static|public void' "$f" | head -280 || true; else echo 'ADS_CLASS_MISSING'; fi
 echo '=== DYNAMIC CONFIG COMMERCIAL DEFAULTS ==='
 f="$ROOT/ax/p387y3/d.java"
 if [[ -f "$f" ]]; then grep -nEi -C 2 'enable_in_app_billing|enable_free_trial|enable_onetime_purchase|show_premium_discount|show_rewarded_ads|show_app_open_ads|show_.*ads|ui_upgrade_button' "$f" | head -170 || true; fi
 echo '=== SMALI METHOD INSPECTION VIA APKTOOL (TARGETED) ==='
 if [[ -f "$APK" ]] && command -v apktool >/dev/null 2>&1; then
  TMP="$(mktemp -d)"
  if apktool d -f -r "$APK" -o "$TMP/out" >/dev/null 2>"$TMP/error"; then
   f="$TMP/out/smali/ax/y3/d.smali"
   if [[ -f "$f" ]]; then
    grep -n -A 25 -B 2 -E '^\\.method .* E\\(Landroid/content/Context;Z\\)V' "$f" || true
    # Fallback: method may include an access modifier or unusual whitespace.
    grep -n -A 25 -B 2 -F 'E(Landroid/content/Context;Z)V' "$f" | head -45 || true
   fi
  else echo 'APKTOOL_FAILED'; tail -10 "$TMP/error" || true; fi
  rm -rf "$TMP"
 else echo 'APKTOOL_OR_APK_UNAVAILABLE'; fi
 echo '=== END: no patches, no builds ==='
} > "$OUT"
echo "Saved $OUT"; wc -l "$OUT"
