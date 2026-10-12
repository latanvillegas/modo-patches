#!/usr/bin/env python3
"""Stage a Cromite APK with the verified DevTools ZIP as an APK asset.

This is a packaging experiment, NOT a Morphe .mpp and NOT an installable
signed APK. It does not modify the source APK or configure the DevTools loader.
"""
import argparse
import hashlib
import os
from pathlib import Path
import tempfile
import zipfile

EXPECTED_APK = "9db12af1af021f42b4371e76d0e9fe476a7085bbbbc76bc410a538d028bf6071"
EXPECTED_FRONTEND = "dadba37f7641d133423799c722c7d5136c05d246639b83c157c5f280f08c8840"
ENTRY = "assets/devtools-cromite-153.zip"

def digest(path):
    h = hashlib.sha256()
    with path.open("rb") as src:
        for block in iter(lambda: src.read(1024 * 1024), b""):
            h.update(block)
    return h.hexdigest()

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path)
    parser.add_argument("frontend_zip", type=Path)
    parser.add_argument("output_apk", type=Path)
    args = parser.parse_args()
    if digest(args.apk) != EXPECTED_APK:
        parser.error("APK original: SHA-256 inesperado")
    if digest(args.frontend_zip) != EXPECTED_FRONTEND:
        parser.error("ZIP DevTools: SHA-256 inesperado")
    if args.output_apk.resolve() == args.apk.resolve():
        parser.error("No se permite sobrescribir el APK original")
    with zipfile.ZipFile(args.frontend_zip) as frontend:
        files = [n for n in frontend.namelist() if not n.endswith("/")]
        if len(files) != 537 or "devtools_app.html" not in files or "entrypoints/main/main.js" not in files:
            parser.error("Contenido del frontend inesperado")
        if frontend.testzip() is not None:
            parser.error("CRC del frontend inválido")
    with zipfile.ZipFile(args.apk) as source:
        if ENTRY in source.namelist():
            parser.error("El APK ya contiene el asset")
    args.output_apk.parent.mkdir(parents=True, exist_ok=True)
    fd, temp = tempfile.mkstemp(prefix=".cromite-stage-", suffix=".apk", dir=args.output_apk.parent)
    os.close(fd)
    try:
        with zipfile.ZipFile(args.apk) as source, zipfile.ZipFile(temp, "w", allowZip64=True) as output:
            for info in source.infolist():
                with source.open(info) as stream, output.open(info, "w", force_zip64=info.file_size > 0xffffffff) as target:
                    for block in iter(lambda: stream.read(1024 * 1024), b""):
                        target.write(block)
            with args.frontend_zip.open("rb") as src, output.open(
                zipfile.ZipInfo(ENTRY), "w"
            ) as dst:
                for block in iter(lambda: src.read(1024 * 1024), b""):
                    dst.write(block)
        with zipfile.ZipFile(temp) as staged:
            assert staged.getinfo(ENTRY).file_size == args.frontend_zip.stat().st_size
            assert hashlib.sha256(staged.read(ENTRY)).hexdigest() == EXPECTED_FRONTEND
        os.replace(temp, args.output_apk)
    finally:
        if os.path.exists(temp):
            os.unlink(temp)
    print("STAGING_OK", args.output_apk)
    print("SHA256_APK_EXPERIMENTAL", digest(args.output_apk))
    print("ASSET", ENTRY)
    print("NOTA: APK no firmado, no instalar; DevTools no se redirige automáticamente")

if __name__ == "__main__":
    main()
