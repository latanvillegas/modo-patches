#!/usr/bin/env bash
set -euo pipefail
ROOT="${1:-$HOME/filemanager-amoled-audit/privacy-v2/jadx/sources}"
OUT="$HOME/filemanager-amoled-audit/privacy-v2/premium-billing-entrypoints-audit.txt"
mkdir -p "$(dirname "$OUT")"
python - "$ROOT" "$OUT" <<'PY'
from pathlib import Path
import re,sys
root=Path(sys.argv[1]);out=Path(sys.argv[2])
terms=[
 r'PaymentActivity',r'BillingClient',r'launchBillingFlow',r'queryPurchasesAsync',
 r'queryProductDetailsAsync',r'PurchasesUpdatedListener',r'getPremiumBasicOnetime',
 r'getPremiumBasicYearly',r'manage_subscription',r'upgrade_to_premium',
 r'premium_expires_on',r'license_server_public_key',
]
rx=re.compile('|'.join(terms))
files=sorted(root.rglob('*.java')) if root.is_dir() else []
with out.open('w') as f:
 f.write('READ ONLY - billing and purchase entrypoints\n')
 f.write(f'SOURCE_EXISTS={root.is_dir()} SCANNED_FILES={len(files)}\n')
 f.write('Matches are references, not proof of network requests.\n')
 for path in files:
  if not (str(path.relative_to(root)).startswith(('com/alphainventor/','ax/p164k3/','ax/p148j3/','ax/p188lb/','ax/p387y3/'))):continue
  lines=path.read_text(errors='replace').splitlines()
  hits=[i for i,line in enumerate(lines) if rx.search(line)]
  if not hits:continue
  f.write(f'\n=== {path.relative_to(root)} HITS={len(hits)} ===\n')
  for i in hits[:50]:
   f.write(f'--- L{i+1} ---\n')
   for j in range(max(0,i-3),min(len(lines),i+5)):
    f.write(f'{j+1}: {lines[j][:350]}\n')
  if len(hits)>50:f.write(f'TRUNCATED={len(hits)-50}\n')
 f.write('\nEND READ ONLY\n')
print(out)
PY
