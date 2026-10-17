#!/usr/bin/env bash
# Read-only audit of File Manager Plus 3.8.2 Premium license acquisition and persistence.
set -euo pipefail
SRC="${1:-$HOME/filemanager-amoled-audit/privacy-v2/jadx/sources}"
OUT="$HOME/filemanager-amoled-audit/privacy-v2/premium-license-lifecycle-audit.txt"
mkdir -p "$(dirname "$OUT")"
python - "$SRC" "$OUT" <<'PY'
from pathlib import Path
import re,sys
root=Path(sys.argv[1]); out=Path(sys.argv[2])
terms=re.compile(r'license_token|SharedPreferences\("license"|getPremiumBasicOnetime|purchaseToken|PurchaseToken|acknowledgePurchase|queryPurchases|onPurchasesUpdated|LicenseByCoupon|void P\(|\bP\(ax\.p188lb\.b|\bA\(String str\)|\bz\(String str\)|\by\(ax\.p188lb\.b|premiumBasicOnetime',re.I)
targets=['ax/p164k3/c.java','com/alphainventor/filemanager/activity/PaymentActivity.java','com/alphainventor/filemanager/license/datatypes/ProductCatalogImpl.java']
files=[root/p for p in targets if (root/p).exists()]
for p in root.rglob('*.java') if root.exists() else []:
 if p in files:continue
 rel=p.relative_to(root).as_posix()
 if rel.startswith(('com/alphainventor/filemanager/license/','ax/p220nb/','ax/p188lb/')):
  files.append(p)
with out.open('w',encoding='utf-8') as f:
 f.write('READ ONLY: Premium license acquisition, persistence and selection\n')
 f.write(f'SOURCE_EXISTS={root.exists()} SCANNED_FILES={len(files)}\n')
 for p in files:
  lines=p.read_text(encoding='utf-8',errors='replace').splitlines()
  ix=[i for i,s in enumerate(lines) if terms.search(s)]
  if not ix:continue
  f.write(f'\n=== {p.relative_to(root)} MATCHES={len(ix)} ===\n')
  ranges=[]
  for i in ix[:35]:
   a=max(0,i-12);b=min(len(lines),i+22)
   if ranges and a<=ranges[-1][1]:ranges[-1]=(ranges[-1][0],max(b,ranges[-1][1]))
   else:ranges.append((a,b))
  for a,b in ranges:
   f.write(f'--- L{a+1}-L{b} ---\n')
   for i in range(a,b):f.write(f'{i+1:5}: {lines[i]}\n')
 f.write('\nEND READ ONLY\n')
print(out)
PY
