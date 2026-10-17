#!/usr/bin/env bash
# Read-only prebuild verification for File Manager Plus Privacy v2.
set -euo pipefail
cd "$HOME/modo-patches-devtools"
BASE="$HOME/filemanager-amoled-audit/privacy-v2"
SRC="$BASE/jadx/sources"
OUT="$BASE/privacy-v2-prebuild-check.txt"
FILE="patches/src/main/kotlin/app/morphe/patches/filemanager/misc/privacy/FileManagerPrivacyV2SessionsPatch.kt"
[[ -s "$SRC/ax/W8/f.java" ]] || { echo "Missing JADX Sessions settings class" >&2; exit 1; }
[[ -s "$FILE" ]] || { echo "Missing Privacy v2 patch; check out feature/filemanager-privacy-v2" >&2; exit 1; }
{
 echo "=== REPOSITORY ==="
 git branch --show-current
 git status --short
 echo
 echo "=== METHOD SIGNATURE AND SOURCE ==="
 grep -n -A 12 'public final boolean d()' "$SRC/ax/W8/f.java"
 echo
 echo "=== PATCH FINGERPRINT ==="
 grep -n -A 10 'FirebaseSessionsEnabledFingerprint' "$FILE"
 echo
 echo "=== PATCH COMPATIBILITY / OPT-IN ==="
 grep -n -E 'name = |default = |compatibleWith|const/4|return p0' "$FILE"
 echo
 echo "=== ORIGINAL PRIVACY V1 UNCHANGED LOCALLY? ==="
 git diff -- patches/src/main/kotlin/app/morphe/patches/filemanager/misc/privacy/FileManagerPrivacyPatch.kt
 echo
 echo "=== ORIGINAL AMOLED V6 UNCHANGED LOCALLY? ==="
 git diff -- patches/src/main/kotlin/app/morphe/patches/filemanager/layout/theme/FileManagerPureAmoledPatch.kt
} > "$OUT"
echo "Saved: $OUT"
cat "$OUT"
