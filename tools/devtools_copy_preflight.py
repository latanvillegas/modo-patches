#!/usr/bin/env python3
"""Read-only preflight for the proven Cromite 153 DevTools frontend payload.

Usage:
  python3 tools/devtools_copy_preflight.py \
    ~/amoled-test/devtools-audit/frontend-completo \
    ~/amoled-test/devtools-audit/main-global-copy-v4-native.js
"""
from __future__ import annotations

import hashlib
import json
import sys
from pathlib import Path

EXPECTED_MAIN_SHA256 = "17db0138b7c9892f7e7849f0d007b8643aa9cefb6ba549d95a9cda33d5e2a9ed"
REQUIRED = (
    "devtools_app.html",
    "entrypoints/main/main.js",
    "core/i18n/locales/en-US.json",
    "core/i18n/locales/es.json",
)


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def main(argv: list[str]) -> int:
    if len(argv) != 3:
        print("Usage: devtools_copy_preflight.py FRONTEND_DIR WORKING_MAIN_JS", file=sys.stderr)
        return 2
    root = Path(argv[1]).expanduser().resolve()
    working = Path(argv[2]).expanduser().resolve()
    errors: list[str] = []
    if not root.is_dir():
        errors.append(f"Frontend directory missing: {root}")
    if not working.is_file():
        errors.append(f"Working main.js missing: {working}")

    report: dict = {"frontend": str(root), "working_main": str(working), "files": {}}
    if root.is_dir():
        for relative in REQUIRED:
            path = root / relative
            if not path.is_file():
                errors.append(f"Required frontend file missing: {relative}")
            else:
                report["files"][relative] = {"bytes": path.stat().st_size, "sha256": sha256(path)}
        all_files = [p for p in root.rglob("*") if p.is_file()]
        report["file_count"] = len(all_files)
        report["total_bytes"] = sum(p.stat().st_size for p in all_files)
        if not all_files:
            errors.append("Frontend directory is empty")
    if working.is_file():
        actual = sha256(working)
        report["working_main_sha256"] = actual
        if actual != EXPECTED_MAIN_SHA256:
            errors.append("Working main.js SHA-256 differs from verified baseline")
        bundled_main = root / "entrypoints/main/main.js"
        if bundled_main.is_file():
            report["bundled_main_matches_working"] = sha256(bundled_main) == actual
            if not report["bundled_main_matches_working"]:
                errors.append("Frontend main.js does not match verified working main.js")

    report["errors"] = errors
    report["status"] = "PASS" if not errors else "FAIL"
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return 0 if not errors else 1


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
