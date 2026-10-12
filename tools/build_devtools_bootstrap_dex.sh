#!/usr/bin/env bash
# Compile the standalone DevTools bootstrap to classes.dex.
# Requires JDK javac, Android SDK android.jar and d8 on PATH.
# This is a payload build only; it does not modify or sign an APK.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SOURCE="$ROOT/tools/devtools-payload/src/app/morphe/cromite/devtools/DevToolsBootstrap.java"
ANDROID_JAR="${ANDROID_JAR:?Set ANDROID_JAR to an Android SDK platform android.jar}"
OUT="${1:-$ROOT/build/devtools-payload}"
command -v javac >/dev/null
command -v d8 >/dev/null
test -f "$ANDROID_JAR"
test -f "$SOURCE"
mkdir -p "$OUT/stub/org/chromium/base" "$OUT/classes" "$OUT/dex"
cat > "$OUT/stub/org/chromium/base/CommandLine.java" <<'EOF'
package org.chromium.base;
/** Compile-time only stub. Never include in the generated DEX. */
public final class CommandLine {
    public static final CommandLine d = new CommandLine();
    public synchronized void a(String name, String value) {}
}
EOF
javac -source 8 -target 8 -encoding UTF-8 \
  -classpath "$ANDROID_JAR" -d "$OUT/classes" \
  "$OUT/stub/org/chromium/base/CommandLine.java" "$SOURCE"
# Only the bootstrap class goes to d8. The CommandLine stub MUST NOT be packaged.
d8 --min-api 26 --lib "$ANDROID_JAR" --output "$OUT/dex" \
  "$OUT/classes/app/morphe/cromite/devtools/DevToolsBootstrap.class"
test -s "$OUT/dex/classes.dex"
if command -v unzip >/dev/null; then
  if unzip -p "$OUT/dex/classes.dex" 2>/dev/null | grep -q 'compile-time only stub'; then
    echo "Unexpected stub in DEX" >&2; exit 1
  fi
fi
echo "DEX_READY $OUT/dex/classes.dex"
sha256sum "$OUT/dex/classes.dex"
echo "NOT_INJECTED: Requires a verified DEX merge and startup call-site."
