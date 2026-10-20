#!/usr/bin/env python3
"""Read-only Lawnchair AMOLED resource inventory (no APK rebuild).

Run in Termux: python audit_lawnchair_surfaces.py "/sdcard/Download/Lawnchair.15.0.0.Beta.3.0.apk"
Requires aapt2, Python 3. Writes a report in the current directory.
"""
import hashlib
import re
import subprocess
import sys
import zipfile
from pathlib import Path

TOKENS = (
    "surface", "background", "container", "popup", "folder", "drawer",
    "all_apps", "taskbar", "card", "preference", "settings", "sheet",
    "scrim", "overlay", "dialog", "panel", "sidebar", "list", "stroke",
)
EXCLUDE = ("text", "icon", "foreground", "ripple", "accent", "tint", "on_surface")
FOCUS = (
    "all_apps_tabs_background", "folder_background_dark",
    "folder_background_light", "popup_color_background",
    "taskbar_background",
)

def run(*args):
    p = subprocess.run(args, text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    return p.returncode, p.stdout, p.stderr

def main():
    if len(sys.argv) != 2 or not Path(sys.argv[1]).is_file():
        raise SystemExit("Usage: python audit_lawnchair_surfaces.py /path/to/Lawnchair.apk")
    apk = Path(sys.argv[1])
    report = Path("lawnchair_amoled_surfaces_audit.txt")
    sha = hashlib.sha256()
    with apk.open("rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            sha.update(chunk)
    code, resources, err = run("aapt2", "dump", "resources", str(apk))
    if code:
        raise SystemExit(f"aapt2 dump resources failed: {err}")
    lines = resources.splitlines()
    matches = []
    for i, line in enumerate(lines):
        lowered = line.lower()
        if not any(t in lowered for t in TOKENS):
            continue
        if not ("resource " in lowered or "config " in lowered or "value=" in lowered):
            continue
        if any(t in lowered for t in EXCLUDE):
            continue
        matches.append((i, line))
    output = [
        "LAWNCHAIR AMOLED SURFACE AUDIT (READ ONLY)",
        f"APK: {apk.name}",
        f"SHA256: {sha.hexdigest()}",
        f"Resources matched: {len(matches)}",
        "",
        "=== RESOURCE DECLARATIONS / CONFIGURATIONS ===",
    ]
    for i, line in matches:
        output.append(f"[line {i+1}] {line}")
        if line.lstrip().startswith("resource "):
            for extra in lines[i+1:i+7]:
                if extra.lstrip().startswith("resource "):
                    break
                output.append("  " + extra.strip())
    output += ["", "=== TARGET RESOURCE MATCHES ==="]
    for name in FOCUS:
        hits = [x for x in lines if name in x]
        output.append(f"{name}: {len(hits)} matching lines")
        output.extend("  " + x for x in hits[:25])
    output += ["", "=== RESOURCE XML FILE CANDIDATES ==="]
    with zipfile.ZipFile(apk) as z:
        candidates = [
            name for name in z.namelist()
            if name.startswith("res/") and name.endswith(".xml")
            and any(t in name.lower() for t in TOKENS)
        ]
    output.extend(candidates[:450])
    if len(candidates) > 450:
        output.append(f"... {len(candidates)-450} additional XML paths omitted")
    report.write_text("\n".join(output) + "\n", encoding="utf-8")
    print(f"Audit complete: {len(matches)} resource lines, {len(candidates)} XML candidates")
    print(f"Report: {report.resolve()}")
    print("No APK files were changed.")

if __name__ == "__main__":
    main()
