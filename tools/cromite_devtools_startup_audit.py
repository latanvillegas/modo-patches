#!/usr/bin/env python3
"""Read-only Cromite 153 DEX startup reconnaissance.

Usage:
  python tools/cromite_devtools_startup_audit.py /sdcard/Download/cromite-release/arm64_ChromePublic.apk
"""
import hashlib
import re
import sys
import zipfile
from pathlib import Path

EXPECTED_APK_SHA256 = "9db12af1af021f42b4371e76d0e9fe476a7085bbbbc76bc410a538d028bf6071"
NEEDLES = (
    "CommandLine", "ChromeApplication", "ChromeBrowserInitializer",
    "LibraryLoader", "BrowserStartupController", "ContentMain",
    "custom-devtools-frontend", "devtools", "command-line",
    "appendSwitch", "appendSwitchWithValue", "getFilesDir",
    "getAssets", "AssetManager", "PathUtils",
)

def printable_strings(data, minimum=5):
    # DEX string_data is UTF-8-ish, with zero terminators. This is a
    # reconnaissance scan, not a DEX parser or proof of call relationships.
    for match in re.finditer(rb"[\x20-\x7e]{5,}", data):
        if len(match.group()) >= minimum:
            yield match.group().decode("ascii", "replace")

def main():
    if len(sys.argv) != 2:
        raise SystemExit("Usage: python cromite_devtools_startup_audit.py APK")
    path = Path(sys.argv[1])
    if not path.is_file():
        raise SystemExit(f"APK not found: {path}")
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    sha = digest.hexdigest()
    print("APK:", path)
    print("SHA256:", sha)
    if sha != EXPECTED_APK_SHA256:
        raise SystemExit("FAIL CLOSED: APK SHA256 differs from Cromite 153 baseline")
    with zipfile.ZipFile(path) as apk:
        names = sorted(
            (name for name in apk.namelist()
             if re.fullmatch(r"classes(?:[2-9][0-9]*)?\.dex", name)),
            key=lambda n: (len(n), n),
        )
        print("DEX files:", len(names))
        for name in names:
            data = apk.read(name)
            if not data.startswith(b"dex\n"):
                print(name, "INVALID DEX MAGIC")
                continue
            matches = {}
            for value in printable_strings(data):
                low = value.lower()
                for needle in NEEDLES:
                    if needle.lower() in low:
                        matches.setdefault(needle, []).append(value[:180])
            if not matches:
                continue
            print(f"\n=== {name} ({len(data)} bytes) ===")
            for needle in NEEDLES:
                values = matches.get(needle, [])
                if values:
                    print(f"[{needle}] {len(values)} matching strings")
                    for value in values[:8]:
                        print(" ", value)
                    if len(values) > 8:
                        print("  ... truncated")
    print("\nNOTE: string presence does not establish executable hooks or method ordering.")

if __name__ == "__main__":
    main()
