#!/usr/bin/env bash
# Read-only inspection of Premium UI conditions in File Manager Plus 3.8.2.
set -euo pipefail
SRC="${1:-$HOME/filemanager-amoled-audit/privacy-v2/jadx/sources}"
OUT="$HOME/filemanager-amoled-audit/privacy-v2/premium-ui-conditions-audit.txt"
mkdir -p "$(dirname "$OUT")"
python - "$SRC" "$OUT" <<'PY'
from pathlib import Path
import sys
src, out = Path(sys.argv[1]), Path(sys.argv[2])
targets = {
 "PaymentActivity": ("com/alphainventor/filemanager/activity/PaymentActivity.java", [298, 492, 659, 993, 1009, 1019, 1022, 1026]),
 "MainActivity": ("com/alphainventor/filemanager/activity/MainActivity.java", [316, 797, 1509]),
 "PremiumGate": ("ax/p387y3/h.java", []),
 "AdPremiumInterplay": ("ax/p387y3/a.java", [28,35,42,95,102,109,123,130]),
}
with out.open("w", encoding="utf-8") as f:
 f.write("READ ONLY - NO APK OR LICENSE CHANGES\n")
 for name,(relative,anchors) in targets.items():
  path=src/relative
  f.write(f"\n=== {name}: {relative} ===\n")
  if not path.is_file():
   f.write("SOURCE_MISSING\n")
   continue
  lines=path.read_text(encoding="utf-8", errors="replace").splitlines()
  if not anchors: anchors=[i for i,line in enumerate(lines,1) if any(x in line for x in ("boolean g(", "boolean i(", "boolean j(", "boolean k(", "boolean h("))]
  windows=[]
  for a in anchors:
   start=max(1,a-16)
   end=min(len(lines),a+26)
   if windows and start<=windows[-1][1]+1: windows[-1]=(windows[-1][0],max(windows[-1][1],end))
   else: windows.append((start,end))
  for start,end in windows:
   f.write(f"--- L{start}-L{end} ---\n")
   for n in range(start,end+1): f.write(f"{n:5}: {lines[n-1]}\n")
 f.write("\nEND READ ONLY\n")
print(out)
print("lines:",sum(1 for _ in out.open(encoding="utf-8")))
PY
