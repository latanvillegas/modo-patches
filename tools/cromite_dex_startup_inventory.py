#!/usr/bin/env python3
"""Read-only Cromite DEX startup inventory. No third-party dependencies."""
from pathlib import Path
import hashlib
import re
import sys
import zipfile

APK = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("/sdcard/Download/cromite-release/arm64_ChromePublic.apk")
NEEDLES = [
    "CommandLine", "LibraryLoader", "BrowserStartupController",
    "ChromeBrowserInitializer", "ChromeApplicationImpl", "ChromeApplication",
    "NativeInitialization", "ContentMain", "AwBrowserProcess",
    "custom-devtools-frontend", "devtools", "initializeNative",
    "startBrowserProcesses", "initCommandLine",
]
EXPECTED = "9db12af1af021f42b4371e76d0e9fe476a7085bbbbc76bc410a538d028bf6071"

def dex_strings(data):
    # DEX string_data_item uses ULEB128 length and MUTF-8. The scan is a
    # candidate inventory, NOT a full DEX parser or proof of call ordering.
    pattern = rb"[A-Za-z0-9_/$;<>.()\[\]-]{5,220}"
    for m in re.finditer(pattern, data):
        yield m.start(), m.group().decode("ascii", errors="replace")

if not APK.is_file():
    sys.exit(f"ERROR: APK no encontrado: {APK}")
h = hashlib.sha256()
with APK.open("rb") as f:
    for block in iter(lambda: f.read(1024 * 1024), b""):
        h.update(block)
digest = h.hexdigest()
print("APK:", APK)
print("SHA256:", digest)
if digest != EXPECTED:
    sys.exit("ERROR: hash distinto al APK estable; análisis cancelado")
with zipfile.ZipFile(APK) as z:
    names = sorted((n for n in z.namelist() if re.fullmatch(r"classes\d*\.dex", n)),
                   key=lambda n: (len(n), n))
    print("DEX encontrados:", len(names))
    for name in names:
        data = z.read(name)
        print(f"\n=== {name} ({len(data)} bytes) ===")
        hits = {}
        for offset, string in dex_strings(data):
            for needle in NEEDLES:
                if needle.lower() in string.lower():
                    hits.setdefault(needle, []).append((offset, string))
        for needle, matches in hits.items():
            print(f"[{needle}] {len(matches)} coincidencias; ejemplos:")
            for offset, string in matches[:8]:
                print(f"  0x{offset:x}: {string[:180]}")
print("\nNOTA: coincidencias no identifican llamadas ni su orden.")
