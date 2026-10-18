#!/usr/bin/env bash
set -euo pipefail

# Build only the File Manager Plus extension, not a patched APK.
# Requires Android SDK, Java 21, and Gradle credentials for Morphe registry.
./gradlew :extensions:filemanager:tasks --all --no-daemon > /tmp/filemanager-gradle-tasks.txt
if ! grep -Eq '(^|[[:space:]])(build|assembleRelease|assembleDebug)([[:space:]]|$)' /tmp/filemanager-gradle-tasks.txt; then
  echo "ERROR: extension module not registered with Gradle" >&2
  exit 1
fi
./gradlew :extensions:filemanager:build --no-daemon

mapfile -d '' mpe_files < <(find extensions/filemanager -type f -name '*.mpe' -print0)
if [ "${#mpe_files[@]}" -eq 0 ]; then
  echo "ERROR: no .mpe artifact generated under extensions/filemanager" >&2
  exit 1
fi
for file in "${mpe_files[@]}"; do
  echo "Extension artifact: $file"
  file "$file"
  sha256sum "$file"
done

echo "PASS: extension build completed and at least one .mpe artifact exists"
echo "NEXT: inspect the .mpe DEX for Lapp/morphe/extension/filemanager/WavApic;"
echo "Do not enable the runtime hook until DEX method signatures are verified."
