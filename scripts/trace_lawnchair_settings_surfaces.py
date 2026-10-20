#!/usr/bin/env python3
"""Read-only targeted settings-surface audit for Lawnchair 15 Beta 3 (Termux)."""
import hashlib, re, subprocess, sys, zipfile
from pathlib import Path

apk=Path(sys.argv[1] if len(sys.argv)>1 else "/sdcard/Download/Lawnchair.15.0.0.Beta.3.0.apk")
out=Path("/sdcard/Download/lawnchair_settings_surface_trace.txt")
if not apk.is_file(): sys.exit("APK not found: "+str(apk))
def run(*args):
    p=subprocess.run(args,stdout=subprocess.PIPE,stderr=subprocess.PIPE,encoding="utf-8",errors="replace")
    return p.returncode,p.stdout,p.stderr
code,resources,error=run("aapt2","dump","resources",str(apk))
if code: sys.exit("aapt2 dump resources failed: "+error)
lines=["LAWNCHAIR SETTINGS SURFACE TRACE — READ ONLY",f"APK: {apk}",f"SHA256: {hashlib.sha256(apk.read_bytes()).hexdigest()}"]
blocks=re.split(r"(?=^\s*resource 0x[0-9a-f]+ )",resources,flags=re.M)
patterns=[
 r"\bcolor/(home_settings|surface|system_neutral1|material_color_surface|popup|folder|cardview|design_dark_default_color|background_material|all_apps_tabs)",
 r"\bstyle/(HomeSettings|Theme\.|Theme$|Lawnchair|Launcher|Preference)",
 r"\bdrawable/(home_settings|preference|ps_settings|popup|folder)",
 r"\blayout/(home_settings|preference|ps_settings)",
]
lines.append("\n=== MATCHED RESOURCE BLOCKS ===")
for block in blocks:
    header=block.splitlines()[0] if block.strip() else ""
    if any(re.search(p,header,re.I) for p in patterns):
        lines.append(block.rstrip()[:9000])
with zipfile.ZipFile(apk) as z:
    names=z.namelist()
    lines.append("\n=== SETTINGS / PREFERENCE / BACKGROUND XML PATHS ===")
    xmls=[n for n in names if n.startswith("res/") and n.endswith(".xml") and re.search(r"settings|preference|surface|card|popup|folder|background",n,re.I)]
    lines.extend(xmls[:350])
    lines.append("\n=== SELECTOR XMLTREE FOR VERIFIED COLOR FILES ===")
    exact=re.compile(r"^res/color(?:-night-v8|-v31|-night-v31)?/(?:all_apps_tabs_background|popup_color_background|surface_light|folder_background_dark|folder_background_light|folder_preview_dark|folder_preview_light|popup_shade_first|popup_shade_second|popup_shade_third)\.xml$")
    targets=[n for n in names if exact.match(n)]
    for path in targets:
        rc,stdout,stderr=run("aapt2","dump","xmltree",str(apk),"--file",path)
        lines.append(f"\nFILE {path} rc={rc}\n"+(stdout[:3000] if rc==0 else stderr[:300]))
    lines.append("\n=== LAYOUT XMLTREE FOR SETTINGS ===")
    layouts=[n for n in names if n.startswith("res/") and n.endswith(".xml") and re.search(r"home_settings|preference_material|preference_list",n,re.I)]
    for path in layouts[:35]:
        rc,stdout,stderr=run("aapt2","dump","xmltree",str(apk),"--file",path)
        lines.append(f"\nFILE {path} rc={rc}\n"+(stdout[:8500] if rc==0 else stderr[:300]))
    lines.append("\n=== DEX STRINGS: SETTINGS AND SURFACE SYMBOLS ===")
    needles=(b"HomeSettings",b"surfaceContainer",b"surfaceColor",b"colorSurface",b"PreferenceGroup",b"MaterialTheme",b"dynamicColor",b"cardBackground",b"SettingsActivity")
    for name in names:
        if not re.fullmatch(r"classes\d*\.dex",name): continue
        data=z.read(name)
        hits=[]
        for needle in needles:
            pos=0
            for _ in range(8):
                pos=data.find(needle,pos)
                if pos<0: break
                context=data[max(0,pos-55):min(len(data),pos+110)]
                hits.append(f"{needle.decode()} offset=0x{pos:x} context={repr(context)}")
                pos+=len(needle)
        lines.append(f"\n{name} bytes={len(data)}\n"+"\n".join(hits))
out.write_text("\n".join(lines)+"\n",encoding="utf-8")
print("Report:",out)
print("Bytes:",out.stat().st_size)
print("APK unchanged. No build triggered.")
