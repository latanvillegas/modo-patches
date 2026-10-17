#!/usr/bin/env bash
# Verify Privacy v4 against the original APK DEX without patching or building.
set -euo pipefail
APK="${1:-/sdcard/Download/com.alphainventor.filemanager_3.8.2-2103082_minAPI21(nodpi)_apkmirror.com.apk}"
OUT="$HOME/filemanager-amoled-audit/privacy-v2/privacy-v4-remoteconfig-dex-audit.txt"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
[[ -f "$APK" ]] || { echo "APK missing: $APK" >&2; exit 1; }
mkdir -p "$(dirname "$OUT")"
{
 echo '=== PRIVACY V4 REMOTE CONFIG RAW DEX AUDIT ==='
 echo "APK: $APK"
 sha256sum "$APK"
 echo '=== AVAILABLE TOOLS ==='
 for cmd in jadx baksmali apktool dexdisasm dexdump unzip python; do command -v "$cmd" || true; done
 echo '=== DEX ENTRIES ==='
 unzip -Z1 "$APK" | grep -E '^classes([0-9]+)?\.dex$' || true
 echo '=== RAW DEX CLASS/METHOD STRING EVIDENCE ==='
 python - "$APK" <<'PY'
import sys,zipfile,struct
apk=sys.argv[1]
targets=(b'Lax/y3/d;',b'Lax/p387y3/d;',b'Lax/A3/b;',b'E',b'Ljava/lang/Boolean;',b'Landroid/content/Context;')
with zipfile.ZipFile(apk) as z:
 for name in z.namelist():
  if not (name.startswith('classes') and name.endswith('.dex')): continue
  d=z.read(name)
  print(name, 'size',len(d))
  if d[:4]!=b'dex\n': print('NOT_DEX');continue
  n=struct.unpack_from('<I',d,0x38)[0];off=struct.unpack_from('<I',d,0x3c)[0]
  strings=[]
  def uleb(pos):
   v=0;shift=0
   while True:
    b=d[pos];pos+=1;v|=(b&127)<<shift
    if b<128:return v,pos
    shift+=7
  for i in range(n):
   p=struct.unpack_from('<I',d,off+i*4)[0]
   _,p=uleb(p)
   end=d.find(b'\x00',p)
   strings.append(d[p:end])
  s=set(strings)
  for t in targets: print('STRING',repr(t),t in s)
  # Confirm class definition, method id and exact prototype via DEX tables.
  type_n,type_o=struct.unpack_from('<II',d,0x40)
  proto_n,proto_o=struct.unpack_from('<II',d,0x48)
  method_n,method_o=struct.unpack_from('<II',d,0x58)
  class_n,class_o=struct.unpack_from('<II',d,0x60)
  types=[struct.unpack_from('<I',d,type_o+4*i)[0] for i in range(type_n)]
  def typ(i):return strings[types[i]].decode('utf-8','replace')
  cls_candidates=[]
  for i in range(class_n):
   class_idx=struct.unpack_from('<I',d,class_o+32*i)[0]
   if typ(class_idx) in ('Lax/y3/d;','Lax/p387y3/d;'):
    cls_candidates.append((typ(class_idx),i))
  print('CLASS_DEFS',cls_candidates)
  for i in range(method_n):
   cls,proto,nameidx=struct.unpack_from('<HHI',d,method_o+8*i)
   if typ(cls) not in ('Lax/y3/d;','Lax/p387y3/d;') or strings[nameidx]!=b'E':continue
   shorty,ret,params=struct.unpack_from('<III',d,proto_o+12*proto)
   ptypes=[]
   if params:
    count=struct.unpack_from('<I',d,params)[0]
    ptypes=[typ(struct.unpack_from('<H',d,params+4+2*j)[0]) for j in range(count)]
   print('METHOD_ID',typ(cls),'E('+''.join(ptypes)+')'+typ(ret),'method_idx',i)
PY
 echo '=== OPTIONAL BAKSMALI METHOD AND REGISTER CHECK ==='
 if command -v baksmali >/dev/null 2>&1; then
  for DEX in $(unzip -Z1 "$APK" | grep -E '^classes([0-9]+)?\.dex$'); do
   unzip -p "$APK" "$DEX" > "$TMP/${DEX}"
   mkdir -p "$TMP/out-${DEX}"
   if baksmali disassemble "$TMP/${DEX}" -o "$TMP/out-${DEX}" >/dev/null 2>&1; then
    for CLASS in "$TMP/out-${DEX}/ax/y3/d.smali" "$TMP/out-${DEX}/ax/p387y3/d.smali"; do
     [[ -f "$CLASS" ]] || continue
     echo "CLASS_SMALI_FOUND $DEX"
     grep -n -A 22 -B 2 -E '^\.method .* E\(Landroid/content/Context;Z\)V' "$CLASS" || true
    done
   fi
  done
 else
  echo 'BAKSMALI_UNAVAILABLE: method code registers cannot be confirmed with this script.'
 fi
 echo '=== APKTOOL SMALI VERIFICATION (IF INSTALLED) ==='
 if command -v apktool >/dev/null 2>&1; then
  if apktool d -f -r "$APK" -o "$TMP/apktool" >/dev/null 2>"$TMP/apktool.err"; then
   MATCHES=0
   while IFS= read -r CLASS; do
    [[ -f "$CLASS" ]] || continue
    MATCHES=$((MATCHES+1))
    echo "CLASS_SMALI_FOUND $CLASS"
    grep -n -A 30 -B 2 -E '^\\.method .* E\(Landroid/content/Context;Z\)V' "$CLASS" || true
   done < <(find "$TMP/apktool" -type f \( -path '*/ax/y3/d.smali' -o -path '*/ax/p387y3/d.smali' \) -print)
   if [[ "$MATCHES" == 0 ]]; then echo 'CLASS_SMALI_NOT_FOUND'; fi
  else
   echo 'APKTOOL_DECODE_FAILED'
   tail -15 "$TMP/apktool.err" || true
  fi
 else
  echo 'APKTOOL_UNAVAILABLE'
 fi
 echo '=== END: READ ONLY, NO BUILD ==='
} > "$OUT"
echo "Saved $OUT"; wc -l "$OUT"
