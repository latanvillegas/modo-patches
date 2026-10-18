#!/usr/bin/env bash
set -euo pipefail
APK="${1:-}"
if [[ ! -f "$APK" ]]; then
  echo "Uso: bash $0 /ruta/al/APK-parcheado.apk" >&2
  exit 2
fi
OUT="/sdcard/Download/filemanager-premium-apk-label-verify.txt"
{
  echo "APK=$APK"
  sha256sum "$APK"
  echo
  echo "== Herramientas =="
  for t in aapt2 aapt apktool unzip; do command -v "$t" || true; done
  echo
  echo "== Referencias de recursos Premium en resources.arsc =="
  python - "$APK" <<'PY'
import sys,zipfile,re
p=sys.argv[1]
with zipfile.ZipFile(p) as z:
 b=z.read("resources.arsc")
 for s in ["Premium Vitalicio","title_premium","Premium","Vitalicio"]:
  u=s.encode("utf-16le"); a=s.encode("utf-8")
  print(f"{s!r}: utf16le={b.count(u)} utf8={b.count(a)}")
 for m in re.finditer("Premium Vitalicio".encode("utf-16le"),b):
  print("Premium Vitalicio offset:",hex(m.start()))
PY
  echo
  if command -v aapt2 >/dev/null 2>&1; then
    echo "== aapt2 dump resources (filtrado) =="
    aapt2 dump resources "$APK" 2>&1 | grep -i -C 3 -E 'title_premium|Premium Vitalicio' | head -160 || true
  elif command -v aapt >/dev/null 2>&1; then
    echo "== aapt dump resources (filtrado) =="
    aapt dump --values resources "$APK" 2>&1 | grep -i -C 3 -E 'title_premium|Premium Vitalicio' | head -160 || true
  else
    echo "No hay aapt/aapt2; instalar aapt2 para comprobar el valor por ID de recurso."
  fi
} > "$OUT"
echo "$OUT"
