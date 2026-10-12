#!/usr/bin/env python3
"""Stage verified Cromite DevTools binary resources into a local repository checkout.

Run on the Termux device that holds the previously verified experimental APK.
Does not modify the APK, invoke Gradle, install apps, or push Git commits.
"""
import hashlib
import pathlib
import shutil
import sys
import zipfile

FRONTEND_SHA = "dadba37f7641d133423799c722c7d5136c05d246639b83c157c5f280f08c8840"
BOOTSTRAP_SHA = "32166284b26f08725e35b1c9f193a77469bb5e69869adde105f600b654518c50"
FRONTEND_NAME = "cromite/devtools/devtools-cromite-153.zip"
BOOTSTRAP_NAME = "cromite/devtools/bootstrap.dex"


def digest(data):
    return hashlib.sha256(data).hexdigest()


def main():
    if len(sys.argv) != 3:
        raise SystemExit("Usage: python tools/stage_devtools_bundle.py /path/to/repo /path/to/experimental.apk")
    repo = pathlib.Path(sys.argv[1]).expanduser().resolve()
    apk = pathlib.Path(sys.argv[2]).expanduser().resolve()
    if not (repo / "patches/build.gradle.kts").is_file():
        raise SystemExit("Invalid repository directory")
    if not (repo / "patches/src/main/kotlin/app/morphe/patches/cromite/devtools/DevToolsCopyPatch.kt").is_file():
        raise SystemExit("DevToolsCopyPatch.kt missing; checkout feature/cromite-devtools-copy first")
    with zipfile.ZipFile(apk) as z:
        frontend = z.read("assets/devtools-cromite-153.zip")
        bootstrap = z.read("classes3.dex")
    if digest(frontend) != FRONTEND_SHA:
        raise SystemExit("Frontend ZIP SHA-256 mismatch; no files written")
    if digest(bootstrap) != BOOTSTRAP_SHA:
        raise SystemExit("Bootstrap DEX SHA-256 mismatch; no files written")
    if not bootstrap.startswith(b"dex\n"):
        raise SystemExit("Invalid DEX header")
    with zipfile.ZipFile(__import__("io").BytesIO(frontend)) as z:
        if len(z.namelist()) != 537 or z.testzip() is not None:
            raise SystemExit("Invalid frontend ZIP")
    destination = repo / "patches/src/main/resources"
    for name, data in ((FRONTEND_NAME, frontend), (BOOTSTRAP_NAME, bootstrap)):
        path = destination / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(data)
        print(f"STAGED {path.relative_to(repo)} size={len(data)} sha256={digest(data)}")
    print("PASS: binary resources staged; Kotlin compilation and Morphe runtime remain unverified")


if __name__ == "__main__":
    main()
