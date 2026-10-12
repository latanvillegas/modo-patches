#!/usr/bin/env python3
"""Read-only structural preflight for experimental Cromite 153 DevTools APKs.

This does NOT establish runtime correctness or Morphe .mpp compatibility.
Usage: python tools/audit_devtools_apk.py /path/to/experimental.apk
"""
import argparse
import hashlib
import struct
import sys
import zipfile

EXPECTED_FRONTEND_SHA256 = "dadba37f7641d133423799c722c7d5136c05d246639b83c157c5f280f08c8840"
EXPECTED_BOOTSTRAP_DEX_SHA256 = "32166284b26f08725e35b1c9f193a77469bb5e69869adde105f600b654518c50"
BOOTSTRAP_DESCRIPTOR = b"Lapp/morphe/cromite/devtools/DevToolsBootstrap;"
HOOK_REFERENCE = b"initializeSafely"
REQUIRED = ("classes.dex", "classes2.dex", "classes3.dex", "assets/devtools-cromite-153.zip")


def sha256(data):
    return hashlib.sha256(data).hexdigest()


def dex_counts(data):
    if len(data) < 0x70 or not data.startswith(b"dex\n"):
        raise ValueError("Invalid DEX header")
    return struct.unpack_from("<I", data, 0x58)[0], struct.unpack_from("<I", data, 0x60)[0]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", help="Experimental APK to inspect without modifying it")
    args = parser.parse_args()
    errors = []
    with zipfile.ZipFile(args.apk) as apk:
        names = set(apk.namelist())
        for name in REQUIRED:
            if name not in names:
                errors.append("Missing " + name)
        if errors:
            for error in errors:
                print("FAIL:", error)
            return 1

        for name in REQUIRED[:3]:
            data = apk.read(name)
            methods, classes = dex_counts(data)
            print(f"{name}: methods={methods}, classes={classes}, sha256={sha256(data)}")
            if methods > 65536:
                errors.append(f"{name} exceeds DEX method_id limit")
            if name == "classes.dex" and HOOK_REFERENCE not in data:
                errors.append("Primary DEX has no initializeSafely string reference")
            if name == "classes3.dex":
                if sha256(data) != EXPECTED_BOOTSTRAP_DEX_SHA256:
                    errors.append("Bootstrap DEX hash differs from verified payload")
                if BOOTSTRAP_DESCRIPTOR not in data:
                    errors.append("Bootstrap class descriptor missing")

        frontend = apk.read(REQUIRED[3])
        print(f"Frontend ZIP: bytes={len(frontend)}, sha256={sha256(frontend)}")
        if sha256(frontend) != EXPECTED_FRONTEND_SHA256:
            errors.append("Frontend ZIP hash mismatch")
        with zipfile.ZipFile(__import__("io").BytesIO(frontend)) as frontend_zip:
            files = frontend_zip.namelist()
            print("Frontend entries:", len(files))
            if len(files) != 537:
                errors.append("Unexpected frontend entry count")
            for name in ("devtools_app.html", "entrypoints/main/main.js"):
                if name not in files:
                    errors.append("Frontend missing " + name)
            bad = frontend_zip.testzip()
            if bad:
                errors.append("Corrupt frontend member: " + bad)

        bad_apk = apk.testzip()
        if bad_apk:
            errors.append("Corrupt APK member: " + bad_apk)

    for error in errors:
        print("FAIL:", error)
    if errors:
        return 1
    print("PASS: structural preflight only; runtime, startup order and signing unverified")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (OSError, ValueError, zipfile.BadZipFile) as exc:
        print("FAIL:", exc, file=sys.stderr)
        sys.exit(1)
