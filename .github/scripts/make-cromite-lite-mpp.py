#!/usr/bin/env python3
"""Create a Cromite-only Morphe bundle and regenerate DEX from retained classes.

The main patch module still compiles its Kotlin sources; this script reduces
what the Android manager downloads and scans. Fail if expected inputs vanish.
"""
import io
import os
import pathlib
import subprocess
import sys
import tempfile
import zipfile

source, target = map(pathlib.Path, sys.argv[1:3])
keep_class_prefixes = (
    "app/morphe/patches/cromite/",
    "app/morphe/util/",
)
with zipfile.ZipFile(source) as archive:
    names = archive.namelist()
    classes = [n for n in names if n.endswith(".class") and n.startswith(keep_class_prefixes)]
    if not any("PureAmoledWebPatchKt.class" in n for n in classes):
        raise SystemExit("Missing Cromite web patch class")
    if "extensions/cromite.mpe" not in names:
        raise SystemExit("Missing Cromite runtime extension")
    with tempfile.TemporaryDirectory() as temp:
        root = pathlib.Path(temp)
        jar = root / "cromite-classes.jar"
        with zipfile.ZipFile(jar, "w", zipfile.ZIP_DEFLATED) as output:
            for name in classes:
                output.writestr(name, archive.read(name))
        dexdir = root / "dex"
        dexdir.mkdir()
        sdk = pathlib.Path(os.environ["ANDROID_HOME"])
        d8 = sdk / "build-tools/36.0.0/d8"
        subprocess.run([
            str(d8), "--min-api", "26", "--lib",
            str(sdk / "platforms/android-36/android.jar"),
            "--output", str(dexdir), str(jar),
        ], check=True)
        dex_files = sorted(dexdir.glob("classes*.dex"))
        if not dex_files:
            raise SystemExit("D8 produced no Cromite DEX")
        with zipfile.ZipFile(target, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as output:
            for item in archive.infolist():
                name = item.filename
                if name.endswith(".class") and name not in classes:
                    continue
                if name.startswith("classes") and name.endswith(".dex"):
                    continue
                # Keep Morphe bundle descriptors and the Cromite extension.
                # Exclude unrelated extension payloads to avoid unnecessary downloads.
                if name.startswith("extensions/") and name != "extensions/cromite.mpe":
                    continue
                output.writestr(item, archive.read(name))
            for dex in dex_files:
                output.write(dex, dex.name)
print(f"Cromite Lite: {len(classes)} classes, {len(dex_files)} DEX; {target.stat().st_size} bytes")
