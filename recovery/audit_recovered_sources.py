#!/usr/bin/env python3
"""Offline, zero-build audit of reconstructed Cromite Kotlin patches.

Checks local source tree against original .class names in patches-1.50.0.mpp.
This is a source-inventory audit, NOT a Kotlin compilation or APK validation.
Usage: python3 recovery/audit_recovered_sources.py /path/to/patches-1.50.0.mpp
"""
import sys
from pathlib import Path
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "patches/src/main/kotlin"
PACKAGE = "app/morphe/patches/cromite/"
MARKERS = {
    "absolute XML path": "document(file.absolutePath)",
    "unscoped exception swallowing": "runCatching {",
    "unverified native injection": "toInstructions()",
}

def audit(bundle: Path) -> int:
    with ZipFile(bundle) as archive:
        classes = sorted(
            name for name in archive.namelist()
            if name.startswith(PACKAGE)
            and name.endswith("PatchKt.class")
            and "$" not in name
        )
    missing = []
    warnings = []
    print(f"Original compiled patch classes: {len(classes)}")
    for class_path in classes:
        source = SRC / class_path.removesuffix("Kt.class").replace("Patch", "Patch") 
        # Kotlin top-level files compile to FileNameKt.class.
        source = SRC / class_path.removesuffix("Kt.class")
        source = source.with_suffix(".kt")
        if not source.exists():
            missing.append(class_path)
            print(f"MISSING {source.relative_to(ROOT)}")
            continue
        data = source.read_text(encoding="utf-8")
        for label, marker in MARKERS.items():
            if marker in data:
                warnings.append((str(source.relative_to(ROOT)), label))
        print(f"PRESENT {source.relative_to(ROOT)}")
    print(f"Source files present: {len(classes) - len(missing)}/{len(classes)}")
    for file, label in warnings:
        print(f"REVIEW {file}: {label}")
    print("NOT VERIFIED: Kotlin compilation, option registration, DEX matching, runtime behavior.")
    return 1 if missing else 0

if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("Usage: audit_recovered_sources.py patches-1.50.0.mpp")
    raise SystemExit(audit(Path(sys.argv[1])))
