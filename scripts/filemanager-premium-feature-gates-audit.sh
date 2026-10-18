#!/usr/bin/env bash
# READ ONLY: identify File Manager Plus 3.8.2 Premium feature gates and their callers.
set -euo pipefail
SRC="${1:-$HOME/filemanager-amoled-audit/privacy-v2/jadx/sources}"
OUT="$HOME/filemanager-amoled-audit/privacy-v2/premium-feature-gates-audit.txt"
mkdir -p "$(dirname "$OUT")"
python - "$SRC" "$OUT" <<'PY'
from pathlib import Path
import re,sys
root=Path(sys.argv[1]); dest=Path(sys.argv[2])
patterns={
 'premium_gate_g':re.compile(r'ax\.p387y3\.h\.g\s*\('),
 'onetime_gate_j':re.compile(r'ax\.p387y3\.h\.j\s*\('),
 'purchased_gate_i':re.compile(r'ax\.p387y3\.h\.i\s*\('),
 'trial_gate_h':re.compile(r'ax\.p387y3\.h\.h\s*\('),
 'free_trial_gate_k':re.compile(r'ax\.p387y3\.h\.k\s*\('),
 'license_status_event':re.compile(r'LICENSE_STATUS_CHANGED'),
}
files=sorted((root/'com/alphainventor/filemanager').rglob('*.java')) if (root/'com/alphainventor/filemanager').exists() else []
files += [root/'ax/p387y3/h.java',root/'ax/p164k3/c.java']
files=[p for p in files if p.is_file()]
with dest.open('w',encoding='utf-8') as f:
 f.write('READ ONLY - Premium feature gates and caller contexts\n')
 f.write(f'SOURCE_EXISTS={root.is_dir()} SCANNED_FILES={len(files)}\n')
 counts={key:0 for key in patterns}
 for p in files:
  lines=p.read_text(encoding='utf-8',errors='replace').splitlines()
  hits=[(i,k) for i,line in enumerate(lines) for k,pat in patterns.items() if pat.search(line)]
  if not hits:continue
  f.write(f'\n=== {p.relative_to(root)} ===\n')
  for i,k in hits[:60]:
   counts[k]+=1
   f.write(f'--- {k} L{i+1} ---\n')
   for j in range(max(0,i-8),min(len(lines),i+12)):
    f.write(f'{j+1:5}: {lines[j]}\n')
 f.write('\nSUMMARY\n')
 for k,v in counts.items():f.write(f'{k}={v}\n')
 f.write('END READ ONLY\n')
print(dest)
PY
