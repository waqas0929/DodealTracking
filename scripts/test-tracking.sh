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
# Our own flags must never reach Gradle — passing --apk straight through made
# Gradle fail on an unknown option, which looked exactly like a broken build.
WANT_APK=0
GRADLE_ARGS=()
for arg in "$@"; do
    if [[ "$arg" == "--apk" ]]; then WANT_APK=1; else GRADLE_ARGS+=("$arg"); fi
done

echo "▸ Unit tests"
sh gradlew :app:testDebugUnitTest ${GRADLE_ARGS[@]+"${GRADLE_ARGS[@]}"} --console=plain | tail -5

echo "▸ Compile check (main + test sources)"
sh gradlew :app:compileDebugKotlin :app:compileDebugUnitTestKotlin --console=plain -q

if [[ "$WANT_APK" == "1" ]]; then
    echo "▸ Debug APK"
    sh gradlew :app:assembleDebug --console=plain -q
    # The project sets archivesName, so the file is NOT app-debug.apk. Printing a
    # path that does not exist is how a green run still leaves you with nothing.
    apk="$(find app/build/outputs/apk/debug -name '*.apk' -maxdepth 1 | head -1)"
    if [[ -z "$apk" ]]; then
        echo "  ✗ no APK produced"
        exit 1
    fi
    echo "  $apk ($(du -h "$apk" | cut -f1))"
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
