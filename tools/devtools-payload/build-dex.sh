#!/usr/bin/env bash
# Compile the standalone DevTools bootstrap against Android SDK and a stub
# matching the *observed* Chromium 153 CommandLine ABI. No APK modifications.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SRC="$ROOT/tools/devtools-payload/src"
OUT="${1:-$HOME/devtools-payload-build}"
ANDROID_JAR="${ANDROID_JAR:-}"
if [[ -z "$ANDROID_JAR" ]]; then
  for candidate in "$PREFIX/share/aapt2/android.jar" "$PREFIX/share/android.jar" "$HOME/android-sdk/platforms/android-35/android.jar" "${ANDROID_HOME:-/nonexistent}/platforms/android-35/android.jar"; do
    if [[ -f "$candidate" ]]; then ANDROID_JAR="$candidate"; break; fi
  done
fi
[[ -f "$ANDROID_JAR" ]] || { echo "ERROR: define ANDROID_JAR=/ruta/android.jar"; exit 2; }
command -v javac >/dev/null || { echo "ERROR: javac no disponible"; exit 2; }
command -v d8 >/dev/null || { echo "ERROR: d8 no disponible"; exit 2; }
mkdir -p "$OUT/stubs/org/chromium/base" "$OUT/classes" "$OUT/dex"
cat > "$OUT/stubs/org/chromium/base/CommandLine.java" <<'JAVA'
package org.chromium.base;
/** Compile-only stub; never include this class in the output DEX. */
public class CommandLine {
    public static final CommandLine d = null;
    public void a(String name, String value) {}
}
JAVA
javac -source 8 -target 8 -cp "$ANDROID_JAR" -d "$OUT/classes" \
  "$OUT/stubs/org/chromium/base/CommandLine.java" \
  "$SRC/app/morphe/cromite/devtools/DevToolsBootstrap.java"
# Deliberately pass ONLY payload classes to D8. Stub must never shadow the
# target application's org.chromium.base.CommandLine.
mkdir -p "$OUT/payload-only"
cp "$OUT/classes/app/morphe/cromite/devtools/DevToolsBootstrap.class" "$OUT/payload-only/"
# javac emits no anonymous inner classes for this source; verify assumption.
if find "$OUT/classes/app/morphe/cromite/devtools" -name 'DevToolsBootstrap\$*.class' | grep -q .; then
  echo "ERROR: detectadas clases internas; adaptar empaquetado" >&2; exit 2
fi
d8 --min-api 26 --lib "$ANDROID_JAR" --output "$OUT/dex" \
  "$OUT/payload-only/DevToolsBootstrap.class"
[[ -s "$OUT/dex/classes.dex" ]] || { echo "ERROR: DEX ausente"; exit 2; }
echo "DEX_OK $OUT/dex/classes.dex"
sha256sum "$OUT/dex/classes.dex"
echo "PENDIENTE: fusionar DEX, añadir asset, inyectar invocación y firmar mediante Morphe"
