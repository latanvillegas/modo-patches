#!/usr/bin/env bash
# File Manager Plus 3.8.2: read-only one-time product metadata audit.
set -euo pipefail
SRC="${1:-$HOME/filemanager-amoled-audit/privacy-v2/jadx/sources}"
OUT="$HOME/filemanager-amoled-audit/privacy-v2/premium-onetime-product-audit.txt"
mkdir -p "$(dirname "$OUT")"
python - "$SRC" "$OUT" <<'PY'
from pathlib import Path
import re,sys
root=Path(sys.argv[1]); out=Path(sys.argv[2])
terms=re.compile(r'premiumBasicOnetime|premium_basic_onetime|premium_basic_one_time|premium.*onetime|MANAGED|SUBSCRIPTION|premium_expires_on',re.I)
preferred=('com/alphainventor/filemanager/','ax/p148j3/','ax/p164k3/','ax/p188lb/','ax/p220nb/')
hits=[]
for path in root.rglob('*.java') if root.exists() else []:
 rel=path.relative_to(root).as_posix()
 if not rel.startswith(preferred): continue
 lines=path.read_text(encoding='utf-8',errors='replace').splitlines()
 matches=[i for i,s in enumerate(lines) if terms.search(s)]
 if not matches: continue
 windows=[]
 for i in matches[:35]:
  lo=max(0,i-7);hi=min(len(lines),i+11)
  if windows and lo<=windows[-1][1]:windows[-1]=(windows[-1][0],max(hi,windows[-1][1]))
  else:windows.append((lo,hi))
 hits.append((rel,lines,windows,len(matches)))
with out.open('w',encoding='utf-8') as f:
 f.write('READ ONLY - one-time product and license model; no modifications\n')
 f.write(f'SOURCE_EXISTS={root.exists()} FILES_WITH_MATCHES={len(hits)}\n')
 for rel,lines,windows,count in hits[:65]:
  f.write(f'\n=== {rel} (matches={count}) ===\n')
  for lo,hi in windows:
   f.write(f'--- L{lo+1}-L{hi} ---\n')
   for i in range(lo,hi):f.write(f'{i+1:5}: {lines[i]}\n')
 f.write('\nEND READ ONLY\n')
print(out)
print('lines:',sum(1 for _ in out.open(encoding='utf-8')))
PY
