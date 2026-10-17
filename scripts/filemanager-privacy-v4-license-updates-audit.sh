#!/usr/bin/env bash
# Read-only JADX audit: billing/license and internal app update entry points.
set -euo pipefail
ROOT="${1:-$HOME/filemanager-amoled-audit/privacy-v2/jadx/sources}"
OUT="$HOME/filemanager-amoled-audit/privacy-v2/privacy-v4-license-updates-audit.txt"
[[ -d "$ROOT" ]] || { echo "Missing JADX sources: $ROOT" >&2; exit 1; }
mkdir -p "$(dirname "$OUT")"
{
 echo '=== LICENSE AND UPDATE AUDIT — READ ONLY ==='
 echo '=== INITIALIZATION OF DYNAMIC CONFIG FLAG ==='
 f="$ROOT/ax/U2/b.java"
 if [[ -f "$f" ]]; then nl -ba "$f" | sed -n '1,210p'; fi
 echo '=== PAYMENT ACTIVITY / BILLING REFERENCES ==='
 for rel in com/alphainventor/filemanager/activity/PaymentActivity.java ax/p100g3/s0.java; do
  f="$ROOT/$rel"; echo "--- $rel ---"
  if [[ -f "$f" ]]; then grep -nEi -C 5 'premium|purchase|billing|license|subscription|onetime|trial|entitle|\.u\(\)|\.x\(\)' "$f" | head -240 || true; fi
 done
 echo '=== INTERNAL UPDATE PATHS ==='
 grep -RInEi --include='*.java' 'AppUpdateManager|inappupdate|updateAvailability|startUpdateFlow|com.google.android.play.core.appupdate|ACTION_VIEW.*market|market://details|checkForUpdates|check_update|auto.update|new.version' "$ROOT/com/alphainventor" "$ROOT/ax" 2>/dev/null | head -180 || true
 echo '=== LICENSE VALIDATION ENTRY POINTS ==='
 grep -RInEi --include='*.java' 'BillingClient|PurchasesUpdatedListener|queryPurchasesAsync|queryProductDetailsAsync|acknowledgePurchase|isPremium|isProVersion|premium.*unlock|license.*check' "$ROOT/com/alphainventor" "$ROOT/ax" 2>/dev/null | head -180 || true
 echo '=== END — no source changes or builds ==='
} > "$OUT"
echo "Saved $OUT"; wc -l "$OUT"
