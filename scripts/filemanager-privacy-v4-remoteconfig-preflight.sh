#!/usr/bin/env bash
# Read-only preflight for Privacy v4 Remote Config; no builds or patching.
set -euo pipefail
ROOT="${1:-$HOME/filemanager-amoled-audit/privacy-v2/jadx/sources}"
OUT="$HOME/filemanager-amoled-audit/privacy-v2/privacy-v4-remoteconfig-preflight.txt"
OWNER="$ROOT/ax/p387y3/d.java"
HELPER="$ROOT/ax/A3/b.java"
[[ -f "$OWNER" && -f "$HELPER" ]] || { echo "Missing JADX inputs" >&2; exit 1; }
mkdir -p "$(dirname "$OUT")"
{
 echo '=== PRIVACY V4 REMOTE CONFIG PREFLIGHT (READ ONLY) ==='
 echo '=== OWNER SIGNATURE AND BODY ==='
 grep -n -A 9 -B 3 -F 'public void E(Context context, boolean z)' "$OWNER" || true
 echo '=== HELPER EARLY RETURN AND FALLBACK ==='
 grep -n -A 21 -B 3 -F 'public void f(Context context, HashMap<String, Object> map, boolean z)' "$HELPER" || true
 grep -n -A 9 -F 'public boolean b(String str)' "$HELPER" || true
 echo '=== ALL CALLERS OF E() KNOWN BY JADX ==='
 grep -RInF --include='*.java' '.E(this.a, this.d)' "$ROOT/ax" "$ROOT/com/alphainventor" 2>/dev/null || true
 echo '=== OPTIONAL SMALI CLASS AND METHOD VERIFICATION ==='
 FOUND=0
 for BASE in "$HOME/filemanager-amoled-audit/privacy-v2" "$HOME/filemanager-amoled-audit"; do
  while IFS= read -r FILE; do
   [[ -f "$FILE" ]] || continue
   FOUND=1
   echo "--- $FILE"
   grep -n -A 12 -B 2 -E '^\.method .* E\(Landroid/content/Context;Z\)V' "$FILE" || true
  done < <(find "$BASE" -type f -path '*/smali*/ax/y3/d.smali' -print 2>/dev/null)
 done
 if [[ "$FOUND" == 0 ]]; then echo 'SMALI_NOT_AVAILABLE: JADX Java alone cannot verify actual DEX descriptor/registers'; fi
 echo '=== PATCH CONTRACT ==='
 echo 'Expected DEX class Lax/y3/d; and instance method E(Landroid/content/Context;Z)V'
 echo 'p0=this, p1=context, p2=boolean; injection const/4 p2, 0x0'
 echo 'NOTE: JADX deobfuscation/package renaming may differ from raw DEX; verify fingerprint against original DEX before building.'
 echo '=== END: no changes, no Actions ==='
} > "$OUT"
echo "Saved $OUT"; wc -l "$OUT"
