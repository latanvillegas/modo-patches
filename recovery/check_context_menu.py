#!/usr/bin/env python3
"""Offline source/bytecode regression checks for restored Cromite context menu.

Usage: python3 recovery/check_context_menu.py patches-1.50.0.mpp
Does not compile or build an APK.
"""
from pathlib import Path
from zipfile import ZipFile
import subprocess
import sys
import tempfile

SOURCE = Path(__file__).resolve().parents[1] / "patches/src/main/kotlin/app/morphe/patches/cromite/layout/theme/PureAmoledContextMenuPatch.kt"
ENTRY = "app/morphe/patches/cromite/layout/theme/PureAmoledContextMenuPatchKt.class"
CHECKS = {
    "owner": ('Lg03;', 'Lg03;'),
    "layout": ('2131624183L', '2131624183l'),
    "inflate anchor": ('LayoutInflater;->inflate', 'LayoutInflater;->inflate'),
    "findViewById anchor": ('View;->findViewById', 'View;->findViewById'),
    "two matches": ('matches.size == 2', 'iconst_2'),
    "view background": ('setBackgroundColor(I)V', 'setBackgroundColor(I)V'),
    "pure black": ('-0x1000000', '-0x1000000'),
    "pure white": ('-0x1', '-0x1'),
    "theme option": ('recoveredBrowserThemeOption', 'getBrowserThemeOption'),
}

def main(bundle_path: str):
    source = SOURCE.read_text()
    with ZipFile(bundle_path) as bundle:
        bytecode = bundle.read(ENTRY)
    with tempfile.TemporaryDirectory() as directory:
        target = Path(directory) / "PureAmoledContextMenuPatchKt.class"
        target.write_bytes(bytecode)
        result = subprocess.run(
            ["javap", "-v", "-p", "-c", str(target)],
            capture_output=True, text=True, check=True,
        )
        original = result.stdout
    failures = []
    for label, (src, compiled) in CHECKS.items():
        passed = src in source and compiled in original
        print(f'{"PASS" if passed else "FAIL"}: {label}')
        if not passed:
            failures.append(label)
    if failures:
        raise SystemExit(f"Failed {len(failures)} checks: {', '.join(failures)}")
    print("Static bytecode/source comparison passed. Kotlin compilation and runtime remain unverified.")

if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("Usage: check_context_menu.py ORIGINAL.mpp")
    main(sys.argv[1])
