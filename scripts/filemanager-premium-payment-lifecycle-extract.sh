#!/usr/bin/env bash
set -euo pipefail
ROOT="${1:-$HOME/filemanager-amoled-audit/privacy-v2/jadx/sources}"
SOURCE="$ROOT/com/alphainventor/filemanager/activity/PaymentActivity.java"
OUT="/sdcard/Download/filemanager-payment-lifecycle.txt"
if [[ ! -f "$SOURCE" ]]; then
  echo "No existe: $SOURCE" >&2
  echo "Indica como primer argumento el directorio JADX/sources" >&2
  exit 1
fi
python - "$SOURCE" "$OUT" <<'PY'
from pathlib import Path
import re,sys
src=Path(sys.argv[1]);out=Path(sys.argv[2])
lines=src.read_text(errors='replace').splitlines()
pattern=re.compile(r'\b(onCreate|onStart|onResume|onDestroy|y0|h0|q0|z0|n0|o0|r0|l0)\s*\(')
with out.open('w') as f:
 f.write('PaymentActivity.java lifecycle and purchase method context; read only\n')
 f.write(f'SOURCE={src} LINES={len(lines)}\n')
 hits=[i for i,s in enumerate(lines) if pattern.search(s)]
 for i in hits:
  f.write(f'\n=== L{i+1}: {lines[i].strip()} ===\n')
  for j in range(max(0,i-4),min(len(lines),i+100)):
   f.write(f'{j+1}: {lines[j]}\n')
 f.write('\n=== MainActivity purchase entrypoint context is in prior audit ===\n')
print(out)
PY
