#!/usr/bin/env bash
# Print the documented signing facts: alias, cert SHA-256, SHA-1, signature algorithm.
# Usage:
#   scripts/print-signing-info.sh <keystore.jks> <alias>        # from the keystore
#   scripts/print-signing-info.sh --apk <path/to/app.apk>       # from a built APK (needs apksigner)
set -euo pipefail

if [ "${1:-}" = "--apk" ]; then
  APK="${2:?apk path required}"
  echo "== apksigner verify --print-certs =="
  apksigner verify --verbose --print-certs "$APK"
  exit 0
fi

KEYSTORE="${1:?keystore path required}"
ALIAS="${2:?alias required}"
echo "== keytool -list (alias=$ALIAS) =="
keytool -list -v -keystore "$KEYSTORE" -alias "$ALIAS" \
  | grep -Ei "Alias name|Signature algorithm|SHA1:|SHA256:|Valid from"
echo
echo "Copy the SHA256 (lowercase, no colons) into:"
echo "  updater/src/main/java/com/dodeal/updater/security/PackageVerifier.kt"
echo "  -> const val APPROVED_CERT_SHA256"
echo
echo "Convert to the format the Updater expects (lowercase, no colons):"
keytool -list -v -keystore "$KEYSTORE" -alias "$ALIAS" \
  | awk -F'SHA256: ' '/SHA256:/{print $2}' | tr -d ': ' | tr 'A-F' 'a-f'
