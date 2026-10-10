#!/usr/bin/env python3
"""Validate preservation of Cromite 1.50.0 patch bytecode in an MPP bundle.

Usage: python3 recovery/verify_cromite_150.py /path/to/patches-1.50.0.mpp
No APK build, network or GitHub Actions required.
"""
import hashlib
import sys
import zipfile

EXPECTED_SHA256 = "ca0e2184907fac3964c9f31ec798dfdb33bf723038272945497aeec12911e5f2"
THEME = "app/morphe/patches/cromite/layout/theme/"
THEME_CLASSES = (
    "PureAmoledAppMenu", "PureAmoledCards", "PureAmoledContextMenu",
    "PureAmoledCoreBackgrounds", "PureAmoledDialogs",
    "PureAmoledForegroundResources", "PureAmoledInteractableChip",
    "PureAmoledItemChooser", "PureAmoledMaterialFilledFields",
    "PureAmoledMaterialListItems", "PureAmoledMaterialSemanticSurfaces",
    "PureAmoledMaterialThemeAttrs", "PureAmoledMessages", "PureAmoledNtp",
    "PureAmoledOmnibox", "PureAmoled", "PureAmoledSettings",
    "PureAmoledTabs", "PureAmoledToolbar", "PureAmoledWidgets",
    "PureAmoledXr",
)
REQUIRED = {THEME + name + "PatchKt.class" for name in THEME_CLASSES}
REQUIRED |= {
    "app/morphe/patches/cromite/misc/fullscreen/HideSettingsStatusBarPatchKt.class",
    "app/morphe/patches/cromite/misc/fullscreen/HideStatusBarPatchKt.class",
}

def main(path):
    sha = hashlib.sha256()
    with open(path, "rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            sha.update(chunk)
    with zipfile.ZipFile(path) as bundle:
        present = set(bundle.namelist())
        missing = sorted(REQUIRED - present)
        dex = sorted(n for n in present if n.startswith("classes") and n.endswith(".dex"))
        manifest = bundle.read("META-INF/MANIFEST.MF").decode("utf-8", "replace") if "META-INF/MANIFEST.MF" in present else ""
    print("SHA256:", sha.hexdigest())
    print("Original SHA256 match:", sha.hexdigest() == EXPECTED_SHA256)
    print("Cromite patch classes:", len(REQUIRED) - len(missing), "/", len(REQUIRED))
    print("DEX:", ", ".join(dex) or "MISSING")
    print("Patcher-Version 1.14.0:", "Patcher-Version: 1.14.0" in manifest)
    for name in missing:
        print("MISSING:", name)
    if missing or not dex:
        raise SystemExit(1)

if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("Usage: verify_cromite_150.py FILE.mpp")
    main(sys.argv[1])
