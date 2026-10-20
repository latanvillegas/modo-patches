#!/usr/bin/env python3
"""Read-only audit of Lawnchair 15 Beta 3 Android 12+ Material surface selectors.

Usage: python audit_lawnchair_dynamic_colors.py /path/to/Lawnchair.apk
Requires aapt2 on PATH. Does not change the APK.
"""
import re
import subprocess
import sys
from pathlib import Path

NAMES = (
    "material_color_surface",
    "material_color_surface_bright",
    "material_color_surface_container",
    "material_color_surface_container_high",
    "material_color_surface_container_highest",
    "material_color_surface_container_low",
    "material_color_surface_container_lowest",
    "material_color_surface_dim",
    "material_color_surface_variant",
)
KNOWN = {
    ("color-v31", "material_color_surface"): "98",
    ("color-night-v31", "material_color_surface"): "6",
    ("color-v31", "material_color_surface_container"): "94",
    ("color-night-v31", "material_color_surface_container"): "12",
}

def main():
    if len(sys.argv) != 2 or not Path(sys.argv[1]).is_file():
        raise SystemExit("Usage: python audit_lawnchair_dynamic_colors.py /path/to/Lawnchair.apk")
    apk = sys.argv[1]
    failures = []
    for directory in ("color-v31", "color-night-v31"):
        for name in NAMES:
            path = f"res/{directory}/{name}.xml"
            proc = subprocess.run(
                ["aapt2", "dump", "xmltree", apk, "--file", path],
                text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
            )
            output = proc.stdout + "\n" + proc.stderr
            colors = re.findall(r"android:color\(0x010101a5\)=([^\s]+)", output)
            lstars = re.findall(r"android:lStar\(0x01010647\)=([^\s]+)", output)
            items = len(re.findall(r"^\s*E: item\b", output, re.M))
            expected = KNOWN.get((directory, name))
            ok = proc.returncode == 0 and items == 1 and len(colors) == 1 and len(lstars) == 1
            if expected is not None:
                ok = ok and lstars[0] == expected if lstars else False
            if colors:
                ok = ok and colors[0] == "@0x01060024"
            print(f"{'OK' if ok else 'CHECK'} {path}: color={colors} lStar={lstars} items={items}")
            if not ok:
                failures.append(path)
    print(f"\nAudited {len(NAMES)*2} selectors; exceptions: {len(failures)}")
    if failures:
        raise SystemExit(1)

if __name__ == "__main__":
    main()
