#!/usr/bin/env bash
set -euo pipefail
ROOT="${1:-$HOME/filemanager-amoled-audit/privacy-v2/jadx/sources}"
OUT="/sdcard/Download/filemanager-premium-settings-label.txt"
[[ -d "$ROOT" ]] || { echo "No existe directorio JADX: $ROOT" >&2; exit 1; }
python - "$ROOT" "$OUT" <<'PY'
import pathlib,re,sys
root=pathlib.Path(sys.argv[1]);out=pathlib.Path(sys.argv[2])
files=list(root.rglob('*.java'))
pat=re.compile(r'premium|ax\.p387y3\.h\.|\bh\.[dgijk]\(|title_premium|premium_expires',re.I)
scored=[]
for f in files:
 if not any(t in str(f).lower() for t in ('setting','pref','mainactivity','paymentactivity')):
  continue
 lines=f.read_text(errors='replace').splitlines()
 hits=[i for i,s in enumerate(lines) if pat.search(s)]
 if hits:scored.append((f,lines,hits))
with out.open('w') as w:
 w.write(f'ROOT={root} FILES={len(files)} MATCHED={len(scored)}\n')
 for f,lines,hits in scored:
  w.write(f'\n=== {f.relative_to(root)} HITS={len(hits)} ===\n')
  for i in hits[:100]:
   w.write(f'\n-- line {i+1} --\n')
   for j in range(max(0,i-5),min(len(lines),i+7)):
    w.write(f'{j+1}: {lines[j]}\n')
print(out)
PY
