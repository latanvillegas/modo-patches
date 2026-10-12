#!/usr/bin/env python3
"""Apply the verified Cromite 153 startup hook to an apktool Smali tree.

This is an experimental Smali integration step, NOT a Morphe .mpp patch.
Run against a disposable apktool extraction, never the original APK.
"""
from pathlib import Path
import argparse

TARGET = ".method public final b(Lkm1;)V"
ANCHOR = """    :cond_5
    new-instance v0, Le72;"""
HOOK = """    :cond_5
    # Morphe DevTools frontend: install before asynchronous browser startup.
    # v1 is dead here; keep original .locals 4 and existing catch ranges.
    sget-object v1, Ln13;->a:Landroid/content/Context;
    invoke-static {v1}, Lapp/morphe/cromite/devtools/DevToolsBootstrap;->initializeSafely(Landroid/content/Context;)V

    new-instance v0, Le72;"""
SIGNATURE = "Lapp/morphe/cromite/devtools/DevToolsBootstrap;->initializeSafely"

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("apktool_directory", type=Path)
    args = parser.parse_args()
    matches = list(args.apktool_directory.rglob("j72.smali"))
    if len(matches) != 1:
        raise SystemExit(f"Expected exactly one j72.smali, found {len(matches)}")
    path = matches[0]
    text = path.read_text()
    start = text.find(TARGET)
    if start < 0:
        raise SystemExit("Target method missing; unsupported APK")
    end = text.find(".end method", start)
    if end < 0:
        raise SystemExit("Malformed target method")
    body = text[start:end]
    if SIGNATURE in body:
        print("ALREADY_PATCHED", path)
        return
    if ".locals 4" not in body or body.count(ANCHOR) != 1:
        raise SystemExit("Method structure mismatch; refusing unsafe patch")
    if "invoke-interface {p1, v0}, Lkm1;->d0(Le72;)V" not in body:
        raise SystemExit("Startup call missing; refusing unsafe patch")
    updated = body.replace(ANCHOR, HOOK, 1)
    path.write_text(text[:start] + updated + text[end:])
    print("HOOK_APPLIED", path)
    print("NOTE: Rebuild payload DEX after latest Java changes; ensure APK contains classes3.dex and frontend asset.")

if __name__ == "__main__":
    main()
