#!/usr/bin/env bash
# READ ONLY: inspect all decompiled Java sources for File Manager Premium call sites.
set -euo pipefail
ROOT="${1:-$HOME/filemanager-amoled-audit/privacy-v2/jadx/sources}"
OUT="$HOME/filemanager-amoled-audit/privacy-v2/premium-all-callers-audit.txt"
mkdir -p "$(dirname "$OUT")"
python - "$ROOT" "$OUT" <<'PY'
from pathlib import Path
import re,sys
root=Path(sys.argv[1]); out=Path(sys.argv[2])
patterns={
 'premium_h_g':r'\b(?:ax\.p387y3\.)?h\.g\s*\(',
 'onetime_h_j':r'\b(?:ax\.p387y3\.)?h\.j\s*\(',
 'purchased_h_i':r'\b(?:ax\.p387y3\.)?h\.i\s*\(',
 'trial_h_h':r'\b(?:ax\.p387y3\.)?h\.h\s*\(',
 'subscription_h_k':r'\b(?:ax\.p387y3\.)?h\.k\s*\(',
 'premium_manager_F':r'\b(?:ax\.p164k3\.)?c\.u\s*\(\s*\)\.F\s*\(',
 'premium_manager_H':r'\b(?:ax\.p164k3\.)?c\.u\s*\(\s*\)\.H\s*\(',
 'premium_manager_I':r'\b(?:ax\.p164k3\.)?c\.u\s*\(\s*\)\.I\s*\(',
 'license_changed':r'LICENSE_STATUS_CHANGED',
}
compiled={k:re.compile(v) for k,v in patterns.items()}
files=sorted(root.rglob('*.java')) if root.is_dir() else []
with out.open('w',encoding='utf-8') as f:
 f.write('READ ONLY - all Java callers for Premium and license signals\n')
 f.write('WARNING: simple h.g() and c.u() patterns may include unrelated obfuscated classes; inspect imports and surrounding code.\n')
 f.write(f'SOURCE_EXISTS={root.is_dir()} SCANNED_FILES={len(files)}\n')
 totals={k:0 for k in compiled}
 for path in files:
  try: lines=path.read_text(encoding='utf-8',errors='replace').splitlines()
  except OSError: continue
  hits=[]
  for i,line in enumerate(lines):
   for k,pat in compiled.items():
    if pat.search(line):hits.append((i,k));totals[k]+=1
  if not hits:continue
  f.write(f'\n=== {path.relative_to(root)} MATCHES={len(hits)} ===\n')
  for i,k in hits[:40]:
   f.write(f'--- {k} L{i+1} ---\n')
   for j in range(max(0,i-4),min(len(lines),i+6)):
    f.write(f'{j+1:5}: {lines[j][:400]}\n')
  if len(hits)>40:f.write(f'TRUNCATED_MATCHES={len(hits)-40}\n')
 f.write('\n=== SUMMARY ===\n')
 for k,v in totals.items():f.write(f'{k}={v}\n')
 f.write('END READ ONLY\n')
print(out)
PY
