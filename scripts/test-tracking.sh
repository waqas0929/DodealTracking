#!/usr/bin/env bash
#
# One command that re-runs everything for the Dodeal Tracking agent.
#
#   ./scripts/test-tracking.sh           # unit tests + debug compile
#   ./scripts/test-tracking.sh --apk     # also assemble the debug APK
#
# The unit suite runs on the JVM: no emulator, no device, no network. A suite
# that needs a handset plugged in is a suite nobody runs.
#
# JAVA_HOME is resolved, never assumed: AGP 8.7 does not accept the JDK 23 that
# is often first on PATH, and Android Studio ships a JDK 21 that always works.
set -euo pipefail

cd "$(dirname "$0")/.."

if [[ -z "${JAVA_HOME:-}" ]] || ! "${JAVA_HOME}/bin/java" -version 2>&1 | grep -qE '"(17|21)\.'; then
    for candidate in \
        "/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
        "$(/usr/libexec/java_home -v 21 2>/dev/null || true)" \
        "$(/usr/libexec/java_home -v 17 2>/dev/null || true)"
    do
        if [[ -n "$candidate" && -x "$candidate/bin/java" ]]; then
            export JAVA_HOME="$candidate"
            break
        fi
    done
fi
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"

echo "▸ JDK: $("${JAVA_HOME}/bin/java" -version 2>&1 | head -1)"

# `sh gradlew` rather than ./gradlew: the source arrives as a download, and macOS
# quarantines it, which makes the executable bit unusable until xattr is cleared.
echo "▸ Unit tests"
sh gradlew :app:testDebugUnitTest "$@" --console=plain | tail -5

echo "▸ Compile check (main + test sources)"
sh gradlew :app:compileDebugKotlin :app:compileDebugUnitTestKotlin --console=plain -q

if [[ "${1:-}" == "--apk" ]]; then
    echo "▸ Debug APK"
    sh gradlew :app:assembleDebug --console=plain -q
    echo "  app/build/outputs/apk/debug/app-debug.apk"
fi

python3 - <<'PY'
import glob, xml.etree.ElementTree as ET
total = failed = 0
for path in glob.glob('app/build/test-results/testDebugUnitTest/*.xml'):
    root = ET.parse(path).getroot()
    total += int(root.get('tests'))
    failed += int(root.get('failures')) + int(root.get('errors'))
print(f"\n── {total} tests, {failed} failed ──")
raise SystemExit(1 if failed else 0)
PY
