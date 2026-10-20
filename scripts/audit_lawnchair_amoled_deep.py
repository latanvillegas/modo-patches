#!/usr/bin/env python3
"""Lawnchair 15 Beta 3: read-only deeper AMOLED inspection for Termux."""
import hashlib
import re
import subprocess
import sys
import zipfile
from pathlib import Path

def cmd(*args):
    p=subprocess.run(args,stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True,errors="replace")
    return p.returncode,p.stdout,p.stderr

def main():
    apk=Path(sys.argv[1] if len(sys.argv)>1 else "/sdcard/Download/Lawnchair.15.0.0.Beta.3.0.apk")
    if not apk.is_file(): raise SystemExit("APK not found: "+str(apk))
    output=Path("/sdcard/Download/lawnchair_amoled_deep_audit.txt")
    lines=["LAWNCHAIR AMOLED DEEP AUDIT — READ ONLY",f"APK: {apk}",f"SHA256: {hashlib.sha256(apk.read_bytes()).hexdigest()}"]
    code,resources,err=cmd("aapt2","dump","resources",str(apk))
    if code: raise SystemExit("aapt2 failed: "+err)
    names=re.compile(r"(material_color_surface|cardview_dark_background|folder_background|folder_preview|surface_(dark|light|header)|popup_color_background|popup_shade|all_apps_tabs_background|all_set_page_background|taskbar_background|home_settings|settings|preference|dialog|background_material|design_dark_default_color|system_neutral1_)",re.I)
    blocks=re.split(r"(?=^\s*resource 0x[0-9a-f]+ )",resources,flags=re.M)
    lines+=["","=== RESOURCE BLOCKS (VALUES + CONFIG VARIANTS) ==="]
    for block in blocks:
        header=block.splitlines()[0] if block.strip() else ""
        if names.search(header) and not re.search(r"\b(attr|id|layout|dimen|string|styleable)/",header):
            lines.append(block.rstrip())
    lines+=["","=== STYLES WITH SETTINGS/SURFACE REFERENCES ==="]
    for block in blocks:
        header=block.splitlines()[0] if block.strip() else ""
        if re.search(r"\bstyle/",header) and re.search(r"settings|preference|lawnchair|theme|material|card",header,re.I):
            lines.append(block[:5000].rstrip())
    lines+=["","=== APK RESOURCES / DEX INVENTORY ==="]
    with zipfile.ZipFile(apk) as z:
        dex=[i for i in z.infolist() if re.fullmatch(r"classes\d*\.dex",i.filename)]
        lines.extend(f"{i.filename} size={i.file_size}" for i in dex)
        candidates=[i.filename for i in z.infolist() if i.filename.startswith("res/") and i.filename.endswith(".xml") and re.search(r"settings|preference|card|popup|folder|surface|dialog|all_apps|taskbar",i.filename,re.I)]
        lines.extend(candidates[:400])
    lines+=["","=== XMLTREE: KEY SELECTORS (FIRST 35 LINES EACH) ==="]
    paths=[
        "res/color/all_apps_tabs_background.xml",
        "res/color-night-v8/all_apps_tabs_background.xml",
        "res/color-v31/all_apps_tabs_background.xml",
        "res/color-night-v31/all_apps_tabs_background.xml",
        "res/color/popup_color_background.xml",
        "res/color-v31/popup_color_background.xml",
        "res/color-night-v31/popup_color_background.xml",
        "res/color/surface.xml",
        "res/color/surface_light.xml",
        "res/color-v31/surface_light.xml",
        "res/color-v31/folder_background_light.xml",
        "res/color-night-v31/folder_background_dark.xml",
    ]
    with zipfile.ZipFile(apk) as z: present=set(z.namelist())
    for path in paths:
        if path not in present: continue
        code,stdout,stderr=cmd("aapt2","dump","xmltree",str(apk),"--file",path)
        lines.append(f"FILE {path} status={code}")
        lines.extend(stdout.splitlines()[:35] if code==0 else stderr.splitlines()[:6])
    output.write_text("\n".join(lines)+"\n",encoding="utf-8")
    print("Report:",output)
    print("Bytes:",output.stat().st_size)
    print("No APK changes; no compilation.")

if __name__=="__main__": main()
