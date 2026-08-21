#!/usr/bin/env bash
# Build + sign + verify the PRODUCTION release APKs (Android SDK required).
# Prereqs: per-app keystores generated (scripts/generate-dodeal-keystore.sh) and keystore.properties
# filled with tracking.* and updater.* entries.
set -euo pipefail
cd "$(dirname "$0")/.."

if [ ! -f keystore.properties ] && [ -z "${DODEAL_TRACKING_STORE_FILE:-}" ]; then
  echo "ERROR: no keystore.properties and no DODEAL_TRACKING_STORE_FILE env."
  echo "Generate per-app keys, then copy keystore.properties.template -> keystore.properties."
  exit 1
fi

echo "== Building RELEASE variants =="
./gradlew clean :app:assembleRelease :updater:assembleRelease

APK_TRACK=$(ls -1 app/build/outputs/apk/release/*-release.apk | head -1)
APK_UPD=$(ls -1 updater/build/outputs/apk/release/*-release.apk | head -1)

APKSIGNER="$(command -v apksigner || true)"
if [ -z "$APKSIGNER" ] && [ -n "${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}" ]; then
  SDK="${ANDROID_HOME:-$ANDROID_SDK_ROOT}"
  APKSIGNER="$(ls -1 "$SDK"/build-tools/*/apksigner 2>/dev/null | sort -V | tail -1 || true)"
fi
[ -z "$APKSIGNER" ] && { echo "apksigner not found. Set ANDROID_HOME."; exit 1; }

check() {
  local apk="$1" label="$2"
  echo; echo "== $label :: apksigner verify --print-certs =="
  "$APKSIGNER" verify --verbose --print-certs "$apk"
  if "$APKSIGNER" verify --print-certs "$apk" | grep -qi "CN=Android Debug"; then
    echo "FAIL: $apk is signed with the Android Debug key."; exit 1
  fi
  local sha
  sha=$("$APKSIGNER" verify --print-certs "$apk" | awk -F': ' '/SHA-256 digest/{print $2; exit}')
  echo "OK (not debug). $label cert SHA-256 to pin: $sha"
}

check "$APK_TRACK" "com.dodeal.deviceagent (Tracking)"
check "$APK_UPD"   "com.dodeal.updater (Updater)"

echo
echo "Pin the TRACKING SHA-256 (and later ACR/Dialer) in:"
echo "  updater/.../security/ApprovedCertificates.kt -> APPROVED_PACKAGES"
echo "The Updater APK itself is signed with its own separate key (not pinned there — it is the installer)."
